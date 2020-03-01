import static sam.books.BookPathMeta.PATH;
import static sam.books.BooksMeta.BOOK_ID;
import static sam.books.BooksMeta.FILE_NAME;
import static sam.books.BooksMeta.ISBN;
import static sam.books.BooksMeta.NAME;
import static sam.books.BooksMeta.PAGE_COUNT;
import static sam.books.BooksMeta.PATH_ID;
import static sam.books.BooksMeta.TABLE_NAME;
import static sam.books.BooksMeta.YEAR;
import static sam.myutils.MyUtilsCheck.isEmpty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import sam.books.BookPathMeta;
import sam.books.BookUtils;
import sam.books.BooksDB;
import sam.books.BooksMeta;
import sam.console.ANSI;
import sam.sql.querymaker.QueryMaker;
import sam.sql.querymaker.Select;
import sam.string.StringBuilder2;

public class Main {
	public static void main(String[] args) throws InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException {
		new Main(args);
	}

	public Main(String[] args) throws InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException {
		if(args.length == 0) {
			try(BooksDB db = new BooksDB()) {
				dirs(Arrays.asList(Paths.get(".").toAbsolutePath().normalize()), db);
			}
		}
		Map<String, List<Path>> files = Arrays.stream(args)
				.map(Paths::get)
				.map(Path::toAbsolutePath)
				.map(Path::normalize)
				.filter(p -> {
					if(Files.notExists(p)) {
						System.out.println(ANSI.red("not found: ")+p);
						return false;
					}
					return true;
				})
				.collect(Collectors.groupingBy(p -> Files.isRegularFile(p) ? "file" : "dir"));

		if(isEmpty(files.get("file")) && isEmpty(files.get("dir"))) {
			System.out.println(ANSI.red("\nNOTHING TO SHOW"));
			return;
		}

		try(BooksDB db = new BooksDB()) {
			files(files.get("file"), db);
			dirs(files.get("dir"), db);
		}
	}

	private final Path bookdir = BookUtils.ROOT.normalize().toAbsolutePath(); 

	private void dirs(List<Path> list, BooksDB db) throws SQLException {
		if(isEmpty(list))
			return;

		int count = bookdir.getNameCount();

		Map<Path, Path> paths = list.stream().filter(p -> {
			if(p.startsWith(bookdir))
				return true;
			System.out.println("not a book dir: "+p);
			return false;
		}).collect(Collectors.toMap(p -> p.subpath(count, p.getNameCount()), p -> p));
		
		/**
		 * ResultSet rs = db.prepareStatementBlock(qm().selectAll().from(BookPathMeta.TABLE_NAME).where(w -> w.inPlaceholder(PATH, list.size())).build(), ps -> {
			int n = 1;
			for (Path path : paths.keySet()) {
				System.out.println(path.toString());
				ps.setString(n++, path.toString());
			}
			System.out.println(ps);
			return ps.executeQuery();
		});
		 */

		Map<Integer, Path> dbpaths =  db.collect(qm().selectAll().from(BookPathMeta.TABLE_NAME).where(w -> w.in(PATH, paths.keySet(), true)).build(), new HashMap<Integer, Path>(), r -> r.getInt("path_id"), r -> Paths.get(r.getString("_path")));

		paths.forEach((subpath, path) -> {
			if(!dbpaths.containsValue(subpath)) {
				System.out.println(ANSI.red("not found in DB: ")+subpath);
				return;
			}
		});
		if(dbpaths.isEmpty()) {
			System.out.println(ANSI.red("nothing to process"));
			return;
			
		}
		db.stream(bookSelect().where(w -> w.in(PATH_ID, dbpaths.keySet(), false)).build(), Book::new, e -> {throw new RuntimeException(e);})
				.collect(Collectors.groupingBy(b -> b.path_id))
				.forEach((id, books) -> {
					System.out.println(ANSI.yellow("-----------------------------------------"));
					System.out.println(ANSI.yellow("("+books.size()+") "+dbpaths.get(id)));
					printBook(books);
					System.out.println(ANSI.yellow("-----------------------------------------"));
				});
	}
	
	private Select bookSelect() {
		return qm().select(BOOK_ID, NAME, FILE_NAME, BooksMeta.PATH_ID, ISBN, PAGE_COUNT, YEAR).from(TABLE_NAME);
	}
	StringBuilder2 sb = new StringBuilder2();
	
	private void printBook(List<Book> books) {
		books.sort(Comparator.comparing((Book b) -> b.year == null ? 0 : Integer.parseInt(b.year)).reversed());
		
		for (Book b : books) {
			sb.setLength(0);
			sb.green("------------------------").ln()
			.yellow("id: ").append(b.id).cyan(" | ")
			.yellow("year: ").append(b.year).cyan(" | ")
			.yellow("pageCount: ").append(b.page_count).cyan(" | ")
			.yellow("path_id: ").append(b.path_id).cyan(" | ")
			.yellow("isbn: ").append(b.isbn).ln()
			.yellow("name: ").append(b.name).ln()
			.yellow("filename: ").append(b.file_name).ln()
			.yellow("combined: ").append(BookUtils.createDirname(b.id, b.file_name)).ln()
			.yellow("json: ").append(BookUtils.toJson(b.id, b.isbn, b.name)).ln()
			.green("------------------------").ln();
			
			System.out.println(sb);
		}
	}

	class Book {
		final int id,path_id;
		final String  name, file_name, isbn, page_count, year;

		public Book(ResultSet rs) throws SQLException {
			this.id=rs.getInt(BOOK_ID);
			this.name=rs.getString(NAME);
			this.file_name=rs.getString(FILE_NAME);
			this.path_id=rs.getInt(PATH_ID);
			this.isbn=rs.getString(ISBN);
			this.page_count=rs.getString(PAGE_COUNT);
			this.year=rs.getString(YEAR);
		}
	}

	QueryMaker qm() {
		return QueryMaker.getInstance();
	} 

	private void files(List<Path> list, BooksDB db) throws SQLException {
		if(isEmpty(list))
			return;
		
		printBook(db.collect(bookSelect().where(w -> w.in(FILE_NAME, list.stream().map(Path::getFileName).iterator(), true)).build(), new ArrayList<Book>(), Book::new));
	}

}
