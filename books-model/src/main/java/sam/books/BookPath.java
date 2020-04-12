package sam.books;

import static sam.books.BookPathMeta.MARKER;
import static sam.books.BookPathMeta.PATH;
import static sam.books.BookPathMeta.PATH_ID;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.almworks.sqlite4java.SQLiteException;
import com.almworks.sqlite4java.SQLiteStatement;

public class BookPath {
	public final int id;
	public final String path;
	public final String marker;
	
	public static final List<String> columns = Collections.unmodifiableList(Arrays.asList(PATH_ID, PATH, MARKER));
	
	public BookPath(SQLiteStatement rs) throws SQLiteException {
	    this.id  = rs.columnInt(0);
	    this.path  = rs.columnString(1);
	    this.marker  = rs.columnString(2);
    }
	
    public BookPath(int id, String path, String marker) {
        this.id = id;
        this.path = path;
        this.marker = marker;
    }

}

