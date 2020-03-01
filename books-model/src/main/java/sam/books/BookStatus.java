package sam.books;

import java.nio.file.Path;
import java.nio.file.Paths;

public enum BookStatus {
	NONE, READ, READING, SKIPPED;
	
	private Path pathName;

	public Path getPathName() {
		if(this == NONE)
			pathName = Paths.get("");
			
		if(pathName == null)
			pathName = Paths.get("_"+toString().toLowerCase()+"_");
		return pathName;
	}
}
