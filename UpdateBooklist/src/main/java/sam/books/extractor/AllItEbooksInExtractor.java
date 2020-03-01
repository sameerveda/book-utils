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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import sam.console.ANSI;
import sam.myutils.System2;
import sam.string.StringUtils;

public class AllItEbooksInExtractor implements Extractor {

    private static final Map<String, String> validKeys = new HashMap<>();
    private static final String MARKER = System2.lookup("allitebooks.in", "allitebooks.in");
    static {
        validKeys.put("book name", NAME);
        validKeys.put("author", AUTHOR);
        validKeys.put("year", YEAR);
        validKeys.put("pages", PAGE_COUNT);
    }

    @Override
    public boolean canExtract(String url) {
        return url.contains(MARKER);

    }

    @Override
    public JSONObject extract(String url) throws IOException {
        if (!canExtract(url))
            return null;

        Document doc = Jsoup.parse(new URL(url), 20000);
        Element elm = Optional.ofNullable(doc.getElementsByClass("td-post-content")).filter(s -> s.size() == 1)
                .map(s -> s.get(0)).orElse(null);
        if (elm == null) {
            System.out.println("\".td-post-content\" not found: " + url);
            return null;
        }

        Elements ps = elm.children();
        Element meta;

        if (ps.get(1).tagName().equals("p"))
            meta = ps.get(1);
        else
            meta = ps.stream().filter(f -> f.tagName().equals("p")).skip(1).findFirst().get();

        List<Element> list = ps.stream().skip(ps.indexOf(meta) + 1)
                .filter(d -> !d.classNames().contains("code-block")
                        && !d.getElementsByTag("span").stream().anyMatch(f -> f.classNames().contains("td_btn")))
                .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder("<html>");
        JSONObject result = new JSONObject();

        if (!list.isEmpty()) {
            list.forEach(s -> sb.append(s).append('\n'));
            sb.append("</html>");
            result.put(DESCRIPTION, sb.toString());
        } else {
            System.out.println(DESCRIPTION + " not found: " + url);
        }

        StringUtils.splitAtNewlineStream(meta.wholeText()).forEach(s -> {
            int n = s.indexOf(':');
            if (n < 0)
                System.out.println(ANSI.red("bad line: ") + s);
            else {
                String k = s.substring(0, n).trim().toLowerCase();

                if (k.startsWith("isbn"))
                    k = ISBN;
                else
                    k = validKeys.get(k);

                if (k != null)
                    result.put(k, s.substring(n + 1).trim());
            }
        });

        return result;
    }
}
