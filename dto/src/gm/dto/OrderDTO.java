package gm.dto;

/** Immutable view of one resting order in an order book. */
public class OrderDTO {

    private final String userName;
    private final String side;
    private final double price;
    private final long quantity;

    public OrderDTO(String userName, String side, double price, long quantity) {
        this.userName = userName;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
    }

    public String getUserName() {
        return userName;
    }

    public String getSide() {
        return side;
    }

    public double getPrice() {
        return price;
    }

    public long getQuantity() {
        return quantity;
    }
}
