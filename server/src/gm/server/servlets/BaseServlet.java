package gm.server.servlets;

import engine.api.Engine;
import engine.exception.EngineOperationException;
import engine.exception.InvalidFileException;
import gm.server.utils.ServletUtils;
import gm.server.utils.SessionUtils;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;

/**
 * Common behaviour for every Guess Market servlet:
 * <ul>
 *   <li>require a logged-in session (otherwise HTTP 401 with a JSON error);</li>
 *   <li>run the engine call under the shared read or write lock;</li>
 *   <li>translate engine exceptions into HTTP status codes with a JSON body
 *       ({@code {"error": "..."}}) so clients can show the message as-is.</li>
 * </ul>
 */
public abstract class BaseServlet extends HttpServlet {

    /** A unit of work against the engine, executed by the servlet on behalf of the given user. */
    @FunctionalInterface
    protected interface EngineCall {
        Object run(Engine engine, String username);
    }

    /** @return the logged-in user name, or {@code null} after writing a 401 response. */
    protected String requireUser(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String username = SessionUtils.getUsername(request);
        if (username == null) {
            ServletUtils.writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "You are not logged in. Please log in first.");
            return null;
        }
        ServletUtils.getPresence(getServletContext()).touch(username);
        return username;
    }

    /** Runs a read-only query under the read lock and writes its JSON result. */
    protected void handleRead(HttpServletRequest request, HttpServletResponse response, EngineCall call)
            throws IOException {
        handle(request, response, call, lock -> lock.readLock());
    }

    /** Runs a state-changing action under the exclusive write lock and writes its JSON result. */
    protected void handleWrite(HttpServletRequest request, HttpServletResponse response, EngineCall call)
            throws IOException {
        handle(request, response, call, lock -> lock.writeLock());
    }

    private void handle(HttpServletRequest request, HttpServletResponse response, EngineCall call,
                        Function<java.util.concurrent.locks.ReentrantReadWriteLock, Lock> lockChooser)
            throws IOException {
        String username = requireUser(request, response);
        if (username == null) {
            return;
        }
        Engine engine = ServletUtils.getEngine(getServletContext());
        Lock lock = lockChooser.apply(ServletUtils.getEngineLock(getServletContext()));
        Object result;
        lock.lock();
        try {
            result = call.run(engine, username);
        } catch (ServletUtils.BadRequestException | InvalidFileException | EngineOperationException e) {
            ServletUtils.writeError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        } catch (RuntimeException e) {
            ServletUtils.writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unexpected server error: " + e.getMessage());
            return;
        } finally {
            lock.unlock();
        }
        ServletUtils.writeOk(response, result);
    }
}
