package req;

public class Result<V> {
    private final V value;
    private final Throwable error;

    private Result(V value, Throwable error) {
        this.value = value;
        this.error = error;
    }

    public static <V> Result<V> ok(V value) {
        return new Result<>(value,  null);
    }

    public static <V> Result<V> err(Throwable err) {
        return new Result<>(null,  err);
    }

    public boolean isOk() {
        return this.value != null;
    }

    public boolean isErr() {
        return this.error != null;
    }

    public V get() {
        return this.value;
    }

    public Throwable getErr() {
        return this.error;
    }
}
