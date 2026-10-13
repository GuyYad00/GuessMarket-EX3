package gm.server.utils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * The logged-in user is remembered in the HTTP session (cookie based, exactly
 * like the course's summarising example). Every request after login carries the
 * session cookie, so servlets can tell who is calling without the client having
 * to repeat its name.
 */
public final class SessionUtils {

    private static final String USERNAME_ATTRIBUTE = "username";

    private SessionUtils() {
    }

    /** @return the logged-in user name, or {@code null} if this session is not logged in. */
    public static String getUsername(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object attribute = session != null ? session.getAttribute(USERNAME_ATTRIBUTE) : null;
        return attribute != null ? attribute.toString() : null;
    }

    public static void setUsername(HttpServletRequest request, String username) {
        request.getSession(true).setAttribute(USERNAME_ATTRIBUTE, username);
    }

    public static void clearSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
