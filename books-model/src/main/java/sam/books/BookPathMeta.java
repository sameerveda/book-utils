package sam.books;

public interface BookPathMeta {
    String PATH_TABLE_NAME = "Paths";

    String PATH_ID = "path_id";    // path_id   INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT UNIQUE
    String PATH = "_path";    // _path  TEXT NOT NULL UNIQUE
    String MARKER = "marker";    // marker  TEXT
}
