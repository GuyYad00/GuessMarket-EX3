package client.common;

import java.util.Locale;

/** Small formatting helpers shared across the UI, kept consistent everywhere. */
public final class Format {

    private Format() {
    }

    /** Money with a thousands separator and two decimals, e.g. {@code $1,234.50}. */
    public static String money(double amount) {
        return String.format(Locale.US, "$%,.2f", amount);
    }

    /** A per-share price with two decimals, e.g. {@code $0.63}. */
    public static String price(double price) {
        return String.format(Locale.US, "$%.2f", price);
    }

    /** A nullable price/statistic, showing {@code -} when it is undefined. */
    public static String optionalPrice(Double value) {
        return value == null ? "-" : price(value);
    }

    /** An LMSR probability (0..1) as a percentage, e.g. {@code 63.2%}. */
    public static String probability(double probability) {
        return String.format(Locale.US, "%.1f%%", probability * 100.0);
    }

    public static String percent(int percentage) {
        return percentage + "%";
    }

    public static String shares(long shares) {
        return String.format(Locale.US, "%,d", shares);
    }
}
