package sam.books;

enum ColumnNames {
	NAME(BooksMeta.NAME),
	FILE_NAME(BooksMeta.FILE_NAME),
	PATH_ID(BooksMeta.PATH_ID),
	AUTHOR(BooksMeta.AUTHOR),
	ISBN(BooksMeta.ISBN),
	PAGE_COUNT(BooksMeta.PAGE_COUNT),
	YEAR(BooksMeta.YEAR),
	DESCRIPTION(BooksMeta.DESCRIPTION), 
	URL(BooksMeta.URL) ;
	
	public final String columnName;
	
	private ColumnNames(String s) {
		columnName = s;
	}

    public void set(NewBook b, String value) {
        switch (this) {
        case NAME: b.name = value; break;
        case AUTHOR: b.author = value; break;
        case ISBN: b.isbn = value; break;
        case PAGE_COUNT: b.page_count = value == null || !value.trim().matches("\\d+") ? 0 : Integer.parseInt(value.trim()); break;
        case YEAR: b.year = value; break;
        case DESCRIPTION: b.description = value; break;
        case URL: b.url = value; break;
		default:
			break;
        }
    }

    public String get(NewBook b) {
        switch (this) {
        case NAME: return  b.name;
        case FILE_NAME: return  b.path.name();
        case PATH_ID: return  String.valueOf(b.path.parent().path_id());
        case AUTHOR: return  b.author;
        case ISBN: return  b.isbn;
        case PAGE_COUNT: return  String.valueOf(b.page_count);
        case YEAR: return  b.year;
        case DESCRIPTION: return  b.description;
		case URL: return b.url;
        }
        return null;
    }
}