package sam.books;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;
import static sam.books.BooksMeta.FILE_NAME;
import static sam.books.BooksMeta.ID;
import static sam.books.BooksMeta.NAME;
import static sam.books.BooksMeta.PAGE_COUNT;
import static sam.books.BooksMeta.PATH_ID;
import static sam.books.BooksMeta.YEAR;

import java.util.List;

import org.json.JSONWriter;

import com.almworks.sqlite4java.SQLiteException;
import com.almworks.sqlite4java.SQLiteStatement;

import sam.myutils.Checker;

public class SmallBook {
    public static final List<String> columns = unmodifiableList(asList(ID, NAME, FILE_NAME, PATH_ID, PAGE_COUNT, YEAR));

    public final int id;
    public final String name;
    public final String file_name;
    public final int path_id;
    public final int page_count;
    public final String year; 
    
    public SmallBook(SQLiteStatement rs) throws SQLiteException {
        int index = 0;
        this.id = rs.columnInt(index++); 
        this.name = rs.columnString(index++);
        this.file_name = rs.columnString(index++);
        this.path_id = rs.columnInt(index++);
        this.page_count = rs.columnInt(index++);
        this.year = rs.columnString(index++);
    }
    
    public void writeJson(JSONWriter w) {
        w
        .key(ID ).value(id )
        .key(NAME).value(name)
        .key(FILE_NAME).value(file_name)
        .key(PATH_ID).value(path_id)
        .key(PAGE_COUNT).value(page_count)
        .key(YEAR).value(year);
    }
    
    String tostring; 
    @Override
    public String toString() {
        if(tostring == null) {
            tostring = name +"\nid: "+id+
                    "  | pages: "+(page_count == -1 ? "--" : page_count)+
                    "  | year: "+ (Checker.isEmptyTrimmed(year) ? "--" : year);
        }
        return tostring;
    }
}
