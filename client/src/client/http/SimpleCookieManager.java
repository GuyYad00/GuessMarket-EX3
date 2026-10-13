package client.http;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal in-memory cookie jar, so the {@code JSESSIONID} the server hands out
 * on login is sent back with every following request (the server identifies the
 * user by the session). Same idea as the course's summarising example, minus the
 * debug logging.
 */
public class SimpleCookieManager implements CookieJar {

    private final Map<String, Map<String, Cookie>> cookiesByHost = new HashMap<>();

    @Override
    public List<Cookie> loadForRequest(HttpUrl url) {
        synchronized (this) {
            Map<String, Cookie> forHost = cookiesByHost.get(url.host());
            return forHost == null ? Collections.emptyList() : new ArrayList<>(forHost.values());
        }
    }

    @Override
    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
        synchronized (this) {
            Map<String, Cookie> forHost = cookiesByHost.computeIfAbsent(url.host(), host -> new HashMap<>());
            for (Cookie cookie : cookies) {
                forHost.put(cookie.name(), cookie);
            }
        }
    }

    public void clear() {
        synchronized (this) {
            cookiesByHost.clear();
        }
    }
}
