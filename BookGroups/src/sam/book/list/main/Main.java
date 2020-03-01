package sam.book.list.main;

import static sam.fx.helpers.FxHelpers.addClass;
import static sam.fx.helpers.FxHelpers.menuitem;
import static sam.fx.helpers.FxHelpers.radioMenuitemGroupMenu;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.sqlite.JDBC;

import javafx.application.Application;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
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
import sam.book.list.view.BookEntryViewManeger;
import sam.fx.alert.FxAlert;
import sam.fx.popup.FxPopupShop;
import sam.properties.myconfig.MyConfig;
import sam.properties.session.Session;

public class Main extends Application {
	public static final Path THUMB_CACHE_FOLDER = Paths.get("cache");
	private static Stage stage;
	private final Model model = Model.getInstance(); 
	private final BookEntryViewManeger viewManeger = BookEntryViewManeger.getInstance(model);
	private final ComboBox<String> groupNameChoices = new ComboBox<>(model.groupNamesProperty());
	private static HostServices hostServices;

	public static HostServices getHostService() {
		return hostServices;
	}
	public static Stage getStage() {
		return stage;
	}
	public static void main(String[] args) {
		launch(args);
	}
	@Override
	public void start(Stage stage) throws Exception {
		Main.stage = stage;
		hostServices = getHostServices();
		FxPopupShop.setParent(stage);
		FxAlert.setParent(stage);
		FxPopupShop.setStyle("white", "#1B1B1B");

		ScrollPane sp = new ScrollPane(viewManeger);
		sp.setHbarPolicy(ScrollBarPolicy.NEVER);
		sp.setVbarPolicy(ScrollBarPolicy.ALWAYS);
		sp.setId("book-entry-view-container-scroll-pane");
		sp.setFitToWidth(true);
		sp.setFitToHeight(true);
		BorderPane root = new BorderPane(sp);
		root.setId("root-2");
		root.setTop(new BorderPane(getTitleGroupPane(), new MenuBar(getFileMenu(), getmenuMenu(), getViewMenu()), null, null, null));

		Scene scene = new Scene(root);
		stage.setScene(scene);
		stage.setTitle("Book(s) Groups");
		scene.getStylesheets().add(ClassLoader.getSystemResource("style.css").toExternalForm());
		stage.getIcons().add(new Image(ClassLoader.getSystemResource("icon.png").toExternalForm()));
		stage.show();
		stage.setX(Session.get("stage.x", 0d, Double::parseDouble));
		stage.setY(Session.get("stage.y", 0d, Double::parseDouble));
		Rectangle2D r2d = Screen.getPrimary().getBounds();
		stage.setWidth(Session.get("stage.width", r2d.getWidth()/2, Double::parseDouble));
		stage.setHeight(Session.get("stage.height", r2d.getHeight(), Double::parseDouble));

		// FxUtils.liveReloadCss(getHostServices(), "rsrc/style.css", scene);

		Platform.runLater(this::start2);
	}
	private Menu getViewMenu() {
		return new Menu("View", null,
				menuitem("Zoom In", e -> viewManeger.zoom(50), BookEntryViewManeger.getThumbHeightProperty().greaterThan(400)),
				menuitem("Zoom Out", e -> viewManeger.zoom(-50), BookEntryViewManeger.getThumbHeightProperty().lessThan(200))
				);
	}
	private void start2() {
		try {
			Files.createDirectories(THUMB_CACHE_FOLDER);
			Class.forName(JDBC.class.getCanonicalName());
			model.load();
			String selectedGroup =  Session.get("selected.group");
			if(selectedGroup != null && groupNameChoices.getItems().contains(selectedGroup))
				groupNameChoices.getSelectionModel().select(selectedGroup);
			else if(!groupNameChoices.getItems().isEmpty())
				groupNameChoices.getSelectionModel().select(0);
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

		if(groupNameChoices.getSelectionModel().getSelectedItem() != null)
			Session.put("selected.group", groupNameChoices.getSelectionModel().getSelectedItem());

		System.exit(0);
		super.stop();
	}
	private Node getTitleGroupPane() {
		Text title = new Text();
		title.textProperty().bind(groupNameChoices.getSelectionModel().selectedItemProperty());
		groupNameChoices.setOnAction(e -> {
			String str = model.changeGroup(groupNameChoices.getSelectionModel().getSelectedItem());
			if(str != null && !str.isEmpty())
				FxAlert.showErrorDialog(null, "Book(s) not found", str);
		});
		BorderPane bp = new BorderPane(title, null, groupNameChoices, null, null);
		BorderPane.setAlignment(title, Pos.CENTER_LEFT);
		bp.setId("title-group-container");
		addClass(title, "title");
		return bp;
	}
	private Menu getmenuMenu() {
		return new Menu("Menu", null,
				radioMenuitemGroupMenu("Sort By", Sorting.values(), viewManeger.getCurrentSorting(), viewManeger::sortingAction), 
				radioMenuitemGroupMenu("Group By", Grouping.values(), viewManeger.getCurrentGrouping(), viewManeger::groupingAction), 
				menuitem("select All", e -> viewManeger.selectUnselectAll(true)),
				menuitem("unselect All", e -> viewManeger.selectUnselectAll(false)),
				menuitem("open selected", viewManeger::openSelected),
				menuitem("remove selected", viewManeger::removeSelected)
				);
	}

	private Menu getFileMenu() {
		return new Menu("File", null, 
				menuitem("Add Book(s)", addNewBooksAction, groupNameChoices.getSelectionModel().selectedItemProperty().isNull()),
				menuitem("Add new Group", addNewGroupAction),
				//TODO menuitem("import", importAction),
				//TODO menuitem("export", exportAction),
				menuitem("open books.db", e -> getHostServices().showDocument(MyConfig.BOOKLIST_DB.toUri().toString())),
				menuitem("open working dir", e -> getHostServices().showDocument(".")),
				menuitem("exit",new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN), e -> exit())
				);
	}

