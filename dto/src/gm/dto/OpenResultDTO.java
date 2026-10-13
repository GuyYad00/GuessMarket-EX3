package gm.dto;

/** The outcome of a market maker opening an event. */
public class OpenResultDTO {

    private final int eventId;
    private final String method;
    private final double amountPaid;
    private final double marketMakerBalance;

    public OpenResultDTO(int eventId, String method, double amountPaid, double marketMakerBalance) {
        this.eventId = eventId;
        this.method = method;
        this.amountPaid = amountPaid;
        this.marketMakerBalance = marketMakerBalance;
    }

    public int getEventId() {
        return eventId;
    }

    public String getMethod() {
        return method;
    }

    public double getAmountPaid() {
        return amountPaid;
    }

    public double getMarketMakerBalance() {
        return marketMakerBalance;
    }
}
