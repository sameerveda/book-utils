package sam.books;


import java.io.File;
import java.util.Vector;

@SuppressWarnings("rawtypes")
public class Vector2 extends Vector {
    private static final long serialVersionUID = 1709677466267505329L;

    final File dir;

    public Vector2(File dir) {
        this.dir = dir;
    }

    @Override
    public synchronized String toString() {
        return dir.getName();
    }
}
