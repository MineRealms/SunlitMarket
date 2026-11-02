package com.society.sunlitmarket.market;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** KubeJS-driven price multipliers, applied on top of the offer's base prices. */
public final class PriceModifiers {
    private static final Map<String, Double> MULTIPLIERS = new ConcurrentHashMap<>();
    private static Map<String, Double> staging;

    private PriceModifiers() {
    }

    private static String key(String merchantId, String item) {
        return merchantId + '\0' + item;
    }

    private static Map<String, Double> target() { return staging == null ? MULTIPLIERS : staging; }
    public static synchronized void beginScripts() { staging = new ConcurrentHashMap<>(); }
    public static synchronized void endScripts(boolean valid) {
        if (staging != null && valid) { MULTIPLIERS.clear(); MULTIPLIERS.putAll(staging); }
        staging = null;
    }

    public static void set(String merchantId, String item, double multiplier) {
        if (merchantId == null || item == null) return;
        if (!Double.isFinite(multiplier) || multiplier <= 0) throw new IllegalArgumentException("Invalid multiplier");
        target().put(key(merchantId, item), Math.max(0.1, Math.min(10.0, multiplier)));
    }

    public static double get(String merchantId, String item) {
        return MULTIPLIERS.getOrDefault(key(merchantId, item), 1.0);
    }

    public static void clear() {
        target().clear();
    }

    public static int apply(int base, double multiplier) {
        return com.society.sunlitmarket.core.TradeMath.price(base, multiplier);
    }
}
