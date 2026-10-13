package gm.server.servlets;

import engine.api.Engine;
import gm.dto.LoginResultDTO;
import gm.server.utils.ServletUtils;
import gm.server.utils.SessionUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.concurrent.locks.Lock;

/** {@code POST /logout}: frees the user name and invalidates the session. */
public class LogoutServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        processRequest(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        processRequest(request, response);
    }

    private void processRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String username = SessionUtils.getUsername(request);
        if (username != null) {
            Engine engine = ServletUtils.getEngine(getServletContext());
            Lock lock = ServletUtils.getEngineLock(getServletContext()).writeLock();
            lock.lock();
            try {
                engine.logout(username);
            } finally {
                lock.unlock();
            }
            ServletUtils.getPresence(getServletContext()).forget(username);
        }
        SessionUtils.clearSession(request);
        ServletUtils.writeOk(response, new LoginResultDTO(username, "Logged out."));
    }
}
