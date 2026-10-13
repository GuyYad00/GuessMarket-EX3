package gm.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * The full drill-down of a single event. Which sections are populated depends
 * on the event's method: LMSR events fill the option-states and trade-history
 * lists; order-book events fill the books and participants lists. The
 * per-option price history is always provided for the graph bonus.
 */
public class EventDetailsDTO {

    private final long version;
    private final EventDTO meta;
    private final List<OptionStateDTO> lmsrOptions;
    private final List<LmsrTradeDTO> lmsrTradeHistory;
    private final List<OrderBookOptionDTO> books;
    private final List<ParticipantDTO> participants;
    private final List<List<Double>> priceHistory;
    private final String winnerName;

    public EventDetailsDTO(long version, EventDTO meta, List<OptionStateDTO> lmsrOptions,
                           List<LmsrTradeDTO> lmsrTradeHistory, List<OrderBookOptionDTO> books,
                           List<ParticipantDTO> participants, List<List<Double>> priceHistory,
                           String winnerName) {
        this.version = version;
        this.meta = meta;
        this.lmsrOptions = copyOrEmpty(lmsrOptions);
        this.lmsrTradeHistory = copyOrEmpty(lmsrTradeHistory);
        this.books = copyOrEmpty(books);
        this.participants = copyOrEmpty(participants);
        this.priceHistory = new ArrayList<>();
        if (priceHistory != null) {
            for (List<Double> series : priceHistory) {
                this.priceHistory.add(new ArrayList<>(series));
            }
        }
        this.winnerName = winnerName;
    }

    private static <T> List<T> copyOrEmpty(List<T> source) {
        return source == null ? new ArrayList<>() : new ArrayList<>(source);
    }

    /** The server state version this snapshot was built from (for change detection while polling). */
    public long getVersion() {
        return version;
    }

    public EventDTO getMeta() {
        return meta;
    }

    public List<OptionStateDTO> getLmsrOptions() {
        return lmsrOptions;
    }

    public List<LmsrTradeDTO> getLmsrTradeHistory() {
        return lmsrTradeHistory;
    }

    public List<OrderBookOptionDTO> getBooks() {
        return books;
    }

    public List<ParticipantDTO> getParticipants() {
        return participants;
    }

    public List<List<Double>> getPriceHistory() {
        return priceHistory;
    }

    public String getWinnerName() {
        return winnerName;
    }
}
