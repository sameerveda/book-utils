package sam.dir.walker;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import sam.dir.walker.model.Dir;
import sam.dir.walker.model.FileWrap;

public class FullWalker {
    private final Predicate<File> fileFilter, dirFilter;

    public FullWalker(Predicate<File> fileFilter, Predicate<File> dirFilter) {
        this.fileFilter = fileFilter;
        this.dirFilter = dirFilter;
    }

    public List<Dir> walk(File rootDir) throws IOException {
        if(!rootDir.isDirectory())
            throw new FileNotFoundException("dir not found: "+rootDir);

        List<Dir> walk = new ArrayList<>();
        Dir root = new Dir(rootDir);
        walk.add(root);

        List<Dir> result = new ArrayList<>();
        List<FileWrap> files = new ArrayList<>();

        while(!walk.isEmpty()) {
            Dir dir = walk.remove(walk.size() - 1);
            result.add(dir);
            files.clear();
            File parent = dir.getFullPath();
            dir.setLastModified(parent.lastModified());

            for (String filename : parent.list()) {
                File path = new File(parent, filename);
                if(path.isDirectory()) {
                    if(dirFilter.test(path))
                        walk.add(new Dir(path));
                } else if(fileFilter.test(path)) {
                    files.add(new FileWrap(filename, path.lastModified()));
                } 
            }
            dir.setFiles(files.isEmpty() ? Dir.EMPTY : files.toArray(Dir.EMPTY));
        }
        
        return result;
    }
}
