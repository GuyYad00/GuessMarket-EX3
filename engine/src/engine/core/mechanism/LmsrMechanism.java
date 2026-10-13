package engine.core.mechanism;

/**
 * The Logarithmic Market Scoring Rule pricing engine for a binary event.
 * <p>
 * It holds the total number of shares bought of each option (across all users),
 * which is all the LMSR price/cost formulas depend on. The formulas are written
 * in a numerically stable "log-sum-exp" form so that a very large purchase does
 * not overflow {@code Math.exp} to infinity.
 */
public class LmsrMechanism {

    /** Every winning LMSR share pays out exactly one unit of currency at close. */
    public static final double PAYOUT_PER_SHARE = 1.0;

    private final int b;
    private final long[] totalShares;

    public LmsrMechanism(int b) {
        this.b = b;
        this.totalShares = new long[2];
    }

    public int getLiquidity() {
        return b;
    }

    public long getTotalShares(int optionIndex) {
        return totalShares[optionIndex];
    }

    /** C(q0, q1) = b * ln(e^(q0/b) + e^(q1/b)), computed without overflow. */
    private double cost(double q0, double q1) {
        double larger = Math.max(q0, q1);
        double gap = Math.abs(q0 - q1);
        return larger + b * Math.log(1 + Math.exp(-gap / b));
    }

    /** Current price (probability in 0..1) of the option at the given index. */
    public double price(int optionIndex) {
        double q0 = totalShares[0];
        double q1 = totalShares[1];
        double mine = totalShares[optionIndex];
        double other = (optionIndex == 0) ? q1 : q0;
        return 1.0 / (1.0 + Math.exp((other - mine) / b));
    }

    /** The subsidy the market maker invests when opening the event: C(0,0) = b*ln(2). */
    public double subsidy() {
        return b * Math.log(2);
    }

    /**
     * Records a purchase of {@code amount} shares of {@code optionIndex} and
     * returns its LMSR cost (the increase in the cost function).
     */
    public double applyBuy(int optionIndex, long amount) {
        double q0 = totalShares[0];
        double q1 = totalShares[1];
        double before = cost(q0, q1);
        double after = (optionIndex == 0) ? cost(q0 + amount, q1) : cost(q0, q1 + amount);
        totalShares[optionIndex] += amount;
        return after - before;
    }

    /** @return true if adding {@code amount} shares would overflow the counter. */
    public boolean wouldOverflow(int optionIndex, long amount) {
        return totalShares[optionIndex] > Long.MAX_VALUE - amount;
    }
}
