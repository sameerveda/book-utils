package sam.dir.walker.model;

public class FileWrap {
    protected final String name;
    protected long lastModified;
    
    public FileWrap(String name) {
        this.name = name;
    }
    public FileWrap(String name, long lastModified) {
        this.name = name;
        this.lastModified = lastModified; 
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public String getName() {
        return name;
    }
}
