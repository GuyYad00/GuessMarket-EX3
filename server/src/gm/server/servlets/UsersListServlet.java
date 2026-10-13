package gm.server.servlets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** {@code GET /users}: every registered user (name, balance, market-maker flag, connected) plus the state version. */
public class UsersListServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handleRead(request, response, (engine, username) -> engine.getUsers());
    }
}
