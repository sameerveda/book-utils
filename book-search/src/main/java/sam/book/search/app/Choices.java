package sam.book.search.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import sam.books.BookStatus;
import sam.books.SortBy;

public enum Choices {
    TYPE(App.SHOW_ALL, new String[]{App.SHOW_ALL, App.BOOKMARK}),
    STATUS(App.SHOW_ALL, BookStatus.values()),
    SORT_BY(SortBy.ADDED, SortBy.values());
    
    final Object defaultValue;
    final List<Object> allValues;
    
    private Choices(Object defaultValue, Object[] allValues) {
        this.defaultValue = defaultValue;
        List<Object> list = Arrays.asList(allValues);
        if(!list.contains(defaultValue)) {
            List<Object> temp = new ArrayList<>();
            temp.add(defaultValue);
            temp.addAll(list);
            list = temp;
        }
        this.allValues = Collections.unmodifiableList(list);
    }
    
}
