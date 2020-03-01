package sam.book.list.model;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.sqlite.JDBC;

import javafx.beans.property.ReadOnlyListWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import sam.fx.alert.FxAlert;
import sam.properties.myconfig.MyConfig;
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
	private static int ID = -1,GROUP = -1,LAST_READ_TIME = -1,ADD_TIME = -1;

	public static int getAddTimeIndex() {
		return ADD_TIME;
	}
	public static int getLastReadTimeIndex() {
		return LAST_READ_TIME;
	}

	private Tsv tsv;
	private final Map<String, BookEntry> idEntryMap = new HashMap<>(); 
	private boolean modified;
	private Consumer<List<BookEntry>> onGroupChange;
	private ReadOnlyListWrapper<String> groupNames = new ReadOnlyListWrapper<>(FXCollections.observableArrayList());
	private List<BookEntry> currentEntries;
	private String currentGroupName;

	private Model(){}
	public void load() throws URISyntaxException, IOException {
		Path path = Paths.get("book_list.tsv");
		if(Files.notExists(path)) {
			tsv = new Tsv("Id","Group","Last Read Time","Add Time");
			tsv.setPath(path);
		}
		else
			tsv = Tsv.parse(path);

		ID = tsv.columnIndexOf("Id");
		GROUP = tsv.columnIndexOf("Group");
		LAST_READ_TIME = tsv.columnIndexOf("Last Read Time");
		ADD_TIME = tsv.columnIndexOf("Add Time");

		tsv.stream().map(r -> r.get(GROUP)).distinct().filter(Objects::nonNull).forEach(groupNames::add);
	}
	public void save() throws IOException {
		if(modified || idEntryMap.values().stream().anyMatch(BookEntry::isModified)) {
			tsv.save();
			System.out.println("tsv saved");
		}
	}
	/**
	 * change group and return not found books 
	 * @param groupName
	 * @return
	 */
	public String changeGroup(String groupName) {
		if(Objects.equals(groupName,currentGroupName))
			return null;
		
		Objects.requireNonNull(groupName, "groupName cannot be null");

		if(!groupNames.stream().anyMatch(s -> s.equals(groupName)))
			throw new IllegalArgumentException("group not found: "+groupName);

		List<Row> rows = tsv.stream()
				.filter(r -> r.get(GROUP).equals(groupName))
				.peek(r -> idEntryMap.put(r.get(ID), null))
				.collect(Collectors.toList());

		loadBooks();

		Map<Boolean, List<Row>> map = rows.stream().collect(Collectors.partitioningBy(r -> entry(r.get(ID)) == null));

		if(map.containsKey(false))
			onGroupChange.accept(currentEntries = map.get(false).stream().map(r -> entry(r.get(ID)).setRow(r)).collect(Collectors.toList()));
		else
			onGroupChange.accept(currentEntries = new ArrayList<>());

		currentGroupName = groupName;

		if(map.containsKey(true))
			return map.get(true).stream().map(String::valueOf).collect(Collectors.joining("\n"));
		else
			return null;
	}
	public void addGroup(String groupName) {
		groupNames.add(groupName);
		currentGroupName = groupName;
		onGroupChange.accept(currentEntries = new ArrayList<>());
	} 
	public void setOnGroupChange(Consumer<List<BookEntry>> consumer) {
		onGroupChange = consumer;
	}
	public void removeAll(List<BookEntry> values) {
		if(values.isEmpty())
			return;

		idEntryMap.values().removeAll(values);
		tsv.removeRows(values.stream().map(BookEntry::getRow).collect(Collectors.toList()));
		currentEntries.removeAll(values);
		modified = true;
	}
	public void remove(BookEntry bookEntry) {
		idEntryMap.values().remove(bookEntry);
		tsv.removeRow(bookEntry.getRow());
		currentEntries.remove(bookEntry);
		modified = true;
	}
	public ObservableList<String> groupNamesProperty() {
		return groupNames.getReadOnlyProperty();
	}
	public String addBooks(int[] bookIds) {
		int[] existing = currentEntries.stream().mapToInt(BookEntry::getId).sorted().toArray();
		Map<Boolean, List<String>> map = IntStream.of(bookIds).boxed().collect(Collectors.partitioningBy(i -> Arrays.binarySearch(existing, i) < 0, Collectors.mapping(String::valueOf, Collectors.toList())));

		StringBuilder sb = new StringBuilder();
		Formatter formatter = new Formatter(sb);

		BiConsumer<String, List<String>> append = (header, ids) -> {
			sb.append(header);
			sb.append("----------------------------------------\n");
			formatter.format("%-5s%s\n", "id", "name");

			ids.stream()
			.map(id -> entry(id))
			.forEach(b -> formatter.format("%-5s%s\n", b.id, b.name));
			sb.append("----------------------------------------\n\n");
		};

		if(map.containsKey(false) && !map.get(false).isEmpty())
			append.accept("adding skipped (already exists in group)\n", map.get(false));

		if(!map.containsKey(true)) {
			sb.append("NO BOOK(S) ADDED");
			formatter.close();
			return sb.toString();
		}

		map.get(true).forEach(id -> idEntryMap.put(id, null));
		loadBooks();
		map = map.get(true).stream().collect(Collectors.partitioningBy(id -> entry(id) == null));

		if(map.containsKey(true) && !map.get(true).isEmpty()) {
			sb.append("adding skipped (id(s) not found)\n");
			sb.append("----------------------------------------\n");
			map.get(true).forEach(s -> sb.append(s).append(", "));
			sb.append("\n---------------------------------------\n\n");
		}

		if(map.containsKey(false)) {
			append.accept("BOOK(s) Added\n", map.get(false));
			map.get(false).stream()
			.map(id -> {
				return entry(id).setRow(
						tsv.rowBuilder()
						.set(ID, id)
						.set(GROUP, currentGroupName)
						.set(LAST_READ_TIME, "0")
						.set(ADD_TIME, String.valueOf(System.currentTimeMillis()))
						.add()
						);
			})
			.forEach(currentEntries::add);
			
			onGroupChange.accept(currentEntries);
			modified = true;
		}
		else
			sb.append("NO BOOK(S) ADDED");

		formatter.close();
		return sb.toString();
	}
	private void loadBooks() {
		List<String> ids = new ArrayList<>();
		idEntryMap.forEach((id, entry) -> {
			if(entry == null)
				ids.add(id);
		});

		if(ids.isEmpty())
			return;

		try(Connection c = DriverManager.getConnection(JDBC.PREFIX+MyConfig.BOOKLIST_DB);
				Statement stmnt = c.createStatement();
				ResultSet rs = stmnt.executeQuery(ids.stream().collect(Collectors.joining(",", "SELECT * FROM Books NATURAL JOIN Paths WHERE _id IN(", ")")));
				) {
			while(rs.next()) idEntryMap.put(rs.getString("_id"), new BookEntry(rs));
		} catch (SQLException e) {
			FxAlert.showErrorDialog(null, "failed while loadBook(...)", e);
		}
	}
	private BookEntry entry(String id) {
		return idEntryMap.get(id);
	}
}
