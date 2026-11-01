package com.society.sunlitmarket.market;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/** Per-merchant/offer stock, reset at the start of each in-game day. */
public final class StockData extends SavedData {
    private static final String NAME = "sunlitmarket_stock";
    private final Map<String, Integer> current = new HashMap<>();
    private long day = Long.MIN_VALUE;

    public static StockData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(StockData::load, StockData::new, NAME);
    }

    public void roll(long currentDay) {
        if (day != currentDay) {
            current.clear();
            day = currentDay;
            setDirty();
        }
    }

    public int get(String key, int initial) {
        if (initial < 0) return -1;
        return Math.max(0, current.getOrDefault(key, initial));
    }

    public boolean take(String key, int initial, int amount) {
        if (initial < 0) return true;
        int available = get(key, initial);
        if (available < amount) return false;
        current.put(key, available - amount);
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong("day", day);
        CompoundTag values = new CompoundTag();
        for (Map.Entry<String, Integer> entry : current.entrySet()) values.putInt(entry.getKey(), entry.getValue());
        tag.put("stock", values);
        return tag;
    }

    public static StockData load(CompoundTag tag) {
        StockData data = new StockData();
        data.day = tag.getLong("day");
        CompoundTag values = tag.getCompound("stock");
        for (String key : values.getAllKeys()) data.current.put(key, values.getInt(key));
        return data;
    }
}
