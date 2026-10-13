package gm.dto;

import java.util.ArrayList;
import java.util.List;

/** The outcome of an LMSR purchase: what was bought and the resulting state. */
public class BuyResultDTO {

    private final String optionName;
    private final long shares;
    private final double cost;
    private final double commissionPaid;
    private final List<OptionStateDTO> optionStates;
    private final double userBalance;
    private final boolean userBlocked;
    private final String message;

    public BuyResultDTO(String optionName, long shares, double cost, double commissionPaid,
                        List<OptionStateDTO> optionStates, double userBalance,
                        boolean userBlocked, String message) {
        this.optionName = optionName;
        this.shares = shares;
        this.cost = cost;
        this.commissionPaid = commissionPaid;
        this.optionStates = new ArrayList<>(optionStates);
        this.userBalance = userBalance;
        this.userBlocked = userBlocked;
        this.message = message;
    }

    public String getOptionName() {
        return optionName;
    }

    public long getShares() {
        return shares;
    }

    public double getCost() {
        return cost;
    }

    public double getCommissionPaid() {
        return commissionPaid;
    }

    public double getTotalPaid() {
        return cost + commissionPaid;
    }

    public List<OptionStateDTO> getOptionStates() {
        return optionStates;
    }

    public double getUserBalance() {
        return userBalance;
    }

    public boolean isUserBlocked() {
        return userBlocked;
    }

    public String getMessage() {
        return message;
    }
}
