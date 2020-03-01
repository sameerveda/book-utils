package sam.books;
import static sam.books.BooksMeta.AUTHOR;
import static sam.books.BooksMeta.DESCRIPTION;
import static sam.books.BooksMeta.FILE_NAME;
import static sam.books.BooksMeta.ISBN;
import static sam.books.BooksMeta.NAME;
import static sam.books.BooksMeta.PAGE_COUNT;
import static sam.books.BooksMeta.PATH_ID;
import static sam.books.BooksMeta.URL;
import static sam.books.BooksMeta.YEAR;
import static sam.console.ANSI.red;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.json.JSONObject;
import org.json.JSONTokener;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import sam.books.extractor.Extractor;
import sam.fx.alert.FxAlert;
import sam.fx.helpers.ErrorApp;
import sam.fx.helpers.FxFxml;
import sam.fx.helpers.FxGridPane;
import sam.fx.helpers.FxUtils;
import sam.fx.popup.FxPopupShop;
import sam.io.fileutils.FileOpenerNE;
import sam.myutils.Checker;
import sam.myutils.MyUtilsException;
import sam.myutils.System2;

public class ExtractorDialog extends Application {
    private Extractor[] extractors; 

    @FXML private StackPane stack;
    @FXML private VBox root;
    @FXML private HBox top;
    @FXML private Text path_id;
    @FXML private Label subpath;
    @FXML private GridPane grid;
    @FXML private Text fileNameT;
    @FXML private TextField nameTF;
    @FXML private TextField authorTF;
    @FXML private TextField isbnTF;
    @FXML private TextField pageCountTF;
    @FXML private TextField yearTF;
    @FXML private TextField urlTF;
    @FXML private Button descriptionBtn;
    @FXML private TextField extractTF;
    @FXML private Button goBtn;
    @FXML private Button prevBtn;
    @FXML private Text countT;
    @FXML private Button nextBtn;
    @FXML private Button urlsBtn;
    @FXML private BorderPane extractor;
    @FXML private TextArea urlsTA;
    @FXML private CheckBox autoloadCB;
    @FXML private Label extractor_error;
    @FXML private Button extractBtn;

    private TextArea descriptionTA;
    private SimpleIntegerProperty countProp;

    private  NewBook current;
    
    private static class Wrap {
        final Text t;
        final TextInputControl t2;

        public Wrap(TextInputControl t2) {
            this.t2 = t2;
            this.t = null;
        }

        public Wrap(Text t) {
            this.t = t;
            this.t2 = null;
        }

        String getText() {
            if(t != null)
                return t.getText();
            else
                return t2.getText();
        }
        void setText(String s) {
            if(t != null)
                t.setText(s);
            else
                t2.setText(s);
        }
    }

    private ListIterator<NewBook> iterator;
    private final Map<String, Wrap> fields = new HashMap<>();

    private JSONObject loadedJson;
    private final Map<Path, NewBook> loaded = new HashMap<>();
    private final Path newbook_backup_json = UpdateDB.SELF_DIR.resolve("newbook-backup.json");

    private Stage stage;
    private static ExtractorDialog instance;

    private static volatile List<NewBook> static_books;
    private volatile List<NewBook> result, temp_result = new ArrayList<>();

    @SuppressWarnings("unused")
    private static final String[] KEYS = {
            NAME,
            FILE_NAME,
            PATH_ID,
            AUTHOR,
            ISBN,
            PAGE_COUNT,
            YEAR,
            DESCRIPTION,
            URL
    };

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;

        stage.setTitle("Details Extracting");
        FxPopupShop.setParent(stage);
        FxAlert.setParent(stage);

        ExtractorDialog.instance = this;

