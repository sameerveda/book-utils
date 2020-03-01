package sam.books;

import static sam.books.BookStatus.NONE;
import static sam.books.BooksDBMinimal.BACKUP_FOLDER;
import static sam.books.BooksDBMinimal.DB_PATH;
import static sam.books.BooksDBMinimal.ROOT;
import static sam.books.BooksMeta.AUTHOR;
import static sam.books.BooksMeta.BOOK_ID;
import static sam.books.BooksMeta.BOOK_TABLE_NAME;
import static sam.books.BooksMeta.CREATED_ON;
import static sam.books.BooksMeta.DESCRIPTION;
import static sam.books.BooksMeta.FILE_NAME;
import static sam.books.BooksMeta.ISBN;
import static sam.books.BooksMeta.MARKER;
import static sam.books.BooksMeta.NAME;
import static sam.books.BooksMeta.PAGE_COUNT;
import static sam.books.BooksMeta.PATH;
import static sam.books.BooksMeta.PATH_ID;
import static sam.books.BooksMeta.PATH_TABLE_NAME;
import static sam.books.BooksMeta.STATUS;
import static sam.books.BooksMeta.URL;
import static sam.books.BooksMeta.YEAR;
import static sam.console.ANSI.createBanner;
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;
import static sam.sql.querymaker.QueryMaker.qm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sam.books.walker.Dir;
import sam.books.walker.Dir.BookFile;
import sam.books.walker.Walker;
import sam.collection.IntSet;
import sam.config.MyConfig;
import sam.console.ANSI;
import sam.myutils.Checker;
import sam.myutils.System2;
import sam.sql.JDBCHelper;
import sam.sql.querymaker.InserterBatch;
import sam.string.StringBuilder2;
import sam.swing.SwingUtils;
import sam.tsv.Tsv;

public class UpdateDB implements Callable<Boolean> {
    private Logger logger = LoggerFactory.getLogger(UpdateDB.class);
    public static final Path SELF_DIR = Paths.get(System2.lookup("SELF_DIR"));

    private static class DeletedDir {
        final int id;
        final Path dir;

        public DeletedDir(int id, Path dir) {
            this.id = id;
            this.dir = dir;
        }
    }

    @Override
    public Boolean call() throws Exception {
        logger.info("{}{}", yellow("WORKING_DIR: "), ROOT);

        logger.info(createBanner("Updating " + DB_PATH) + "\n");

        List<Dir> dirsList = new Walker().walk(SELF_DIR, ROOT);
        Map<Path, Dir> dirs = new HashMap<>();

        dirsList.forEach(d -> {
            if (toBookStatus(d) == NONE)
                dirs.put(d.subpath(), d);
        });

        if (dirs.isEmpty() || !check_duplicates(dirsList))
            throw new IllegalStateException("no dirs found in: " + ROOT);

        boolean modified = false;

        try (BooksDBMinimal db = new BooksDBMinimal()) {
            ArrayList<DeletedDir> deleted = new ArrayList<>();
            db.iterate("SELECT * FROM ".concat(PATH_TABLE_NAME), rs -> {
                int id = rs.getInt(PATH_ID);
                Path p = Paths.get(rs.getString(PATH));
                Dir dir = dirs.get(p);

                if (dir == null)
                    deleted.add(new DeletedDir(id, p));
                else  {
                    if(dir.path_id() >= 0)
                        throw new IllegalStateException();
                    
                    dir.path_id(id);
                    dir.forEachDir(d -> {
                        if(toBookStatus(d) != NONE)
                            d.path_id(id);
                    });
                }
            });

            // checking for dirs not in database
            modified = newDirs(db, dirs) || modified;

            // TODO
            // extra dirs in db
            modified = processDeletedDir(deleted, db) || modified;

            Map<String, BookFile> bookFiles = new HashMap<>();
            forEachBook(dirsList, f -> bookFiles.put(f.name, f));

            // {book_id, file_pame, path_id}
            ArrayList<BookDB> dbBooksData = new ArrayList<>(bookFiles.size() + 10);
            List<BookDB> extras = new ArrayList<>();

            db.iterate(BookDB.SELECT_SQL, rs -> {
                BookDB b = new BookDB(rs);
                BookFile f = bookFiles.get(b.file_name);

                if (f == null) {
                    extras.add(b);
                } else {
                    f.setFound(true);
                    b.file(f);
                    dbBooksData.add(b);
                }
            });

            // delete extra books
            modified = deleteExtraBooks(extras, dirs, deleted, db) || modified;

            // respect path changes
            modified = lookForPathChanges(db, dbBooksData, bookFiles, dirs) || modified;

            List<NewBook> newBooks = bookFiles.values().stream()
                    .filter(f -> !f.isFound())
                    .map(f -> new NewBook(f))
                    .collect(Collectors.toList());

         // TODO

            // list non-listed books
            modified = processNewBooks(newBooks, db) || modified;
            db.commit();
        } catch (SQLException e1) {
            if ("user refused to proceed".equals(e1.getMessage())) {
                logger.info(red("user refused to proceed"));
                return false;
            }
            e1.printStackTrace();
        }
        logger.info(ANSI.FINISHED_BANNER + "\n\n\n");

        if (modified) {
            Path t = BACKUP_FOLDER.resolve(DB_PATH.getFileName() + "_" + LocalDate.now().getDayOfWeek().getValue());
            Files.createDirectories(t.getParent());
            Files.copy(DB_PATH, t, StandardCopyOption.REPLACE_EXISTING);
        }

        return modified;
    }

