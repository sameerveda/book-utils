package sam.book.list.view;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javafx.beans.binding.NumberBinding;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import sam.book.list.Grouping;
import sam.book.list.Sorting;
import sam.book.list.main.Main;
import sam.book.list.model.BookEntry;
import sam.book.list.model.Model;
import sam.book.list.model.ModelEvent;
import sam.config.MyConfig;
import sam.config.Session;
import sam.fx.alert.FxAlert;
import sam.fx.popup.FxPopupShop;
import sam.weak.WeakStore;

public class ViewManeger extends VBox implements ModelEvent {
    private static volatile ViewManeger instance;
    private final Logger logger = MyLoggerFactory.logger(getClass().getName());

    public static ViewManeger getInstance(Model model) {
        if (instance == null) {
            synchronized (ViewManeger.class) {
                if (instance == null)
                    instance = new ViewManeger(model);
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
    private GroupSort groupSort; 

    private final WeakStore<UnitGroup> groups = new WeakStore<>(UnitGroup::new);
    private final List<BookEntryView> entries = new ArrayList<>();

    private ViewManeger(Model model) {
        super(10);
        this.model = model;
        setId("book-entry-view-container");
        setPadding(new Insets(0, 0, 50, 0));
    }
    public void init() throws IOException {
        groupSort = GroupSort.getInstance();
        thumbWidthProperty.set(Session.get("thumb.width", 200, Integer::parseInt)); 
        widthHeightRatioProperty.set(Session.get("thumb.ratio", 1.5, Double::parseDouble));
        model.addListener(this);

    }
    public void close() throws IOException {
        groupSort.save();
        Session.put("thumb.width", String.valueOf(thumbWidthProperty.get()));
    }
    public void selectUnselectAll(boolean select) {
        for (BookEntryView b : entries)
            b.setSelected(select);
    }
    public Sorting getCurrentSorting() {
        return groupSort.getSorting();
    }
    public Grouping getCurrentGrouping() {
        return groupSort.getGrouping();
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

        model.removeAll(entries.stream().filter(BookEntryView::isSelected).map(BookEntryView::getBookEntry).collect(Collectors.toList()));
    }
    public void copyselectedTo(ActionEvent e) {
        List<Path> paths = getSelected()
                .map(BookEntryView::getBookEntry)
                .map(BookEntry::getFullPath)
                .collect(Collectors.toList());

        if(paths.isEmpty()) {
            FxPopupShop.showHidePopup("nothing selected", 1500);
            return;
        }
        DirectoryChooser choose = new DirectoryChooser();
        choose.setInitialDirectory(new File(MyConfig.COMMONS_DIR));
        choose.setTitle("save book to ");

        File file = choose.showDialog(Main.getStage());

        if(file == null) {
            FxPopupShop.showHidePopup("cancelled", 1000);
            return;
        }

        try {
            Path root = file.toPath();
            Files.createDirectories(root);

            for (Path path : paths) {
                try {
                    Files.copy(path, root.resolve(path.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e2) {
                    FxAlert.showErrorDialog(file, "failed to create dir", e);
                }
            }
            FxPopupShop.showHidePopup("Finished copy", 2000);
        } catch (Exception e2) {
            FxAlert.showErrorDialog(file, "failed to create dir", e);
        }

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
            FxAlert.showErrorDialog(null, "Files not found", map.get(false).stream().map(b -> b.getBookEntry().getParentFolderSubpath()+"\\"+b.getBookEntry().getFileName()).collect(Collectors.joining("\n")));

        if(map.containsKey(true) && groupSort.getSorting() == Sorting.READ_TIME)
            forEach(u -> u.sort(groupSort));
    }
    private Stream<BookEntryView> getSelected() {
        return getChildren()
                .stream()
                .map(n -> (UnitGroup)n)
                .flatMap(UnitGroup::getSelected)
                .filter(BookEntryView::isSelected);
    }

    public void sortingAction(Sorting sorting){
        Objects.requireNonNull(sorting);

        if(sorting == groupSort.getSorting()) {
            forEach(UnitGroup::reverse);
            groupSort.flipSorting();
        } else {
            groupSort.set(sorting, false);
            sortingAction();
        }
    }
    public void sortingAction(){
        forEach(u -> u.sort(groupSort));
    }

    public void groupingAction(Grouping grouping) {
        if(groupSort.getGrouping() == grouping) {
            reverse();
            groupSort.flipGrouping();
            return;
        }
        groupSort.set(grouping, grouping.reverse);
        groupingAction();
    }
    private synchronized void groupingAction(){
        forEach(unit -> {
            unit.clear();
            groups.add(unit);
        });

        getChildren().clear();
        if(entries.isEmpty()) 
            return;

        if(groupSort.getGrouping() == Grouping.NONE) {
            UnitGroup grp = groups.poll();
            grp.set(null, entries);
            getChildren().add(grp);
            sortingAction();
            return;
        }

        HashMap<Object, UnitGroup> map = new HashMap<>();
        Function<Object, UnitGroup> computer = key -> {
            UnitGroup unit = groups.poll();
            unit.setKey(key);
            return unit;
        };

        Function<BookEntryView, ?> classifier = groupSort.getClassifier();

        for (BookEntryView b : entries) {
            Object key = classifier.apply(b);
            map.computeIfAbsent(key, computer).addChild(b);
        }
        getChildren().setAll(map.values());
        map.clear();
        sort();
        sortingAction();
    }
    public void zoom(int amount) {
        thumbWidthProperty.set(thumbWidthProperty.get()+amount);
    }
    private final WeakStore<BookEntryView> bookEntryViewCache = new WeakStore<>();

    @Override
    public void groupChanged(String groupName, List<BookEntry> currentItems) {
        logger.fine(() -> "group change: to: "+groupName+"  children-size: " +currentItems.size());
        bookEntryViewCache.addAll(entries);
        entries.clear();

        for (BookEntry b : currentItems)
            entries.add(create(b));

        groupSort.changeGroup(groupName);
        groupingAction();
    }
    private BookEntryView create(BookEntry b) {
        BookEntryView be = bookEntryViewCache.poll();
        if(be == null)
            be = new BookEntryView(b);
        else
            be.setBookEntry(b);

        return be;
    }
    @Override
    public void dataSaved() {}

    @Override
    public void removed(List<BookEntry> removeditems, List<BookEntry> currentItems) {
        entries.removeIf(be -> {
            if(removeditems.contains(be.getBookEntry())) {
                ((UnitGroup)be.getParent().getParent()).remove(be);
                return true;
            }
            return false;
        });
        getChildren().removeIf(u -> ((UnitGroup)u).isEmpty());
    }
    @Override
    public void added(List<BookEntry> addedItems, List<BookEntry> currentItems) {
        if(addedItems.isEmpty())
            return;

        List<BookEntryView> list = addedItems.stream().map(this::create).collect(Collectors.toList());
        entries.addAll(list);

        if(groupSort.getGrouping() == Grouping.NONE) {
            if(isEmpty())
                getChildren().add(new UnitGroup(entries));
            else
                ((UnitGroup)getChildren().get(0)).addChildren(list);
            return ;
        }

        Map<Object, List<BookEntryView>> map = list.stream().collect(Collectors.groupingBy(groupSort.getClassifier()));
        Map<Object, UnitGroup> unitmap = new HashMap<>();
        forEach(u -> unitmap.put(u.getKey(), u));

        boolean[] modified = {false};

        map.forEach((key, cldrn) -> {
            UnitGroup u = unitmap.get(key);
            if(u != null)
                u.addChildren(cldrn);
            else {
                u = new UnitGroup(); 
                getChildren().add(u);
                u.set(key, cldrn);
                modified[0] = true;
            }
            u.sort(groupSort);
        });

        if(modified[0])
            sort();

        unitmap.clear();
        map.clear();

    }
    private boolean isEmpty() {
        return getChildren().isEmpty();
    }
    private void forEach(Consumer<UnitGroup> c) {
        for (Node n : getChildren()) 
            c.accept((UnitGroup) n);
    }
    private void reverse() {
        FXCollections.reverse(getChildren());
    }
    @SuppressWarnings("unchecked")
    private void sort() {
        Comparator<Object> c = Comparator.comparing(o -> (Comparable<Object>)(((UnitGroup)o).getKey()));
        if(groupSort.isGroupingReverse())
            c = c.reversed();
        FXCollections.sort(getChildren(), c);
    }
}
