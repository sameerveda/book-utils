package sam.books;

import static sam.books.BooksMeta.AUTHOR;
import static sam.books.BooksMeta.CREATED_ON;
import static sam.books.BooksMeta.DESCRIPTION;
import static sam.books.BooksMeta.ISBN;
import static sam.books.BooksMeta.STATUS;
import static sam.books.BooksMeta.URL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.json.JSONWriter;

import com.almworks.sqlite4java.SQLiteException;
import com.almworks.sqlite4java.SQLiteStatement;

public class Book extends SmallBook {
    public static final List<String> columns;

    static {
        List<String> list = new ArrayList<>(SmallBook.columns);
        list.addAll(Arrays.asList(AUTHOR, ISBN, DESCRIPTION, STATUS, URL, CREATED_ON));
        if (list.size() != new HashSet<>(list).size()) {
            throw new IllegalStateException();
        }
        columns = list;
    }

    public final String author;
    public final String isbn;
    public final String description;
    public final BookStatus status;
    public final String url;
    public final long created_on;

    public Book(SQLiteStatement rs) throws SQLiteException {
        super(rs);
        int index = SmallBook.columns.size();
        this.author = rs.columnString(index++);
        this.isbn = rs.columnString(index++);
        this.description = rs.columnString(index++);
        this.status = Optional.ofNullable(rs.columnString(index++)).map(BookStatus::valueOf).orElse(BookStatus.NONE);
        this.url = rs.columnString(index++);
        this.created_on = rs.columnLong(index++);
    }

    @Override
    public void writeJson(JSONWriter w) {
        super.writeJson(w);
        w.key(AUTHOR).value(author).key(ISBN).value(isbn).key(DESCRIPTION).value(description).key(STATUS).value(status)
                .key(URL).value(url).key(CREATED_ON).value(created_on);
    }
}
