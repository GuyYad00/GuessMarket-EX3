package gm.dto;

/**
 * One line in a user's account ledger. Every action that changes the balance
 * (loading funds, opening an event, buying shares, a filled order, a commission
 * received as market maker, a payout on close, ...) produces exactly one line.
 */
public class TransactionDTO {

    private final int number;
    private final String type;
    private final String description;
    private final double amount;
    private final double balanceAfter;

    public TransactionDTO(int number, String type, String description, double amount, double balanceAfter) {
        this.number = number;
        this.type = type;
        this.description = description;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
    }

    /** 1-based running number of the line within the user's ledger. */
    public int getNumber() {
        return number;
    }

    /** Short category, e.g. "Load funds", "Buy", "Commission", "Payout". */
    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    /** Signed change to the balance (negative = money left the account). */
    public double getAmount() {
        return amount;
    }

    public double getBalanceAfter() {
        return balanceAfter;
    }
}
