package gm.server.servlets;

import gm.dto.LoginResultDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * {@code GET /me}: tells a client who it is logged in as (used by the web client
 * after a page reload). Returns 401 when the session is not logged in.
 */
public class CurrentUserServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handleRead(request, response, (engine, username) ->
                new LoginResultDTO(username, "Logged in as " + username + "."));
    }
}
