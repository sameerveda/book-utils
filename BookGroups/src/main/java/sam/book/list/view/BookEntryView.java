package sam.book.list.view;

import static sam.fx.helpers.FxClassHelper.addClass;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.text.TextAlignment;
import sam.book.list.model.BookEntry;
import sam.books.BookStatus;
import sam.books.BookUtils;
import sam.fx.alert.FxAlert;
import sam.fx.popup.FxPopupShop;
import sam.myutils.MyUtilsPath;

public class BookEntryView extends StackPane  implements EventHandler<MouseEvent> {
    public static final Path THUMB_CACHE_FOLDER = Paths.get("cache");
    public static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private BookEntry bookEntry;
    private boolean selected;
    private volatile ImageView imageView;

    static {
        try {
            Files.createDirectories(THUMB_CACHE_FOLDER);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final Controls controls = Controls.getInstance();
    private static final ThreadPoolExecutor POOL = (ThreadPoolExecutor) Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()); 

    public BookEntryView(BookEntry be) {
        addClass(this, "bookentry");
        setOnMouseClicked(this);
        setOnMouseEntered(this);

        getStyleClass().add("book-entry");
        prefWidthProperty().bind(ViewManeger.getThumbWidthProperty());
        prefHeightProperty().bind(ViewManeger.getThumbHeightProperty());

        setBookEntry(be);
    }
    private void setLabelAtCenter(String string) {
        getChildren().removeIf(n -> n instanceof Label);
        if(string != null) {
            Label label = new Label(string);
            label.setAlignment(Pos.CENTER);
            label.setTextAlignment(TextAlignment.CENTER);
            addClass(label, "center-label");
            getChildren().add(0, label);
        }		
    }
    private void setImage(Image img) {
        if(img == null) 
            setLabelAtCenter("NO IMAGE FOUND");
        else {
            setLabelAtCenter(null);

            if(imageView == null) {
                synchronized (this) {
                    if(imageView != null)
                        return;
                    imageView = new ImageView(img);
                    imageView.fitWidthProperty().bind(ViewManeger.getThumbWidthProperty());
                    imageView.fitHeightProperty().bind(ViewManeger.getThumbHeightProperty());
                }
            }
            imageView.setImage(img);
            if(!getChildren().contains(imageView))
                getChildren().add(0, imageView);
        }
    }
    public BookEntryView setBookEntry(BookEntry bookEntry) {
        Objects.requireNonNull(bookEntry);

        if(this.bookEntry != null && this.bookEntry.getBookId() == bookEntry.getBookId()) {
            this.bookEntry = bookEntry;
            return this;
        }

        this.bookEntry = bookEntry;
        setLabelAtCenter("LOADING");
        Platform.runLater(this::loadImage);
        return this;
    }

    private static final CopyOnWriteArraySet<Integer> pdfTried = new CopyOnWriteArraySet<>(); 
    void loadImage() {
        if(imageView != null) 
            imageView.setImage(null);
        Platform.runLater(() -> {
            Path cachePath = THUMB_CACHE_FOLDER.resolve(String.valueOf(bookEntry.getBookId()));

            if(Files.notExists(cachePath)) {
                if(pdfTried.contains(bookEntry.getBookId()))
                    setLabelAtCenter("thumb not found");
                else
                    loadFromPdf(cachePath);
                return;
            }

            POOL.execute(() -> {
                try(InputStream is = Files.newInputStream(cachePath)) {
                    Image img = new Image(is);
                    Platform.runLater(() -> setImage(img));
                } catch (IOException e2) {
                    Platform.runLater(() -> setLabelAtCenter("failed loading thumb: "+e2));
                }            
            });
        });
    }
    private void loadFromPdf(Path cachePath) {
        POOL.execute(() -> {
            try (PDDocument doc = PDDocument.load(bookEntry.getFullPath().toFile())){
                PDFRenderer renderer = new PDFRenderer(doc);
                BufferedImage img = renderer.renderImage(0);
                ImageIO.write(img, "png", cachePath.toFile());
                Image img2 = SwingFXUtils.toFXImage(img, null);
                Platform.runLater(() -> setImage(img2));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    FxAlert.showErrorDialog(bookEntry, "failed to extract image from pdf", e);
                    setLabelAtCenter("error\n"+e.getMessage());
                });
            }
        });
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
    public void clear() {
        setSelected(false);
        getChildren().removeIf(n -> n instanceof ImageView);
        setLabelAtCenter("LOADING");
    }
    public void changeStatus(BookStatus status) {
    	Path expectedPath = bookEntry.getExpepectedFullPath();
    	Path fullpath = BookUtils.findBook(expectedPath);
    	
        if(fullpath == null || Files.notExists(fullpath))
            FxAlert.showErrorDialog("File move failed:\n"+expectedPath+"\n"+fullpath, "File not found", null);
        else {
            try {
                Path target = expectedPath.resolveSibling(status.getPathName()).resolve(expectedPath.getFileName());
                boolean confirm = FxAlert.showConfirmDialog(
                        "src: "+MyUtilsPath.subpath(fullpath, BookUtils.ROOT)+
                        "\ntarget: "+MyUtilsPath.subpath(target, BookUtils.ROOT), "Move to _read_ dir");

                if(!confirm)
                    return;

                Files.createDirectories(target.getParent());
                Files.move(fullpath, target, StandardCopyOption.REPLACE_EXISTING);
                FxPopupShop.showHidePopup("moved to "+status.getPathName(), 1500);
                System.out.println("moved to :"+target);
            } catch (IOException e) {
                FxAlert.showErrorDialog("File move failed:\n"+fullpath, "Error", e);				
            }
        }
    }

}