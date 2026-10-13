package gm.server.servlets;

import gm.server.utils.ServletUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** {@code POST /events/close?id=N&winner=K}: the market maker closes the event with option K (1-based) winning. */
public class CloseEventServlet extends BaseServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handleWrite(request, response, (engine, username) -> engine.closeEvent(
                ServletUtils.requireIntParameter(request, "id"),
                ServletUtils.requireIntParameter(request, "winner"),
                username));
    }
}
