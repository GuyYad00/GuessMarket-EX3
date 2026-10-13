package gm.server.servlets;

import gm.server.utils.ServletUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * {@code POST /events/order?id=N&option=K&side=Buy|Sell&price=P&quantity=Q}:
 * submits an order to an active order-book event.
 */
public class PlaceOrderServlet extends BaseServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handleWrite(request, response, (engine, username) -> engine.placeOrder(
                ServletUtils.requireIntParameter(request, "id"),
                username,
                ServletUtils.requireIntParameter(request, "option"),
                ServletUtils.requireParameter(request, "side"),
                ServletUtils.requireDoubleParameter(request, "price"),
                ServletUtils.requireLongParameter(request, "quantity")));
    }
}