	private void exit() {
		try {
			stop();
		} catch (Exception e) {
			FxAlert.alertBuilder(AlertType.ERROR)
			.headerText("failed to stop")
			.contentText("force stop?")
			.buttonTypes(ButtonType.YES, ButtonType.NO)
			.exception(e)
			.expanded(true)
			.showAndWait()
			.filter(b -> b == ButtonType.YES)
			.ifPresent(b -> System.exit(0));
		}
	}

	final EventHandler<ActionEvent> addNewGroupAction = e_e -> {
		TextField tf = new TextField();
		TextArea ta = new TextArea();
		ta.setEditable(false);

		tf.setOnKeyPressed(e -> {
			String s = tf.getText();
			if(s == null || (s = s.trim()).isEmpty())
				ta.setText(null);
			else {
				String s2 = s.toLowerCase();
				ta.setText(
						groupNameChoices.getItems().stream()
						.filter(s1 -> s1.toLowerCase().contains(s2))
						.collect(Collectors.joining("\n"))
						);	
			}
		});
		Platform.runLater(tf::requestFocus);

		HBox hbox = new HBox(5, new Text("Group Name: "),tf);
		HBox.setHgrow(tf, Priority.ALWAYS);
		hbox.setAlignment(Pos.CENTER_LEFT);

		FxAlert.alertBuilder(AlertType.CONFIRMATION)
		.content(hbox)
		.headerText("Add New Group")
		.expandableContent(new VBox(10, new Text("  Similar Groups"), ta))
		.expanded(true)
		.showAndWait()
		.filter(b -> b == ButtonType.OK)
		.ifPresent(b -> {
			String s = tf.getText();
			if(s == null || (s = s.trim()).isEmpty())
				FxPopupShop.showHidePopup("no group name entered", 1500);
			else {
				String current = groupNameChoices.getSelectionModel().getSelectedItem();
				if(current != null && current.equalsIgnoreCase(s))
					FxPopupShop.showHidePopup("group exists: "+s, 3000);
				else {
					String s2 = s;
					String match = 
							groupNameChoices.getItems().stream()
							.filter(s1 -> s1.equalsIgnoreCase(s2))
							.findFirst()
							.orElse("");
					if(match.equals("")) {
						model.addGroup(s2);
						groupNameChoices.getSelectionModel().select(s2);
						FxPopupShop.showHidePopup("group added: "+s2, 3000);
					}
					else {
						FxPopupShop.showHidePopup("group exists: "+match, 3000);
						groupNameChoices.getSelectionModel().select(match);
					}
				}
			}
		});
	};

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
			.headerText("New Books added?")
			.expandableText(model.addBooks(array))
			.expanded(true)
			.show();
		});

	};
	final EventHandler<ActionEvent> importAction = e -> {/* TODO Copy from */};
	final EventHandler<ActionEvent> exportAction = e -> {/* TODO Copy from */};
}
