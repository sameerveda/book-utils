package sam.books.walker;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.Collections.emptyMap;
import static java.util.Collections.newSetFromMap;
import static java.util.Collections.unmodifiableMap;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sam.books.walker.Dir.BookFile;
import sam.console.ANSI;
import sam.io.serilizers.DataReader;
import sam.io.serilizers.DataWriter;
import sam.nopkg.Resources;
import sam.string.LazyStringBuilder;

public class Walker {
    private static final Logger logger = LoggerFactory.getLogger(Walker.class);
    private static final long MARKER = 1561476915839L;

    private Predicate<Path> excludes;
    private Predicate<String> valid_ext;
    private ArrayList<BookFile> filesList = new ArrayList<>();
    private ArrayList<Dir> dirsList = new ArrayList<>();

    public ArrayList<Dir> walk(Path selfDir, Path rootDirPath) throws IOException {
        if(excludes != null)
            throw new IllegalStateException();

        selfDir = selfDir.resolve(getClass().getName());
        Files.createDirectories(selfDir);

        this.excludes = excludes(selfDir.resolve("ignore-files.txt"));
        this.valid_ext = valid_ext(selfDir); 

        Path walkCachePath = cachePath(selfDir);

        if(Files.notExists(walkCachePath))
            return walkFull(rootDirPath.toFile());
        else 
            return new ArrayList<>(loadCached(rootDirPath, walkCachePath).values());
    }
    
    private Predicate<String> valid_ext(Path selfDir) throws IOException {
        Path p = selfDir.resolve("valid_ext.txt");

        if(Files.notExists(p)) {
            System.out.println("not found: "+p);
            logger.debug("valid extensions: [pdf, epub]");
            
            return s -> {
                s = ext(s);
                return s.equals("pdf") || s.equals("epub");
            };
        } else {
            Set<String> set = read(p).map(String::toLowerCase).collect(Collectors.toSet());
            if(set.isEmpty())
                throw new IllegalStateException("no valid ext specified");
            
            logger.debug("valid extensions: {}", set);
            return k -> set.contains(ext(k));    
        }
    }

    private HashMap<Path, Dir> loadCached(Path rootDirPath, Path walkCachePath) throws IOException {
        Root root;
        HashMap<Path, Dir> dirs;
        
        try(FileChannel fc = FileChannel.open(walkCachePath, READ);
                Resources r = Resources.get();
                DataReader d = new DataReader(fc, r.buffer());
                ) {
            d.setChars(r.chars());
            d.setDecoder(r.decoder());
            d.setStringBuilder(r.sb());
            
            if(MARKER != d.readLong())
                throw new IOException("invalid file");
            
            dirs = new HashMap<>(d.readInt() + 50);
            root = new Root(d);
            
            if(!Files.isSameFile(root.fullPath().toPath(), rootDirPath))
                throw new IOException("was dir: "+root.fullPath()+" != "+rootDirPath);
        }
        
        Stack<Dir> stack = new Stack<>();
        stack.add(root);
        
        while(!stack.isEmpty()) {
            Dir dr = stack.pop();
            
            if(dr.fullPath().exists()) {
                dirs.put(dr.subpath(), dr);
                dr.forEachDir(stack::push);    
            } else {
                logger.debug("deleted: {}", dr.subpath());
            }
        }
        
        LazyStringBuilder sb = LazyStringBuilder.create(logger.isDebugEnabled())
                .append(ANSI.yellow("modified dirs: \n"));
        stack.clear();
        
        dirs.forEach((p, dir) -> {
            if(dir.isModified()) {
                sb.append("  ").append(p).append('\n');
                stack.add(dir);
            }
        });
        

        if(stack.isEmpty()){
            logger.info(ANSI.green("no dir modified"));
            return dirs;
        }
        
        logger.debug("{}", sb.append("\n\n"));
        
        Map<Path, Dir> unmod = unmodifiableMap(dirs);
        HashMap<String, BookFile> files = new HashMap<>();
        
        Set<Dir> uniqueDirs = newSetFromMap(new IdentityHashMap<>());
        sb.setLength(0);
        sb.append(ANSI.yellow("new Dirs\n"));
        
        while(!stack.isEmpty()) {
            Dir dr = stack.pop();
            
            files.clear();
            dr.forEachFiles(b -> files.put(b.name, b));
            
            update(dr, files, null, unmod);
            
            dr.forEachDir(d -> {
                if(!uniqueDirs.contains(d)) {
                    Dir dold = dirs.put(d.subpath(), d);
                    sb.append("  ").append(dold == null ? d : (dold + " -> "+d)).append('\n');
                    uniqueDirs.add(d);
                    stack.add(d);
                }
            });
        }
        
        logger.debug("{}", sb.append("\n\n"));
        return dirs;
    }

