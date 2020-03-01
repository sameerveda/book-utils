package sam.books;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static sam.books.BooksDBMinimal.BACKUP_FOLDER;
import static sam.books.BooksDBMinimal.DB_PATH;
import static sam.books.BooksDBMinimal.ROOT;
import static sam.books.BooksDBMinimal.getStatusFromDir;
import static sam.books.BooksDBMinimal.getStatusFromFile;
import static sam.books.BooksMeta.AUTHOR;
import static sam.books.BooksMeta.BOOK_ID;
import static sam.books.BooksMeta.BOOK_TABLE_NAME;
import static sam.books.BooksMeta.CREATED_ON;
import static sam.books.BooksMeta.FILE_NAME;
import static sam.books.BooksMeta.ISBN;
import static sam.books.BooksMeta.MARKER;
import static sam.books.BooksMeta.NAME;
import static sam.books.BooksMeta.PAGE_COUNT;
import static sam.books.BooksMeta.PATH;
import static sam.books.BooksMeta.PATH_ID;
import static sam.books.BooksMeta.PATH_TABLE_NAME;
import static sam.books.BooksMeta.STATUS;
import static sam.books.BooksMeta.YEAR;
import static sam.console.ANSI.createBanner;
import static sam.console.ANSI.green;
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;
import static sam.sql.querymaker.QueryMaker.qm;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;

import sam.collection.OneOrMany;
import sam.config.MyConfig;
import sam.console.ANSI;
import sam.myutils.System2;
import sam.sql.querymaker.InserterBatch;
import sam.tsv.Tsv; 

public class UpdateDB implements Callable<Boolean> {
	Path[] pathIdPathMap;
    public static final Path SELF_DIR = Paths.get(System2.lookup("SELF_DIR"));

