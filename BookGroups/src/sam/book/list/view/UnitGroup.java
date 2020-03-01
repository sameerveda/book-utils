package sam.book.list.view;

import static sam.fx.helpers.FxHelpers.addClass;

import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.TilePane;
import sam.book.list.Sorting;
import sam.book.list.model.BookEntry;

public class UnitGroup extends BorderPane {
	private Object key;
	private Label title = new Label();
	private TilePane children = new TilePane(10, 10);

	UnitGroup() {
		addClass(this, "unit-group");
		title.setMaxWidth(Double.MAX_VALUE);
		addClass(title, "title");
		addClass(children, "children");
		setCenter(children);
		setTop(title);
		children.setPrefColumns(10);
	}
	public void set(Object key, List<BookEntryView> children) {
		setKey(key);
		setChildren(children);
	}
	Object getKey() {
		return key;
	}
	void setKey(Object key) {
		this.key = key;
		title.setVisible(key != null);
		title.setText(key == null ? null : key.toString());
	}
	public void remove(BookEntryView bookEntryView) {
		children().remove(bookEntryView);
		((BookEntryViewManeger)getParent()).remove(bookEntryView.getBookEntry());
	}
	List<BookEntryView> removeSelected() {
		List<BookEntryView> list = getSelected().collect(Collectors.toList());
		children().removeIf(b -> ((BookEntryView)b).isSelected());
		return list;
	}
	Stream<BookEntryView> getSelected() {
		return children().stream()
				.map(b -> (BookEntryView)b)
				.filter(BookEntryView::isSelected);
	}
	private ObservableList<Node> children() {
		return this.children.getChildren();
	}
	public Stream<BookEntryView> walk() {
		return children().stream().map(n -> (BookEntryView)n);
	}
	void setChildren(List<BookEntryView> children) {
		children().setAll(children);
	}
	void clear() {
		children().clear();
	}
	private static final LinkedList<WeakReference<Node>> bookEntryViewCache = new LinkedList<>();
	void clearPermanently() {
		for (Node n : children()) {
			((BookEntryView)n).setSelected(false);
			bookEntryViewCache.add(new WeakReference<Node>(n));
		}
		children().clear();
	}
	static Stream<BookEntryView> mapToBookEntryView(Stream<BookEntry> stream) {
		return stream.map(UnitGroup::createBookEntryView);
	}
	static BookEntryView createBookEntryView(BookEntry be) {
		if(bookEntryViewCache.isEmpty())
			return new BookEntryView(be);
		else {
			Node bev = bookEntryViewCache.pop().get();
			if(bev == null)
				return new BookEntryView(be);
			else
				return ((BookEntryView)bev).setBookEntry(be);
		}
	}
	void reverse() {
		FXCollections.reverse(children());
	}
	void sort(Sorting sorting, boolean reverse) {
		FXCollections.sort(children(), reverse ? sorting.comparator.reversed() : sorting.comparator); 
	}
}
