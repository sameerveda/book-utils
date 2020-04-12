package sam.books;

import java.io.File;

import sam.dir.walker.model.Dir;
import sam.dir.walker.model.FileWrap;

public class BookFile {
    public final Dir dir;
    public final FileWrap file;
    
    public BookFile(Dir dir, FileWrap file) {
        this.dir = dir;
        this.file = file;
    }
    
    private File fullpath;

    public File getFullPath() {
        if(fullpath == null)
            fullpath = new File(dir.getFullPath(), file.getName());
        return fullpath;
    }
}
