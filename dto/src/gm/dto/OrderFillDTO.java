package gm.dto;

/**
 * Immutable description of one fill produced while processing an order - either
 * a regular trade (buyer and seller set) or one leg of a mint (seller is
 * {@code null} because the shares were freshly created).
 */
public class OrderFillDTO {

    private final boolean mint;
    private final String optionName;
    private final long quantity;
    private final double price;
    private final String buyerName;
    private final String sellerName;

    public OrderFillDTO(boolean mint, String optionName, long quantity, double price,
                        String buyerName, String sellerName) {
        this.mint = mint;
        this.optionName = optionName;
        this.quantity = quantity;
        this.price = price;
        this.buyerName = buyerName;
        this.sellerName = sellerName;
    }

    public boolean isMint() {
        return mint;
    }

    public String getOptionName() {
        return optionName;
    }

    public long getQuantity() {
        return quantity;
    }

    public double getPrice() {
        return price;
    }

    public String getBuyerName() {
        return buyerName;
    }

    public String getSellerName() {
        return sellerName;
    }
}
