package com.society.sunlitmarket.market;

import com.google.gson.JsonArray;
import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One OHLC bar built only from real fills. Besides price it keeps the raw volume, the capped
 * weight (each owner contributes at most {@code ownerCap} lots per bar, so one account cannot
 * dominate the price) and the per-owner volume that drives both the cap and the trader count.
 */
public final class Candle {
    public final long bucket;
    public int open, high, low, close;
    public long volume, weight;
    /** Owner key → lots traded in this bar. */
    public final Map<String, Long> owners = new LinkedHashMap<>();

    public Candle(long bucket, int price) {
        this.bucket = bucket;
        this.open = this.high = this.low = this.close = price;
    }

    /** Folds one fill in; {@code ownerCap <= 0} leaves weight uncapped. */
    public void add(int price, long quantity, String owner, long ownerCap) {
        high = Math.max(high, price);
        low = Math.min(low, price);
        close = price;
        long qty = Math.max(0L, quantity);
        volume = saturatingAdd(volume, qty);
        long before = owners.getOrDefault(owner, 0L);
        long after = saturatingAdd(before, qty);
        owners.put(owner, after);
        weight = saturatingAdd(weight, ownerCap <= 0 ? qty : Math.min(after, ownerCap) - Math.min(before, ownerCap));
    }

    public int traders() {
        return owners.size();
    }

    /** {bucket, open, high, low, close, volume, weight, traders}; the first five match the old wire format. */
    public int[] wire() {
        return new int[]{(int) bucket, open, high, low, close, clampInt(volume), clampInt(weight), traders()};
    }

    public JsonArray json() {
        JsonArray a = new JsonArray();
        a.add(bucket);
        a.add(open);
        a.add(high);
        a.add(low);
        a.add(close);
        a.add(volume);
        a.add(weight);
        a.add(traders());
        return a;
    }

    public Candle copy() {
        Candle c = new Candle(bucket, open);
        c.high = high;
        c.low = low;
        c.close = close;
        c.volume = volume;
        c.weight = weight;
        c.owners.putAll(owners);
        return c;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("b", bucket);
        t.putIntArray("ohlc", new int[]{open, high, low, close});
        t.putLong("v", volume);
        t.putLong("w", weight);
        CompoundTag o = new CompoundTag();
        owners.forEach(o::putLong);
        t.put("owners", o);
        return t;
    }

    public static Candle load(CompoundTag t) {
        int[] ohlc = t.getIntArray("ohlc");
        if (ohlc.length != 4) throw new IllegalArgumentException("bad candle");
        Candle c = new Candle(t.getLong("b"), ohlc[0]);
        c.high = ohlc[1];
        c.low = ohlc[2];
        c.close = ohlc[3];
        c.volume = t.getLong("v");
        c.weight = t.getLong("w");
        CompoundTag o = t.getCompound("owners");
        for (String key : o.getAllKeys()) c.owners.put(key, o.getLong(key));
        return c;
    }

    private static int clampInt(long value) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
    }

    private static long saturatingAdd(long a, long b) {
        long r = a + b;
        return ((a ^ r) & (b ^ r)) < 0 ? Long.MAX_VALUE : r;
    }
}
