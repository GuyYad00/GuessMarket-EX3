package gm.server.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers when each logged-in user was last heard from. Every authenticated
 * request (the clients poll about once a second) refreshes the timestamp.
 * <p>
 * Purpose: if a client dies without logging out (killed window, lost network),
 * its user name would stay "connected" forever and nobody could log in with it
 * again. A login attempt for a name that has been silent for longer than
 * {@link #STALE_AFTER_MS} is therefore allowed to take the name over.
 */
public final class PresenceTracker {

    public static final long STALE_AFTER_MS = 20_000;

    private final Map<String, Long> lastSeen = new ConcurrentHashMap<>();

    public void touch(String username) {
        if (username != null) {
            lastSeen.put(username, System.currentTimeMillis());
        }
    }

    public void forget(String username) {
        if (username != null) {
            lastSeen.remove(username);
        }
    }

    /** @return {@code true} only if we have heard from this user before and not for a while. */
    public boolean isStale(String username) {
        Long seen = username == null ? null : lastSeen.get(username);
        return seen != null && System.currentTimeMillis() - seen > STALE_AFTER_MS;
    }
}
