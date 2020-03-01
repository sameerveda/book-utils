

import static sam.books.BooksDBMinimal.DB_PATH;
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Optional;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.StringArrayOptionHandler;

import javafx.application.Application;
import sam.books.BookMove;
import sam.books.Replace;
import sam.books.UpdateDB;
import sam.console.ANSI;
import sam.myutils.System2;
import sam.swing.SwingUtils;

public class Main {
    final double VERSION = Double.parseDouble(System2.lookup("APP_VERSION"));

    @Option(name= "-h", aliases= "--help", usage="print this")
    private boolean help;
    @Option(name="-v", aliases="--version", usage="version")
    private boolean version;
    @Option(name="-u", aliases="--updatedb", usage="update books.db")
    private boolean updatedb;

    @Option(name="-m", aliases="--move", usage="move given books -to", metaVar="bookids...", handler=StringArrayOptionHandler.class)
    String[] move = new String[0];
    @Option(name="-t", aliases="--to", usage="move -to", metaVar="[new path id]")
    String to;
    
    @Option(name="--replace", usage="move books to their original positions")
    boolean replace;

    public static void main(String[] args) throws ClassNotFoundException, IOException, CmdLineException, URISyntaxException{
        new Main(args);
    }

    public Main(String[] args) throws CmdLineException, URISyntaxException, IOException {
        CmdLineParser parser = new CmdLineParser(this);
        parser.parseArgument(args);

        if(args.length == 0 || help) {
            if(args.length == 0)
                println(red("Invalid uses of command: zero number of commands\n"));
            printUsage();
        }
        if(version){
            println(yellow("version: "+VERSION+"\n\n"));
            System.exit(0);
        }
        
        if(updatedb){
            try {
            	if(new UpdateDB().call()) {
            		Optional.ofNullable(System2.lookup("db.backup"))
                    .map(Paths::get)
                    .filter(Files::exists)
                    .ifPresent(p -> {
                    	Path target = p.resolve(DB_PATH.getFileName());
                    	try {
    						Files.copy(DB_PATH, target, StandardCopyOption.REPLACE_EXISTING);
    						System.out.println(ANSI.green("backup created: ")+target);
    					} catch (IOException e) {
    						System.out.println(ANSI.red("failed backup: ")+target+"  "+e);
    					}
                    });	
            	}
            } catch (Exception e1) {
                SwingUtils.showErrorDialog(null, e1);
                return;
            }
        } else if(move.length != 0 || to !=null ) {
            if(move == null || move.length == 0) {
                System.out.println(red("--move not specified"));
                printUsage();
            }
            if(to == null) {
                System.out.println(red("--to not specified"));
                printUsage();
            }
            try {
                new BookMove(move, to);
            } catch (InstantiationException | IllegalAccessException | ClassNotFoundException | SQLException e) {
                SwingUtils.showErrorDialog(null, e);
            }
        } else if(replace) {
            Application.launch(Replace.class, args);
        } else{
            println(red("failed to recognize command: ")+Arrays.toString(args));
            printUsage();
        }
        System.exit(0);
    }

    private void printUsage() {
        new CmdLineParser(this).printUsage(System.out);
        System.exit(0);
    }

    private static void println(Object o) {
        System.out.println(o);
    }
}
