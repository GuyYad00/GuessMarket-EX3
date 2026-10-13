package gm.server.servlets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** {@code GET /events}: every event in the system plus the state version (polled by clients). */
public class EventsListServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handleRead(request, response, (engine, username) -> engine.getEvents());
    }
}
