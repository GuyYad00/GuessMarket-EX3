package gm.dto;

/** Immutable LMSR state of a single option: its price (probability) and share count. */
public class OptionStateDTO {

    private final String optionName;
    private final double price;
    private final long totalShares;

    public OptionStateDTO(String optionName, double price, long totalShares) {
        this.optionName = optionName;
        this.price = price;
        this.totalShares = totalShares;
    }

    public String getOptionName() {
        return optionName;
    }

    public double getPrice() {
        return price;
    }

    public long getTotalShares() {
        return totalShares;
    }
}
