package engine.core.mechanism;

import engine.core.OptionBook;
import engine.core.Order;
import engine.core.OrderSide;

/**
 * The order-book matching engine for a binary event. It owns the two option
 * books and turns an incoming order into trades / mints, resting whatever is
 * left. It reports every fill through a {@link TradeLedger}; it never moves
 * money or shares itself.
 * <p>
 * Prices are handled as whole cents (long) so the matching is exact and
 * deterministic - matching the deterministic simulator supplied by the course.
 */
public class OrderBookMechanism {

    private static final int OPTION_COUNT = 2;

    private final int d;
    private final int initial;
    private final boolean allowMint;
    private final OptionBook[] books;
    private long sequenceCounter;

    public OrderBookMechanism(int d, int initial, boolean allowMint) {
        this.d = d;
        this.initial = initial;
        this.allowMint = allowMint;
        this.books = new OptionBook[]{new OptionBook(), new OptionBook()};
    }

    public int getD() {
        return d;
    }

    public int getInitial() {
        return initial;
    }

    public boolean isAllowMint() {
        return allowMint;
    }

    /** The base value expressed in cents (the effective per-share payout ceiling). */
    public long baseValueCents() {
        return d * 100L;
    }

    public OptionBook getBook(int optionIndex) {
        return books[optionIndex];
    }

    public long reservedSellShares(String userName, int optionIndex) {
        return books[optionIndex].reservedSellShares(userName);
    }

    /**
     * Processes an order and returns the quantity that ended up resting in the
     * book (0 if it was fully matched).
     */
    public long process(int optionIndex, OrderSide side, long priceCents, long quantity,
                        String userName, TradeLedger ledger) {
        long remaining = (side == OrderSide.BUY)
                ? processBuy(optionIndex, priceCents, quantity, userName, ledger)
                : processSell(optionIndex, priceCents, quantity, userName, ledger);

        if (remaining > 0) {
            books[optionIndex].addOrder(new Order(nextSequence(), userName, side, priceCents, remaining));
        }
        return remaining;
    }

    private long processBuy(int optionIndex, long priceCents, long quantity,
                            String userName, TradeLedger ledger) {
        long remaining = quantity;
        OptionBook book = books[optionIndex];

        // 1. Direct match: buy against the cheapest sell orders we can afford.
        while (remaining > 0) {
            Order ask = book.bestAsk();
            if (ask == null || ask.getPriceCents() > priceCents) {
                break;
            }
            long fill = Math.min(remaining, ask.getRemaining());
            long tradePrice = ask.getPriceCents();
            ledger.executeTrade(optionIndex, fill, tradePrice, userName, ask.getUserName());
            ask.reduce(fill);
            remaining -= fill;
            book.setLastTradeCents(tradePrice);
            book.removeFilledOrders();
        }

        // 2. Mint: pair with buyers of the opposite option when prices reach the base value.
        if (allowMint && remaining > 0) {
            int other = 1 - optionIndex;
            OptionBook otherBook = books[other];
            while (remaining > 0) {
                Order oppositeBid = otherBook.bestBid();
                if (oppositeBid == null || priceCents + oppositeBid.getPriceCents() < baseValueCents()) {
                    break;
                }
                long fill = Math.min(remaining, oppositeBid.getRemaining());
                long restingPrice = oppositeBid.getPriceCents();
                long incomingPrice = baseValueCents() - restingPrice; // complementary to the base value
                ledger.executeMint(fill, optionIndex, userName, incomingPrice,
                        other, oppositeBid.getUserName(), restingPrice);
                oppositeBid.reduce(fill);
                remaining -= fill;
                book.setLastTradeCents(incomingPrice);
                otherBook.setLastTradeCents(restingPrice);
                otherBook.removeFilledOrders();
            }
        }
        return remaining;
    }

    private long processSell(int optionIndex, long priceCents, long quantity,
                             String userName, TradeLedger ledger) {
        long remaining = quantity;
        OptionBook book = books[optionIndex];

        // Sell against the highest bids that meet our asking price.
        while (remaining > 0) {
            Order bid = book.bestBid();
            if (bid == null || bid.getPriceCents() < priceCents) {
                break;
            }
            long fill = Math.min(remaining, bid.getRemaining());
            long tradePrice = bid.getPriceCents();
            ledger.executeTrade(optionIndex, fill, tradePrice, bid.getUserName(), userName);
            bid.reduce(fill);
            remaining -= fill;
            book.setLastTradeCents(tradePrice);
            book.removeFilledOrders();
        }
        return remaining;
    }

    private long nextSequence() {
        return sequenceCounter++;
    }

    public int getOptionCount() {
        return OPTION_COUNT;
    }
}
