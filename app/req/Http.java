package req;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import java.util.concurrent.TimeUnit;

public class Http {
    public static final PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager();

    static {
        manager.setMaxTotal(100);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> manager.close()));
    }

    public static final CloseableHttpClient client = HttpClientBuilder.create()
            .setConnectionTimeToLive(5L, TimeUnit.SECONDS).setConnectionManager(manager).build();
}