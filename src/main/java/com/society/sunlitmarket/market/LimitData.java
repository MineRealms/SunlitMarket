package com.society.sunlitmarket.market;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-player, per-offer daily purchase counters. */
public final class LimitData extends SavedData {
    private static final String NAME = "sunlitmarket_limits";
    private final Map<UUID, Map<String, Integer>> used = new HashMap<>();
    private long day = Long.MIN_VALUE;

    public static LimitData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(LimitData::load, LimitData::new, NAME);
    }

    public void roll(long currentDay) {
        if (day != currentDay) {
            used.clear();
            day = currentDay;
            setDirty();
        }
    }

    public int used(Player player, String key) {
        Map<String, Integer> map = used.get(player.getUUID());
        return map == null ? 0 : map.getOrDefault(key, 0);
    }

    public boolean canBuy(Player player, String key, int limit, int quantity) {
        if (limit < 0) return true;
        return used(player, key) + quantity <= limit;
    }

    public void add(Player player, String key, int quantity) {
        used.computeIfAbsent(player.getUUID(), k -> new HashMap<>())
                .merge(key, quantity, Integer::sum);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong("day", day);
        CompoundTag players = new CompoundTag();
        for (Map.Entry<UUID, Map<String, Integer>> entry : used.entrySet()) {
            CompoundTag values = new CompoundTag();
            for (Map.Entry<String, Integer> value : entry.getValue().entrySet()) {
                values.putInt(value.getKey(), value.getValue());
            }
            players.put(entry.getKey().toString(), values);
        }
        tag.put("players", players);
        return tag;
    }

    public static LimitData load(CompoundTag tag) {
        LimitData data = new LimitData();
        data.day = tag.getLong("day");
        CompoundTag players = tag.getCompound("players");
        for (String uuid : players.getAllKeys()) {
            try {
                CompoundTag values = players.getCompound(uuid);
                Map<String, Integer> map = new HashMap<>();
                for (String key : values.getAllKeys()) map.put(key, values.getInt(key));
                data.used.put(UUID.fromString(uuid), map);
            } catch (Throwable ignored) {
            }
        }
        return data;
    }
}
