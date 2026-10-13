package gm.server.servlets;

import gm.server.utils.ServletUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** {@code POST /events/open?id=N}: the market maker (the caller) opens the event. */
public class OpenEventServlet extends BaseServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handleWrite(request, response, (engine, username) ->
                engine.openEvent(ServletUtils.requireIntParameter(request, "id"), username));
    }
}
