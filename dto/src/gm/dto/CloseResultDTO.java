package gm.dto;

/** The outcome of a market maker closing (resolving) an event. */
public class CloseResultDTO {

    private final String winnerName;
    private final double totalPaidToWinners;
    private final double commissionCollected;
    private final double leftoverToMarketMaker;

    public CloseResultDTO(String winnerName, double totalPaidToWinners,
                          double commissionCollected, double leftoverToMarketMaker) {
        this.winnerName = winnerName;
        this.totalPaidToWinners = totalPaidToWinners;
        this.commissionCollected = commissionCollected;
        this.leftoverToMarketMaker = leftoverToMarketMaker;
    }

    public String getWinnerName() {
        return winnerName;
    }

    public double getTotalPaidToWinners() {
        return totalPaidToWinners;
    }

    public double getCommissionCollected() {
        return commissionCollected;
    }

    public double getLeftoverToMarketMaker() {
        return leftoverToMarketMaker;
    }
}