    private boolean check_duplicates(List<Dir> dirs) {
        Map<String, TempList<BookFile>> map = new HashMap<>();
        Function<String, TempList<BookFile>> computer = s -> new TempList<>();
        forEachBook(dirs, f -> map.computeIfAbsent(f.name, computer).add(f));
        map.values().removeIf(v -> v.size() < 2);

        if (!map.isEmpty()) {
            StringBuilder2 sb = new StringBuilder2();
            sb.append(createBanner("repeated books")).ln();

            map.forEach((s, t) -> {
                sb.yellow(s).ln();
                t.multiple.forEach(k -> sb.append("  ").append(k.subpath()).ln());
            });

            sb.red("\n\ndb update skipped\n");
            sb.append(separator()).ln();
            logger.info(sb.toString());

            return false;
        }

        return true;
    }

    private void forEachBook(List<Dir> dirs, Consumer<BookFile> consumer) {
        dirs.forEach(d -> d.forEachFiles(consumer));
    }

    private static class TempList<E> {
        E single;
        List<E> multiple;

        public void add(E e) {
            if (single == null)
                single = e;
            else {
                if (multiple == null) {
                    multiple = new ArrayList<>();
                    multiple.add(single);
                }
                multiple.add(e);
            }
        }

        int size() {
            if (multiple != null)
                return multiple.size();

            return single == null ? 0 : 1;
        }
    }

    private boolean processNewBooks(List<NewBook> newBooks, BooksDBMinimal db) throws SQLException {
        if (Checker.isEmpty(newBooks))
            return false;

        Map<Dir, List<NewBook>> map = newBooks.stream()
                .collect(Collectors.groupingBy(d -> dir(d.path().parent()), IdentityHashMap::new, Collectors.toList()));

        StringBuilder2 sb = new StringBuilder2();
        sb.green("new books : (" + newBooks.size() + ")\n");

        map.forEach((dir, books) -> {
            sb.append(" ").cyan(dir.path_id()).append(" : ").yellow(dir.subpath()).ln();
            books.forEach(f -> sb.append("  ").append(f.path().subpath()).ln());
            sb.ln();
        });

        sb.ln();

        List<NewBook> books;

        try {
            books = ExtractorDialog.get(newBooks);
        } catch (Throwable e) {
            SwingUtils.showErrorDialog("failed to init AboutBookExtractor", e);
            return false;
        }

        if (Checker.isEmpty(books))
            return false;

        Tsv tsv = new Tsv(BOOK_ID, NAME, FILE_NAME, AUTHOR, ISBN, PAGE_COUNT, YEAR);

        int max = db.findFirst("select seq from sqlite_sequence where name='" + BOOK_TABLE_NAME + "'",
                rs -> rs.getInt(1)) + 1;

        for (NewBook b : books) {
            b.id = max++;
            tsv.addRow(String.valueOf(b.id), b.name, b.path().name, b.author, b.isbn, String.valueOf(b.page_count),
                    b.year);
        }

        InserterBatch<NewBook> insert = new InserterBatch<>(BOOK_TABLE_NAME);
        insert.setInt(BOOK_ID, b -> b.id);

        String[] columns = { FILE_NAME, NAME, AUTHOR, PATH_ID, ISBN, PAGE_COUNT, YEAR, DESCRIPTION, URL };

        for (String n : columns)
            insert.setString(n, b -> b.get(n));

        long time = System.currentTimeMillis();
        insert.setLong(CREATED_ON, b -> time);

        sb.yellow("\nexecutes: ").append(insert.execute(db, books)).ln();

        Path p = Paths.get(MyConfig.COMMONS_DIR,
                "new-books-" + LocalDateTime.now().toString().replace(':', '_') + ".tsv");
        try {
            tsv.save(p);
            sb.yellow("created: ").append(p).ln();
        } catch (IOException e2) {
            sb.red("failed to save: ").append(p).append(", error: ").append(e2).ln();
        }

        sb.append(separator());
        logger.info(sb.toString());
        return true;
    }

