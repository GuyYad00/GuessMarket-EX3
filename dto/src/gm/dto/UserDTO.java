package gm.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable summary of a user, used by the "other users" table on the account
 * screen: name, balance, whether the user is a market maker (and of which
 * events), whether the user is currently connected, and how many events the
 * user takes part in.
 */
public class UserDTO {

    private final String name;
    private final double balance;
    private final boolean blocked;
    private final boolean connected;
    private final List<Integer> marketMakerEventIds;
    private final int participatingEventCount;

    public UserDTO(String name, double balance, boolean blocked, boolean connected,
                   List<Integer> marketMakerEventIds, int participatingEventCount) {
        this.name = name;
        this.balance = balance;
        this.blocked = blocked;
        this.connected = connected;
        this.marketMakerEventIds = new ArrayList<>(marketMakerEventIds);
        this.participatingEventCount = participatingEventCount;
    }

    public String getName() {
        return name;
    }

    public double getBalance() {
        return balance;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public boolean isConnected() {
        return connected;
    }

    /** @return {@code true} when the user is the market maker of at least one event. */
    public boolean isMarketMaker() {
        return !marketMakerEventIds.isEmpty();
    }

    public List<Integer> getMarketMakerEventIds() {
        return marketMakerEventIds;
    }

    public int getParticipatingEventCount() {
        return participatingEventCount;
    }
}
