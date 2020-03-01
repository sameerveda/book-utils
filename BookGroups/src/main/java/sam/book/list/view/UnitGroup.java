package sam.book.list.view;

import static sam.fx.helpers.FxClassHelper.addClass;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.TilePane;

class UnitGroup extends BorderPane {
    private Object key;
    private final SimpleObjectProperty<Object> keyPropery = new SimpleObjectProperty<>();
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
    public UnitGroup(List<BookEntryView> children) {
        this();
        set(null, children);
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
        keyPropery.set(key);
        title.setVisible(key != null);
        title.textProperty().bind(Bindings.concat(keyPropery, "  (", Bindings.size(children()), ")"));
    }
    List<BookEntryView> removeSelected() {
        List<BookEntryView> list = getSelected().collect(Collectors.toList());
        children().removeIf(b -> ((BookEntryView)b).isSelected());
        return list;
    }
    Stream<BookEntryView> getSelected() {
        return stream().filter(BookEntryView::isSelected);
    }
    public Stream<BookEntryView> stream(){
        return children().stream()
                .map(b -> (BookEntryView)b);
    }
    private ObservableList<Node> children() {
        return this.children.getChildren();
    }
    public void addChild(BookEntryView b) {
        children().add(b);
    }
    public void remove(BookEntryView bookEntryView) {
        children().remove(bookEntryView);
    }
    void setChildren(List<BookEntryView> children) {
        children().setAll(children);
    }
    public void addChildren(List<BookEntryView> list) {
        children().addAll(list);
    }
    public boolean isEmpty() {
        return children().isEmpty();
    }
    
    void clear() {
        children().clear();
    }
    void reverse() {
        FXCollections.reverse(children());
    }
    void sort(GroupSort groupSort) {
        FXCollections.sort(children(), groupSort.getSorter()); 
    }
    
}
