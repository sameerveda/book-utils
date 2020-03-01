package sam.books;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Set;
import java.util.Stack;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import sam.cached.filetree.walk.Dir;
import sam.cached.filetree.walk.IPathWrap;
import sam.cached.filetree.walk.PathWrap;
import sam.console.ANSI;
import sam.io.serilizers.DataReader;

public class RootDir extends sam.cached.filetree.walk.RootDir {
    private Predicate<Path> excludes;
    private Predicate<String> valid_ext;
    private ArrayList<DirImpl> dirs = new ArrayList<>();
    private DirImpl rootDir;
    private Path walkCachePath;

    public void walk(Path selfDir, Path rootDirPath) throws IOException {
        if(rootDir != null || excludes != null)
            throw new IllegalStateException();
        
        selfDir = selfDir.resolve(getClass().getName());
        Files.createDirectories(selfDir);

        this.excludes = excludes(selfDir.resolve("ignore-files.txt"));
        Path p = selfDir.resolve("valid_ext.txt");

        if(Files.notExists(p)) {
            System.out.println("not found: "+p);
            valid_ext = s -> {
                s = ext(s);
                return s.equals("pdf") || s.equals("epub");
            };
        } else {
            Set<String> set = read(p).map(String::toLowerCase).collect(Collectors.toSet());
            if(set.isEmpty())
                throw new IllegalStateException("no valid ext specified");

            this.valid_ext = k -> set.contains(ext(k));    
        }

        this.walkCachePath = selfDir.resolve("walked_cache");

        DirImpl dir;
        if(Files.notExists(walkCachePath))
            dir =  walkFull(rootDirPath);
        else {
            dir = (DirImpl) loadCache(rootDirPath, walkCachePath);
            dir.initAsRoot(rootDirPath);
        } 

        this.rootDir = dir;
    }

    private String ext(String s) {
        int n = s.lastIndexOf('.');
        return n < 0 ? null : s.substring(n+1).toLowerCase();
    }

    private Predicate<Path> excludes(Path p) throws IOException {
        if(Files.notExists(p))
            return (k -> false);

        Set<Path> set = read(p)
                .map(Paths::get)
                .collect(Collectors.toSet());

        if(set.isEmpty())
            return (k -> false);

        if(set.size() == 1) {
            Path path = set.iterator().next();
            return path::equals;
        } else {
            return set::contains;
        }
    }

    private Stream<String> read(Path p) throws IOException {
        return Files.lines(p)
                .map(String::trim)
                .filter(s -> !s.isEmpty() && s.charAt(0) != '#');
    }

    private DirImpl walkFull(Path rootDir) {
        System.out.println(ANSI.yellow("full walk"));

        File file = rootDir.toFile();
        DirImpl maindir = new DirImpl(null, file.getName(), file, -1, null);
        maindir.initAsRoot(rootDir);

        Stack<DirImpl> stack = new Stack<>();
        stack.push(maindir);

        while(!stack.isEmpty()) {
            DirImpl dir = stack.pop();
            dirs.add(dir);

            for (PathWrap d : dir.list()) {
                if(d.isDir())
                    stack.push((DirImpl) d);   
            }
        }
        
        return maindir;
    }

    @Override
    protected DirImpl0 newDirImpl(DirImpl0 parent, DataReader reader) throws IOException{
        return new DirImpl(parent, reader);
    }

    public class PathWrapImpl extends PathWrapImpl0 {
        private boolean found;

        public PathWrapImpl(Dir parent, String name, File file, long lastModified, Path subpath) {
            super(parent, name, file, lastModified);
            this.subpath = subpath;
        }
        public PathWrapImpl(DirImpl0 parent, DataReader reader) throws IOException {
            super(parent, reader);
        }
        public void setFound(boolean found) {
            this.found = found;
        }
        public boolean isFound() {
            return found;
        }
        @Override
        public DirImpl parent() {
            return (DirImpl) super.parent();
        }
    }

    public class DirImpl extends DirImpl0 {
        private int path_id = -10;
        
        public DirImpl(Dir parent, DataReader reader) throws IOException {
            super(parent, reader);
        }
        public void initAsRoot(Path rootDirPath) {
            this.subpath = Paths.get("");
            this.fullpath = rootDirPath;

            this.file_subpath = new File("");
            this.file_fullpath = rootDirPath.toFile();
        }

        @Override
        protected PathWrap[] list() {
            return super.list();
        }

        public DirImpl(Dir parent, String name, File file, long lastModified, Path subpath) {
            super(parent, name, file, lastModified);
            this.subpath = subpath;
        }

        public void path_id(int path_id) {
            if(path_id != -10)
                throw new IllegalStateException();

            this.path_id = path_id;
        }

        public int path_id() {
            return path_id;
        }
        @Override
        protected PathWrap create(DataReader reader) throws IOException {
            return reader.readBoolean() ? new DirImpl(this, reader) : new PathWrapImpl(this, reader);
        }
        @Override
        protected PathWrap create(String name) {
            Path subpath = subpath().resolve(name);

            if(excludes.test(subpath))
                return null;

            File file = new File(fullpathAsFile(), name);

            if(file.isDirectory()) 
                return new DirImpl(this, name, file, -1, subpath);
            else if(valid_ext.test(name))
                return new PathWrapImpl(this, name, file, -1, subpath);
            else
                return null;
        }
    }

    public void forEach(Consumer<IPathWrap> action) {
        Stack<DirImpl> stack = new Stack<>();
        stack.push(rootDir);
        
        while(!stack.isEmpty()) {
            DirImpl dir = stack.pop();
            dir.forEach(d -> {
                action.accept(d);
                if(d.isDir())
                    stack.push((DirImpl) d);
            });
        }
    }
}
