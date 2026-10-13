package engine.core;

import engine.core.mechanism.LmsrMechanism;
import engine.core.mechanism.OrderBookMechanism;
import engine.exception.EngineOperationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single binary event. It carries the descriptive data, the life-cycle status
 * and exactly one trading mechanism (LMSR or order book). It also owns the
 * event "contract" account and the per-option price history used by the graph
 * bonus. Money movements between users and this account are orchestrated by the
 * engine facade; this class only guards its own state transitions.
 */
public class Event {

    private final int id;
    private final String name;
    private final String description;
    private final int commissionPercentage;
    private final CommissionType commissionType;
    private final List<String> optionNames;
    private final MarketMethod method;
    private final LmsrMechanism lmsr;
    private final OrderBookMechanism orderBook;
    private final List<List<Double>> priceHistory;
    private final List<LmsrTradeRecord> lmsrTradeLog;

    private String marketMakerName;
    private EventStatus status;
    private double accountBalance;
    private int winnerIndex;

    private Event(int id, String name, String description, int commissionPercentage,
                  CommissionType commissionType, List<String> optionNames,
                  MarketMethod method, LmsrMechanism lmsr, OrderBookMechanism orderBook) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.commissionPercentage = commissionPercentage;
        this.commissionType = commissionType;
        this.optionNames = new ArrayList<>(optionNames);
        this.method = method;
        this.lmsr = lmsr;
        this.orderBook = orderBook;
        this.status = EventStatus.NOT_STARTED;
        this.accountBalance = 0;
        this.winnerIndex = -1;
        this.priceHistory = new ArrayList<>();
        for (int i = 0; i < optionNames.size(); i++) {
            this.priceHistory.add(new ArrayList<>());
        }
        this.lmsrTradeLog = new ArrayList<>();
    }

    public static Event lmsrEvent(int id, String name, String description, int commissionPercentage,
                                  CommissionType commissionType, List<String> optionNames, LmsrMechanism lmsr) {
        return new Event(id, name, description, commissionPercentage, commissionType, optionNames,
                MarketMethod.LMSR, lmsr, null);
    }

    public static Event orderBookEvent(int id, String name, String description, int commissionPercentage,
                                       CommissionType commissionType, List<String> optionNames,
                                       OrderBookMechanism orderBook) {
        return new Event(id, name, description, commissionPercentage, commissionType, optionNames,
                MarketMethod.ORDER_BOOK, null, orderBook);
    }

    // --- Life cycle ---

    public void open() {
        if (status != EventStatus.NOT_STARTED) {
            throw new EngineOperationException("Event #" + id + " ('" + name
                    + "') cannot be opened because it is already " + status.getDisplayName().toLowerCase() + ".");
        }
        status = EventStatus.ACTIVE;
    }

    public void close(int winnerOptionIndex) {
        if (status != EventStatus.ACTIVE) {
            throw new EngineOperationException("Event #" + id + " ('" + name
                    + "') cannot be closed because it is " + status.getDisplayName().toLowerCase() + ".");
        }
        this.winnerIndex = winnerOptionIndex;
        this.status = EventStatus.CLOSED;
    }

    public void requireActive(String action) {
        if (status != EventStatus.ACTIVE) {
            throw new EngineOperationException("Cannot " + action + " in event #" + id + " ('" + name
                    + "') because it is " + status.getDisplayName().toLowerCase() + ".");
        }
    }

    // --- Account ---

    public double getAccountBalance() {
        return accountBalance;
    }

    public void addToAccount(double delta) {
        accountBalance += delta;
    }

    // --- Price history (graph bonus) ---

    public void recordPrice(int optionIndex, double price) {
        priceHistory.get(optionIndex).add(price);
    }

    public List<Double> getPriceHistory(int optionIndex) {
        return new ArrayList<>(priceHistory.get(optionIndex));
    }

    // --- LMSR event-level trade log ---

    public void addLmsrTrade(LmsrTradeRecord record) {
        lmsrTradeLog.add(record);
    }

    /** All LMSR trades of this event, ordered newest-first for display. */
    public List<LmsrTradeRecord> getLmsrTradesNewestFirst() {
        List<LmsrTradeRecord> copy = new ArrayList<>(lmsrTradeLog);
        Collections.reverse(copy);
        return copy;
    }

    // --- Getters ---

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getCommissionPercentage() {
        return commissionPercentage;
    }

    public CommissionType getCommissionType() {
        return commissionType;
    }

    public List<String> getOptionNames() {
        return Collections.unmodifiableList(optionNames);
    }

    public int getOptionCount() {
        return optionNames.size();
    }

    public MarketMethod getMethod() {
        return method;
    }

    public LmsrMechanism getLmsr() {
        return lmsr;
    }

    public OrderBookMechanism getOrderBook() {
        return orderBook;
    }

    public EventStatus getStatus() {
        return status;
    }

    public String getMarketMakerName() {
        return marketMakerName;
    }

    public void setMarketMakerName(String marketMakerName) {
        this.marketMakerName = marketMakerName;
    }

    public int getWinnerIndex() {
        return winnerIndex;
    }

    public boolean isClosed() {
        return status == EventStatus.CLOSED;
    }
}
