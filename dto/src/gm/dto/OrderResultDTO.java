package gm.dto;

import java.util.ArrayList;
import java.util.List;

/** The outcome of submitting an order-book order: the fills and the aftermath. */
public class OrderResultDTO {

    private final List<OrderFillDTO> fills;
    private final long restingQuantity;
    private final double userBalance;
    private final boolean userBlocked;
    private final String message;

    public OrderResultDTO(List<OrderFillDTO> fills, long restingQuantity, double userBalance,
                          boolean userBlocked, String message) {
        this.fills = new ArrayList<>(fills);
        this.restingQuantity = restingQuantity;
        this.userBalance = userBalance;
        this.userBlocked = userBlocked;
        this.message = message;
    }

    public List<OrderFillDTO> getFills() {
        return fills;
    }

    public long getRestingQuantity() {
        return restingQuantity;
    }

    public boolean isFullyFilled() {
        return restingQuantity == 0;
    }

    public double getUserBalance() {
        return userBalance;
    }

    public boolean isUserBlocked() {
        return userBlocked;
    }

    public String getMessage() {
        return message;
    }
}
