package sam.books.extractor;

import static sam.books.BooksMeta.AUTHOR;
import static sam.books.BooksMeta.DESCRIPTION;
import static sam.books.BooksMeta.ISBN;
import static sam.books.BooksMeta.NAME;
import static sam.books.BooksMeta.PAGE_COUNT;
import static sam.books.BooksMeta.YEAR;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import sam.myutils.System2;

public class AllItEbooksOrgExtractor implements Extractor {

    private static final Map<String, String> validKeys = new HashMap<>();
    private static final String MARKER = System2.lookup("allitebooks.org", "allitebooks.org");
    static {
        validKeys.put("author:", AUTHOR);
        validKeys.put("year:", YEAR);
        validKeys.put("pages:", PAGE_COUNT);
    }

    @Override
    public boolean canExtract(String url) {
        return url != null && url.contains(MARKER);
    }

    @Override
    public JSONObject extract(String url) throws IOException {
        if (!canExtract(url))
            return null;

        Document doc = Jsoup.parse(new URL(url), 20000);
        Element elm = doc.getElementsByClass("entry-content").get(0);
        JSONObject result = new JSONObject();

        result.put(DESCRIPTION, "<html>" + elm.html().toString().replaceFirst("<h3>.+</h3>\\s+", "") + "</html>");
        result.put(NAME, doc.getElementsByClass("single-title").get(0).text());

        elm = doc.getElementsByClass("book-detail").get(0);
        elm.getElementsByTag("dt").forEach(e -> {
            String label = null;
            String key = e.text().toLowerCase();

            if (key.startsWith("isbn"))
                label = ISBN;
            else
                label = validKeys.get(key.trim());

            if (label != null)
                result.put(label, ((Element) e.nextSibling()).text());
        });

        return result;
    }
}
