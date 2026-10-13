package engine.core;

/**
 * A single resting (or in-flight) order in an order book: who placed it, on
 * which side, at what price (in whole cents) and how many shares still remain
 * to be filled.
 */
public class Order {

    private final long sequence;
    private final String userName;
    private final OrderSide side;
    private final long priceCents;
    private long remaining;

    public Order(long sequence, String userName, OrderSide side, long priceCents, long quantity) {
        this.sequence = sequence;
        this.userName = userName;
        this.side = side;
        this.priceCents = priceCents;
        this.remaining = quantity;
    }

    public long getSequence() {
        return sequence;
    }

    public String getUserName() {
        return userName;
    }

    public OrderSide getSide() {
        return side;
    }

    public long getPriceCents() {
        return priceCents;
    }

    public long getRemaining() {
        return remaining;
    }

    public void reduce(long amount) {
        remaining -= amount;
    }

    public boolean isFilled() {
        return remaining <= 0;
    }
}
