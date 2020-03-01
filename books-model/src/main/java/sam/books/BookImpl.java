package sam.books;

import static sam.books.BooksMeta.AUTHOR;
import static sam.books.BooksMeta.BOOK_TABLE_NAME;
import static sam.books.BooksMeta.CREATED_ON;
import static sam.books.BooksMeta.DESCRIPTION;
import static sam.books.BooksMeta.FILE_NAME;
import static sam.books.BooksMeta.ID;
import static sam.books.BooksMeta.ISBN;
import static sam.books.BooksMeta.NAME;
import static sam.books.BooksMeta.PAGE_COUNT;
import static sam.books.BooksMeta.PATH_ID;
import static sam.books.BooksMeta.STATUS;
import static sam.books.BooksMeta.URL;
import static sam.books.BooksMeta.YEAR;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Table(name = BOOK_TABLE_NAME)
public class BookImpl {
    @Column(name = ID)
    @Id
    @GeneratedValue(strategy = GenerationType.TABLE)
    private int id;

    @Column(name = NAME, nullable = false, unique = true)
    private String name;
    
    @Column(name = FILE_NAME, nullable = false, unique = true)
    private String fileName;
    
    @Column(name = PATH_ID, nullable = false)
    private int pathId;
    
    @Column(name = AUTHOR)
    private String author;
    
    @Column(name = ISBN, nullable = true, unique = true)
    private String isbn;
    
    @Column(name = PAGE_COUNT)
    private int pageCount;
    
    @Column(name = YEAR)
    private String year;
    
    @Column(name = DESCRIPTION)
    private String description;
    
    @Column(name = STATUS)
    private BookStatus status = BookStatus.NONE;
    
    @Column(name = URL)
    private String url;
    
    @Column(name = CREATED_ON)
    private long createdOn;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getPathId() {
        return pathId;
    }

    public void setPathId(int pathId) {
        this.pathId = pathId;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public int getPageCount() {
        return pageCount;
    }

    public void setPageCount(int pageCount) {
        this.pageCount = pageCount;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BookStatus getStatus() {
        return status;
    }

    public void setStatus(BookStatus status) {
        this.status = status;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public long getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(long createdOn) {
        this.createdOn = createdOn;
    }
}
