package sam.book.list.model;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

import sam.books.BookBase;
import sam.books.BookStatus;
import sam.tsv.Row;

public final class BookEntry extends BookBase {
    private static final Logger LOGGER = MyLoggerFactory.logger(BookEntry.class.getName());

    private long lastReadTime, addTime;
    private boolean modified;
    private final Row row;
    private BookStatus sts;

    public BookEntry(ResultSet rs, String parentFolderSubpath, Row row) throws SQLException {
        super(rs, parentFolderSubpath);
        this.row = row;
        this.sts = super.getStatus();
        
        this.addTime = COLUMNS.ADD_TIME.getLong(row);
        this.lastReadTime = COLUMNS.LAST_READ_TIME.getLong(row);
    }
    
    @Override
    public BookStatus getStatus() {
    	return sts;
    }
    Row getRow() {
        return row;
    }
    boolean isModified() {
        return modified;
    }
    public long getAddTime() {
        return addTime;
    }
    public long getLastReadTime() {
        return lastReadTime;
    }
    public void updateLastReadTime() {
        if(row == null)
            return;
        // Objects.requireNonNull(row, "Tsv.row is not set");
        modified = true;
        this.lastReadTime = System.currentTimeMillis();
        COLUMNS.LAST_READ_TIME.setLong(row, lastReadTime);

        LOGGER.fine(() -> getBookId() +"  "+getName()+"  lastReadTime to: "+lastReadTime);
    }
}