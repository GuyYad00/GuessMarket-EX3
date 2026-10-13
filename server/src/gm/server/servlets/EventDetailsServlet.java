package gm.server.servlets;

import gm.server.utils.ServletUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** {@code GET /events/details?id=N}: the full drill-down of one event. */
public class EventDetailsServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handleRead(request, response, (engine, username) ->
                engine.getEventDetails(ServletUtils.requireIntParameter(request, "id")));
    }
}