    private boolean newDirs(BooksDBMinimal db, Map<Path, Dir> dirs) throws SQLException {
        int[] max = { 0 };

        List<Dir> missings = dirs.values().stream().peek(w -> max[0] = Math.max(max[0], w.path_id()))
                .filter(w -> w.path_id() < 0).collect(Collectors.toList());

        if (!missings.isEmpty()) {
            logger.info(yellow("new folders"));
            String format = "%-5s%s\n";

            try (PreparedStatement ps = db
                    .prepareStatement(JDBCHelper.insertSQL(PATH_TABLE_NAME, PATH_ID, PATH, MARKER))) {
                StringBuilder2 sb = new StringBuilder2();

                for (Dir w : missings) {
                    int id = ++max[0];
                    String str = w.subpath().toString();
                    sb.format(format, id, str);
                    w.path_id(id);

                    ps.setInt(1, id);
                    ps.setString(2, str);
                    ps.setString(3, w.name);

                    ps.addBatch();
                }

                sb.yellow("\nexecutes: ").append(ps.executeBatch().length).append(separator());

                logger.info(sb.ln().toString());
            }
            return true;
        }
        return false;
    }

    private boolean lookForPathChanges(BooksDBMinimal db, List<BookDB> dbBooksData, Map<String, BookFile> bookFiles,
            Map<Path, Dir> dirs) throws SQLException {
        
        db.prepareStatementBlock(
                qm().update(BOOK_TABLE_NAME).placeholders(STATUS).where(w -> w.eqPlaceholder(BOOK_ID)).build(), ps -> {
                    int n = 0;
                    for (BookDB b : dbBooksData) {
                        BookStatus s = toBookStatus(b.file().parent());

                        if (b.status != s) {
                            if (n == 0)
                                logger.info(ANSI.yellow("STATUS CHANGES"));
                            logger.info(b.file_name + "\t" + b.status + " -> " + s);
                            n++;
                            ps.setString(1, s == null ? null : s.toString());
                            ps.setInt(2, b.id);
                            ps.addBatch();
                        }
                    }
                    if (n != 0)
                        logger.info("status change: " + ps.executeBatch().length + "\n");
                    return null;
                });

        ArrayList<BookDB> changed = new ArrayList<>();

        for (BookDB o : dbBooksData) {
            Dir idNew = bookFiles.get(o.file_name).parent();
            if (toBookStatus(idNew) != NONE)
                idNew = idNew.parent;

            if (toBookStatus(idNew) != NONE)
                throw new IllegalStateException();

            if (idNew.path_id() != o.path_id) {
                o.setNewPathId(idNew.path_id());
                changed.add(o);
            }
        }
        if (!changed.isEmpty()) {
            StringBuilder2 sb = new StringBuilder2();
            sb.red("changed books paths (" + changed.size() + ")\n");
            String format = "%-5s%-16s%s\n";
            sb.append(yellow(String.format(format, "id", "path change", "file_name")));
            String arrow = ANSI.cyan(" -> ");
            for (BookDB o : changed)
                sb.format(format, o.id, o.path_id + arrow + o.getNewPathId(), o.file_name);

            sb.ln();

            String format2 = "%-8s%s\n";
            sb.ln().yellow(String.format(format2, "path_id", "path"));

            BitSet set = new BitSet();

            changed.stream().forEach(o -> {
                set.set(o.path_id);
                set.set(o.getNewPathId());
            });

            dirs.forEach((p, dir) -> {
                if (dir.path_id() >= 0 && set.get(dir.path_id()))
                    sb.format(format2, dir.path_id(), p);
            });

            db.prepareStatementBlock(
                    qm().update(BOOK_TABLE_NAME).placeholders(PATH_ID).where(w -> w.eqPlaceholder(BOOK_ID)).build(),
                    ps -> {
                        for (BookDB o : changed) {
                            ps.setInt(1, o.getNewPathId());
                            ps.setInt(2, o.id);
                            ps.addBatch();
                        }
                        sb.yellow("\nexecutes: ").append(ps.executeBatch().length).ln();
                        return null;
                    });

            logger.info(sb.append(separator()).toString());
            return true;
        }
        return false;
    }

