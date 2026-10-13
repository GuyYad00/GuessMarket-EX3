package engine.core.mechanism;

/**
 * Callback used by {@link OrderBookMechanism} to report the trades it produces
 * while matching. The order book stays a pure matching engine: it never touches
 * balances or holdings. The implementer (the engine facade) is the one that
 * moves money, transfers shares, charges commission and applies the
 * negative-balance blocking rule.
 */
public interface TradeLedger {

    /**
     * A regular trade: existing shares of {@code optionIndex} move from the
     * seller to the buyer at {@code priceCents} per share.
     */
    void executeTrade(int optionIndex, long quantity, long priceCents,
                      String buyerName, String sellerName);

    /**
     * A mint: two opposing buy orders whose prices together reach the base value
     * cause new share pairs to be created. Each buyer receives {@code quantity}
     * shares of their own option and pays their respective price into the event
     * account.
     */
    void executeMint(long quantity,
                     int incomingOption, String incomingBuyer, long incomingPriceCents,
                     int restingOption, String restingBuyer, long restingPriceCents);
}
