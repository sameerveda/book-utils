package sam.book.list.main;

import static sam.fx.helpers.FxClassHelper.addClass;
import static sam.fx.helpers.FxMenu.menuitem;
import static sam.fx.helpers.FxMenu.radioMenuItemGroup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.sqlite.JDBC;

import javafx.application.Application;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Screen;
import javafx.stage.Stage;
import sam.book.list.Grouping;
import sam.book.list.Sorting;
import sam.book.list.model.Model;
import sam.book.list.view.ViewManeger;
import sam.books.BookUtils;
import sam.config.MyConfig;
import sam.config.Session;
import sam.fx.alert.FxAlert;
import sam.fx.clipboard.FxClipboard;
import sam.fx.popup.FxPopupShop;
import sam.sql.sqlite.SQLiteManeger;

public class Main extends Application {
    private final Logger logger = MyLoggerFactory.logger(getClass().getName());
    private final double VERSION = 1.24;
    
    public static void main(String[] args) throws IOException {
        Files.createDirectories(Paths.get("logs"));
        
        System.setProperty("java.util.logging.config.file","logging.properties");
        launch(args);
    }
    
    static {
        Session.setPath(Paths.get("."));
    }
    private static Stage stage;
    private final Model model = Model.getInstance(); 
    private final ViewManeger viewManeger = ViewManeger.getInstance(model);
    private final ComboBox<String> groupNameChoiceBox = new ComboBox<>(model.groupNamesProperty());
    
    private static HostServices hostServices;

    public static HostServices getHostService() {
        return hostServices;
    }
    public static Stage getStage() {
        return stage;
    }
    
    private BorderPane root;
    
    @Override
    public void start(Stage stage) throws Exception {
        Main.stage = stage;
        hostServices = getHostServices();

        root = new BorderPane(getViewManeger());
        root.setId("root-2");

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.setTitle("Book(s) Groups ("+VERSION+")");
        scene.getStylesheets().add("style.css");
        stage.getIcons().add(new Image("icon.png"));
        stage.show();

        stage.setX(Session.get("stage.x", 0d, Double::parseDouble));
        stage.setY(Session.get("stage.y", 0d, Double::parseDouble));
        Rectangle2D r2d = Screen.getPrimary().getBounds();
        stage.setWidth(Session.get("stage.width", r2d.getWidth()/2, Double::parseDouble));
        stage.setHeight(Session.get("stage.height", r2d.getHeight(), Double::parseDouble));
        
        Platform.runLater(this::start2);
    }
    private Node getTopNode() {
        return new BorderPane(getTitleGroupPane(), new MenuBar(getFileMenu(), getmenuMenu(), getViewMenu()), null, null, null);
    }
    private Node getViewManeger() {
        ScrollPane sp = new ScrollPane(viewManeger);
        sp.setHbarPolicy(ScrollBarPolicy.NEVER);
        sp.setVbarPolicy(ScrollBarPolicy.ALWAYS);
        sp.setId("book-entry-view-container-scroll-pane");
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        return sp;
    }
    private Menu getViewMenu() {
        return new Menu("View", null,
                menuitem("Zoom In", e -> viewManeger.zoom(50), ViewManeger.getThumbHeightProperty().greaterThan(400)),
                menuitem("Zoom Out", e -> viewManeger.zoom(-50), ViewManeger.getThumbHeightProperty().lessThan(200))
                );
    }
    private void start2() {
        try {
            FxPopupShop.setParent(stage);
            FxAlert.setParent(stage);
            FxPopupShop.setStyle("white", "#1B1B1B");

            viewManeger.init();
            model.init();
            root.setTop(getTopNode());
            String selectedGroup =  Session.get("selected.group");
            logger.fine(() -> "selected.group: "+selectedGroup);
            
            groupNameChoiceBox.getSelectionModel().selectedIndexProperty().addListener((p, o, n) -> Platform.runLater(() -> model.changeGroup(n.intValue())));
            
            if(selectedGroup != null && groupNameChoiceBox.getItems().contains(selectedGroup))
                groupNameChoiceBox.getSelectionModel().select(selectedGroup);
            else if(!groupNameChoiceBox.getItems().isEmpty())
                groupNameChoiceBox.getSelectionModel().select(0);
        } catch (Exception e) {
            FxAlert.showErrorDialog(null, "failed to start app", e, true);
            System.exit(0);
        }
    }
    @Override
    public void stop() throws Exception {
        viewManeger.close();
        model.save();

        Session.put("stage.x", String.valueOf((int)stage.getX()));
        Session.put("stage.y", String.valueOf((int)stage.getY()));
        Session.put("stage.width", String.valueOf((int)stage.getWidth()));
        Session.put("stage.height", String.valueOf((int)stage.getHeight()));

        if(groupNameChoiceBox.getSelectionModel().getSelectedItem() != null)
            Session.put("selected.group", groupNameChoiceBox.getSelectionModel().getSelectedItem());

        System.exit(0);
    }
    private Node getTitleGroupPane() {
        Text title = new Text();
        title.textProperty().bind(groupNameChoiceBox.getSelectionModel().selectedItemProperty());
        groupNameChoiceBox.setOnAction(e -> model.changeGroup(groupNameChoiceBox.getSelectionModel().getSelectedIndex()));
        BorderPane bp = new BorderPane(title, null, groupNameChoiceBox, null, null);
        BorderPane.setAlignment(title, Pos.CENTER_LEFT);
        bp.setId("title-group-container");
        addClass(title, "title");
        return bp;
    }
    private Menu getmenuMenu() {
        
        return new Menu("Menu", null,
                radioMenuItemGroup("Sort By", viewManeger.getCurrentSorting(), viewManeger::sortingAction, Sorting.values()), 
                radioMenuItemGroup("Group By", viewManeger.getCurrentGrouping(), viewManeger::groupingAction, Grouping.values()), 
                menuitem("select All", e -> viewManeger.selectUnselectAll(true)),
                menuitem("unselect All", e -> viewManeger.selectUnselectAll(false)),
                menuitem("open selected", viewManeger::openSelected),
                menuitem("remove selected", viewManeger::removeSelected),
                menuitem("copy selected to", viewManeger::copyselectedTo)
                );
    }

