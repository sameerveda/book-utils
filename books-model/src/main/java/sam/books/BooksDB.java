package sam.books;

import static sam.books.BookStatus.NONE;
import static sam.books.BookStatus.READ;
import static sam.books.BookStatus.SKIPPED;
import static sam.books.BookStatus.valueOf;
import static sam.books.BooksMeta.BOOK_TABLE_NAME;
import static sam.books.BooksMeta.FILE_NAME;
import static sam.books.BooksMeta.ID;
import static sam.books.Env.ROOT;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.almworks.sqlite4java.SQLiteException;
import com.almworks.sqlite4java.SQLiteStatement;
import com.carrotsearch.hppc.IntObjectMap;
import com.carrotsearch.hppc.IntObjectScatterMap;
import com.carrotsearch.hppc.cursors.IntObjectCursor;

import sam.myutils.Checker;
import sam.myutils.MyUtilsException;
import sam.myutils.MyUtilsPath;
import sam.sql.QueryHelper;
import sam.sql.Sqlite4javaHelper;
import sam.string.StringUtils;

public class BooksDB extends Sqlite4javaHelper {
    private final IntObjectMap<String> paths = new IntObjectScatterMap<>();

    public BooksDB() throws SQLiteException {
        this(Env.DB_PATH);
    }

    public BooksDB(Path dbpath) throws SQLiteException {
        super(dbpath, false);
    }

    public SQLiteStatement queryIds(BookStatus status, SortBy sort, SortDir sortDir, String nameLike,
            String additionalWhere) throws SQLiteException {
        if (Checker.isEmptyTrimmed(nameLike))
            nameLike = null;
        if (Checker.isEmptyTrimmed(additionalWhere))
            additionalWhere = null;

        if (status == null && sort == null && nameLike == null && additionalWhere == null) {
            return con.prepare("SELECT _id FROM Books;");
        }

        if (status == null && nameLike == null && additionalWhere == null) {
            return con.prepare(String.format("SELECT _id FROM Books ORDER BY %s %s;", sort.field,
                    sortDir == null ? sort.dir : sortDir));
        }

        StringBuilder sql = new StringBuilder("SELECT _id FROM Books WHERE ");

        if (status != null) {
            sql.append("(");
            if (status == BookStatus.NONE)
                sql.append("status IS NULL OR ");
            sql.append("status = '").append(status).append("') ");
        }

        if (nameLike != null) {
            if (status != null)
                sql.append(" AND ");
            sql.append(" name LIKE ? ");
        }

        if (additionalWhere != null) {
            if (status != null || nameLike != null)
                sql.append(" AND ");
            sql.append(additionalWhere);
        }

        if (sort != null) {
            sql.append(" ORDER BY ").append(sort.field).append(" ").append(sortDir == null ? sort.dir : sortDir);
        }
        
        SQLiteStatement st = con.prepare(sql.toString());
        if (nameLike != null)
            st.bind(1, "%" + nameLike + "%");
        
        return st;
    }

    public static Path findBook(Path expectedPath) {
        if (Files.exists(expectedPath))
            return expectedPath;
        Path path = expectedPath.resolveSibling("_read_").resolve(expectedPath.getFileName());

        if (Files.exists(path))
            return path;

        File[] dirs = expectedPath.getParent().toFile().listFiles(f -> f.isDirectory());

        if (dirs == null || dirs.length == 0)
            return null;

        String name = expectedPath.getFileName().toString();

        for (File file : dirs) {
            File f = new File(file, name);
            if (f.exists())
                return f.toPath();
        }
        return null;
    }

    public static BookStatus getStatusFromDir(Path dir) {
        Path name = dir.getFileName();
        if (name.equals(READ.getPathName()))
            return READ;
        if (name.equals(SKIPPED.getPathName()))
            return SKIPPED;

        return toBookStatus(name.toString());
    }

    public static BookStatus getStatusFromFile(Path p) {
        return getStatusFromDir(p.getParent());
    }

    public static BookStatus toBookStatus(String dirName) {
        if (dirName.charAt(0) == '_' && dirName.charAt(dirName.length() - 1) == '_' && dirName.indexOf(' ') < 0)
            return valueOf(dirName.substring(1, dirName.length() - 1).toUpperCase());

        return NONE;
    }
    
