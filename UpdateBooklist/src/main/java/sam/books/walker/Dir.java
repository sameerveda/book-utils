package sam.books.walker;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import sam.io.serilizers.DataReader;
import sam.io.serilizers.DataWriter;

public class Dir {
    private static final int MARKER = -10;
    public static final BookFile[] EMPTY = new BookFile[0];
    public static final Dir[] EMPTY_DIRS = new Dir[0];

    private BookFile[] files;
    private Dir[] dirs;
    private int path_id = MARKER;
    public  final Dir parent;
    private long old_last_modified, last_modified = MARKER;
    public  final String name;
    protected  File dir;
    protected  Path subpath;

    public Dir(Dir parent, String name, File dir, Path subpath) {
        this.parent = parent;
        this.name = name;
        this.dir = dir;
        this.subpath = subpath;
    }
    public Dir(DataReader d, Dir parent) throws IOException {
        this.old_last_modified = d.readLong();
        this.name = d.readUTF();
        this.parent = parent;
        
        int size = d.readInt();
        this.files = size == 0 ? EMPTY : new BookFile[size];
        
        for (int i = 0; i < files.length; i++) 
            files[i] = new BookFile(d.readUTF(), null);
        
        size = d.readInt();
        this.dirs = size == 0 ? EMPTY_DIRS : new Dir[size];
        
        for (int i = 0; i < dirs.length; i++) 
            dirs[i] = new Dir(d, this);
    }
    
    public void write(DataWriter d) throws IOException {
        d.writeLong(old_last_modified);
        d.writeUTF(name);
        
        d.writeInt(files.length);
        for (BookFile f : files) 
            d.writeUTF(f.name);
        
        d.writeInt(dirs.length);
        
        for (Dir f : dirs)
            f.write(d);
    }
    
    public Path subpath() {
        if(subpath == null)
            subpath = parent.subpath().resolve(name);
        
        return subpath;
    }
    public void forEachDir(Consumer<Dir> action) {
        for (Dir dir : dirs) 
            action.accept(dir);
    }
    public void forEachFiles(Consumer<BookFile> action) {
        for (BookFile dir : files) 
            action.accept(dir);
    }
    public File fullPath() {
        if(dir == null)
            dir = new File(parent.fullPath(), name);
        return dir;
    }
    
    public long last_modified() {
        return last_modified;
    }
    public int path_id() {
        return path_id;
    }
    public BookFile child(String name, File file) {
        return new BookFile(name, file);
    }
    public void setFiles(List<BookFile> files) {
        this.files = files.isEmpty() ? EMPTY : files.toArray(new BookFile[files.size()]);
    }
    public void setDirs(List<Dir> dirs) {
        this.dirs = dirs.isEmpty() ? EMPTY_DIRS : dirs.toArray(new Dir[dirs.size()]);
    }

    public void path_id(int path_id) {
        if(this.path_id != MARKER)
            throw new IllegalStateException("already set: "+this.path_id+", "+subpath());

        this.path_id = path_id; 
    }

    public boolean isModified() {
        if(last_modified == MARKER)
            last_modified = fullPath().lastModified();
        
        return last_modified != old_last_modified;
    }
    @Override
    public String toString() {
        return subpath().toString();
    }
    
   public class BookFile {
        public final String name;
        private File file;
        private Path subpath;

         public BookFile(String name, File file) {
             this.name = name;
             this.file = file;
         }

        public Path subpath() {
            if(subpath == null)
                subpath = parent().subpath().resolve(name);
            
            return subpath;
        }
        
        public Dir parent() {
            return Dir.this;
        }

        private boolean found;
        public void setFound(boolean found) {
            this.found = found;
        }
        public boolean isFound() {
            return found;
        }

        public File fullPath() {
            if(file == null)
                file = new File(parent().fullPath(), name);
            return file;
        }
     }
}