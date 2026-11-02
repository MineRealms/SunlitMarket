package com.society.sunlitmarket.market;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Settlement price from fill-built candles: a weighted time average over a window, with outlier
 * bars dropped and a credibility floor. Pure, unit-tested, no Minecraft lookups.
 */
public final class FillMath {
    public record Settlement(boolean credible, int price, String reason, int buckets, int traders) {
    }

    private FillMath() {
    }

    /**
     * @param window     traded bars inside the settlement window (empty bars simply do not exist)
     * @param minTraders distinct owners required across the surviving bars
     * @param minBuckets traded bars required after outlier removal
     * @param band       bars whose close is more than this fraction away from the window median are dropped
     */
    public static Settlement settle(List<Candle> window, int minTraders, int minBuckets, double band) {
        if (window == null || window.isEmpty()) return new Settlement(false, -1, "NO_TRADES", 0, 0);
        double median = median(window);
        List<Candle> kept = new ArrayList<>();
        for (Candle c : window) {
            if (median > 0 && Math.abs(c.close / median - 1.0D) <= band) kept.add(c);
        }
        Set<String> owners = new HashSet<>();
        long weightSum = 0;
        double priceSum = 0;
        for (Candle c : kept) {
            owners.addAll(c.owners.keySet());
            weightSum += c.weight;
            priceSum += (double) c.weight * c.close;
        }
        if (kept.size() < Math.max(1, minBuckets)) return new Settlement(false, -1, "TOO_FEW_BUCKETS", kept.size(), owners.size());
        if (owners.size() < Math.max(1, minTraders)) return new Settlement(false, -1, "TOO_FEW_TRADERS", kept.size(), owners.size());
        if (weightSum <= 0) return new Settlement(false, -1, "NO_WEIGHT", kept.size(), owners.size());
        return new Settlement(true, (int) Math.round(priceSum / weightSum), "OK", kept.size(), owners.size());
    }

    static double median(List<Candle> window) {
        double[] closes = window.stream().mapToDouble(c -> c.close).sorted().toArray();
        int n = closes.length;
        return n % 2 == 1 ? closes[n / 2] : (closes[n / 2 - 1] + closes[n / 2]) / 2.0D;
    }
}
