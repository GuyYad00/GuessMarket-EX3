package client.http;

import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** The single OkHttp client of the application (one connection pool, one cookie jar). */
public final class HttpClientUtil {

    private static final SimpleCookieManager COOKIES = new SimpleCookieManager();
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .cookieJar(COOKIES)
            .followRedirects(false)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    private HttpClientUtil() {
    }

    /** Asynchronous call: used from the JavaFX Application Thread, which must never block on I/O. */
    public static void runAsync(Request request, Callback callback) {
        CLIENT.newCall(request).enqueue(callback);
    }

    /** Synchronous call: only ever used from the background polling thread. The caller must close the response. */
    public static Response runSync(Request request) throws IOException {
        return CLIENT.newCall(request).execute();
    }

    public static void clearCookies() {
        COOKIES.clear();
    }

    public static void shutdown() {
        CLIENT.dispatcher().executorService().shutdown();
        CLIENT.connectionPool().evictAll();
    }
}
