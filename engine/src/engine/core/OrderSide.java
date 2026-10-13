package engine.core;

/** Whether an order-book order is a buy (bid) or a sell (ask). */
public enum OrderSide {
    BUY("Buy"),
    SELL("Sell");

    private final String displayName;

    OrderSide(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
