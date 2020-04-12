package sam.book.search.app;

import java.io.IOException;

import org.json.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.web.WebView;
import javafx.stage.Window;
import sam.books.Book;
import sam.books.BooksDB;
import sam.books.Env;
import sam.fx.helpers.FxFxml;
import sam.io.fileutils.FileOpenerNE;
import sam.myutils.Checker;

public abstract class FullView extends VBox {
	@FXML private Text idText;
	@FXML private Text nameText;
	@FXML private Text path_idText;
	@FXML private Text statusText;
	@FXML private Hyperlink pathText;
	@FXML private Text authorText;
	@FXML private Text isbnText;
	@FXML private Text page_countText;
	@FXML private Text yearText;
	@FXML private WebView descriptionText;
	
	Book currentBook;
	
	public FullView() {
		try {
			FxFxml.load(ClassLoader.getSystemResource("fxml/FullView.fxml"), this, this);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	void reset(Book n) {
		setVisible(n != null);
		if(n == null) return;

		currentBook = n;
		Book b = currentBook;

		idText.setText(string(b.id));
		nameText.setText(b.name);
		path_idText.setText(string(b.path_id));
		pathText.setText(pathForPathId(b.path_id)+"\\\n"+b.file_name);
		pathText.setVisited(false);
		authorText.setText(b.author);
		isbnText.setText(b.isbn);
		page_countText.setText(String.valueOf(b.page_count));
		yearText.setText(String.valueOf(b.year));
		descriptionText.getEngine().loadContent(Checker.isEmpty(b.description) ? "NO DESCRIPTION" : b.description);
		statusText.setText(b.status == null ? null : b.status.toString());
		descriptionText.getEngine().setUserStyleSheetLocation(ClassLoader.getSystemResource("css/description.css").toString());
	}

	protected abstract void bookmark(Book currentBook);
	protected abstract String pathForPathId(int path_id);
	protected abstract Window stage();

	private static String string(int value) {
		return String.valueOf(value);
	}
	
	@FXML
	private void copyCombinedAction(ActionEvent e) {
	    App.copyToClipboard(BooksDB.createDirname(currentBook.id, currentBook.file_name));
	}
	@FXML
	private void infoGridClickAction(MouseEvent e) {
		if(e.getClickCount() > 1) {
			Object s = e.getTarget();
			if(s instanceof Text) {
				Text t = (Text) s;
				if(!Checker.isEmptyTrimmed(t.getId()))
				    App.copyToClipboard(t.getText());
			}
		}
	} 
	@FXML
	private void openPathAction(ActionEvent e){
	    FileOpenerNE.openFileLocationInExplorer(Env.ROOT.resolve(pathForPathId(currentBook.path_id)).resolve(currentBook.file_name).toFile());
	}
	@FXML
	private void openFileAction(ActionEvent e) {
	    FileOpenerNE.openFile(Env.ROOT.resolve(pathForPathId(currentBook.path_id)).resolve(currentBook.file_name).toFile());
	}

	@FXML
	private void copyJsonAction(ActionEvent e) {
	    StringBuilder sb = new StringBuilder();
	    new JSONWriter(sb)
	    .object()
	    .key("id").value(currentBook.id)
	    .key("isbn").value(currentBook.isbn)
	    .key("name").value(currentBook.name)
	    .endObject();
	    App.copyToClipboard(sb.toString());
	}
	
	@FXML
    private void bookmarkAction(ActionEvent e) {
        bookmark(currentBook);
    }
}
