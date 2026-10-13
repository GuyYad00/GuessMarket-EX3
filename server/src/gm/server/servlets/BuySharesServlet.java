package gm.server.servlets;

import gm.server.utils.ServletUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** {@code POST /events/buy?id=N&option=K&shares=S}: buys S shares of option K (1-based) in an LMSR event. */
public class BuySharesServlet extends BaseServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handleWrite(request, response, (engine, username) -> engine.buyLmsrShares(
                ServletUtils.requireIntParameter(request, "id"),
                username,
                ServletUtils.requireIntParameter(request, "option"),
                ServletUtils.requireLongParameter(request, "shares")));
    }
}
