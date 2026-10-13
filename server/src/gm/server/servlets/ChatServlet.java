package gm.server.servlets;

import gm.server.chat.ChatManager;
import gm.server.utils.ServletUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * Bonus chat.
 * <ul>
 *   <li>{@code GET /chat?version=V}: the lines added since the client's version V.</li>
 *   <li>{@code POST /chat?text=...} (or the text as the request body): adds a line.</li>
 * </ul>
 * The chat has its own small lock (inside {@link ChatManager}) and does not touch
 * the engine lock at all.
 */
public class ChatServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (requireUser(request, response) == null) {
            return;
        }
        int version = ServletUtils.optionalIntParameter(request, "version", 0);
        ChatManager chat = ServletUtils.getChatManager(getServletContext());
        ServletUtils.writeOk(response, chat.getSince(version));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String username = requireUser(request, response);
        if (username == null) {
            return;
        }
        String text = request.getParameter("text");
        if (text == null) {
            text = readBody(request);
        }
        ChatManager chat = ServletUtils.getChatManager(getServletContext());
        try {
            chat.addLine(username, text);
        } catch (IllegalArgumentException e) {
            ServletUtils.writeError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        }
        ServletUtils.writeOk(response, chat.getSince(Integer.MAX_VALUE));
    }

    private String readBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }
}