        start2(static_books);
    }

    public static List<NewBook> get(List<NewBook> books) throws InterruptedException {
        static_books = books;
        Application.launch(ExtractorDialog.class, new String[0]);

        return instance.result;
    }
    public void start2(List<NewBook> books) {
        try {
            this.extractors = extractors();

            FxFxml.load(this, stage, this);

            ColumnConstraints c = new ColumnConstraints();
            c.setFillWidth(true);
            c.setHgrow(Priority.ALWAYS);

            stage.getScene().getStylesheets().add("styles.css");
            FxGridPane.setColumnConstraint(grid, 4, c);

            startProcess(books);
        } catch (Throwable e) {
            ErrorApp.set(null, e);
            FxUtils.setErrorTa(stage, "failed to init", null, e);
            this.result = null;
        }
        stage.show();
        fx(() -> nextAction(null));
    }

    @Override
    public void stop() throws Exception {
        if(loadedJson != null && loadedJson.length() != 0) {
            try(BufferedWriter w = Files.newBufferedWriter(newbook_backup_json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                loadedJson.write(w, 4, 4);
            } catch (Exception e) {
                e.printStackTrace();
            }   
        }
    }

    public void startProcess(List<NewBook> books) throws IOException {
        descriptionTA = new TextArea();

        goBtn.disableProperty().bind(extractTF.textProperty().isEmpty());
        extractor.getBottom().disableProperty().bind(urlsTA.textProperty().isEmpty());

        if(Files.exists(newbook_backup_json)) {
            try(BufferedReader reader = Files.newBufferedReader(newbook_backup_json)) {
                loadedJson = new JSONObject(new JSONTokener(reader));
            }
        } else {
            loadedJson = new JSONObject();
        }

        this.iterator = books.listIterator();
        root.getChildren().remove(extractor);

        fields.put(NAME, new Wrap(nameTF));
        fields.put(FILE_NAME, new Wrap(fileNameT));
        fields.put(PATH_ID, new Wrap(path_id));
        fields.put(AUTHOR, new Wrap(authorTF));
        fields.put(ISBN, new Wrap(isbnTF));
        fields.put(PAGE_COUNT, new Wrap(pageCountTF));
        fields.put(YEAR, new Wrap(yearTF));
        fields.put(DESCRIPTION, new Wrap(descriptionTA)); 
        fields.put(URL, new Wrap(urlTF));

        countProp = new SimpleIntegerProperty();
        countT.textProperty().bind(Bindings.concat(countProp, " / ", books.size()));

        stage.setOnCloseRequest(e -> {
            if(FxAlert.showConfirmDialog("Details Extracting Not Completed\nNothing will be saved", "exiting"))
                result = null;
            else 
                e.consume();
        });
    }

    @SuppressWarnings("rawtypes")
    private Extractor[] extractors() throws IOException, InstantiationException, IllegalAccessException, ClassNotFoundException {
        String s = System2.lookupAny("extractors", "EXTRACTORS");
        InputStream is;
        if(s != null)
            is = Files.newInputStream(Paths.get(s.trim()), StandardOpenOption.READ);
        else 
            is = ClassLoader.getSystemResourceAsStream("extractors");

        try(BufferedReader reader = new BufferedReader(new InputStreamReader(is, "utf-8"))) {
            ArrayList<Extractor> list = new ArrayList<>();
            String line;
            while((line = reader.readLine()) != null) {
                line = line.trim();
                if(!line.isEmpty() && line.charAt(0) != '#') {
                    Object o = Class.forName(line).newInstance();
                    if(o instanceof Extractor) 
                        list.add((Extractor)o);
                     else if(o instanceof Function) 
                        list.add(wrap((Function)o));
                     else 
                        throw new IllegalArgumentException("unknown type: "+o.getClass());
                }
            }

            if(list.isEmpty())
                throw new IllegalStateException("no \"extractors\" specified");

            return list.toArray(new Extractor[list.size()]);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Extractor wrap(Function f) {
        return new Extractor() {
            @Override
            public JSONObject extract(String url) throws IOException {
                Object o = f.apply(url);
                if(o == null || o instanceof JSONObject)
                    return (JSONObject)o;
                else if(o instanceof Map) 
                    return new JSONObject((Map)o);
                else
                    throw new IllegalArgumentException("unknown return type: "+o.getClass()); 
            }
            
            @Override
            public boolean canExtract(String url) {
                return true;
            }
        };
    }

    private void open(boolean openFile) {
        Optional.ofNullable(current)
        .map(c -> c.path().fullPath())
        .ifPresent(f -> {
            if(openFile)
                FileOpenerNE.openFile(f);
            else
                FileOpenerNE.openFileLocationInExplorer(f);
        });
    }

    private BorderPane descriptionPane;

    @FXML
    private void showDescription(Event e) {
        if(descriptionPane == null) 
            descriptionPane = backablePane(descriptionTA);

        List<Node> nodes = stack.getChildren();
        nodes.get(nodes.size() - 1).setDisable(true);
        nodes.add(descriptionPane);
    }

    private BorderPane backablePane(TextArea center) {
        BorderPane bp = new BorderPane();
        Button back = new Button("back");
        bp.setTop(back);
        back.setOnAction(e -> {
            List<Node> nodes = stack.getChildren();

            if(nodes.remove(bp))
                nodes.get(nodes.size() - 1).setDisable(false);
        });
        bp.setCenter(center);
        return bp;
    }


    @FXML
    private void prevAction(Event e) {
        current = iterator.previous();
        update();
    }

    private void update() {
        countProp.set(iterator.nextIndex());
        prevBtn.setDisable(!iterator.hasPrevious());

        NewBook b = loaded.get(current.path().subpath());
        if(b != null)
            current.apply(b);
        else {
            JSONObject json = loadedJson.optJSONObject(current.path().subpath().toString());
            if(json != null)
                json.keys().forEachRemaining(s -> current.set(s, json.getString(s)));
        }

        subpath.setText(String.valueOf(current.path.parent().subpath()));
        fields.forEach((c,f) -> f.setText(current.get(c)));

        if(current.name == null)
            nameTF.setText(current.path().name.replaceFirst("\\.pdf$", ""));
    }

    @FXML
    private void nextAction(Event e) {
        descriptionBtn.setDisable(true);

        if(current != null) {
            fields.forEach((colName, field) -> {
                String s = field.getText();
                if(colName != DESCRIPTION && s != null) {
                    s = s.replaceAll("[\r\n\t\f]", "").trim();
                    s = s.isEmpty() ? null : s;
                }
                current.set(colName, s);
            });

            if(Arrays.equals(new String[] {current.isbn, current.page_count == 0 ? null : String.valueOf(current.page_count), current.year, current.description}, new String[4]) && !FxAlert.showConfirmDialog("Fields Are Empty", "Sure to Proceed"))
                return;

            loaded.put(current.path().subpath(), current);
            temp_result.add(current);
        }

        if(!iterator.hasNext()){
            this.result = temp_result;
            Platform.exit();
            return;
        }

        current = iterator.next();
        update();

        extractTF.setText(null);
        setUrl();
    }

    @FXML private void openAction(Event e) { open(true); }
    @FXML private void openLocationAction(Event e) { open(false); }


    @FXML
    private void showHideExtractorAction(Event e) {
        List<Node> list = root.getChildren();
        if(list.contains(extractor))
            list.remove(extractor);
        else
            list.add(extractor);
        
        stage.sizeToScene();
    }
    
    private class Loading implements AutoCloseable {
        private final Group view = new Group(new ProgressIndicator());

        public void start() {
            fx(() -> {
                stack.getChildren().add(view);
                root.setDisable(true);
            });
        }
        @Override
        public void close() {
            fx(() -> {
                stack.getChildren().remove(view);
                root.setDisable(false);
            });
        }
    }

    private Executor threads;
    private Loading loading;

    @FXML
    private void urlGoAction(Event _e) {
        String urlsString = extractTF.getText().trim();

        if(urlsString.isEmpty())
            return;

        if(loading == null) 
            loading = new Loading();

        if(threads == null)
            threads = Executors.newSingleThreadExecutor();

        loading.start();

        threads.execute(() -> {
            try(Loading l = loading) {
                for (Extractor f : extractors) {
                    JSONObject result = f.canExtract(urlsString) ? f.extract(urlsString) : null;

                    if(result != null && result.length() != 0) {
                        fx(() -> {
                            result.keys().forEachRemaining(s -> {
                                Wrap c = fields.get(s);

                                if(c == null)
                                    System.out.println(red("unknown field: ")+s);
                                else 
                                    c.setText(result.getString(s));
                            });
                            result.put(URL, extractTF.getText());
                            urlTF.setText(extractTF.getText());
                            loadedJson.put(URL, extractTF.getText());
                            loadedJson.put(current.path.subpath().toString(), result);
                        });
                        return;
                    }
                }
                fx(() -> FxAlert.showMessageDialog(urlsString, "no extractor found for"));
            } catch (Throwable e) {
                fx(() -> {
                    StringBuilder sb = new StringBuilder("failed to extract\nurl: ").append(urlsString).append('\n');
                    MyUtilsException.append(sb, e, true);

                    stack.getChildren().add(backablePane(new TextArea(sb.toString())));
                });
            }
        });
    }

    private void fx(Runnable r) {
        Platform.runLater(r);
    }
    public void setUrl() {
        extractUrlAction(null);
    }
    @FXML
    private void extractUrlAction(Event _e) {
        if(extractBtn.isDisable())
            return;

        String urls = urlsTA.getText();

        if(Checker.isEmptyTrimmed(urls)) {
            urlsTA.clear();
            return;
        }


        urls = urls.trim();
        Set<String> list = new HashSet<>(Arrays.asList(urls.split("\r?\n")));
        list.removeIf(Checker::isEmptyTrimmed);

        if(list.isEmpty()) {
            urlsTA.clear();
            return;
        }

        Map<String, String> urlFileName = list.stream().collect(Collectors.toMap(Function.identity(), s -> {
            try {
                return new URL(s).getFile().replace('/', ' ').trim();
            } catch (Exception e1) {
                System.out.println(red(s+"  "+e1));
                return "";
            }
        }, (o, n) -> n, HashMap::new));

        urlFileName.values().removeIf(String::isEmpty);

        String temp = nameTF.getText().toLowerCase().replaceAll("\\W+|_", "");
        Iterator<String> iterator = urlFileName.keySet().iterator();

        while(iterator.hasNext()) {
            String s = iterator.next();
            if(temp.equals(urlFileName.get(s).replaceAll("\\W+|_", ""))){
                extractTF.setText(s);
                iterator.remove();
                if(autoloadCB.isSelected())
                    goBtn.fire();
                break;
            }
        }

        urlsTA.setText(String.join("\n", urlFileName.keySet()));
    }
}