    public Path getExpectedSubpath(SmallBook book) {
        String dir = subpathByPathId(book.path_id);
        return Paths.get(dir, book.file_name);
    }
    
    public String subpathByPathId(int path_id) {
        String s = paths.get(path_id);
        if(s != null) 
            return s;
        s = MyUtilsException.noError(() -> getFirstByInt("SELECT _path FROM Paths WHERE path_id=?", path_id, st -> st.columnString(0)));
        paths.put(path_id, s);
        return s;
    }
    public Path getExpepectedFullPath(SmallBook book) {
        return Env.ROOT.resolve(getExpectedSubpath(book));
    }
    public Path getFullPath(SmallBook book) {
        Path p2 = findBook(getExpepectedFullPath(book));
        return p2 != null ? p2 : getExpepectedFullPath(book);
    }
    
    public int changeBookStatus(IntObjectMap<Path> bookIdPathMap, final BookStatus newStatus) throws Exception {
        Objects.requireNonNull(newStatus);
        Objects.requireNonNull(bookIdPathMap);
        
        if(bookIdPathMap.isEmpty())
            return 0;
        
        int n = 0;
        Exception exception = null;
        
        ArrayList<Path[]> moved = new ArrayList<>();
        IntObjectMap<String> bookIdFileNameMap = new IntObjectScatterMap<>();
        iterate(QueryHelper.selectWhereFieldInSQL(BOOK_TABLE_NAME, ID, bookIdPathMap.keys().toArray(), ID,FILE_NAME).toString(), rs -> bookIdFileNameMap.put(rs.columnInt(0), rs.columnString(1)));
        
        
        try {
            Logger logger = LoggerFactory.getLogger(getClass()); 
            
            for (IntObjectCursor<Path> entry : bookIdPathMap) {
                Path path = Objects.requireNonNull(entry.value);
                int book_id = Objects.requireNonNull(entry.key);
            
                if(Files.notExists(path))
                    throw new FileNotFoundException(path.toString());
                
                String s = bookIdFileNameMap.get(book_id);
                
                if(s == null)
                    throw new SQLException("no data found for book_id: "+book_id);
                
                if(!path.getFileName().toString().equals(s))
                    throw new SQLException("file_name mismatch: expected_name:"+path.getFileName()+"  found_name:"+s);
                
                BookStatus status = getStatusFromFile(path);
                Path target = path;
                if(status != NONE)
                    target = path.getParent();
                
                target = target.resolveSibling(newStatus.getPathName()).resolve(path.getFileName());
                
                 if(Files.notExists(target) || !Files.isSameFile(path, target)) {
                    Files.createDirectories(target.getParent());
                    Files.move(path, target, StandardCopyOption.REPLACE_EXISTING);
                    Path p = target;
                    moved.add(new Path[] {path, target});
                    logger.info("moved: "+ MyUtilsPath.subpath(path, ROOT)+" -> " + MyUtilsPath.subpath(p, ROOT));
                }
            }
            n = execute("UPDATE "+BOOK_TABLE_NAME+"status=? WHERE _id IN"+Arrays.toString(bookIdFileNameMap.keys().toArray()).replace('[', '(').replace(']', ')'), st -> st.bind(1, newStatus == null ? null : newStatus.toString()));
        } catch (Exception e) {
            exception = e;
        } finally {
            if(exception != null) {
                for (Path[] p : moved) {
                    try {
                        Files.move(p[1], p[0], StandardCopyOption.REPLACE_EXISTING);
                    } catch (Exception e) {}
                }
            }
        }
        
        if(exception != null)
            throw exception;
        
        return n;
    }
    
    private static WeakReference<Pattern> pattern = new WeakReference<Pattern>(null); 
    public static String createDirname(int book_id, String file_name) {
        StringBuilder sb = StringUtils.joinToStringBuilder(book_id,"-",file_name);
        Pattern p = pattern.get();
        if(p == null)
            pattern = new WeakReference<Pattern>(p = Pattern.compile("\\W+"));
        if(sb.length() > 4 && sb.substring(sb.length() - 4).equalsIgnoreCase(".pdf"))
            sb.setLength(sb.length() - 4);
        return p.matcher(sb).replaceAll("-");
    }
}
