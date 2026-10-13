package gm.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable snapshot of one option's order book: the resting buy/sell orders
 * and the derived statistics (last, bid, ask, mid, spread). A {@code null}
 * statistic means it is currently undefined (no trade / no orders on that side).
 */
public class OrderBookOptionDTO {

    private final String optionName;
    private final List<OrderDTO> buyOrders;
    private final List<OrderDTO> sellOrders;
    private final Double last;
    private final Double bid;
    private final Double ask;
    private final Double mid;
    private final Double spread;

    public OrderBookOptionDTO(String optionName, List<OrderDTO> buyOrders, List<OrderDTO> sellOrders,
                              Double last, Double bid, Double ask, Double mid, Double spread) {
        this.optionName = optionName;
        this.buyOrders = new ArrayList<>(buyOrders);
        this.sellOrders = new ArrayList<>(sellOrders);
        this.last = last;
        this.bid = bid;
        this.ask = ask;
        this.mid = mid;
        this.spread = spread;
    }

    public String getOptionName() {
        return optionName;
    }

    public List<OrderDTO> getBuyOrders() {
        return buyOrders;
    }

    public List<OrderDTO> getSellOrders() {
        return sellOrders;
    }

    public Double getLast() {
        return last;
    }

    public Double getBid() {
        return bid;
    }

    public Double getAsk() {
        return ask;
    }

    public Double getMid() {
        return mid;
    }

    public Double getSpread() {
        return spread;
    }
}
