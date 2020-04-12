import org.slf4j.event.Level;

import javafx.application.Application;
import sam.book.search.app.App;
import sam.myutils.LoggerUtils;

public class Main {
    public static void main(String[] args) {
        LoggerUtils.enableSlf4jSimple(Level.DEBUG);
        Application.launch(App.class, args);
    }
}
