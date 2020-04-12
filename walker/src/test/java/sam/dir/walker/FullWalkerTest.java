package sam.dir.walker;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static sam.dir.walker.WalkerUtils.*;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;

import org.json.JSONArray;
import org.junit.jupiter.api.Test;

import sam.dir.walker.model.Dir;
import sam.dir.walker.model.FileWrap;

class FullWalkerTest {

    @Test
    void test() throws IOException {
        Path root = Paths.get("H:\\ProgrammingComputer Books");
        HashMap<Path, List<String>> map = new HashMap<>();
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                map.put(dir, new ArrayList<>());
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                map.get(file.getParent()).add(file.getFileName().toString());
                return FileVisitResult.CONTINUE;
            }
        });

        List<Dir> dirs = new FullWalker(s -> true, s -> true).walk(root.toFile());

        System.out.println(map.size());
        assertEquals(map.size(), dirs.size());
        assertArrayEquals(map(map.keySet(), Path::toString), map(dirs, d -> d.getFullPath().toString()));
        String[] EMPTY = new String[0];

        for (Dir dir : dirs) {
            Path p =  dir.getFullPath().toPath();
            String[] expected = map(map.get(p), s -> s);
            String[] actual = dir.getFilesSafe() == null ? EMPTY : map(Arrays.asList(dir.getFilesSafe()), FileWrap::getName);
            if(p.getNameCount() != root.getNameCount())
                System.out.println(actual.length+": "+p.subpath(root.getNameCount(), p.getNameCount()));
            System.out.println("    "+String.join("\n    ", actual));
            assertArrayEquals(expected, actual);
        }
        
        StringBuilder sb = new StringBuilder();
        serialize(dirs, sb, defaultPathTrimmer(root.toFile()));
        dirs = null;
        String json = sb.toString();
        Dir[] dirs2 = deserialize(new StringReader(json), defaultPathResolver(root.toFile()));
        sb.setLength(0);
        serialize(Arrays.asList(dirs2), sb, defaultPathTrimmer(root.toFile()));
        assertEquals(json, sb.toString());
        System.err.println(new JSONArray(json).toString(4));
    }

    private static <E> String[] map(Collection<E> col, Function<E, String> mapper) {
        String[] result = new String[col.size()];
        int n[] = {0};
        col.forEach(t -> result[n[0]++] = mapper.apply(t));
        Arrays.sort(result);
        return result;
    }

}
