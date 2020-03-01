package sam.books;


import java.io.File;

public class File2 {
    final File file;
    public File2(File file) {
        this.file = file;
    }

    @Override
    public String toString() {
        return file.getName();
    }
}