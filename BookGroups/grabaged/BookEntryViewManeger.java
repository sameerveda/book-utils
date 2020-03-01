package sam.book.list.view;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javafx.beans.binding.NumberBinding;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import sam.book.list.Grouping;
import sam.book.list.Sorting;
import sam.book.list.main.Main;
import sam.book.list.model.BookEntry;
import sam.book.list.model.Model;
import sam.config.Session;
import sam.fx.alert.FxAlert;
import sam.fx.popup.FxPopupShop;
import sam.weak.WeakStore;

public class BookEntryViewManeger extends VBox {
	private static transient BookEntryViewManeger instance;

	public static BookEntryViewManeger getInstance(Model model) {
		if (instance == null) {
			synchronized (BookEntryViewManeger.class) {
				if (instance == null)
					instance = new BookEntryViewManeger(model);
			}
		}
		return instance;
	}
	
	private static final ReadOnlyIntegerWrapper thumbWidthProperty = new ReadOnlyIntegerWrapper();
	private static final SimpleDoubleProperty widthHeightRatioProperty = new SimpleDoubleProperty();
	private static NumberBinding thumbHeightProperty = thumbWidthProperty.multiply(widthHeightRatioProperty);
	
	public static ReadOnlyIntegerProperty  getThumbWidthProperty() {
		return thumbWidthProperty.getReadOnlyProperty();
	}
	public static NumberBinding getThumbHeightProperty() {
		return thumbHeightProperty;
	}
	
	private final Model model;
	private Sorting currentSorting;
	private Grouping currentGrouping;
	private boolean sortingReverse, groupingReverse;