    public static BookStatus toBookStatus(Dir d) {
        return BooksDBMinimal.toBookStatus(d.name);
    }

    private boolean deleteExtraBooks(List<BookDB> extras, Map<Path, Dir> dirs, List<DeletedDir> deletedDirs,
            BooksDBMinimal db) throws SQLException {
        if (Checker.isEmpty(extras))
            return false;

        IntSet set = new IntSet();
        extras.forEach(e -> set.add(e.path_id));

        Map<Integer, Path> parents = new HashMap<>();
        dirs.forEach((p, d) -> {
            int id = dir(d).path_id();
            
            if (set.contains(id))
                parents.put(id, d.subpath());
        });
        deletedDirs.forEach(d -> {
            if (set.contains(d.id))
                parents.put(d.id, d.dir);
        });
        
        StringBuilder2 sb = new StringBuilder2();
        sb.red("extra books in DB (" + extras.size() + ")\n");
        
        set.forEach(id -> {
            sb.cyan(id).append(" : ").yellow(parents.get(id).toString()).ln();
            
            extras.stream()
            .filter(b -> b.path_id == id)
            .forEach(b -> sb.format("%-6s%s\n", b.id, b.file_name));
            
            sb.ln();
        });

        logger.info(sb.toString());

        int option = JOptionPane.showConfirmDialog(null,
                "<html>sure?<br>yes : confirm delete book(s)<br>no : continue without deleting<br>cancel : stop app",
                "delete extra  book(s)", JOptionPane.YES_NO_CANCEL_OPTION);

        if (option == JOptionPane.YES_OPTION) {
            logger.info(yellow("\nexecutes: ") + db.executeUpdate(extras.stream().map(o -> String.valueOf(o.id))
                    .collect(Collectors.joining(",", "DELETE FROM Books WHERE _id IN(", ")"))));
            
            logger.info(separator());
            return true;
        } else if (option != JOptionPane.NO_OPTION)
            throw new SQLException("user refused to proceed");

        logger.info(separator());
        return false;
    }

    public static Dir dir(Dir d) {
        if (toBookStatus(d) != NONE)
            d = d.parent;

        if (toBookStatus(d) != NONE)
            throw new IllegalStateException();
        
        return d;
    }

    private boolean processDeletedDir(List<DeletedDir> notfound, BooksDBMinimal db) throws SQLException {
        if (Checker.isEmpty(notfound))
            return false;

        StringBuilder2 sb = new StringBuilder2();
        sb.red("\nTHESE Dirs WILL BE DELETED\n");

        StringBuilder sql = new StringBuilder("DELETE FROM Paths WHERE path_id IN(");
        notfound.forEach(e -> {
            sb.append(e.id).append(": ").append(e.dir).ln();
            sql.append(e.id).append(',');
        });
        sql.setCharAt(sql.length() - 1, ')');

        sb.yellow("\nexecutes: ").append(db.executeUpdate(sql.toString()));
        sb.append(separator());

        logger.info(sb.toString());
        return true;
    }

    private String separator() {
        return "\n----------------------------------------------\n";
    }
}
