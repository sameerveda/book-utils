package sam.books;

public interface ChangeLogMeta {
    String CHANGE_LOG_TABLE_NAME = "change_log";

    String LOG_NUMBER = "log_number";    // nth     integer NOT NULL PRIMARY KEY AUTOINCREMENT UNIQUE
    String DML_TYPE = "dml_type";    // _type   text NOT NULL
    String TABLENAME = "table_name";    // _table   text NOT NULL
    String ID = "_id";    // _id    integer NOT NULL
}
