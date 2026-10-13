package engine.core;

/** The trading mechanism used by an event. */
public enum MarketMethod {
    LMSR("LMSR"),
    ORDER_BOOK("Order Book");

    private final String displayName;

    MarketMethod(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
