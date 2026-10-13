package gm.server.servlets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * {@code GET /users/details}: the caller's own full account (balance, roles,
 * participations and the complete ledger). The user screen is private, so the
 * name is always taken from the session and never from a parameter.
 */
public class UserDetailsServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handleRead(request, response, (engine, username) -> engine.getUserDetails(username));
    }
}
