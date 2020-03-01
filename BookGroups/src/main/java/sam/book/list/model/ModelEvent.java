package sam.book.list.model;

import java.util.List;

public interface ModelEvent {
    public void dataSaved();
    public void groupChanged(String groupName, List<BookEntry> currentItems);
    public void added(List<BookEntry> addedItems, List<BookEntry> currentItems);
    public void removed(List<BookEntry> removeditems, List<BookEntry> currentItems);    
}
