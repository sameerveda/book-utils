package sam.books;

import com.carrotsearch.hppc.IntObjectMap;

public interface BooksDB {
    IntObjectMap<String> getFileNames(int[] ids);
}
