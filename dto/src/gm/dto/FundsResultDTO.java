package gm.dto;

/** Returned after the user loads money into the account. */
public class FundsResultDTO {

    private final double amountLoaded;
    private final double newBalance;

    public FundsResultDTO(double amountLoaded, double newBalance) {
        this.amountLoaded = amountLoaded;
        this.newBalance = newBalance;
    }

    public double getAmountLoaded() {
        return amountLoaded;
    }

    public double getNewBalance() {
        return newBalance;
    }
}
