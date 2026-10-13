package engine.core;

/**
 * One line in a user's account ledger: what happened, by how much the balance
 * changed and what the balance was right after. Immutable.
 */
public class Transaction {

    private final int number;
    private final String type;
    private final String description;
    private final double amount;
    private final double balanceAfter;

    public Transaction(int number, String type, String description, double amount, double balanceAfter) {
        this.number = number;
        this.type = type;
        this.description = description;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
    }

    public int getNumber() {
        return number;
    }

    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public double getAmount() {
        return amount;
    }

    public double getBalanceAfter() {
        return balanceAfter;
    }
}
