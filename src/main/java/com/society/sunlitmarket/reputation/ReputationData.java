package com.society.sunlitmarket.reputation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Independent reputation, keyed by player UUID and faction id. */
public final class ReputationData extends SavedData {
    private static final String NAME = "sunlitmarket_reputation";
    private final Map<UUID, Map<String, Integer>> values = new HashMap<>();

    public static ReputationData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ReputationData::load, ReputationData::new, NAME);
    }

    public int get(Player player, String faction) {
        return get(player.getUUID(), faction);
    }

    public int get(UUID player, String faction) {
        Map<String, Integer> map = values.get(player);
        return map == null ? 0 : map.getOrDefault(faction == null ? "" : faction, 0);
    }

    public void set(Player player, String faction, int value) {
        set(player.getUUID(), faction, value);
    }

    public void set(UUID player, String faction, int value) {
        values.computeIfAbsent(player, k -> new HashMap<>()).put(faction == null ? "" : faction, value);
        setDirty();
    }

    public void add(Player player, String faction, int delta) {
        set(player, faction, get(player, faction) + delta);
    }

    /** Read-only view of every stored row, used for the one-time migration to AlignmentData. */
    public Map<UUID, Map<String, Integer>> snapshotAll() {
        Map<UUID, Map<String, Integer>> copy = new HashMap<>();
        for (Map.Entry<UUID, Map<String, Integer>> entry : values.entrySet()) {
            copy.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return copy;
    }

    public static ReputationData load(CompoundTag tag) {
        ReputationData data = new ReputationData();
        ListTag players = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag entry = players.getCompound(i);
            try {
                UUID uuid = UUID.fromString(entry.getString("uuid"));
                ListTag reps = entry.getList("reps", Tag.TAG_COMPOUND);
                Map<String, Integer> map = new HashMap<>();
                for (int j = 0; j < reps.size(); j++) {
                    CompoundTag rep = reps.getCompound(j);
                    map.put(rep.getString("faction"), rep.getInt("value"));
                }
                data.values.put(uuid, map);
            } catch (Throwable ignored) {
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag players = new ListTag();
        for (Map.Entry<UUID, Map<String, Integer>> entry : values.entrySet()) {
            CompoundTag player = new CompoundTag();
            player.putString("uuid", entry.getKey().toString());
            ListTag reps = new ListTag();
            for (Map.Entry<String, Integer> rep : entry.getValue().entrySet()) {
                CompoundTag r = new CompoundTag();
                r.putString("faction", rep.getKey());
                r.putInt("value", rep.getValue());
                reps.add(r);
            }
            player.put("reps", reps);
            players.add(player);
        }
        tag.put("players", players);
        return tag;
    }
}
