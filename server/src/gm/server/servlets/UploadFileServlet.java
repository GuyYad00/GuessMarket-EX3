package gm.server.servlets;

import gm.server.utils.ServletUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

/**
 * {@code POST /upload} (multipart/form-data, exactly as in the course example).
 * <p>
 * The XML is read straight from the uploaded part's input stream into the engine
 * and is never written to the server's disk. If the file is valid all of its
 * events are added and the uploader becomes their market maker; otherwise HTTP
 * 400 carries the exact reason and nothing changes. The multipart limits are
 * configured in {@code web.xml}.
 */
public class UploadFileServlet extends BaseServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Part filePart = findFilePart(request);
        if (filePart == null) {
            // requireUser() first so an anonymous caller still gets a 401 rather than a 400.
            if (requireUser(request, response) == null) {
                return;
            }
            ServletUtils.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "No file was attached to the request (expected a multipart part named 'file').");
            return;
        }
        try (InputStream content = filePart.getInputStream()) {
            handleWrite(request, response, (engine, username) -> engine.uploadEvents(content, username));
        }
    }

    private Part findFilePart(HttpServletRequest request) throws ServletException, IOException {
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("multipart/")) {
            return null;
        }
        Collection<Part> parts = request.getParts();
        for (Part part : parts) {
            if ("file".equals(part.getName())) {
                return part;
            }
        }
        // Be forgiving: accept the first part that actually carries a file name.
        for (Part part : parts) {
            if (part.getSubmittedFileName() != null) {
                return part;
            }
        }
        return null;
    }
}
