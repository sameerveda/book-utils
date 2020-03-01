package sam.book.list.view;

import static sam.fx.helpers.FxHelpers.addClass;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.TextAlignment;
import sam.book.list.main.Main;
import sam.book.list.model.BookEntry;
import sam.fx.alert.FxAlert;
import sam.properties.myconfig.MyConfig;

public class BookEntryView extends BorderPane  implements EventHandler<MouseEvent> {
	public static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
	private BookEntry bookEntry;
	private boolean selected;

	private static final BookEntryViewControls controls = BookEntryViewControls.getInstance();
	private static BlockingQueue<BookEntryView> loadFromPdf = new LinkedBlockingQueue<>();
	private static Set<Integer> pdfFailedBookEntryIds = Collections.synchronizedSet(new HashSet<>());

	static {
		Thread t = new Thread(() -> {
			while(true) {
				BookEntryView view = null;
				try {
					view = loadFromPdf.take();
				} catch (InterruptedException e) {
					System.out.println("loadFromPdf.take():  "+e);
				}
				pdfFailedBookEntryIds.add(view.getBookEntry().id);
				BookEntryView v2 = view;

				Path book = MyConfig.BOOKLIST_ROOT.resolve(view.getBookEntry().getPath());
				if(Files.notExists(book)) {
					Platform.runLater(() -> v2.setLabelAtCenter("Book Not Found"));	
					return;
				}
				Path p = Main.THUMB_CACHE_FOLDER.resolve(String.valueOf(view.getBookEntry().id));

				try (PDDocument doc = PDDocument.load(book.toFile())){
					PDFRenderer renderer = new PDFRenderer(doc);
					BufferedImage img = renderer.renderImage(0);
					ImageIO.write(img, "png", p.toFile());
					Platform.runLater(() -> v2.setImage(p.toUri().toString()));
				} catch (Exception e) {
					Platform.runLater(() -> {
						FxAlert.showErrorDialog(book, "failed to extract image from pdf", e);
						v2.setLabelAtCenter("error\n"+e.getMessage());
					});
				}
			}
		}); 
		t.setDaemon(true);
		t.start();
	}


	public BookEntryView(BookEntry be) {
		setBookEntry(be);
		addClass(this, "bookentry");
		setOnMouseClicked(this);
		setOnMouseEntered(this);

		getStyleClass().add("book-entry");
		prefWidthProperty().bind(BookEntryViewManeger.getThumbWidthProperty());
		prefHeightProperty().bind(BookEntryViewManeger.getThumbHeightProperty());
	}
	private void setLabelAtCenter(String string) {
		Label label = new Label(string);
		label.setAlignment(Pos.CENTER);
		label.setTextAlignment(TextAlignment.CENTER);
		addClass(label, "center-label");
		setCenter(label);
	}
	private void setImage(String uri) {
		if(uri == null) 
			setLabelAtCenter("NO IMAGE FOUND");
		else {
			getChildren().remove(getCenter());
			// setBackground(new Background(new BackgroundImage(new Image(uri), BackgroundRepeat.REPEAT, BackgroundRepeat.REPEAT, BackgroundPosition.DEFAULT, BackgroundSize.DEFAULT)));
			setStyle("-fx-background-image: url("+uri+");");
		}
	}
	public BookEntryView setBookEntry(BookEntry bookEntry) {
		if(this.bookEntry != null && this.bookEntry.id == bookEntry.id) {
		    this.bookEntry = bookEntry;
			return this;
		}

		this.bookEntry = bookEntry;
		Platform.runLater(this::loadImage);
		return this;
	}
	private void loadImage() {
		Path p = Main.THUMB_CACHE_FOLDER.resolve(String.valueOf(bookEntry.id));

		if(Files.notExists(p)) {
			if(pdfFailedBookEntryIds.contains(bookEntry.id))
				setImage(null);
			else {
				setLabelAtCenter("LOADING");
				loadFromPdf.add(this);
			}
		}
		else
			Platform.runLater(() -> setImage(p.toUri().toString()));
	}
	public BookEntry getBookEntry() {
		return bookEntry;
	}
	public boolean isSelected() {
		return selected;
	}
	public void setSelected(boolean selected) {
		if(this.selected == selected)
			return;
		this.selected = selected;
		pseudoClassStateChanged(SELECTED, selected);
	}
	@Override
	public void handle(MouseEvent event) {
		if(event.getEventType() == MouseEvent.MOUSE_CLICKED)
			setSelected(!selected);
		else
			controls.switchView(this);
	}
	public void remove() {
		if(FxAlert.showConfirmDialog(bookEntry.id+"  "+bookEntry.name, "Sure to Remove?"))
			((UnitGroup)(getParent().getParent())).remove(this);
	}

}