    private Menu getFileMenu() {
        return new Menu("File", null,
                menuitem("Add Book(s)", new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN), addNewBooksAction,  groupNameChoiceBox.getSelectionModel().selectedItemProperty().isNull()),
                menuitem("Create Group", new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN), addNewGroupAction),
                //TODO menuitem("import", importAction),
                //TODO menuitem("export", exportAction),
                menuitem("open books.db", e -> getHostServices().showDocument(Paths.get(MyConfig.BOOKLIST_DB).toUri().toString())),
                menuitem("open working dir", e -> getHostServices().showDocument(".")),
                menuitem("exit",new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN), e -> exit())
                );
    }

    private void exit() {
        try {
            stop();
        } catch (Exception e) {
            FxAlert.alertBuilder(AlertType.ERROR)
            .header("failed to stop")
            .content("force stop?")
            .buttons(ButtonType.YES, ButtonType.NO)
            .exception(e)
            .expanded(true)
            .showAndWait()
            .filter(b -> b == ButtonType.YES)
            .ifPresent(b -> System.exit(0));
        }
    }
    //TODO DELETE
    private static class TempBook {
        final String id;
        final String name;
        final String nameLowercased;
        final String year;
        final String pageCount;

        public TempBook(ResultSet rs) throws SQLException {
            this.id = rs.getString("_id");
            this.name = rs.getString("name");
            this.nameLowercased = name.toLowerCase();
            this.year = rs.getString("year");
            this.pageCount = rs.getString("page_count");
        }
        
        static List<TempBook> loadBooks() {
            ArrayList<TempBook> t = new ArrayList<>();
            
            try(SQLiteManeger c = new SQLiteManeger(BookUtils.DB)) {
                c.iterate("SELECT _id, name, year, page_count FROM Books", rs -> t.add(new TempBook(rs)));
            } catch (InstantiationException | IllegalAccessException | ClassNotFoundException 
                    | SQLException e) {
                FxAlert.showErrorDialog(BookUtils.DB, "failed to load books", e);
            }
            return t;
        }
    }
    final EventHandler<ActionEvent> addNewGroupAction = e_e -> {
        TextField tf = new TextField();
        TextArea ta = new TextArea();
        ta.setEditable(false);

        List<TempBook> books = TempBook.loadBooks();
        TableView<TempBook> table = getTable();

        tf.setOnKeyPressed(e -> {
            String s = tf.getText();
            table.getItems().clear();
            
            if(s == null || (s = s.trim()).isEmpty())
                ta.setText(null);
            else {
                String s2 = s.toLowerCase();
                ta.setText(
                        groupNameChoiceBox.getItems().stream()
                        .filter(s1 -> s1.toLowerCase().contains(s2))
                        .collect(Collectors.joining("\n"))
                        );
                books.stream()
                .filter(b -> b.nameLowercased.contains(s2))
                .forEach(table.getItems()::add);
            }
        });
        
        CheckBox add = new CheckBox("Add these to group");
        
        Platform.runLater(tf::requestFocus);

        HBox hbox = new HBox(5, new Text("Group Name: "),tf);
        HBox.setHgrow(tf, Priority.ALWAYS);
        hbox.setAlignment(Pos.CENTER_LEFT);

        FxAlert.alertBuilder(AlertType.CONFIRMATION)
        .content(hbox)
        .header("Add New Group")
        .expandableContent(new SplitPane(new VBox(10, new Text("  Similar Groups"), ta), new BorderPane(table, null, null, add, null)))
        .expanded(true)
        .showAndWait()
        .filter(b -> b == ButtonType.OK)
        .ifPresent(b -> {
            String s = tf.getText();
            if(s == null || (s = s.trim()).isEmpty()) {
                FxPopupShop.showHidePopup("no group name entered", 1500);
                return;
            }
            if(add.isSelected()) {
                if(table.getItems().isEmpty()) {
                    FxPopupShop.showHidePopup("no items in table", 1500);
                    return;
                }
                addGroup(s, false);
                Platform.runLater(() -> model.addBooks(table.getItems().stream().mapToInt(t -> Integer.parseInt(t.id)).toArray()));
            }
            else
                addGroup(s, true);
        });
    };
    private void addGroup(String groupName, boolean show) {
        String current = groupNameChoiceBox.getSelectionModel().getSelectedItem();
        if(current != null && current.equalsIgnoreCase(groupName)) {
            if(show) FxPopupShop.showHidePopup("group exists: "+groupName, 3000);
        }
        else {
            String match = 
                    groupNameChoiceBox.getItems().stream()
                    .filter(s1 -> s1.equalsIgnoreCase(groupName))
                    .findFirst()
                    .orElse("");
            
            if(match.equals("")) {
                model.addGroup(groupName);
                groupNameChoiceBox.getSelectionModel().select(groupName);
                if(show) FxPopupShop.showHidePopup("group added: "+groupName, 3000);
            }
            else {
                if(show) FxPopupShop.showHidePopup("group exists: "+match, 3000);
                groupNameChoiceBox.getSelectionModel().select(match);
            }
        }
    }
    @SuppressWarnings("rawtypes")
    private TableView<TempBook> getTable() {
        TableView<TempBook> table = new TableView<>();
        table.getSelectionModel().setCellSelectionEnabled(true);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setEditable(false);
        
        TableColumn<TempBook, String> cl = new TableColumn<>("id");
        cl.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().id));
        table.getColumns().add(cl);
        
        cl = new TableColumn<>("name");
        cl.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name));
        table.getColumns().add(cl);
        
        cl = new TableColumn<>("year");
        cl.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().year));
        table.getColumns().add(cl);
        
        cl = new TableColumn<>("page_count");
        cl.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().pageCount));
        table.getColumns().add(cl);
        
        table.setOnKeyReleased(e -> {
            if(e.getCode() == KeyCode.DELETE)
                table.getItems().removeAll(new ArrayList<>(table.getSelectionModel().getSelectedItems()));
            if(e.getCode() == KeyCode.C && e.isShortcutDown()) {
                List<TablePosition> tps  = table.getSelectionModel().getSelectedCells();
                StringBuilder sb = new StringBuilder();
                for (TablePosition t : tps)
                    sb.append(t.getTableColumn().getCellData(t.getRow())).append('\n');
                FxClipboard.copyToClipboard(sb.toString());
            }
        });
        
        return table;
    }

    final EventHandler<ActionEvent> addNewBooksAction = e_e -> {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("add new books");
        dialog.initOwner(stage);
        dialog.setHeaderText("enter id(s) seperated by space");
        dialog.setContentText("id(s)");

        dialog.showAndWait()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .ifPresent(value -> {
            int[] array = Stream.of(value.split("\\D+"))
                    .filter(s -> s.matches("\\d+"))
                    .mapToInt(Integer::parseInt)
                    .distinct()
                    .toArray();

            if(array.length == 0) {
                FxPopupShop.showHidePopup("no input", 2000);
                return;
            }

            FxAlert.alertBuilder(AlertType.INFORMATION)
            .title("Result")
            .header("New Books added?")
            .expandableText(model.addBooks(array))
            .expanded(true)
            .show();
        });

    };
    final EventHandler<ActionEvent> importAction = e -> {/* TODO Copy from */};
    final EventHandler<ActionEvent> exportAction = e -> {/* TODO Copy from */};
}
