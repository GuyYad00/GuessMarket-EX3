package client.http;

/**
 * Where the server lives. The assignment allows assuming {@code localhost:8080}
 * and that the WAR keeps its name, so the context path is fixed to
 * {@code /guessmarket} (= {@code guessmarket.war}).
 * <p>
 * For convenience the base URL can still be overridden with the system property
 * {@code -Dgm.server=http://host:port/context} when running the client.
 */
public final class Constants {

    private static final String DEFAULT_BASE_URL = "http://localhost:8080/guessmarket";

    public static final String BASE_URL = System.getProperty("gm.server", DEFAULT_BASE_URL)
            .replaceAll("/+$", "");

    public static final String LOGIN = BASE_URL + "/login";
    public static final String LOGOUT = BASE_URL + "/logout";
    public static final String ME = BASE_URL + "/me";
    public static final String UPLOAD = BASE_URL + "/upload";
    public static final String EVENTS = BASE_URL + "/events";
    public static final String EVENT_DETAILS = BASE_URL + "/events/details";
    public static final String EVENT_OPEN = BASE_URL + "/events/open";
    public static final String EVENT_CLOSE = BASE_URL + "/events/close";
    public static final String EVENT_BUY = BASE_URL + "/events/buy";
    public static final String EVENT_ORDER = BASE_URL + "/events/order";
    public static final String USERS = BASE_URL + "/users";
    public static final String USER_DETAILS = BASE_URL + "/users/details";
    public static final String USER_FUNDS = BASE_URL + "/users/funds";
    public static final String CHAT = BASE_URL + "/chat";

    /** How often the client pulls fresh data from the server (the assignment allows up to 2 seconds). */
    public static final int POLL_INTERVAL_MS = 800;

    private Constants() {
    }
}
