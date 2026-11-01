package com.society.sunlitmarket.market;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** KubeJS-driven price multipliers, applied on top of the offer's base prices. */
public final class PriceModifiers {
    private static final Map<String, Double> MULTIPLIERS = new ConcurrentHashMap<>();

    private PriceModifiers() {
    }

    private static String key(String merchantId, String item) {
        return merchantId + '\u0000' + item;
    }

    public static void set(String merchantId, String item, double multiplier) {
        if (merchantId == null || item == null) return;
        MULTIPLIERS.put(key(merchantId, item), Math.max(0.1, Math.min(10.0, multiplier)));
    }

    public static double get(String merchantId, String item) {
        return MULTIPLIERS.getOrDefault(key(merchantId, item), 1.0);
    }

    public static void clear() {
        MULTIPLIERS.clear();
    }

    public static int apply(int base, double multiplier) {
        if (base <= 0) return 0;
        return Math.max(1, (int) Math.round(base * multiplier));
    }
}
