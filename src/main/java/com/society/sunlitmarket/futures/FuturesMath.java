package com.society.sunlitmarket.futures;

/**
 * Bounded, fully collateralised futures arithmetic. Pure and unit-tested.
 *
 * <p>A contract struck at price P (index points) lives in the band [L, U] around P. The long side
 * escrows (P - L) x multiplier per lot, the short side (U - P) x multiplier. At expiry the
 * settlement price S is clamped into [L, U]; the long receives (S - L) x mult, the short (U - S) x
 * mult. The two payouts always add up to exactly what both sides escrowed, so nobody can owe more
 * than they deposited and the house never takes a position.
 */
public final class FuturesMath {
    public static final long DAY = 24000L;

    private FuturesMath() {
    }

    /** {L, U} for a strike; L may be 0 but never negative. */
    public static long[] bounds(int price, double band) {
        long low = Math.max(0L, (long) Math.floor(price * (1.0D - band)));
        long high = (long) Math.ceil(price * (1.0D + band));
        return new long[]{low, high};
    }

    public static long collateralPerLot(boolean isLong, int price, long low, long high, int multiplier) {
        return (isLong ? price - low : high - price) * multiplier;
    }

    public static long payout(boolean isLong, int settle, long low, long high, int multiplier, int lots) {
        long s = Math.max(low, Math.min(high, settle));
        return (isLong ? s - low : high - s) * multiplier * lots;
    }

    /** Per-side fee per lot, in basis points of notional (P x multiplier). */
    public static long feePerLot(int price, int multiplier, int feeBps) {
        return (long) price * multiplier * feeBps / 10_000L;
    }

    /**
     * The next fixed expiry day: a multiple of the tenor, far enough out that trading has not
     * already closed for the settlement window.
     */
    public static long expiryDay(long nowTick, int tenorDays, long windowTicks) {
        long day = (nowTick / DAY / tenorDays + 1) * tenorDays;
        while (day * DAY - windowTicks <= nowTick) day += tenorDays;
        return day;
    }

    public static String series(String index, long expiryDay) {
        return index + "@" + expiryDay;
    }
}
