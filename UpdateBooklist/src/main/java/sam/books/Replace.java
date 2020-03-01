package sam.books;
import static sam.books.BooksDBMinimal.ROOT;
import static sam.books.BooksDBMinimal.findBook;
import static sam.books.BooksMeta.BOOK_TABLE_NAME;
import static sam.books.BooksMeta.FILE_NAME;
import static sam.books.BooksMeta.PATH;
import static sam.books.BooksMeta.PATH_ID;
import static sam.books.BooksMeta.PATH_TABLE_NAME;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

import javafx.application.Application;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import sam.collection.Iterables;
import sam.collection.Iterators;
import sam.config.MyConfig;
import sam.console.ANSI;
import sam.console.VT100;
import sam.sql.querymaker.QueryMaker;

public class Replace extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        process(stage);
        System.exit(0); 
        
    }
    private void process(Stage stage) {
        FileChooser fc = new FileChooser();
        File init = new File(MyConfig.COMMONS_DIR);
        if(init.exists())
            fc.setInitialDirectory(init);
        fc.getExtensionFilters().add(new ExtensionFilter("pdf file", "*.pdf"));
        List<File> files =  fc.showOpenMultipleDialog(stage);
        
        if(files == null || files.isEmpty()) {
            System.out.println(ANSI.red("nothing selected"));
            System.exit(0);
        }
        
        try(BooksDBMinimal db = new BooksDBMinimal()) {
            List<Temp> books =  db.prepareStatementBlock(QueryMaker.getInstance()
                    .select(PATH_ID, FILE_NAME)
                    .from(BOOK_TABLE_NAME)
                    .where(w -> w.in(FILE_NAME, Iterators.repeat('?', files.size()), false))
                    .build(), ps -> {
                        int n = 1;
                        for (File f : files)
                            ps.setString(n++, f.getName());
                        
                        List<Temp> temp = new ArrayList<>();
                        try(ResultSet rs = ps.executeQuery()){
                            while(rs.next())  temp.add(new Temp(rs));
                        }
                        return temp;
                    });
            
            if(books.isEmpty()) {
                System.out.println("\n\n"+ANSI.red("no books to process"));
                return;
            }
            
            Map<String, File> filemap = files.stream().collect(Collectors.toMap(File::getName, f -> f));

            for (Temp b : books)
                b.path = filemap.remove(b.filename);
            
            if(!filemap.isEmpty()) {
                for (String s : filemap.keySet()) {
                    System.out.println(ANSI.red("no data found for: ")+s);
                }
            }
            HashMap<Integer, String> map = db.collect(QueryMaker.getInstance().selectAllFrom(PATH_TABLE_NAME).where(w -> w.in(PATH_ID, Iterables.map(books, b -> b.pathid), false)).build(), new HashMap<>(), rs -> rs.getInt(PATH_ID), rs -> rs.getString(PATH));
            
            String format = ANSI.green("moved: ")+"%s\n%8s%n";
            
            for (Temp b : books) {
                Path subpath = Paths.get(map.get(b.pathid), b.filename);
                Path p = findBook(ROOT.resolve(subpath));
                if(Files.notExists(p)) {
                    System.out.println(ANSI.red("original file not found at: ")+p);
                    if(!confirm())
                        continue;
                }
                try {
                    Files.move(b.path.toPath(), p, StandardCopyOption.REPLACE_EXISTING);
                    System.out.printf(format, b.path.getName(), subpath);
                } catch (IOException e) {
                    System.out.printf(ANSI.red("failed to move:")+" %s -> %s [%s]%n",  b.path.toPath(), subpath, e);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private boolean confirm() {
        System.out.print(ANSI.yellow("continue? [Y/n] : "));
        VT100.save_cursor();
        try(Scanner sc = new Scanner(System.in)){
            while(sc.hasNextLine()) {
                String s = sc.nextLine().trim();
                if(s.isEmpty()) {
                    VT100.unsave_cursor();
                    VT100.erase_down();
                    VT100.save_cursor();
                } else {
                    boolean b = s.equalsIgnoreCase("y") || s.equalsIgnoreCase("yes");
                    VT100.unsave_cursor();
                    VT100.erase_down();
                    System.out.println(b ? ANSI.green("YES") : ANSI.red("NO"));
                    return b; 
                }
            }
        }
        return false;
    }
    private class Temp {
        public File path;
        final String filename;
        final int pathid;
        
        public Temp(ResultSet rs ) throws SQLException {
            filename = rs.getString(FILE_NAME);
            pathid = rs.getInt(PATH_ID);
        }
    }

}
