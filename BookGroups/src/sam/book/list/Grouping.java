package sam.book.list;

import java.nio.file.Path;
import java.util.function.Function;

import sam.book.list.model.BookEntry;
import sam.book.list.view.BookEntryView;

public enum Grouping {
	NONE(null, false),
	FOLDER(o -> {
		Path p = toBookEntry(o).getPath();
		return p.subpath(0, p.getNameCount() - 1);
	}, false),
	FIRST_LETTER(o -> {
		char ch = toBookEntry(o).name.charAt(0);
		return Character.isAlphabetic(ch) ? Character.toUpperCase(ch) : '#';
	}, false),
	YEAR(o -> toBookEntry(o).year, true);

	public final Function<Object, ?> classifier;
	public final boolean reverse;

	private Grouping(Function<Object, ?> classifier, boolean reverse) {
		this.classifier = classifier;
		this.reverse = reverse;
	}
	private static BookEntry toBookEntry(Object o) {
		return ((BookEntryView)o).getBookEntry();
	}
} 	 

