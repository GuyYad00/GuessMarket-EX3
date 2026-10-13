package gm.server.utils;

import com.google.gson.Gson;
import engine.api.Engine;
import engine.api.EngineImpl;
import gm.dto.ErrorDTO;
import gm.server.chat.ChatManager;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Shared plumbing for all servlets: the single engine instance, the single chat
 * manager, the read/write lock that serialises access to the engine, Gson, and
 * small helpers for writing JSON responses and parsing parameters.
 * <p>
 * The engine and the lock live as attributes of the {@link ServletContext} (one
 * per web application), following the pattern of the course's summarising
 * example. Their creation is the only thing that needs its own synchronisation;
 * afterwards fetching them is lock free.
 */
public final class ServletUtils {

    private static final String ENGINE_ATTRIBUTE = "gm.engine";
    private static final String CHAT_ATTRIBUTE = "gm.chat";
    private static final String LOCK_ATTRIBUTE = "gm.lock";
    private static final String PRESENCE_ATTRIBUTE = "gm.presence";

    private static final Object ENGINE_CREATION_LOCK = new Object();
    private static final Object CHAT_CREATION_LOCK = new Object();
    private static final Object LOCK_CREATION_LOCK = new Object();
    private static final Object PRESENCE_CREATION_LOCK = new Object();

    public static final Gson GSON = new Gson();

    private ServletUtils() {
    }

    public static Engine getEngine(ServletContext context) {
        synchronized (ENGINE_CREATION_LOCK) {
            if (context.getAttribute(ENGINE_ATTRIBUTE) == null) {
                context.setAttribute(ENGINE_ATTRIBUTE, new EngineImpl());
            }
        }
        return (Engine) context.getAttribute(ENGINE_ATTRIBUTE);
    }

    public static ChatManager getChatManager(ServletContext context) {
        synchronized (CHAT_CREATION_LOCK) {
            if (context.getAttribute(CHAT_ATTRIBUTE) == null) {
                context.setAttribute(CHAT_ATTRIBUTE, new ChatManager());
            }
        }
        return (ChatManager) context.getAttribute(CHAT_ATTRIBUTE);
    }

    /**
     * One read/write lock guards the engine: the many polling GET requests take
     * the read lock and run concurrently; every mutating request takes the write
     * lock and runs alone. This is the "synchronise high, at the servlet level"
     * approach recommended in the course forum, with cheaper reads.
     */
    public static ReentrantReadWriteLock getEngineLock(ServletContext context) {
        synchronized (LOCK_CREATION_LOCK) {
            if (context.getAttribute(LOCK_ATTRIBUTE) == null) {
                context.setAttribute(LOCK_ATTRIBUTE, new ReentrantReadWriteLock(true));
            }
        }
        return (ReentrantReadWriteLock) context.getAttribute(LOCK_ATTRIBUTE);
    }

    public static PresenceTracker getPresence(ServletContext context) {
        synchronized (PRESENCE_CREATION_LOCK) {
            if (context.getAttribute(PRESENCE_ATTRIBUTE) == null) {
                context.setAttribute(PRESENCE_ATTRIBUTE, new PresenceTracker());
            }
        }
        return (PresenceTracker) context.getAttribute(PRESENCE_ATTRIBUTE);
    }

    // ---- JSON responses ----

    public static void writeJson(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.print(GSON.toJson(body));
            out.flush();
        }
    }

    public static void writeOk(HttpServletResponse response, Object body) throws IOException {
        writeJson(response, HttpServletResponse.SC_OK, body);
    }

    public static void writeError(HttpServletResponse response, int status, String message) throws IOException {
        writeJson(response, status, new ErrorDTO(message));
    }

    // ---- parameter parsing (the server never trusts the client) ----

    public static String requireParameter(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        if (value == null || value.trim().isEmpty()) {
            throw new BadRequestException("Missing required parameter '" + name + "'.");
        }
        return value.trim();
    }

    public static int requireIntParameter(HttpServletRequest request, String name) {
        String value = requireParameter(request, name);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Parameter '" + name + "' must be a whole number (got '" + value + "').");
        }
    }

    public static long requireLongParameter(HttpServletRequest request, String name) {
        String value = requireParameter(request, name);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Parameter '" + name + "' must be a whole number (got '" + value + "').");
        }
    }

    public static double requireDoubleParameter(HttpServletRequest request, String name) {
        String value = requireParameter(request, name);
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Parameter '" + name + "' must be a number (got '" + value + "').");
        }
    }

    public static int optionalIntParameter(HttpServletRequest request, String name, int defaultValue) {
        String value = request.getParameter(name);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** Thrown by the parameter helpers; mapped to HTTP 400 by {@link gm.server.servlets.BaseServlet}. */
    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) {
            super(message);
        }
    }
}
