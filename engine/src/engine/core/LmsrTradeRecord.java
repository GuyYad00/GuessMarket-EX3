package engine.core;

/**
 * One LMSR purchase performed by a user inside an event, kept for the
 * "trade history" view (newest first) required by the assignment.
 */
public class LmsrTradeRecord {

    private final String userName;
    private final String optionName;
    private final long shares;
    private final double cost;
    private final double commissionPaid;

    public LmsrTradeRecord(String userName, String optionName, long shares, double cost, double commissionPaid) {
        this.userName = userName;
        this.optionName = optionName;
        this.shares = shares;
        this.cost = cost;
        this.commissionPaid = commissionPaid;
    }

    public String getUserName() {
        return userName;
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
}
