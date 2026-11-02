package com.society.sunlitmarket.market;

/**
 * Pure index math for the market-maker price indices. Everything here is unit-testable
 * and must stay free of Minecraft classes.
 *
 * <p>An index is the geometric mean of its members' price ratios (current mid / script
 * base), scaled to start at 1000. Geometric, not arithmetic: one member doubling moves
 * the index as^(weight), and a doubling offsets a halving, which matches how a basket
 * of goods behaves. With the default equal weights the index is a true constant-basket
 * measure; volume-weighted refinements come later, when trade volume exists to weight by.
 */
public final class IndexMath {

    /** Hard clamp for a single member ratio so one broken price cannot explode the exp(). */
    private static final double MIN_RATIO = 0.05D, MAX_RATIO = 20.0D;

    private IndexMath() {
    }

    /** 1000 * exp(mean(ln(ratio))) over the member ratios. */
    public static double value(double[] ratios) {
        if (ratios == null || ratios.length == 0) {
            throw new IllegalArgumentException("Index needs at least one member");
        }
        double sum = 0.0D;
        for (double ratio : ratios) {
            if (!Double.isFinite(ratio) || ratio <= 0) {
                throw new IllegalArgumentException("Invalid member ratio");
            }
            sum += Math.log(Math.max(MIN_RATIO, Math.min(MAX_RATIO, ratio)));
        }
        return 1000.0D * Math.exp(sum / ratios.length);
    }

    /** Seeds a new candle: {open, high, low, close}. */
    public static int[] seedCandle(int price) {
        return new int[]{price, price, price, price};
    }

    /** Folds one sample into the current candle. */
    public static void updateCandle(int[] candle, int price) {
        candle[1] = Math.max(candle[1], price);
        candle[2] = Math.min(candle[2], price);
        candle[3] = price;
    }
}
