package req;

public class ContentNotFoundException extends Exception {
    private static final long serialVersionUID = 1L;

    public ContentNotFoundException(String msg) {
        super(msg);
    }
}
