package sam.book.list.view;

import static sam.fx.helpers.FxButton.button;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;

import javafx.animation.FadeTransition;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import sam.book.list.main.Main;
import sam.book.list.model.BookEntry;
import sam.book.list.model.Model;
import sam.books.BookStatus;
import sam.config.Session;
import sam.fx.alert.FxAlert;
import sam.fx.clipboard.FxClipboard;
import sam.fx.helpers.FxClassHelper;
import sam.fx.popup.FxPopupShop;

class Controls extends BorderPane {
	private static volatile Controls instance;
	private BookEntryView bookEntryView;
	private final FadeTransition transition;
	private WeakReference<AboutBook> aboutBook = new WeakReference<AboutBook>(null);
	private final Label about = new Label();

	public static Controls getInstance() {
		if (instance == null) {
			synchronized (Controls.class) {
				if (instance == null)
					instance = new Controls();
			}
		}
		return instance;
	}
	private Controls() {
		Pane pane = new Pane();
		setId("book-entry-view-controls");
		Button moveRead;

		VBox v = new VBox(5, 
				button("Remove", "Cancel_30px.png", e -> {
					BookEntry b = bookEntryView.getBookEntry();
					if(FxAlert.showConfirmDialog(b.getBookId()+"  "+b.getName(), "Sure to Remove?"))
						Model.getInstance().remove(b); 
				}),
				moveRead = button("change book status", "Checked Checkbox_30px.png", e -> changeStatus()),
				pane,
				button("Open Containing Dir", "Folder_30px.png", openContainingDirAction),
				button("Open File", "PDF_30px.png", openFileAction),
				button("About Book", "Info_30px.png", AboutBookAction)
				);

		FxClassHelper.addClass(v, "right");
		setRight(v);

		about.setOnMouseClicked(e -> {
			if(e.getClickCount() > 1) {
				FxClipboard.copyToClipboard(about.getText());
				FxPopupShop.showHidePopup("copied", 1500);
			}
		});
		setCenter(about);
		FxClassHelper.setClass(about, "about");
		BorderPane.setAlignment(about, Pos.BOTTOM_LEFT);

		moveRead.setVisible(Session.get("move.read", false, Boolean::valueOf));

		VBox.setVgrow(pane, Priority.ALWAYS);
		transition = new FadeTransition(Duration.millis(500), v);
		transition.setFromValue(0);
		transition.setToValue(1);
	}
	private void changeStatus() {
		if(bookEntryView.getBookEntry() == null)
			return;
		
		ChoiceDialog<BookStatus> dialog = new ChoiceDialog<BookStatus>(bookEntryView.getBookEntry().getStatus(), BookStatus.values());
		dialog.setTitle("Change Status");
		dialog.setContentText("Status");
		dialog.setHeaderText("Change Status");
		dialog.showAndWait().ifPresent(bookEntryView::changeStatus);
	}
	private final EventHandler<ActionEvent> AboutBookAction = e_e -> {
		BookEntry be = bookEntryView.getBookEntry();
		AboutBook ab =  aboutBook.get();

		if(ab == null) {
			ab = new AboutBook();
			aboutBook = new WeakReference<>(ab);
		}
		ab.changeBookEntry(be);

	};
	private final EventHandler<ActionEvent> openContainingDirAction = e_e -> {
		BookEntry be = bookEntryView.getBookEntry();
		Path p = be.getFullPath();
		if(Files.exists(p)) {
			if(System.getProperty("os.name").toLowerCase().contains("windows")) {
				try {
					Runtime.getRuntime().exec("explorer /select, \""+p.getFileName()+"\"", null, p.getParent().toFile());
				} catch (IOException e) {
					FxAlert.showErrorDialog(p, "Failed to open File", e);
				}
			} else
				Main.getHostService().showDocument(p.getParent().toUri().toString());
		}
		else if(Files.exists(p.getParent())) 
			Main.getHostService().showDocument(p.getParent().toUri().toString());
		else 
			FxAlert.showErrorDialog(p, "File not found", null);
	};
	private final EventHandler<ActionEvent> openFileAction = e_e -> {
		BookEntry be = bookEntryView.getBookEntry();
		Path p = be.getFullPath();
		if(Files.exists(p)) {
			Main.getHostService().showDocument(p.toUri().toString());
			be.updateLastReadTime();
		}
		else
			FxAlert.showErrorDialog(p, "File not found", null);
	};

	public void switchView(BookEntryView newView) {
		transition.stop();
		if(bookEntryView != null)
			bookEntryView.getChildren().remove(this);
		bookEntryView = newView;
		if(newView == null)
			return;

		BookEntry b = newView.getBookEntry();
		about.setText(b.getBookId() + "\n"+ 
				b.getAuthor() + "\n"+ 
				b.getIsbn() + "\n"+ 
				b.getYear() + "\n");

		newView.getChildren().add(this);
		transition.play();
	}
}
