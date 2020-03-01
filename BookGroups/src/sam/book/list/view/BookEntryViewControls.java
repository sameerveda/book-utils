package sam.book.list.view;

import static sam.fx.helpers.FxHelpers.button;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;

import javafx.animation.FadeTransition;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import sam.book.list.main.Main;
import sam.book.list.model.BookEntry;
import sam.fx.alert.FxAlert;
import sam.properties.myconfig.MyConfig;

public class BookEntryViewControls extends VBox {
	private static volatile BookEntryViewControls instance;
	private BookEntryView parent;
	private final FadeTransition transition;
	private WeakReference<AboutBook> aboutBook = new WeakReference<AboutBook>(null); 

	public static BookEntryViewControls getInstance() {
		if (instance == null) {
			synchronized (BookEntryViewControls.class) {
				if (instance == null)
					instance = new BookEntryViewControls();
			}
		}
		return instance;
	}
	private BookEntryViewControls() {
		super(5);
		setId("book-entry-view-controls");
		Pane pane = new Pane();
		getChildren()
		.addAll(
				button("Remove", "Cancel_30px.png", e -> parent.remove()),
				pane,
				button("Open Containing Dir", "Folder_30px.png", openContainingDirAction),
				button("Open File", "PDF_30px.png", openFileAction),
				button("About Book", "Info_30px.png", AboutBookAction)
				);
		
		VBox.setVgrow(pane, Priority.ALWAYS);
		transition = new FadeTransition(Duration.millis(500), this);
		transition.setFromValue(0);
		transition.setToValue(1);
	}
	private final EventHandler<ActionEvent> AboutBookAction = e_e -> {
		BookEntry be = parent.getBookEntry();
		AboutBook ab =  aboutBook.get();
		
		if(ab == null) {
			ab = new AboutBook();
			aboutBook = new WeakReference<>(ab);
		}
		ab.changeBookEntry(be);
			
	};
	private final EventHandler<ActionEvent> openContainingDirAction = e_e -> {
		BookEntry be = parent.getBookEntry();
		Path p = MyConfig.BOOKLIST_ROOT.resolve(be.getPath());
		if(Files.exists(p)) {
			if(System.getProperty("os.name").toLowerCase().contains("windows")) {
				try {
					Runtime.getRuntime().exec("explorer /select, \""+p.getFileName()+"\"", null, p.getParent().toFile());
				} catch (IOException e) {
					FxAlert.showErrorDialog(p, "Failed to open File", e);
				}
			}
			else
				Main.getHostService().showDocument(p.getParent().toUri().toString());
		}
		else if(Files.exists(p.getParent())) 
			Main.getHostService().showDocument(p.getParent().toUri().toString());
		else 
			FxAlert.showErrorDialog(p, "File not found", null);
	};
	private final EventHandler<ActionEvent> openFileAction = e_e -> {
		BookEntry be = parent.getBookEntry();
		Path p = MyConfig.BOOKLIST_ROOT.resolve(be.getPath());
		if(Files.exists(p))
			Main.getHostService().showDocument(p.toUri().toString());
		else
			FxAlert.showErrorDialog(p, "File not found", null);
	};
	
	public void switchView(BookEntryView newParent) {
		transition.stop();
		if(parent != null)
			parent.getChildren().remove(this);
		parent = newParent;
		if(newParent == null)
			return;
		newParent.setRight(this);
		transition.play();
	}
}
