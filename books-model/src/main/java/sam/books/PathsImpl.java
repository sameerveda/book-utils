package sam.books;
import static sam.books.PathMeta.MARKER;
import static sam.books.PathMeta.PATH;
import static sam.books.PathMeta.PATH_ID;
import static sam.books.PathMeta.PATH_TABLE_NAME;

import javax.persistence.Column;
import javax.persistence.Table;

@Table(name=PATH_TABLE_NAME)
public class PathsImpl {
	@Column(name=PATH_ID) private int id;
	@Column(name=PATH)    private String path;
	@Column(name=MARKER)  private String marker;
	
	public int getId(){ return this.id; }
	public void setId(int id){ this.id = id; }

	public String getPath(){ return this.path; }
	public void setPath(String path){ this.path = path; }

	public String getMarker(){ return this.marker; }
	public void setMarker(String marker){ this.marker = marker; }
}

