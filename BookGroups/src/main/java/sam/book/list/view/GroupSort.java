package sam.book.list.view;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Function;

import sam.book.list.Grouping;
import sam.book.list.Sorting;
import sam.tsv.Row;
import sam.tsv.Tsv;

class GroupSort {
    private static volatile GroupSort instance;

    public static GroupSort getInstance() throws IOException {
        if (instance == null) {
            synchronized (GroupSort.class) {
                if (instance == null)
                    instance = new GroupSort();
            }
        }
        return instance;
    }
    
    private static final String groupNameK = "groupName"; 
    private static final String sortingK = "sorting";
    private static final String groupingK = "grouping";
    private static final String sortingReverseK = "sortingReverse";
    private static final String groupingReverseK = "groupingReverse";
    
    private static final String selfChange = "\0\0\0_SELF_CHANGE\n\r\0\0";
    
    private Sorting sorting = Sorting.NAME;
    private Grouping grouping = Grouping.NONE;
    private boolean sortingReverse;
    private boolean groupingReverse;
    private String groupName;
    
    private final Tsv tsv;
    private boolean modified;
    
    private GroupSort() throws IOException {
        Path  p = Paths.get("meta.tsv");
        
        if(Files.exists(p))
            tsv = Tsv.parse(p);
        else
            tsv = new Tsv(groupNameK,sortingK,groupingK,sortingReverseK,groupingReverseK);
    }
    
    public void save() throws IOException {
        changeGroup(selfChange);
        if(!modified)
            return;
        
        tsv.save(Paths.get("meta.tsv"));
    }
    public Sorting getSorting() {
        return sorting;
    }
    public Grouping getGrouping() {
        return grouping;
    }
    public Function<BookEntryView, ?> getClassifier() {
        return grouping.classifier;
    }
    public Comparator<Object> getSorter() {
        return sortingReverse ? sorting.comparator.reversed() : sorting.comparator;
    }
    public void flipSorting() {
        sortingReverse = !sortingReverse;
    }
    public void flipGrouping() {
        groupingReverse = !groupingReverse;
    }
    public void set(Sorting s, boolean reverse) {
        this.sorting = s;
        this.sortingReverse = reverse;
    }
    public void set(Grouping g, boolean reverse) {
        this.grouping = g;
        this.groupingReverse = reverse;
    }
    public void set(Grouping g) {
        this.grouping = g;
    }
    public void set(Sorting s) {
        this.sorting = s;
    }
    public void changeGroup(String name) {
        Objects.requireNonNull(name);
        
        if(groupName != null) {
            tsv.rowBuilder()
            .set(groupNameK, groupName)
            .set(sortingK,String.valueOf(sorting))
            .set(groupingK,String.valueOf(grouping))
            .set(sortingReverseK,String.valueOf(sortingReverse))
            .set(groupingReverseK,String.valueOf(groupingReverse))
            .add();
            
            modified = true;
        }
        
        if(name == selfChange)
            return;
        
        groupName = name;
        
        Row row = tsv.getWhere(groupNameK, name);
        
        if(row == null) {
            sorting = Sorting.NAME;
            grouping = Grouping.NONE;
            sortingReverse = false;
            groupingReverse = false;            
        } else {
            sorting = Sorting.valueOf(row.get(sortingK));
            grouping = Grouping.valueOf(row.get(groupingK));
            sortingReverse = row.get(sortingReverseK).equalsIgnoreCase("true");
            groupingReverse = row.get(groupingReverseK).equalsIgnoreCase("true");
            
            tsv.remove(row);
        }
    }
    public boolean isGroupingReverse() {
        return groupingReverse;
    }
}
