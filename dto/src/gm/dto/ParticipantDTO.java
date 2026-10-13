package gm.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable view of one participant inside an order-book event: how many shares
 * of each option they hold and the current value of those holdings.
 */
public class ParticipantDTO {

    private final String userName;
    private final List<Long> shares;
    private final List<Double> values;

    public ParticipantDTO(String userName, List<Long> shares, List<Double> values) {
        this.userName = userName;
        this.shares = new ArrayList<>(shares);
        this.values = new ArrayList<>(values);
    }

    public String getUserName() {
        return userName;
    }

    public List<Long> getShares() {
        return shares;
    }

    public List<Double> getValues() {
        return values;
    }
}
