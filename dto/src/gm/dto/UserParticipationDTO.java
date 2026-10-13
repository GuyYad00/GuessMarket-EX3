package gm.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * One user's involvement in one event, as shown on the user screen. LMSR events
 * expose the purchase history; order-book events expose per-option holdings and
 * their cost, plus the realised profit/loss once the event is closed.
 */
public class UserParticipationDTO {

    private final int eventId;
    private final String eventName;
    private final String method;
    private final String status;
    private final boolean marketMaker;
    private final List<String> optionNames;
    private final List<LmsrTradeDTO> lmsrTrades;
    private final List<Long> holdings;
    private final List<Double> paidPerOption;
    private final Double profitLoss;
    private final String winnerName;

    public UserParticipationDTO(int eventId, String eventName, String method, String status,
                                boolean marketMaker, List<String> optionNames,
                                List<LmsrTradeDTO> lmsrTrades, List<Long> holdings,
                                List<Double> paidPerOption, Double profitLoss, String winnerName) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.method = method;
        this.status = status;
        this.marketMaker = marketMaker;
        this.optionNames = copyOrEmpty(optionNames);
        this.lmsrTrades = copyOrEmpty(lmsrTrades);
        this.holdings = copyOrEmpty(holdings);
        this.paidPerOption = copyOrEmpty(paidPerOption);
        this.profitLoss = profitLoss;
        this.winnerName = winnerName;
    }

    private static <T> List<T> copyOrEmpty(List<T> source) {
        return source == null ? new ArrayList<>() : new ArrayList<>(source);
    }

    public int getEventId() {
        return eventId;
    }

    public String getEventName() {
        return eventName;
    }

    public String getMethod() {
        return method;
    }

    public String getStatus() {
        return status;
    }

    public boolean isMarketMaker() {
        return marketMaker;
    }

    public List<String> getOptionNames() {
        return optionNames;
    }

    public List<LmsrTradeDTO> getLmsrTrades() {
        return lmsrTrades;
    }

    public List<Long> getHoldings() {
        return holdings;
    }

    public List<Double> getPaidPerOption() {
        return paidPerOption;
    }

    public Double getProfitLoss() {
        return profitLoss;
    }

    public String getWinnerName() {
        return winnerName;
    }
}
