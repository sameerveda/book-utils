package sam.book.list.model;

import sam.tsv.Row;
import sam.tsv.Tsv;

enum COLUMNS {
    ID("id"), GROUP("group"), LAST_READ_TIME("last_read_time"), ADD_TIME("add_time");

    final String colname;
    private int index;
    
    private COLUMNS(String colname) {
        this.colname = colname;
    }
    public void setIndex(Tsv c) {
        this.index = c.indexOfColumn(colname);
    }
    public int getIndex() {
        return index;
    }
    public String get(Row row) {
        return row.get(index);
    }
    public long getLong(Row row) {
        return row.getLong(index);
    }
    public int getInt(Row row) {
        return row.getInt(index);
    }
    public void set(Row row, String value) {
        row.set(index, value);
    }
    public void setLong(Row row, long value) {
         row.set(index, String.valueOf(value));
    }
}