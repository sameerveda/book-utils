package sam.books;
import static sam.books.BooksDBMinimal.ROOT;
import static sam.books.BooksDBMinimal.findBook;
import static sam.books.BooksMeta.BOOK_ID;
import static sam.books.BooksMeta.BOOK_TABLE_NAME;
import static sam.books.BooksMeta.FILE_NAME;
import static sam.books.BooksMeta.PATH;
import static sam.books.BooksMeta.PATH_ID;
import static sam.books.BooksMeta.PATH_TABLE_NAME;
import static sam.sql.querymaker.QueryMaker.qm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.swing.JOptionPane;

import sam.collection.Iterators;
import sam.console.ANSI;

public class BookMove {
    private int inputPathId = -1;
    private Path dest;

    public BookMove(String[] move, String to) throws InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException {
        System.out.println();
        
        try(BooksDBMinimal db = new BooksDBMinimal()) {
            if(to.matches("\\d+")) {
                db.executeQuery(qm().selectAllFrom(PATH_TABLE_NAME).where(w -> w.eq(PATH_ID, to, false)).build(), this::map);
            } else {
                Path path2 = Paths.get(to);
                if(ROOT.equals(path2)) {
                    System.out.println(ANSI.red("moving book to this location not allowed:  ")+path2);
                    return;
                }
                if(path2.startsWith(ROOT)) 
                    path2 = subpath(path2);
                
                Path path = path2;

                db.executeQuery(qm().selectAllFrom(PATH_TABLE_NAME).where(w -> w.eq(PATH, path, true).or().eq(PATH, path.toString().replace('\\', '/'), true)).build(), this::map);
            }

            if(dest == null) {
                System.out.println(ANSI.red("no path found for: ")+to);
                return;                
            }
            System.out.printf(ANSI.yellow("path: ")+"(%s) -> "+ANSI.yellow("%s%n%n"), inputPathId, dest);

            List<Temp> temps = db.collect(qm().select(BOOK_ID, FILE_NAME, PATH_ID).from(BOOK_TABLE_NAME).where(w -> w.in(BOOK_ID, Iterators.of(move), false)).build(), new ArrayList<>(), Temp::new);

            if(temps.isEmpty()) {
                System.out.println(ANSI.red("no valid books specified"));
                return;
            }

            String format = "%-5s%-10s%s\n";
            System.out.printf(ANSI.yellow(format), "id", "path_id", "file_name");
            temps.forEach(s -> System.out.printf(format, s.bookId, s.pathId, s.fileName));

            System.out.println(ANSI.cyan("\n---------------------\nWHERE\n---------------------\n"));
            String fm = "%-10s%s%n";
            System.out.printf(ANSI.yellow(fm), "path_id", "path");

            HashMap<Integer, String> path = db.collect(qm().selectAllFrom(PATH_TABLE_NAME).where(w -> w.in(PATH_ID, temps.stream().mapToInt(t -> t.pathId).distinct().toArray())).build(), new HashMap<>(), rs -> rs.getInt(PATH_ID), rs -> rs.getString(PATH));

            path.forEach((s,t) -> System.out.printf(fm, s, t));
            
            System.out.println(ANSI.cyan("\n---------------------\nSummery\n---------------------\n"));
            String fm2 = ANSI.yellow("in: ")+"%s%n"+ANSI.green("to: ")+"%s%n";
            String fmError = ANSI.yellow("in: ")+"%s%n"+ANSI.red("move will not occurs, as file not found\n");
            
            temps.forEach(t -> {
                Path in = findBook(ROOT.resolve(path.get(t.pathId)).resolve(t.fileName));
                if(Files.notExists(in))
                    System.out.printf(fmError, in);
                Path out = ROOT.resolve(dest).resolve(t.fileName);
                System.out.printf(fm2, subpath(in), subpath(out));
            });

            if(JOptionPane.showConfirmDialog(null, "continue?") != JOptionPane.YES_OPTION)
                return;
            
            db.prepareStatementBlock(qm().update(BOOK_TABLE_NAME).placeholders(PATH_ID).where(w -> w.eqPlaceholder(BOOK_ID)).build(), ps -> {
                for (Temp t : temps) {
                    Path in = findBook(ROOT.resolve(path.get(t.pathId)).resolve(t.fileName));
                    Path out = ROOT.resolve(dest).resolve(t.fileName);
                    
                    try {
                        Files.move(in, out);
                        ps.setInt(1, inputPathId);
                        ps.setInt(2, t.bookId);
                        ps.addBatch();
                    } catch (IOException e) {
                        System.out.println(ANSI.red("failed to move: "));
                        System.out.printf(fm2, subpath(in), subpath(out));
                        System.out.println(ANSI.red(e));
                        System.out.println();
                    }
                }

                System.out.println(ANSI.yellow("excutes: ")+ps.executeBatch().length);
                return null;
            });
            
            db.commit();
            
            System.out.println(ANSI.FINISHED_BANNER);
        }
    }

    private Path subpath(Path path2) {
        return path2.subpath(ROOT.getNameCount(), path2.getNameCount());
    }

    private Void map(ResultSet rs) throws SQLException {
        if(!rs.next())
            return null;

        inputPathId = rs.getInt(PATH_ID);
        dest = Paths.get(rs.getString(PATH));
        
        return null;
    }

    private class Temp {
        final int bookId, pathId;
        final String fileName;

        public Temp(ResultSet rs) throws SQLException {
            bookId = rs.getInt(BOOK_ID);
            pathId = rs.getInt(PATH_ID);
            fileName = rs.getString(FILE_NAME);
        }
    }

}
