package sam.books;

import static sam.books.BookStatus.NONE;
import static sam.books.BookStatus.READ;
import static sam.books.BookStatus.SKIPPED;
import static sam.books.BookStatus.valueOf;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;

import sam.config.MyConfig;
import sam.sql.sqlite.SQLiteDB;

public class BooksDBMinimal extends SQLiteDB {
	
	public static final Path ROOT;
	public static final Path APP_FOLDER;
	public static final Path BACKUP_FOLDER;
	public static final Path DB_PATH;

	static {
		
	}
	
	public BooksDBMinimal() throws  SQLException {
		this(DB_PATH);
	}
	public BooksDBMinimal(Path dbpath) throws  SQLException {
		super(dbpath);
	}
	public static Path findBook(Path expectedPath) {
		if(Files.exists(expectedPath))
			return expectedPath;
		Path path = expectedPath.resolveSibling("_read_").resolve(expectedPath.getFileName());

		if(Files.exists(path))
			return path;

		File[] dirs = expectedPath.getParent().toFile().listFiles(f -> f.isDirectory());

		if(dirs == null || dirs.length == 0)
			return null;

		String name = expectedPath.getFileName().toString();

		for (File file : dirs) {
			File f = new File(file, name);
			if(f.exists())
				return f.toPath();
		}
		return null;
	}
	public static BookStatus getStatusFromDir(Path dir) {
		Path name = dir.getFileName();
		if(name.equals(READ.getPathName()))
			return READ;
		if(name.equals(SKIPPED.getPathName()))
			return SKIPPED;
		
		return toBookStatus(name.toString());
	}
	public static BookStatus getStatusFromFile(Path p) {
		return getStatusFromDir(p.getParent()); 
	} 
	public static BookStatus toBookStatus(String dirName) {
        if(dirName.charAt(0) == '_' && dirName.charAt(dirName.length() - 1) == '_' && dirName.indexOf(' ') < 0)
            return valueOf(dirName.substring(1, dirName.length() - 1).toUpperCase());

        return NONE;
	}

}