    private Path cachePath(Path selfDir) {
        return selfDir.resolve("walked_cache");
    }

    public void write(Path selfDir, List<Dir> dirs) throws IOException {
        if(dirs.isEmpty()) {
            Files.deleteIfExists(cachePath(selfDir));
            return;
        }
        
        Root root = dirs.stream().filter(d -> d.getClass() == Root.class).findFirst().map(d -> (Root)d).orElseThrow(() -> new IllegalArgumentException("Root not found in dirs"));
        
        try(FileChannel fc = FileChannel.open(cachePath(selfDir), WRITE, TRUNCATE_EXISTING, CREATE);
                Resources r = Resources.get();
                DataWriter d = new DataWriter(fc, r.buffer());
                ) {
            d.setEncoder(r.encoder());
            
            d.writeLong(MARKER);
            d.writeInt(dirs.size());
            
            root.write(d);
        }
    }

    private String ext(String s) {
        int n = s.lastIndexOf('.');
        return n < 0 ? null : s.substring(n+1).toLowerCase();
    }

    private Predicate<Path> excludes(Path p) throws IOException {
        if(Files.notExists(p)) {
            logger.warn("not found: {}", p);
            return (k -> false);
        }
            

        LazyStringBuilder sb = LazyStringBuilder.create(logger.isDebugEnabled());
        sb.append("excludes: \n  ");
        
        Set<Path> set = read(p)
                .peek(s -> sb.append(s).append("\n  "))
                .map(Paths::get)
                .collect(Collectors.toSet());
        
        sb.append('\n');
        logger.debug(sb.toString());

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
    
    private ArrayList<Dir> walkFull(File rootDir) {
        System.out.println(ANSI.yellow("full walk"));

        ArrayList<Dir> dirs = new ArrayList<>(300);
        Stack<Dir> stack = new Stack<>();
        
        stack.push(new Root(rootDir.toString(), rootDir, Paths.get("")));
        
        while(!stack.isEmpty()) {
            Dir dir = stack.pop();
            dirs.add(dir);
            
            update(dir, emptyMap(), emptyMap(), null);
            dir.forEachDir(stack::add);
        }
        
         return dirs;
    }
    
    private void update(Dir dir, Map<String, BookFile> files, Map<String, Dir> dirs, Map<Path, Dir> dirsBySubPath) {
        filesList.clear();
        dirsList.clear();
        
        for (String name : dir.fullPath().list()) {
            Path sp = dir.subpath.resolve(name);
            
            if(excludes.test(sp)) {
                logger.debug("exclude: {}", sp);
                continue;
            }
            
            File f = new File(dir.dir, name);
            
            if(f.isDirectory()){
                Dir d = dirsBySubPath != null ? dirsBySubPath.get(sp) : dirs.get(name);
                dirsList.add(d != null ? d : new Dir(dir, name, f, sp));
            }  else if(valid_ext.test(name)) {
                BookFile f2 = files.get(name);
                filesList.add(f2 != null ? f2 : dir.child(name, f));
            } else {
                logger.debug("exclude(invalid ext): {} -> {}", name, sp);
            } 
        }
        
        dir.setFiles(filesList);
        dir.setDirs(dirsList);
    }
    
    private static class Root extends Dir {
        public Root(String name, File dir, Path subpath) {
            super(null, name, dir, subpath);
        }

        public Root(DataReader d) throws IOException {
            super(d, null);
            
            this.subpath = Paths.get("");
            this.dir = new File(name);
        }

        public void write(DataWriter d) throws IOException {
            super.write(d);
        }
    }
}
