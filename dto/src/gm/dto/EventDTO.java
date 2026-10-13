package gm.dto;

import java.util.ArrayList;
import java.util.List;

/** Immutable summary of an event, used by the events list and detail header. */
public class EventDTO {

    private final int id;
    private final String name;
    private final String description;
    private final int commissionPercentage;
    private final String commissionType;
    private final String method;
    private final String status;
    private final List<String> optionNames;
    private final double accountBalance;
    private final String marketMakerName;

    public EventDTO(int id, String name, String description, int commissionPercentage,
                    String commissionType, String method, String status,
                    List<String> optionNames, double accountBalance, String marketMakerName) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.commissionPercentage = commissionPercentage;
        this.commissionType = commissionType;
        this.method = method;
        this.status = status;
        this.optionNames = new ArrayList<>(optionNames);
        this.accountBalance = accountBalance;
        this.marketMakerName = marketMakerName;
    }

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

    public String getCommissionType() {
        return commissionType;
    }

    public String getMethod() {
        return method;
    }

    public String getStatus() {
        return status;
    }

    public List<String> getOptionNames() {
        return optionNames;
    }

    public double getAccountBalance() {
        return accountBalance;
    }

    public String getMarketMakerName() {
        return marketMakerName;
    }
}
