package sam.books.extractor;

import java.io.IOException;

import org.json.JSONObject;

public interface Extractor {
    boolean canExtract(String url);
    JSONObject extract(String url) throws IOException;
}
