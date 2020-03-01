package sam.book.list.model;

import static sam.properties.myconfig.MyConfig.BOOKLIST_ROOT;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.stream.Stream;

import sam.tsv.Row;

public final class BookEntry {
	public final int id, pageCount, year;
	public final String name, author, isbn, description;
	public final Path path;
	private long lastReadTime, addTime;
	private boolean modified;
	private Row row;

	BookEntry(ResultSet rs) throws SQLException {
		this.id = rs.getInt("_id");
		this.pageCount = rs.getInt("page_count");
		this.year = rs.getInt("year");
		this.name = rs.getString("name");
		this.author = rs.getString("author");
		this.isbn = rs.getString("isbn");
		this.description = rs.getString("description");

		Path folder = Paths.get(rs.getString("_path"));
		String name = rs.getString("file_name");

		Path f = folder.resolve(name);
		if(Files.exists(BOOKLIST_ROOT.resolve(f)))
			this.path = f;
		else {
			this.path = 
					Stream.of(BOOKLIST_ROOT.resolve(folder).toFile().listFiles(File::isDirectory))
					.map(fldr -> new File(fldr, name))
					.filter(File::exists)
					.findFirst()
					.map(File::toPath)
					.map(p -> p.subpath(BOOKLIST_ROOT.getNameCount(), p.getNameCount()))
					.orElse(f);
		}
	}
	BookEntry setRow(Row row) {
		this.row = row;
		this.addTime = Long.parseLong(row.get(Model.getAddTimeIndex()));
		this.lastReadTime = Long.parseLong(row.get(Model.getLastReadTimeIndex()));
		return this;
	} 
	public int getId() {
		return id;
	}
	Row getRow() {
		return row;
	}
	boolean isModified() {
		return modified;
	}
	public String getName() {
		return name;
	}
	public Path getPath() {
		return path;
	}
	public long getAddTime() {
		return addTime;
	}
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("BookEntry [id=").append(id).append(", pageCount=").append(pageCount).append(", year=")
		.append(year).append(", name=").append(name).append(", author=").append(author).append(", isbn=")
		.append(isbn).append(", description=").append(description).append(", path=").append(path)
		.append("]");
		return builder.toString();
	}
	public long getLastReadTime() {
		return lastReadTime;
	}
	public void updateLastReadTime() {
		Objects.requireNonNull(row, "Tsv.row is not set");
		modified = true;
		this.lastReadTime = System.currentTimeMillis();
		row.set(Model.getLastReadTimeIndex(), String.valueOf(lastReadTime));
	}
}