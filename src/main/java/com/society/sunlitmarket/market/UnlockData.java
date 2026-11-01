package com.society.sunlitmarket.market;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player unlock flags (quest stages / themed merchant access).
 * Pushed from KubeJS or {@code /sunlitmarket unlock <id>}; never read from other mods.
 */
public final class UnlockData extends SavedData {
    private static final String NAME = "sunlitmarket_unlocks";
    private final Map<UUID, Set<String>> unlocked = new HashMap<>();

    public static UnlockData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(UnlockData::load, UnlockData::new, NAME);
    }

    public boolean has(Player player, String unlock) {
        if (unlock == null || unlock.isBlank()) return true;
        Set<String> set = unlocked.get(player.getUUID());
        return set != null && set.contains(unlock);
    }

    public boolean grant(Player player, String unlock) {
        if (unlock == null || unlock.isBlank()) return false;
        boolean added = unlocked.computeIfAbsent(player.getUUID(), k -> new HashSet<>()).add(unlock);
        if (added) setDirty();
        return added;
    }

    public boolean revoke(Player player, String unlock) {
        Set<String> set = unlocked.get(player.getUUID());
        if (set == null) return false;
        boolean removed = set.remove(unlock);
        if (removed) setDirty();
        return removed;
    }

    public Set<String> list(Player player) {
        Set<String> set = unlocked.get(player.getUUID());
        return set == null ? Set.of() : Set.copyOf(set);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag players = new ListTag();
        for (Map.Entry<UUID, Set<String>> entry : unlocked.entrySet()) {
            CompoundTag player = new CompoundTag();
            player.putString("uuid", entry.getKey().toString());
            ListTag ids = new ListTag();
            for (String id : entry.getValue()) {
                CompoundTag value = new CompoundTag();
                value.putString("id", id);
                ids.add(value);
            }
            player.put("unlocks", ids);
            players.add(player);
        }
        tag.put("players", players);
        return tag;
    }

    public static UnlockData load(CompoundTag tag) {
        UnlockData data = new UnlockData();
        ListTag players = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag entry = players.getCompound(i);
            try {
                UUID uuid = UUID.fromString(entry.getString("uuid"));
                ListTag ids = entry.getList("unlocks", Tag.TAG_COMPOUND);
                Set<String> set = new HashSet<>();
                for (int j = 0; j < ids.size(); j++) set.add(ids.getCompound(j).getString("id"));
                data.unlocked.put(uuid, set);
            } catch (Throwable ignored) {
            }
        }
        return data;
    }
}