	private BookEntryViewManeger(Model model) {
		super(10);
		this.model = model;
		setId("book-entry-view-container");
		setPadding(new Insets(0, 0, 50, 0));
	}
	public void init() {
		String grp = Grouping.class.getName();
		String srt = Sorting.class.getName();
		currentSorting = Session.get(srt, Sorting.ADD_TIME, Sorting::valueOf);
		currentGrouping = Session.get(grp, Grouping.NONE, Grouping::valueOf);
		sortingReverse = Session.get(srt+".reverse", false, s -> s.equals("true"));
		groupingReverse = Session.get(grp+".reverse", false, s -> s.equals("true"));
		thumbWidthProperty.set(Session.get("thumb.width", 200, Integer::parseInt)); 
		widthHeightRatioProperty.set(Session.get("thumb.ratio", 1.5, Double::parseDouble));
		model.setOnGroupChange(this::onGroupChange);
	}
	public void close() {
		String grp = Grouping.class.getName();
		String srt = Sorting.class.getName();

		Session.put(srt, String.valueOf(currentSorting));
		Session.put(grp, String.valueOf(currentGrouping));
		Session.put(srt+".reverse", String.valueOf(sortingReverse));
		Session.put(grp+".reverse", String.valueOf(groupingReverse));
		Session.put("thumb.width", String.valueOf(thumbWidthProperty.get()));
	}
	public void selectUnselectAll(boolean select) {
		unitGroups().flatMap(UnitGroup::stream).forEach(b -> b.setSelected(select));
	}
	public Sorting getCurrentSorting() {
		return currentSorting;
	}
	public Grouping getCurrentGrouping() {
		return currentGrouping;
	}
	public void removeSelected(Object ignore) {
		String str = getSelected()
				.map(BookEntryView::getBookEntry)
				.map(BookEntry::getName)
				.collect(Collectors.joining("\n"));

		if(str.isEmpty()) {
			FxPopupShop.showHidePopup("nothing selected", 1500);
			return;
		}

		if(!FxAlert.showConfirmDialog(str, "These book(s) will be deleted"))
			return;

		model.removeAll(unitGroups()
				.map(UnitGroup::removeSelected)
				.flatMap(List::stream)
				.map(BookEntryView::getBookEntry)
				.collect(Collectors.toList()));
	}
	public void openSelected(Object ignore) {
		Map<Boolean, List<BookEntryView>> map = 
				getSelected()
				.collect(Collectors.partitioningBy(bev -> {
					Path p = bev.getBookEntry().getFullPath();
					if(Files.exists(p)) {
						Main.getHostService().showDocument(p.toUri().toString());
						bev.getBookEntry().updateLastReadTime();
						return true;
					}
					return false;
				}));

		if(map.isEmpty() || map.values().stream().mapToInt(List::size).sum() == 0) {
			FxPopupShop.showHidePopup("nothing selected", 1500);
			return;
		}
		if(map.containsKey(false))
			FxAlert.showErrorDialog(null, "Files not found", map.get(false).stream().map(b -> b.getBookEntry().getRelativePath().toString()).collect(Collectors.joining("\n")));

		if(map.containsKey(true) && currentSorting == Sorting.READ_TIME)
			unitGroups().forEach(u -> u.sort(currentSorting, sortingReverse));
	}
	private Stream<BookEntryView> getSelected() {
		return getChildren()
				.stream()
				.map(n -> (UnitGroup)n)
				.flatMap(UnitGroup::getSelected)
				.filter(BookEntryView::isSelected);
	}
	private Stream<UnitGroup> unitGroups() {
		return getChildren()
				.stream()
				.map(n -> (UnitGroup)n);
	}
	private void onGroupChange(List<BookEntry> entries) {
		unitGroups().forEach(UnitGroup::clearPermanently);		
		groupingAction(currentGrouping, UnitGroup.mapToBookEntryView(entries.stream()));
		unitGroups().forEach(u -> u.sort(currentSorting, sortingReverse));
	}
	public void sortingAction(Sorting sorting){
		if(sorting == currentSorting) {
			unitGroups().forEach(UnitGroup::reverse);
			sortingReverse = !sortingReverse; 
		}
		else {
			sortingReverse = false;
			currentSorting = sorting;
			unitGroups().forEach(u -> u.sort(sorting, false));
		}
	}
	public void groupingAction(Grouping grouping){
		groupingAction(grouping, null);
	}
	@SuppressWarnings("unchecked")
	private void groupingAction(Grouping grouping, Stream<BookEntryView> elements){
		if(elements == null) {
			if(grouping == Grouping.NONE){
				if(currentGrouping == Grouping.NONE)
					return;

				currentGrouping = grouping;
				List<BookEntryView> list = unitGroups().flatMap(UnitGroup::walk).collect(Collectors.toList());
				resizeChildren(1);
				childAt(0).set(null, list);
				return;
			}

			if(grouping == currentGrouping) {
				groupingReverse = !groupingReverse;
				FXCollections.reverse(getChildren());
			}

			currentGrouping = grouping;
			groupingReverse = grouping.reverse;
		}
		if(elements != null && currentGrouping == Grouping.NONE) {
			resizeChildren(1);
			childAt(0).set(null, elements.collect(Collectors.toList()));
			return;
		}
		Map<Object, List<BookEntryView>> map = (elements != null ? elements : unitGroups().flatMap(UnitGroup::walk))
				.collect(Collectors.groupingBy(currentGrouping.classifier));

		resizeChildren(map.size());

		int index[] = {0};
		map.forEach((key, list) -> childAt(index[0]++).set(key, list));

		FXCollections.sort(getChildren(), Comparator.comparing(o -> (Comparable<Object>)(((UnitGroup)o).getKey())));
		if(groupingReverse)
			FXCollections.reverse(getChildren());
	}
	private UnitGroup childAt(int index) {
		return ((UnitGroup)getChildren().get(index));
	}
	private final WeakStore<Node> unitGroupCache = new WeakStore<>(UnitGroup::new); 
	
	private void resizeChildren(int newSize) {
		unitGroups().forEach(UnitGroup::clear);

		if(getChildren().size() == newSize)
			return;
		if(getChildren().size() < newSize) {
			while(getChildren().size() != newSize)
				getChildren().add(unitGroupCache.get());
		}
		else {
			List<Node> sublist = getChildren().subList(newSize, getChildren().size());
			unitGroupCache.addAll(sublist);
			sublist.clear(); 
		}
	}
	public void zoom(int amount) {
		thumbWidthProperty.set(thumbWidthProperty.get()+amount);
	}
	public void remove(BookEntry bookEntry) {
		model.remove(bookEntry);
	}
}
