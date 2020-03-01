package sam.books;
import static sam.books.ChangeLogMeta.CHANGE_LOG_TABLE_NAME;
import static sam.books.ChangeLogMeta.DML_TYPE;
import static sam.books.ChangeLogMeta.ID;
import static sam.books.ChangeLogMeta.LOG_NUMBER;
import static sam.books.ChangeLogMeta.TABLENAME;

import javax.persistence.Column;
import javax.persistence.Table;

@Table(name=CHANGE_LOG_TABLE_NAME)
public class ChangeLog {
	@Column(name=LOG_NUMBER) private int logNumber;
	@Column(name=DML_TYPE) private ChangeType dmlType;
	@Column(name=TABLENAME) private String tableName;
	@Column(name=ID) private int id;

	public int getLogNumber(){ return this.logNumber; }
	public void setLogNumber(int logNumber){ this.logNumber = logNumber; }

	public ChangeType getDmlType(){ return this.dmlType; }
	public void setDmlType(ChangeType dmlType){ this.dmlType = dmlType; }

	public String getTableName(){ return this.tableName; }
	public void setTableName(String tableName){ this.tableName = tableName; }

	public int getId(){ return this.id; }
	public void setId(int id){ this.id = id; }
}
