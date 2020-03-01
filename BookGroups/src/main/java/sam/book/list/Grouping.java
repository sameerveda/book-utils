package sam.book.list;

import java.nio.file.Paths;
import java.util.function.Function;

import sam.book.list.model.BookEntry;
import sam.book.list.view.BookEntryView;

public enum Grouping {
	NONE(null, false),
	FOLDER(o -> Paths.get(toBookEntry(o).getParentFolderSubpath()), false),
	FIRST_LETTER(o -> {
		char ch = toBookEntry(o).getName().charAt(0);
		return Character.isAlphabetic(ch) ? Character.toUpperCase(ch) : '#';
	}, false),
	YEAR(o -> toBookEntry(o).getYear(), true);

	public final Function<BookEntryView, ?> classifier;
	public final boolean reverse;

	private Grouping(Function<BookEntryView, ?> classifier, boolean reverse) {
		this.classifier = classifier;
		this.reverse = reverse;
	}
	private static BookEntry toBookEntry(BookEntryView o) {
		return o.getBookEntry();
	}
} 	 

