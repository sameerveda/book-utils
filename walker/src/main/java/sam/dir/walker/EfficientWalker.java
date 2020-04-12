package sam.dir.walker;

import static sam.dir.walker.model.DirDescriptor.dirsAs;
import static sam.dir.walker.model.DirDescriptor.newDir;
import static sam.dir.walker.model.DirDescriptor.updated;
import static sam.dir.walker.model.FileType.DELETED;
import static sam.dir.walker.model.FileType.NEW;
import static sam.dir.walker.model.FileType.OLD;
import static sam.dir.walker.model.FileType.UPDATED;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import sam.dir.walker.model.Dir;
import sam.dir.walker.model.DirDescriptor;
import sam.dir.walker.model.FileWrap;
import sam.dir.walker.model.State;

public class EfficientWalker {
    private final List<Dir> dirs;
    private final Predicate<File> fileFilter, dirFilter;
    private final FileWrap[] NEW_DIR = new FileWrap[0]; 
    private boolean collectUnmodifiedDir = false;

    public EfficientWalker(Collection<Dir> dirs, Predicate<File> fileFilter, Predicate<File> dirFilter) {
        this.dirs = new ArrayList<>(dirs);
        this.dirs.sort(Comparator.comparing(Dir::getFullPath));
        this.fileFilter = fileFilter;
        this.dirFilter = dirFilter;
    }

    private Map<File, Dir> dirsMap;

    public List<DirDescriptor> walk() {
        if (dirs.isEmpty())
            return Collections.emptyList();

        dirsMap = dirs.stream().collect(Collectors.toMap(Dir::getFullPath, Function.identity()));

        Set<File> processed = new HashSet<>();
        List<DirDescriptor> result = new ArrayList<>();

        while (!dirs.isEmpty()) {
            Dir dir = Objects.requireNonNull(dirs.remove(dirs.size() - 1));
            File dirPath = dir.getFullPath();
            if (processed.add(dirPath)) {
                DirDescriptor d = process(dir);
                if(d != null)
                    result.add(d);    
            }

        }

        return result;
    }

    private DirDescriptor process(final Dir dir) {
        File dirPath = dir.getFullPath();
        if (!dirPath.isDirectory())
            return dirsAs(dir, State.DELETED, -1, OLD);

        long newLastMod = dirPath.lastModified();
        if (dir.getLastModified() == newLastMod) {
            return collectUnmodifiedDir ? dirsAs(dir, State.UNMODIFIED, newLastMod, OLD) : null;
        } else {
            String[] list = dir.getFullPath().list();
            if (list.length == 0)
                return dirsAs(dir, State.UPDATED, newLastMod, DELETED);
            else {
                Map<String, FileWrap> map = dir.getFilesSafe().length == 0 ? Collections.emptyMap() : Arrays.stream(dir.getFilesSafe()).collect(Collectors.toMap(FileWrap::getName, Function.identity()));
                final DirDescriptor result;
                if(dir.getFilesSafe() == NEW_DIR) {
                    dir.setFiles(null);
                    result = newDir(dir, newLastMod);
                } else {
                    result = updated(dir, newLastMod);
                }

                for (String filename : list) {
                    File path = new File(dirPath, filename);
                    if (path.isDirectory()) {
                        if (dirFilter.test(path) && !dirsMap.containsKey(path)) {
                            Dir d = new Dir(path);
                            d.setFiles(NEW_DIR);
                            this.dirs.add(d);
                        }
                    } else if(fileFilter.test(path)) {
                        FileWrap oldFile = map.remove(filename);
                        long lastMod = path.lastModified();
                        if(oldFile == null) {
                            result.files.get(NEW).add(new FileWrap(filename, lastMod));
                        } else {
                            result.files.get(oldFile.getLastModified() == lastMod ? OLD : UPDATED).add(oldFile);
                            oldFile.setLastModified(lastMod);
                        }
                    }
                }
                if(!map.isEmpty())
                    result.files.get(DELETED).addAll(map.values());
                
                if(!collectUnmodifiedDir) {
                    int empty[] = {0};
                    result.files.forEach((s,t) -> {
                        if(s == OLD || t.isEmpty())
                            empty[0]++;
                    });
                    if(result.files.size() == empty[0])
                        return null;
                }
                
                return result;
            }
        }
    }
}
