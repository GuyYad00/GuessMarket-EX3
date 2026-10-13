package engine.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The order book of a single option: the resting buy orders (bids) and sell
 * orders (asks), plus the last traded price. Ordering priority is price first
 * and then time (lower sequence = earlier).
 */
public class OptionBook {

    private final List<Order> bids = new ArrayList<>();
    private final List<Order> asks = new ArrayList<>();
    private Long lastTradeCents;

    public void addOrder(Order order) {
        if (order.getSide() == OrderSide.BUY) {
            bids.add(order);
        } else {
            asks.add(order);
        }
    }

    /** Highest-priced bid, earliest first on ties, or {@code null} if there are none. */
    public Order bestBid() {
        return bids.stream()
                .max(Comparator.comparingLong(Order::getPriceCents)
                        .thenComparing(Comparator.comparingLong(Order::getSequence).reversed()))
                .orElse(null);
    }

    /** Lowest-priced ask, earliest first on ties, or {@code null} if there are none. */
    public Order bestAsk() {
        return asks.stream()
                .min(Comparator.comparingLong(Order::getPriceCents)
                        .thenComparingLong(Order::getSequence))
                .orElse(null);
    }

    public void removeFilledOrders() {
        bids.removeIf(Order::isFilled);
        asks.removeIf(Order::isFilled);
    }

    public Long getLastTradeCents() {
        return lastTradeCents;
    }

    public void setLastTradeCents(long priceCents) {
        this.lastTradeCents = priceCents;
    }

    public Long getBestBidCents() {
        Order best = bestBid();
        return best == null ? null : best.getPriceCents();
    }

    public Long getBestAskCents() {
        Order best = bestAsk();
        return best == null ? null : best.getPriceCents();
    }

    /** Shares the user has parked in resting sell orders (already promised to the book). */
    public long reservedSellShares(String userName) {
        long total = 0;
        for (Order ask : asks) {
            if (ask.getUserName().equals(userName)) {
                total += ask.getRemaining();
            }
        }
        return total;
    }

    /** Bids sorted for display: best price first. */
    public List<Order> getBidsForDisplay() {
        List<Order> copy = new ArrayList<>(bids);
        copy.sort(Comparator.comparingLong(Order::getPriceCents).reversed()
                .thenComparingLong(Order::getSequence));
        return copy;
    }

    /** Asks sorted for display: best (lowest) price first. */
    public List<Order> getAsksForDisplay() {
        List<Order> copy = new ArrayList<>(asks);
        copy.sort(Comparator.comparingLong(Order::getPriceCents)
                .thenComparingLong(Order::getSequence));
        return copy;
    }
}
