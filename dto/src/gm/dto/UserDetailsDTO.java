package gm.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * The full drill-down of a single user: account, roles, participations and the
 * complete account ledger (one line per action that changed the balance).
 * <p>
 * Exercise 3: users are no longer defined in the XML file, so there is no
 * "initial cash". Instead the DTO reports how much money the user loaded in
 * total, and carries the {@code version} of the server state it was built from
 * so the client can skip redundant redraws while polling.
 */
public class UserDetailsDTO {

    private final long version;
    private final String name;
    private final boolean connected;
    private final double totalLoaded;
    private final double balance;
    private final boolean blocked;
    private final List<Integer> marketMakerEventIds;
    private final List<UserParticipationDTO> participations;
    private final List<TransactionDTO> transactions;
    private final List<Double> balanceHistory;

    public UserDetailsDTO(long version, String name, boolean connected, double totalLoaded, double balance,
                          boolean blocked, List<Integer> marketMakerEventIds,
                          List<UserParticipationDTO> participations, List<TransactionDTO> transactions,
                          List<Double> balanceHistory) {
        this.version = version;
        this.name = name;
        this.connected = connected;
        this.totalLoaded = totalLoaded;
        this.balance = balance;
        this.blocked = blocked;
        this.marketMakerEventIds = new ArrayList<>(marketMakerEventIds);
        this.participations = new ArrayList<>(participations);
        this.transactions = new ArrayList<>(transactions);
        this.balanceHistory = new ArrayList<>(balanceHistory);
    }

    public long getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public boolean isConnected() {
        return connected;
    }

    public double getTotalLoaded() {
        return totalLoaded;
    }

    public double getBalance() {
        return balance;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public List<Integer> getMarketMakerEventIds() {
        return marketMakerEventIds;
    }

    public List<UserParticipationDTO> getParticipations() {
        return participations;
    }

    public List<TransactionDTO> getTransactions() {
        return transactions;
    }

    public List<Double> getBalanceHistory() {
        return balanceHistory;
    }
}
