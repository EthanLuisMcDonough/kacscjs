package req;

import java.util.ArrayList;
import java.util.List;

public class ArrayListExceptions<E> extends ArrayList<E> {
    private static final long serialVersionUID = 1L;
    private List<Exception> exceptions = new ArrayList<Exception>();

    public boolean successful() {
        return exceptions.isEmpty();
    }

    public void addException(Exception e) {
        exceptions.add(e);
    }

    public List<Exception> getExceptions() {
        return exceptions;
    }
}
