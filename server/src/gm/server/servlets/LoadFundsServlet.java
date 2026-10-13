package gm.server.servlets;

import gm.server.utils.ServletUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** {@code POST /users/funds?amount=A}: loads money into the caller's own account. */
public class LoadFundsServlet extends BaseServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handleWrite(request, response, (engine, username) ->
                engine.loadFunds(username, ServletUtils.requireDoubleParameter(request, "amount")));
    }
}
