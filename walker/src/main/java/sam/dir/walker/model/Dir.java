package sam.dir.walker.model;

import java.io.File;
import java.util.function.Consumer;

public class Dir {
    public  static final FileWrap[] EMPTY = new FileWrap[0];
    
    protected final File fullPath;
    protected FileWrap[] files;
    protected long lastModified;
    
    public Dir(File fullPath) {
        this.fullPath = fullPath;
    }
   
    public Dir(File fullPath, long lastModified, FileWrap[] files) {
        this.fullPath = fullPath;
        this.files = files;
        this.lastModified = lastModified;
    }

    public File getFullPath() {
        return fullPath;
    }
    public long getLastModified() {
        return lastModified;
    }
    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }
    public FileWrap[] getFilesSafe() {
        return files == null ? EMPTY : files;
    }
    public FileWrap[] getFiles() {
        return files;
    }

    public void setFiles(FileWrap[] files) {
        this.files = files;
    }
    public void forEach(Consumer<FileWrap> action) {
        if(files == null || files.length == 0)
            return;
        
        for (FileWrap f : files)
            action.accept(f);
    }
}
