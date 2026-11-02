package com.society.sunlitmarket.futures;

import com.google.gson.JsonObject;

/**
 * Contract terms for one index. Offers and contracts copy the terms they were opened under, so
 * changing or removing a spec never rewrites a position that already exists.
 *
 * @param multiplier  spur per index point per lot
 * @param tenorDays   expiries fall on economy days that are multiples of this
 * @param band        price band around the strike, and the circuit breaker around the reference
 * @param feeBps      per-side fee in basis points of notional; burned
 * @param maxLots     open lots (offers + contracts) per owner per series
 * @param minTraders  distinct owners needed in the settlement window, else the series voids
 * @param minBuckets  traded 4-hour bars needed in the settlement window
 * @param windowTicks settlement window before expiry; trading in the series stops when it opens
 * @param unlock      optional unlock id required to trade
 */
public record FuturesSpec(String index, int multiplier, int tenorDays, double band, int feeBps, int maxLots,
                          int minTraders, int minBuckets, long windowTicks, String unlock) {

    public static FuturesSpec parse(JsonObject o) {
        String index = text(o, "index");
        if (index.isBlank()) throw new IllegalArgumentException("Futures spec needs an index");
        FuturesSpec spec = new FuturesSpec(index, number(o, "multiplier", 10), number(o, "tenorDays", 7),
                o.has("band") ? o.get("band").getAsDouble() : 0.2D, number(o, "feeBps", 50), number(o, "maxLots", 20),
                number(o, "minTraders", 2), number(o, "minBuckets", 2),
                o.has("windowTicks") ? o.get("windowTicks").getAsLong() : FuturesMath.DAY, text(o, "unlock"));
        spec.validate();
        return spec;
    }

    void validate() {
        if (multiplier < 1 || multiplier > 10_000) throw new IllegalArgumentException("multiplier must be 1..10000");
        if (tenorDays < 1 || tenorDays > 365) throw new IllegalArgumentException("tenorDays must be 1..365");
        if (!(band >= 0.01D && band <= 0.9D)) throw new IllegalArgumentException("band must be 0.01..0.9");
        if (feeBps < 0 || feeBps > 1000) throw new IllegalArgumentException("feeBps must be 0..1000");
        if (maxLots < 1 || maxLots > 10_000) throw new IllegalArgumentException("maxLots must be 1..10000");
        if (minTraders < 1 || minTraders > 100) throw new IllegalArgumentException("minTraders must be 1..100");
        if (minBuckets < 1 || minBuckets > 1000) throw new IllegalArgumentException("minBuckets must be 1..1000");
        if (windowTicks < 4000L || windowTicks >= tenorDays * FuturesMath.DAY)
            throw new IllegalArgumentException("windowTicks must be at least 4000 and shorter than the tenor");
    }

    public JsonObject json() {
        JsonObject o = new JsonObject();
        o.addProperty("index", index);
        o.addProperty("multiplier", multiplier);
        o.addProperty("tenorDays", tenorDays);
        o.addProperty("band", band);
        o.addProperty("feeBps", feeBps);
        o.addProperty("maxLots", maxLots);
        o.addProperty("minTraders", minTraders);
        o.addProperty("minBuckets", minBuckets);
        o.addProperty("windowTicks", windowTicks);
        o.addProperty("unlock", unlock);
        return o;
    }

    private static String text(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    private static int number(JsonObject o, String key, int fallback) {
        return o.has(key) ? o.get(key).getAsInt() : fallback;
    }
}
