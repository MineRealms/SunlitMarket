package com.society.sunlitmarket.market;

/**
 * Inventory-skewed pricing for stocked shelf offers. Pure functions, fully unit-testable.
 *
 * <p>A merchant shelf is a real inventory: buying drains it, selling refills it. One
 * scarcity factor drives both directions of the same offer - a depleted shelf makes the
 * merchant's remaining goods dearer AND the goods he wants to restock worth more, while
 * an overstocked shelf makes him discount everything. Selling into a full merchant is
 * therefore the dumping path the fixed-price shelf never had, and it is self-limiting
 * because the discount applies to the dumper first.
 */
public final class PriceEngine {

    private PriceEngine() {
    }

    /**
     * Scarcity multiplier for one offer. {@code stock} is the current shelf level,
     * {@code initial} the restock level. Unlimited shelves ({@code initial <= 0} or
     * {@code stock < 0}) carry no scarcity signal and return 1.0.
     */
    public static double scarcity(int stock, int initial, double elasticity, double minMult, double maxMult) {
        if (initial <= 0 || stock < 0) {
            return 1.0D;
        }
        double ratio = initial / (double) Math.max(stock, 1);
        double multiplier = Math.pow(ratio, elasticity);
        if (!Double.isFinite(multiplier) || multiplier <= 0) {
            return 1.0D;
        }
        return Math.max(minMult, Math.min(maxMult, multiplier));
    }

    /**
     * Shelf level after the merchant buys {@code amount} units from a player, capped at
     * {@code capacity} times the restock level so dumping beyond that stops moving price.
     */
    public static int afterSale(int stock, int initial, int amount, double capacity) {
        if (initial <= 0 || stock < 0 || amount <= 0) {
            return stock;
        }
        long cap = Math.min((long) Integer.MAX_VALUE, (long) Math.ceil(initial * capacity));
        long base = stock < 0 ? initial : stock;
        return (int) Math.min(cap, base + amount);
    }
}
