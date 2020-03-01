package sam.book.list.view;

import static sam.fx.helpers.FxClassHelper.addClass;
import static sam.fx.helpers.FxGridPane.gridPane;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Optional;
import java.util.function.BiConsumer;

import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import sam.book.list.main.Main;
import sam.book.list.model.BookEntry;
import sam.books.BookStatus;
import sam.books.BookUtils;
import sam.fx.clipboard.FxClipboard;
import sam.fx.helpers.FxText;
import sam.fx.popup.FxPopupShop;
import sam.myutils.MyUtilsPath;

class AboutBook extends Stage {
	private final Label thumbView = new Label();
	private final WebView description = new WebView();
	private final SplitPane container;

	private final Text id = text("id"),
			pageCount = text("pageCount"),
			year = text("year"),
			name = text("name"),
			author = text("author"),
			isbn = text("isbn"),
			lastReadTime = text("lastReadTime"),
			addTime = text("addTime"),
					status = text("status");
	
	private final Label path = new Label("path");
	{
		path.setWrapText(true);
		path.setMaxHeight(Double.MAX_VALUE);
		path.minHeight(100);
	}

	public AboutBook() {
		initModality(Modality.APPLICATION_MODAL);
		initOwner(Main.getStage());

		BorderPane top = new BorderPane();
		addClass(top, "top");

		top.setLeft(thumbView);
		addClass(thumbView, "thumb", "value");
		thumbView.setAlignment(Pos.CENTER);
		thumbView.setTextAlignment(TextAlignment.CENTER);
		thumbView.prefHeightProperty().bind(top.heightProperty());
		thumbView.prefWidthProperty().bind(top.heightProperty().divide(1.5));

		GridPane grid = gridPane(5);
		addClass(grid, "details");

		int[] row = {1};

		EventHandler<MouseEvent> copyAction = e -> {
			if(e.getClickCount() > 1){
				Text text = (Text) e.getSource();
				FxClipboard.copyToClipboard(text.getText());
				FxPopupShop.showHidePopup("copied", 1500);
			}
		};

		BiConsumer<String, Node> addRow = (header, valueT) -> {
			Text headerT = text(header);
			addClass(headerT, "header");
			addClass(valueT, "value");

			valueT.setOnMouseClicked(copyAction);
			grid.addRow(row[0]++, headerT, valueT);
			GridPane.setColumnSpan(valueT, GridPane.REMAINING);
			if(header.equals("path: "))
				GridPane.setRowSpan(valueT, GridPane.REMAINING);
		};
		addRow.accept("id: ", id);
		addRow.accept("name: ", name);
		addRow.accept("author: ", author);
		addRow.accept("page count: ", pageCount);
		addRow.accept("year: ", year);
		addRow.accept("isbn: ", isbn);
		addRow.accept("Status: ", status);
		addRow.accept("Last Read: ", lastReadTime);
		addRow.accept("Added: ", addTime);
		addRow.accept("path: ", path);

		description.getEngine().setUserStyleSheetLocation(ClassLoader.getSystemResource("about-book-style.css").toExternalForm());
		top.setCenter(grid);

		container = new SplitPane(top, description);
		container.setOrientation(Orientation.VERTICAL);
		container.setDividerPosition(0, 0.5);
		container.setId("about-book-root");

		Scene scene = new Scene(container, 600, 500);
		scene.getStylesheets().add(ClassLoader.getSystemResource("about-book-style.css").toExternalForm());
		scene.setOnKeyReleased(e -> {
			if(e.getCode() == KeyCode.ESCAPE)
				close();
		});
		setScene(scene);
	}
	private Text text(String string) {
        return FxText.ofString(string);
    }
    public void changeBookEntry(BookEntry be) {
		id.setText(string(be.getBookId()));
		pageCount.setText(be.getPageCount());
		year.setText(be.getYear());
		name.setText(be.getName());
		author.setText(be.getAuthor() );
		isbn.setText(be.getIsbn());
		path.setText(String.valueOf(MyUtilsPath.subpath(be.getFullPath(), BookUtils.ROOT)));
		lastReadTime.setText(time(be.getLastReadTime()));
		addTime.setText(time(be.getAddTime()));
		status.setText(Optional.ofNullable(be.getStatus()).filter(b -> b != BookStatus.NONE ).map(String::valueOf).orElse("--"));

		Path p = BookEntryView.THUMB_CACHE_FOLDER.resolve(String.valueOf(be.getBookId()));
		boolean b = Files.exists(p); 
		thumbView.setText(b ? null : "IMAGE NOT FOUND");
		thumbView.setStyle(
				"-fx-background-image:" +(b ? "url("+p.toUri().toString()+")" : "null")+
				";-fx-background-color:"+ (b ? "null;" : "#1B2C1A;")
				);

		String desc = be.getDescription();
		if(desc == null || (desc = desc.trim()).isEmpty())
			container.getItems().remove(this.description);
		else {
			if(!desc.startsWith("<html>"))
				desc = "<html>"+desc+"</html>";

			this.description.getEngine().loadContent(desc);
			if(!container.getItems().contains(this.description))
				container.getItems().add(this.description);
		}
		sizeToScene();
		showAndWait();
	}
	private final ZoneOffset offset  = ZoneOffset.of("+05:30");
	private final DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.MEDIUM);
	private String time(long t) {
		if(t == 0)
			return "N/A";
		LocalDateTime dt = LocalDateTime.ofEpochSecond(t/1000, 0, offset);
		return dt.format(formatter);
	}
	private String string(int i) {
		return String.valueOf(i);
	}

}