    public Boolean call() throws IOException, SQLException, URISyntaxException, ClassNotFoundException {
        println(yellow("WORKING_DIR: ")+ROOT);
        println("");
        println(yellow("skip dirs: "));
        
        println(createBanner("Updating "+DB_PATH)+"\n");

        int count = ROOT.getNameCount();
        Path nonBook = ROOT.resolve("non-book materials");
        Path filesListPath = SELF_DIR.resolve("fileslist.dat");
        Set<String> scannedFiles = Files.notExists(filesListPath) ? new HashSet<>() : Files.lines(filesListPath, Charset.forName("utf-8")).collect(Collectors.toSet());
        Path ignoreFilesPath = SELF_DIR.resolve("ignore-files.txt");
        Set<String> skipfiles = Files.notExists(ignoreFilesPath) ? Collections.emptySet() : Files.lines(ignoreFilesPath).collect(Collectors.collectingAndThen(Collectors.toSet(), s -> s.isEmpty() ? Collections.emptySet() : s));
        	
        final int size = scannedFiles.size();

        Map<Boolean, List<Path>> walk = Files.list(ROOT)
                .filter(p -> !p.equals(nonBook) && Files.isDirectory(p))
                .flatMap(t -> {
                    try {
                        return Files.walk(t);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .filter(p -> {
                    String name = p.getFileName().toString();
                    
                    if(scannedFiles.contains(name))
                        return true;
                    
                    try {
                        if(Files.isHidden(p) || skipfiles.contains(name))
                            return false;
                    } catch (IOException e) {
                        println(red("error with file: ")+e);
                        return false;
                    }
                    
                    scannedFiles.add(name);
                    return true;
                })
                .collect(Collectors.partitioningBy(Files::isDirectory, Collectors.mapping(p -> p.subpath(count, p.getNameCount()), Collectors.toList())));
        
        if(scannedFiles.size() != size)
        	Files.write(filesListPath, scannedFiles,Charset.forName("utf-8"), CREATE, TRUNCATE_EXISTING);

        List<Path> dirs = walk.get(true);
        dirs.removeIf(p -> getStatusFromDir(p) != BookStatus.NONE);

        Map<String, OneOrMany<Path>> bookFilesTemp = new HashMap<>();
        Function<String, OneOrMany<Path>> computer = s -> new OneOrMany<>();

        for (Path file : walk.get(false))
            bookFilesTemp.computeIfAbsent(file.getFileName().toString(), computer).add(file);

        if(bookFilesTemp.values().stream().anyMatch(p -> p.size() != 1)) {
            println(createBanner("repeated books"));
            bookFilesTemp.forEach((s,t) -> {
                if(t.size() == 1)
                    return;
                println(yellow(s));
                t.forEach(z -> println("   "+z));
            });
            println(ANSI.red("db update skipped"));
            JOptionPane.showMessageDialog(null, "db update skipped");

            printSeparator();
            return false;
        }

        Map<String, Path> bookFiles = new HashMap<>();
        bookFilesTemp.forEach((s,t) -> bookFiles.put(s, t.get(0)));

        boolean modified = false;

        try (BooksDBMinimal db = new BooksDBMinimal()) {
        	pathIdPathMap = new Path[db.getSequnceValue(PATH_TABLE_NAME)+1] ;
            db.iterate("SELECT * FROM "+PATH_TABLE_NAME, rs -> pathIdPathMap[rs.getInt(PATH_ID)] = Paths.get(rs.getString(PATH)));

            //checking for dirs not in database
            modified = checkMissingDbDirs(db, dirs) || modified;

            Set<Path> dirsSet = new HashSet<>(dirs); 
            Map<Integer, Path> extraDirs = new TreeMap<>();
            
            forEachPath((id, path) -> {
            	if(!dirsSet.contains(path))
                    extraDirs.put(id, path);
            });
            
            //extra dirs in db
            modified = processExtraDirs(extraDirs, db) || modified;
            
            //{book_id, file_pame, path_id}
            ArrayList<Book1> dbBooksData = new ArrayList<>();
            db.iterate(Book1.SELECT_SQL, rs -> {
            	Book1 b = new Book1(rs);
            	b.subpath(bookFiles.get(b.file_name));
            	dbBooksData.add(b);
            });

            List<Book1> extras = dbBooksData.stream()
                    .filter(o -> !bookFiles.containsKey(o.file_name))
                    .collect(Collectors.toList());
            dbBooksData.removeAll(extras);

            //delete extra books
            modified = deleteExtraBooks(extras,db) || modified;

            HashMap<Path, Integer> pathToPathIdMap = new HashMap<>();
            forEachPath((s,t) -> pathToPathIdMap.put(t, s));

            //respect path changes
            modified = lookForPathChanges(db, dbBooksData, bookFiles, pathToPathIdMap) || modified;

            Set<String> temp = dbBooksData.stream().map(b -> b.file_name).collect(Collectors.toSet());
            List<NewBook> newBooks = new ArrayList<>();
            bookFiles.forEach((name,path) -> {
                if(!temp.contains(name))
                    newBooks.add(new NewBook(name, path, pathToPathIdMap.get(getParent(path))));
            });

            //list non-listed books
            modified = processNewBooks(newBooks, db) || modified;

            db.commit();
        } catch (SQLException e1) {
            if("user refused to proceed".equals(e1.getMessage())) {
                println(red("user refused to proceed"));
                return false;
            }
            e1.printStackTrace();
        }
        println(ANSI.FINISHED_BANNER+"\n\n\n");

        if(modified){
            Path t = BACKUP_FOLDER.resolve(DB_PATH.getFileName()+"_"+LocalDate.now().getDayOfWeek().getValue());
            Files.createDirectories(t.getParent());
            Files.copy(DB_PATH, t, StandardCopyOption.REPLACE_EXISTING);
        }
        return modified;
    }
    private void forEachPath(BiConsumer<Integer, Path> action) {
    	for (int i = 0; i < pathIdPathMap.length; i++) {
			Path p = pathIdPathMap[i];
			if(p != null)
				action.accept(i, p);
		}
	}
	private boolean processNewBooks(List<NewBook> newBooks, BooksDBMinimal db) throws SQLException {
        if(newBooks.isEmpty())
            return false;

        println(green("new books : ("+newBooks.size()+")\n"));
        
        newBooks.stream().collect(Collectors.groupingBy(s -> s.path().getParent()))
        .forEach((s,b) -> {
        	println(yellow(s));
        	b.forEach(t -> println("  "+t.path().getFileName()));
        });
        println("");
		 
        List<NewBook> books =  new AboutBookExtractor(newBooks).getResult();

        if(books == null || books.isEmpty())
            return false;

        Tsv tsv = new Tsv(BOOK_ID,
                NAME,
                FILE_NAME,
                AUTHOR,
                ISBN,
                PAGE_COUNT,
                YEAR);
        
        int max = db.findFirst("select seq from sqlite_sequence where name='"+BOOK_TABLE_NAME+"'", rs -> rs.getInt(1)) + 1;
        
        for (NewBook b : books) {
        	b.id = max++;
            tsv.addRow(String.valueOf(b.id),
                    b.name,
                    b.file_name,
                    b.author,
                    b.isbn,
                    String.valueOf(b.page_count),
                    b.year);
        }
        
        InserterBatch<NewBook> insert = new InserterBatch<>(BOOK_TABLE_NAME);
        insert.setInt(BOOK_ID, b -> b.id);
        
        for (ColumnNames n : ColumnNames.values())
			insert.setString(n.columnName, b -> n.get(b));
        
        long time = System.currentTimeMillis(); 
        insert.setLong(CREATED_ON, b -> time);
        
        println(yellow("\nexecutes: ")+insert.execute(db, books));
        
        Path p = Paths.get(MyConfig.COMMONS_DIR, "new-books-"+LocalDateTime.now().toString().replace(':', '_') + ".tsv");
        try {
            tsv.save(p);
            println(yellow("created: ")+p);
        } catch (IOException e2) {
            println(red("failed to save: ")+p);
        }
        
        printSeparator();
        return true;
    }
    private boolean checkMissingDbDirs(BooksDBMinimal db, List<Path> dirs) throws SQLException {
    	Set<Path> pathset = new HashSet<>();
    	forEachPath((i, p) -> pathset.add(p));
        List<Path> missings = dirs.stream().filter(p -> !pathset.contains(p)).collect(Collectors.toList());

        if(!missings.isEmpty()){
            println(yellow("new folders"));
            int[] max = {pathIdPathMap.length};

            Map<Integer, Path> map = new HashMap<>();
            missings.forEach(p -> map.put(max[0]++, p));
            String format = "%-"+(String.valueOf(max[0]).length() + 3)+"s%s\n";
            map.forEach((s,t) -> printf(format, s, t));

            pathIdPathMap = Arrays.copyOf(pathIdPathMap, max[0]);
            map.forEach((s,t) -> pathIdPathMap[s] = t);
            
            InserterBatch<Entry<Integer, Path>> insert = new InserterBatch<>(PATH_TABLE_NAME);
            insert.setInt(PATH_ID, e -> e.getKey());
            insert.setString(PATH, e -> e.getValue().toString());
            insert.setString(MARKER, e -> e.getValue().getFileName().toString());
            
            int count = insert.execute(db, map.entrySet());
            println(yellow("\nexecutes: ")+count+"\n----------------------------------------------\n");
            return true;
        }
        return false;
    }
    private boolean lookForPathChanges(BooksDBMinimal db, List<Book1> dbBooksData, Map<String, Path> bookFiles, Map<Path, Integer> pathToPathIdMap) throws SQLException {
        db.prepareStatementBlock(qm().update(BOOK_TABLE_NAME).placeholders(STATUS).where(w -> w.eqPlaceholder(BOOK_ID)).build(), ps -> {
        	int n = 0;
        	for (Book1 b : dbBooksData) {
        		BookStatus s = getStatusFromFile(b.subpath());
        		if(b.status != s) {
        			if(n == 0)
        				println(ANSI.yellow("STATUS CHANGES"));
        			println(b.file_name+"\t"+b.status +" -> "+ s);
        			n++;
        			ps.setString(1, s == null ? null : s.toString());
        			ps.setInt(2, b.id);
        			ps.addBatch();
        		}
			}
        	if(n != 0)  
        		println("status change: "+ps.executeBatch().length+"\n");
        	return null;
        });
        
        ArrayList<Book1> changed = new ArrayList<>();

        for (Book1 o : dbBooksData) {
            Path p2 = bookFiles.get(o.file_name);

            int idNew = pathToPathIdMap.get(getParent(p2));

            if(idNew != o.path_id) {
                o.setNewPathId(idNew);
                changed.add(o);
            }
        }
        if(!changed.isEmpty()){
            println(red("changed books paths ("+changed.size()+")\n"));
            String format = "%-5s%-12s%-12s%s\n";
            print(yellow(String.format(format, "id", "old_path_id", "new_path_id", "file_name")));
            for (Book1 o : changed) printf(format, o.id, o.path_id, o.getNewPathId(), o.file_name);

            println("");

            String format2 = "%-10s%s\n";
            print("\n"+yellow(String.format(format2, "path_id", "path")));
            changed.stream()
            .map(o -> o.getNewPathId())
            .distinct()
            .forEach(path_id -> printf(format2, path_id, pathIdPathMap[path_id]));

            db.prepareStatementBlock(qm().update(BOOK_TABLE_NAME).placeholders(PATH_ID).where(w -> w.eqPlaceholder(BOOK_ID)).build(), 
                    ps -> {
                        for (Book1 o : changed) {
                            ps.setInt(1, o.getNewPathId());
                            ps.setInt(2, o.id);
                            ps.addBatch();
                        }
                        println(yellow("\nexecutes: ")+ps.executeBatch().length);
                        return null;
                    });
            printSeparator();
            return true;
        }
        return false;
    }
    
    private Object getParent(Path file) {
        if(file == null)
            return null;
        BookStatus s = getStatusFromFile(file);
        return s == BookStatus.NONE ? file.getParent() : file.getParent().getParent();
    }
    private boolean deleteExtraBooks(List<Book1> extras, BooksDBMinimal db) throws SQLException{

        if(!extras.isEmpty()){
            println(red("extra books in DB ("+extras.size()+")\n"));

            String format = "%-10s%-10s%s%n";
            print(yellow(String.format(format, "id", "path_id", "file_name")));
            for (Book1 o : extras) printf(format, o.id, o.path_id, o.file_name);

            println("");

            format = "%-10s%s\n";
            print(yellow(String.format(format, "path_id", "path")));
            for (Book1 o : extras) printf(format, o.path_id, pathIdPathMap[o.path_id]);

            println("");

            int option = JOptionPane.showConfirmDialog(null, "<html>sure?<br>yes : confirm delete book(s)<br>no : continue without deleting<br>cancel : stop app", "delete extra  book(s)", JOptionPane.YES_NO_CANCEL_OPTION);

            if(option == JOptionPane.YES_OPTION){
                println(yellow("\nexecutes: ")
                        +db.executeUpdate(extras.stream().map(o -> String.valueOf(o.id)).collect(Collectors.joining(",", "DELETE FROM Books WHERE _id IN(", ")"))));
                return true;
            }
            else if(option != JOptionPane.NO_OPTION)
                throw new SQLException("user refused to proceed");

            printSeparator();
        }
        return false;
    }
    private boolean processExtraDirs(Map<Integer, Path> extraDirs, BooksDBMinimal db) throws SQLException {
        boolean dbModified = false;

        if(!extraDirs.isEmpty()){
            println(extraDirs.values().stream().map(String::valueOf).collect(Collectors.joining("\n", red("\nTHESE Dirs WILL BE DELETED\n"), "\n")));

            println(yellow("\nexecutes: ") +db.executeUpdate(extraDirs.keySet().stream().map(String::valueOf).collect(Collectors.joining(",", "DELETE FROM Paths WHERE path_id IN(", ")"))));
            dbModified = true;

            printSeparator();
        }
        return dbModified;
    }

    private void printSeparator() {
        println("\n----------------------------------------------\n");
    }
    private static void print(Object o) {
        System.out.println(o);
    }
    private static void println(Object o) {
        System.out.println(o);
    }
    private static void printf(String format, Object...args) {
        System.out.printf(format, args);
    }

}