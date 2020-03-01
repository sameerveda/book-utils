package sam.book.list.model;

import static java.util.stream.Collectors.toList;
import static sam.book.list.model.COLUMNS.ADD_TIME;
import static sam.book.list.model.COLUMNS.GROUP;
import static sam.book.list.model.COLUMNS.ID;
import static sam.book.list.model.COLUMNS.LAST_READ_TIME;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyListWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import sam.books.BookList;
import sam.books.BooksMeta;
import sam.tsv.Row;
import sam.tsv.Tsv;

public class Model {
    private static volatile Model instance;
    public static Model getInstance() {
        if (instance == null) {
            synchronized (Model.class) {
                if (instance == null)
                    instance = new Model();
            }
        }
        return instance;
    }

    private final Logger logger = MyLoggerFactory.logger(getClass().getName());

    private boolean modified;

    private Tsv tsv;
    private ReadOnlyListWrapper<String> groupNames = new ReadOnlyListWrapper<>(FXCollections.observableArrayList());
    private final Map<Integer, List<BookEntry>> groupEntryMap = new HashMap<>();
    private final List<ModelEvent> listeners = new ArrayList<>();

    private String currentGroupName;
    private List<BookEntry> currentEntries;

    private Model(){}
    public void init() throws IOException {
        Path path = Paths.get("book_list.tsv");
        if(Files.notExists(path)) {
            logger.warning(() -> "File not found: "+path);
            tsv = new Tsv(ID.colname, GROUP.colname, LAST_READ_TIME.colname, ADD_TIME.colname);
            tsv.setPath(path);
        } else
            tsv = Tsv.parse(path);

        ID.setIndex(tsv);
        GROUP.setIndex(tsv);
        LAST_READ_TIME.setIndex(tsv);
        ADD_TIME.setIndex(tsv);

        tsv.stream().map(GROUP::get).distinct().forEach(groupNames::add);
        logger.fine("initialized");
    }
    public void save() throws IOException {
        if(modified || groupEntryMap.values().stream().flatMap(List::stream).anyMatch(BookEntry::isModified)) {
            tsv.save();
            logger.info("tsv saved");
            Platform.runLater(() -> listeners.forEach(ModelEvent::dataSaved));
        }
    }
    public void addListener(ModelEvent event) {
        listeners.add(event);
    }
    public ObservableList<String> groupNamesProperty(){
        return groupNames.getReadOnlyProperty();
    }

