package gm.dto;

/** Immutable view of a single LMSR purchase, for trade-history tables. */
public class LmsrTradeDTO {

    private final String userName;
    private final String optionName;
    private final long shares;
    private final double cost;
    private final double commissionPaid;

    public LmsrTradeDTO(String userName, String optionName, long shares, double cost, double commissionPaid) {
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
