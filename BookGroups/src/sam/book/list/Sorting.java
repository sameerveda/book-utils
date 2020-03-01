package sam.book.list;

import java.util.Comparator;

import sam.book.list.model.BookEntry;
import sam.book.list.view.BookEntryView;

public enum Sorting {
	NAME(Comparator.comparing(entry -> toBookEntry(entry).name.toUpperCase())),
	READ_TIME(Comparator.comparingLong(entry -> toBookEntry(entry).getLastReadTime()).thenComparingLong(entry -> toBookEntry(entry).getAddTime()).reversed()),
	ADD_TIME(Comparator.comparingLong(entry -> toBookEntry(entry).getAddTime()).reversed());

	public final Comparator<Object> comparator;
	private Sorting(Comparator<Object> comparator) {
		this.comparator = comparator;
	}
	private static BookEntry toBookEntry(Object o) {
		return ((BookEntryView)o).getBookEntry();
	}
}
