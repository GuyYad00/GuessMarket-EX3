package engine.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A market participant. Holds the cash balance, the full account ledger, the
 * events the user takes part in (as a trader) and the events the user is the
 * market maker of.
 * <p>
 * Exercise 3: users are created when they log in (not from the XML file), start
 * with an empty account and load money themselves. A user that is pushed into a
 * negative balance is permanently blocked from any further action, per the
 * assignment's rule. The connected flag tracks whether a client is currently
 * logged in under this name (the name stays taken while connected).
 */
public class User {

    private final String name;
    private double balance;
    private double totalLoaded;
    private boolean blocked;
    private boolean connected;

    private final Map<Integer, Participation> participations = new LinkedHashMap<>();
    private final Set<Integer> marketMakerEventIds = new TreeSet<>();
    private final List<Double> balanceHistory = new ArrayList<>();
    private final List<Transaction> transactions = new ArrayList<>();

    public User(String name) {
        this.name = name;
        this.balance = 0;
        this.totalLoaded = 0;
        this.blocked = false;
        this.connected = false;
        this.balanceHistory.add(balance);
    }

    public String getName() {
        return name;
    }

    public double getBalance() {
        return balance;
    }

    public double getTotalLoaded() {
        return totalLoaded;
    }

    /**
     * Applies a cash change, records the new balance for the balance-over-time
     * graph and appends one line to the account ledger.
     */
    public void apply(String type, String description, double delta) {
        balance += delta;
        balanceHistory.add(balance);
        transactions.add(new Transaction(transactions.size() + 1, type, description, delta, balance));
    }

    public void loadFunds(double amount) {
        totalLoaded += amount;
        apply("Load funds", "Loaded money into the account", amount);
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void block() {
        blocked = true;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public List<Double> getBalanceHistory() {
        return new ArrayList<>(balanceHistory);
    }

    public List<Transaction> getTransactions() {
        return new ArrayList<>(transactions);
    }

    public void addMarketMakerEvent(int eventId) {
        marketMakerEventIds.add(eventId);
    }

    public boolean isMarketMakerOf(int eventId) {
        return marketMakerEventIds.contains(eventId);
    }

    public boolean isMarketMaker() {
        return !marketMakerEventIds.isEmpty();
    }

    public Set<Integer> getMarketMakerEventIds() {
        return new TreeSet<>(marketMakerEventIds);
    }

    /** Returns the participation for the event, creating it on first activity. */
    public Participation participationFor(int eventId, int optionCount) {
        return participations.computeIfAbsent(eventId, id -> new Participation(id, optionCount));
    }

    /** @return the existing participation for the event, or {@code null} if the user never acted in it. */
    public Participation existingParticipation(int eventId) {
        return participations.get(eventId);
    }

    public List<Participation> getParticipations() {
        return new ArrayList<>(participations.values());
    }
}
