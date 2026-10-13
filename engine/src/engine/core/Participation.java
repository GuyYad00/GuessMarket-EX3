package engine.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single user's involvement in a single event: how many shares of each option
 * the user holds, how much was paid for them, the running cash flow (used to
 * present profit/loss once the event is resolved) and - for LMSR events - the
 * full purchase history.
 */
public class Participation {

    private final int eventId;
    private final long[] holdings;
    private final double[] paidPerOption;
    private double cashFlow;
    private final List<LmsrTradeRecord> lmsrTrades;

    public Participation(int eventId, int optionCount) {
        this.eventId = eventId;
        this.holdings = new long[optionCount];
        this.paidPerOption = new double[optionCount];
        this.cashFlow = 0;
        this.lmsrTrades = new ArrayList<>();
    }

    public int getEventId() {
        return eventId;
    }

    public long getHoldings(int optionIndex) {
        return holdings[optionIndex];
    }

    public void addShares(int optionIndex, long amount) {
        holdings[optionIndex] += amount;
    }

    public void removeShares(int optionIndex, long amount) {
        holdings[optionIndex] -= amount;
    }

    public double getPaid(int optionIndex) {
        return paidPerOption[optionIndex];
    }

    public void addPaid(int optionIndex, double amount) {
        paidPerOption[optionIndex] += amount;
    }

    public double getCashFlow() {
        return cashFlow;
    }

    public void addCashFlow(double delta) {
        cashFlow += delta;
    }

    public void addLmsrTrade(LmsrTradeRecord record) {
        lmsrTrades.add(record);
    }

    /** LMSR purchase history, ordered newest-first for display. */
    public List<LmsrTradeRecord> getTradesNewestFirst() {
        List<LmsrTradeRecord> copy = new ArrayList<>(lmsrTrades);
        Collections.reverse(copy);
        return copy;
    }

    public boolean holdsAnything() {
        for (long h : holdings) {
            if (h != 0) {
                return true;
            }
        }
        return false;
    }
}
