package sam.books;
import static sam.books.BooksMeta.AUTHOR;
import static sam.books.BooksMeta.DESCRIPTION;
import static sam.books.BooksMeta.FILE_NAME;
import static sam.books.BooksMeta.ISBN;
import static sam.books.BooksMeta.NAME;
import static sam.books.BooksMeta.PAGE_COUNT;
import static sam.books.BooksMeta.PATH_ID;
import static sam.books.BooksMeta.URL;
import static sam.books.BooksMeta.YEAR;

import java.io.Serializable;

import sam.books.walker.Dir.BookFile;

public class NewBook implements Serializable {
    private static final long serialVersionUID = -8062536072287690587L;

    final BookFile path;
    String name;
    public int id;
    String author;
    String isbn;
    int page_count;
    String year;
    String description;
    String url;

    public NewBook(BookFile path) {
        this.path = path;
    }
    public void apply(NewBook existing) {
        if(existing == null)
            return;

        this.name = existing.name;
        this.id = existing.id;
        this.author = existing.author;
        this.isbn = existing.isbn;
        this.page_count = existing.page_count;
        this.year = existing.year;
        this.description = existing.description;
        this.url = existing.url;
    }

    public BookFile path() {
        return path;
    }
    public void set(String colName, String value) {
        switch (colName) {
            case NAME:          this.name = value; break;
            case AUTHOR:        this.author = value; break;
            case ISBN:          this.isbn = value; break;
            case PAGE_COUNT:    this.page_count = value == null || !value.trim().matches("\\d+") ? 0 : Integer.parseInt(value.trim()); break;
            case YEAR:          this.year = value; break;
            case DESCRIPTION:   this.description = value; break;
            case URL:           this.url = value; break;
            default:
                break;
        }
    }
    public String get(String c) {
        switch (c) {
            case NAME:        return  this.name;
            case FILE_NAME:   return  this.path.name;
            case PATH_ID:     return  String.valueOf(UpdateDB.dir(this.path.parent()).path_id());
            case AUTHOR:      return  this.author;
            case ISBN:        return  this.isbn;
            case PAGE_COUNT:  return  String.valueOf(this.page_count);
            case YEAR:        return  this.year;
            case DESCRIPTION: return  this.description;
            case URL:         return this.url;
        }
        return null;
    }
}
