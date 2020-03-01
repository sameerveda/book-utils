package sam.books;

import java.nio.file.Path;
import java.nio.file.Paths;

import sam.config.EnvHelper;
import sam.config.Properties2;

public class Env {
    public static final Path
    
    ROOT,
    APP_DIR,
    DB_PATH,
    BACKUP_FOLDER;
    
    static {
        Properties2 c = EnvHelper.read(Env.class, "f89934fd-5359-4d7b-9216-89411e37545f.properties");
        
        ROOT = Paths.get(c.get("ROOT")).normalize();
        APP_DIR = Paths.get(c.get("APP_DIR")).normalize();
        DB_PATH = Paths.get(c.get("DB_PATH")).normalize();
        BACKUP_FOLDER = APP_DIR.resolve("backups").normalize();
        
        EnvHelper.printMissing(Env.class);
    }
}