    /**
     * change group and return not found books 
     * @param name
     * @return
     */
    public void changeGroup(final int index) {
        String name = groupNames.get(index);

        if(Objects.equals(currentGroupName, name))
            return;

        List<BookEntry> rows = groupEntryMap.get(index);

        if(rows == null) {
            rows = loadBooks(null, tsv.stream().filter(r -> GROUP.get(r).equals(name)).collect(toList()));
            groupEntryMap.put(index, rows);
        }
        currentGroupName = name;
        currentEntries = rows;
        Platform.runLater(() -> listeners.forEach(e -> e.groupChanged(name, Collections.unmodifiableList(currentEntries))));
    }
    public void addGroup(String name) {
        Objects.requireNonNull(name, "group name cannot be null");

        if(name.trim().isEmpty())
            throw new IllegalArgumentException("empty group name not allowed");

        if(!groupNames.contains(name))
            groupNames.add(name);

        changeGroup(groupNames.indexOf(name));
    } 
    public void removeAll(List<BookEntry> values) {
        if(values.isEmpty())
            return;

        currentEntries.removeAll(values);
        values.forEach(b -> tsv.remove(b.getRow()));

        modified = true;
        List<BookEntry> removed = Collections.unmodifiableList(values);
        List<BookEntry> current = Collections.unmodifiableList(currentEntries);

        Platform.runLater(() -> listeners.forEach(e -> e.removed(removed, current)));
    }
    public void remove(BookEntry bookEntry) {
        removeAll(Arrays.asList(bookEntry));
    }
    public String addBooks(int[] bookIds) {
        Objects.requireNonNull(bookIds, "bookIds cannot be null");
        if(bookIds.length == 0) {
            logger.warning("empty bookIds supplied");
            return "NO BOOK(S) ADDED";
        }

        int[] existing = currentEntries.stream().mapToInt(BookEntry::getBookId).sorted().toArray();

        List<Integer> exists = new ArrayList<>();
        List<Integer> notExists = new ArrayList<>();

        IntStream.of(bookIds).distinct()
        .forEach(id -> (Arrays.binarySearch(existing, id) < 0 ? notExists : exists).add(id));

        StringBuilder sb = new StringBuilder();
        Formatter formatter = new Formatter(sb); 

        final  String format = "%-5s%s\n";
        BiConsumer<String, List<Integer>> append = (header, ids) -> {
            sb.append(header);
            sb.append("----------------------------------------\n");
            formatter.format(format, "id", "name");


            if(ids != null) {
                for (Integer id : ids) {
                    BookEntry b = entry(id);
                    formatter.format(format, b.getBookId(), b.getName());
                }
            }
            sb.append("----------------------------------------\n\n");
        };

        if(!exists.isEmpty())
            append.accept("skipped because already exists\n", exists);

        if(notExists.isEmpty()) {
            append.accept("adding skipped (all already exists in group)\n", null);

            sb.append("NO BOOK(S) ADDED");
            formatter.close();
            return sb.toString();
        }

        List<BookEntry> bes = loadBooks(notExists, null);

        if(bes.size() != notExists.size()) {
            sb.append("adding skipped (id(s) not found)\n");
            sb.append("----------------------------------------\n");

            if(bes.isEmpty()) 
                notExists.forEach(id -> sb.append(id).append("\n"));
            else {
                int[] temp = bes.stream().mapToInt(b -> b.getBookId()).sorted().toArray();
                notExists.forEach(id -> {
                    if(Arrays.binarySearch(temp, id) < 0)
                        sb.append(id).append("\n");
                });    
            }
            sb.append("---------------------------------------\n\n");
        }

        if(bes.isEmpty())
            sb.append("NO BOOK(S) ADDED");
        else {
            currentEntries.addAll(bes);
            append.accept("\nnew Books ("+bes.size()+")", bes.stream().map(b -> b.getBookId()).collect(Collectors.toList()));

            List<BookEntry> added = Collections.unmodifiableList(bes);
            List<BookEntry> current = Collections.unmodifiableList(currentEntries);

            Platform.runLater(() -> listeners.forEach(e -> e.added(added, current)));
            modified = true;
        }
        formatter.close();
        return sb.toString();
    }
    private List<BookEntry> loadBooks(List<Integer> ids, List<Row> rows) {
        if((ids == null || ids.isEmpty()) && (rows == null || rows.isEmpty()))
            return new ArrayList<>();

        try{
            Map<Integer, Row> map = new HashMap<>();
            if(rows != null)
                rows.forEach(r -> map.put(r.getInt(COLUMNS.ID.colname), r));
            
            IntFunction<Row> getRow = id -> {
                Row r = map.get(id);
                if(r != null)
                    return r;
                
                r = tsv.rowBuilder()
                        .set(ID.getIndex(), String.valueOf(id))
                        .set(GROUP.getIndex(), currentGroupName)
                        .set(LAST_READ_TIME.getIndex(), String.valueOf(System.currentTimeMillis()))
                        .set(ADD_TIME.getIndex(), String.valueOf(System.currentTimeMillis()))
                        .add();
                
                return r;
            };

            return new BookList<BookEntry>(ids != null ? ids : map.keySet()) {
                private static final long serialVersionUID = 1L;

                @Override
                protected BookEntry newBook(ResultSet rs, String parentFolderSubpath) throws SQLException {
                    BookEntry be = new BookEntry(rs, parentFolderSubpath, getRow.apply(rs.getInt(BooksMeta.BOOK_ID)));
                    return be;
                }
            };
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException | SQLException e) {
            e.printStackTrace();
            System.exit(0);
        }
        return null;
    }
    private BookEntry entry(int id) {
        return currentEntries.stream().filter(b -> b.getBookId() == id).findFirst().orElse(null);
    }
}
