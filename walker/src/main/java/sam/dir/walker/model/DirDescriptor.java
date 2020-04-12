package sam.dir.walker.model;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonMap;
import static java.util.Collections.unmodifiableList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
public class DirDescriptor {
    public static final List<FileWrap> EMPTY = emptyList();
    
    public final Dir source;
    public final State state;
    public final Map<FileType, List<FileWrap>> files;
    public final long lastModified;
    
    public DirDescriptor(Dir source, State state, long lastMod, Map<FileType, List<FileWrap>> files) {
        this.source = source;
        this.state = state;
        this.files = files;
        this.lastModified = lastMod;
    }
    public long getLastModified() {
        return lastModified;
    }
    public Dir getSource() {
        return source;
    }
    public State getState() {
        return state;
    }
    public Map<FileType, List<FileWrap>> getFiles() {
        return files;
    }
    public static DirDescriptor dirsAs(Dir dir, State state, long lastMod, FileType type) {
        return new DirDescriptor(dir, state, lastMod, singletonMap(type, list(dir.getFilesSafe())));
    }
    private static List<FileWrap> list(FileWrap[] files) {
        return unmodifiableList(Arrays.asList(files));
    }
    public static DirDescriptor newDir(Dir dir, long lastMod) {
        return new DirDescriptor(dir, State.NEW, lastMod, singletonMap(FileType.NEW, new ArrayList<>()));
    }
    public static DirDescriptor updated(Dir dir, long lastMod) {
        EnumMap<FileType, List<FileWrap>> map = new EnumMap<>(FileType.class);
        for (FileType f : FileType.values()) 
            map.put(f, new ArrayList<>());
        
        return new DirDescriptor(dir, State.UPDATED, lastMod, map);
    }
}
