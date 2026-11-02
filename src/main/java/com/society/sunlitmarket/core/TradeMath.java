package com.society.sunlitmarket.core;

/** Pure, bounded calculations shared by quotes, persistence and tests. */
public final class TradeMath {
    private TradeMath() {}
    public static int amount(long value) {
        if (value < 0 || value > Integer.MAX_VALUE) throw new IllegalArgumentException("Amount out of range");
        return (int) value;
    }
    public static int price(int base, double multiplier) {
        if (base <= 0) return 0;
        if (!Double.isFinite(multiplier) || multiplier <= 0) throw new IllegalArgumentException("Invalid multiplier");
        return amount(Math.max(1L, Math.round(base * multiplier)));
    }
    public static int clamp(long value, int min, int max) {
        return (int) Math.max(min, Math.min((long) max, value));
    }
    public static long days(long previous, long current) {
        if (previous == Long.MIN_VALUE || current <= previous) return 0;
        return Math.min(100000000L, current - previous);
    }
    public static int decay(int value, long days, int daily) {
        long step = Math.min(Integer.MAX_VALUE, Math.max(0L, days) * Math.max(0, daily));
        return value < 0 ? (int) Math.min(0L, value + step) : (int) Math.max(0L, value - step);
    }
}
