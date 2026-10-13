package gm.server.servlets;

import engine.api.Engine;
import engine.exception.EngineOperationException;
import gm.dto.LoginResultDTO;
import gm.server.utils.PresenceTracker;
import gm.server.utils.ServletUtils;
import gm.server.utils.SessionUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.concurrent.locks.Lock;

/**
 * {@code POST /login?username=...} (GET is accepted too, for easy testing).
 * <p>
 * Login is by name only (no passwords, no sign-up). The "is this name taken?"
 * check and the registration must be one atomic step, so the whole thing runs
 * as a single engine call under the write lock. On success the name is stored in
 * the HTTP session; a taken name yields HTTP 409 with an explanation so the user
 * can try another name.
 */
public class LoginServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        processRequest(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        processRequest(request, response);
    }

    private void processRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String existing = SessionUtils.getUsername(request);
        if (existing != null) {
            // This session is already logged in: answer idempotently.
            ServletUtils.writeOk(response, new LoginResultDTO(existing, "You are already logged in as " + existing + "."));
            return;
        }

        String requested = request.getParameter("username");
        if (requested == null || requested.trim().isEmpty()) {
            ServletUtils.writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Please enter a user name.");
            return;
        }

        Engine engine = ServletUtils.getEngine(getServletContext());
        PresenceTracker presence = ServletUtils.getPresence(getServletContext());
        Lock lock = ServletUtils.getEngineLock(getServletContext()).writeLock();
        LoginResultDTO result;
        lock.lock();
        try {
            String trimmed = requested.trim();
            if (presence.isStale(trimmed)) {
                // The previous client with this name vanished without logging out: free the name.
                engine.logout(trimmed);
                presence.forget(trimmed);
            }
            result = engine.login(trimmed);
            presence.touch(result.getUserName());
        } catch (EngineOperationException e) {
            boolean taken = e.getMessage() != null && e.getMessage().contains("already connected");
            ServletUtils.writeError(response, taken ? HttpServletResponse.SC_CONFLICT
                    : HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        } finally {
            lock.unlock();
        }
        SessionUtils.setUsername(request, result.getUserName());
        ServletUtils.writeOk(response, result);
    }
}
