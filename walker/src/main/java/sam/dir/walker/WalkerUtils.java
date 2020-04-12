package sam.dir.walker;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.json.JSONWriter;

import sam.dir.walker.model.Dir;
import sam.dir.walker.model.FileWrap;

public interface WalkerUtils {
    String PATH = "path";
    String LAST_MODIFIED = "last_modified";
    String FILES = "files";
    String NAME = "name";
    
    public static Function<File, String> defaultPathTrimmer(File rootFile) {
        String root = rootFile.toString();
        
        return file -> {
            String path = file.toString();
            if(path.equals(root))
                return "";
            if(path.length() > root.length() && path.startsWith(root)) 
                return path.substring(root.length() + 1);
             throw new IllegalArgumentException("bad path: "+path);
        };
    }
    
    public static Function<String, File> defaultPathResolver(File rootFile) {
        return path -> path.isEmpty() ? rootFile : new File(rootFile, path);
    }

    public static void serialize(Iterable<Dir> dirs, Path saveTo, Function<File, String> pathTrimmer) throws IOException {
        Objects.requireNonNull(dirs);
        try(GZIPOutputStream gos = new GZIPOutputStream(Files.newOutputStream(saveTo, CREATE, TRUNCATE_EXISTING));
                OutputStreamWriter w = new OutputStreamWriter(gos);
                ) {
            serialize(dirs, w, pathTrimmer);
        }
    }

    public static void serialize(Iterable<Dir> dirs, Appendable sink, Function<File, String> pathTrimmer) {
        JSONWriter w = new JSONWriter(sink);
        w.array();
        dirs.forEach(d -> {
            w.object()
            .key(PATH).value(pathTrimmer.apply(d.getFullPath()))
            .key(LAST_MODIFIED).value(d.getLastModified())
            .key(FILES).array();
            d.forEach(f -> {
                w.object()
                .key(NAME).value(f.getName())
                .key(LAST_MODIFIED).value(f.getLastModified())
                .endObject(); 
            });
            w.endArray().endObject();
        });
        w.endArray();
    }
    
    public static Dir[] deserialize(Path readFrom, Function<String, File> resolver) throws IOException {
        try(GZIPInputStream gos = new GZIPInputStream(Files.newInputStream(readFrom, CREATE, TRUNCATE_EXISTING));
                BufferedReader reader = new BufferedReader(new InputStreamReader(gos));
                ) {
           return deserialize(reader, resolver);
        }
    }

    public static Dir[] deserialize(Reader src, Function<String, File> resolver) {
        JSONArray dirsArray = new JSONArray(new JSONTokener(src));
        Dir[] dirs = new Dir[dirsArray.length()];
        
        for (int i = 0; i < dirsArray.length(); i++) {
            JSONObject json = dirsArray.getJSONObject(i);
            JSONArray filesArray = json.optJSONArray(FILES);
            FileWrap[] files = null;
            if(filesArray != null && !filesArray.isEmpty()) {
                files = new FileWrap[filesArray.length()];
                for (int k = 0; k < filesArray.length(); k++) {
                    JSONObject js = filesArray.getJSONObject(k);
                    files[k] = new FileWrap(js.getString(NAME), js.getLong(LAST_MODIFIED)); 
                }
            }
            dirs[i] = new Dir(resolver.apply(json.getString(PATH)), json.getLong(LAST_MODIFIED), files);
        }
        
        return dirs;
    }
    
}
