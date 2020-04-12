package sam.books;

import static sam.books.BooksMeta.ID;
import static sam.books.BooksMeta.NAME;
import static sam.books.SortDir.ASC;
import static sam.books.SortDir.DESC;
public enum SortBy {
	ADDED(ID, DESC), 
	YEAR(BooksMeta.YEAR, DESC), 
	TITLE(NAME, DESC),
	PAGE_COUNT(BooksMeta.PAGE_COUNT, ASC);
	
	public final String field;
	public final SortDir dir;
	
	private SortBy(String field, SortDir dir) {
		this.field = field;
		this.dir = dir;
	}
}
