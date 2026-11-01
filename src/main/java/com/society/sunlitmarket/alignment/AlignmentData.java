package com.society.sunlitmarket.alignment;

import com.society.sunlitmarket.SunlitMarketMod;
import com.society.sunlitmarket.config.SunlitMarketConfig;
import com.society.sunlitmarket.reputation.ReputationData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Player x faction alignment, plus a wanted level.
 *
 * <p>Storage is keyed by "owner" rather than player so the FTB Teams dimension can be
 * added later without a format change: {@link #ownerOf(Player)} is the single place that
 * decides whether a player reads and writes their own row or their team's.</p>
 */
public final class AlignmentData extends SavedData {
    private static final String NAME = "sunlitmarket_alignment";

    private final Map<UUID, Entry> values = new HashMap<>();
    private boolean migrated;

    public static final class Entry {
        public final Map<String, Integer> alignment = new HashMap<>();
        public int wanted;
        public long lastDecayDay = Long.MIN_VALUE;
    }

    public static AlignmentData get(MinecraftServer server) {
        AlignmentData data = server.overworld().getDataStorage()
                .computeIfAbsent(AlignmentData::load, AlignmentData::new, NAME);
        data.ensureMigrated(server);
        return data;
    }

    /** Single seam for the future FTB Teams dimension. */
    public static UUID ownerOf(Player player) {
        return player.getUUID();
    }

    private Entry entry(UUID owner) {
        return values.computeIfAbsent(owner, k -> new Entry());
    }

    public int get(Player player, String faction) {
        return getByOwner(ownerOf(player), faction);
    }

    public int getByOwner(UUID owner, String faction) {
        if (!SunlitMarketConfig.ALIGNMENT_ENABLED.get()) return 0;
        Entry e = values.get(owner);
        if (e == null) return 0;
        return e.alignment.getOrDefault(normalize(faction), 0);
    }

    public void set(Player player, String faction, int value) {
        setByOwner(ownerOf(player), faction, value);
    }

    public void setByOwner(UUID owner, String faction, int value) {
        if (faction == null || faction.isBlank()) return;
        Entry e = entry(owner);
        e.alignment.put(normalize(faction), clamp(value));
        setDirty();
    }

    /**
     * Applies a delta and, when enabled, the diplomatic backlash: gaining with A costs
     * you with every faction A is hostile to.
     */
    public void add(Player player, String faction, int delta) {
        addMany(player, Map.of(normalize(faction), delta));
    }

    public void addMany(Player player, Map<String, Integer> deltas) {
        if (deltas.isEmpty()) return;
        UUID owner = ownerOf(player);
        Entry e = entry(owner);
        Map<String, Integer> applied = new HashMap<>(deltas);
        if (SunlitMarketConfig.DOUBLE_DROP_ENABLED.get()) {
            double ratio = SunlitMarketConfig.DOUBLE_DROP_RATIO.get();
            for (Map.Entry<String, Integer> d : deltas.entrySet()) {
                int v = d.getValue();
                if (v <= 0) continue;
                for (String enemy : FactionRelations.hostilesOf(d.getKey())) {
                    if (enemy.equals(d.getKey())) continue;
                    applied.merge(enemy, -(int) Math.ceil(v * ratio), Integer::sum);
                }
            }
        }
        for (Map.Entry<String, Integer> d : applied.entrySet()) {
            int now = e.alignment.getOrDefault(d.getKey(), 0);
            e.alignment.put(d.getKey(), clamp(now + d.getValue()));
        }
        setDirty();
    }

    public int wanted(Player player) {
        Entry e = values.get(ownerOf(player));
        return e == null ? 0 : e.wanted;
    }

    public void addWanted(Player player, int delta) {
        Entry e = entry(ownerOf(player));
        e.wanted = Math.max(0, Math.min(SunlitMarketConfig.WANTED_MAX.get(), e.wanted + delta));
        setDirty();
    }

    public void setWanted(Player player, int value) {
        Entry e = entry(ownerOf(player));
        e.wanted = Math.max(0, Math.min(SunlitMarketConfig.WANTED_MAX.get(), value));
        setDirty();
    }

    /** Non-zero factions only, so the UI does not drown in 17 empty rows. */
    public Map<String, Integer> snapshot(Player player) {
        Entry e = values.get(ownerOf(player));
        Map<String, Integer> out = new HashMap<>();
        if (e == null) return out;
        for (Map.Entry<String, Integer> a : e.alignment.entrySet()) {
            if (a.getValue() != 0) out.put(a.getKey(), a.getValue());
        }
        return out;
    }

    /** Once-per-day drift toward zero. Idempotent for a given day. */
    public void roll(long day, MinecraftServer server) {
        if (!SunlitMarketConfig.DECAY_ENABLED.get()) return;
        int decay = SunlitMarketConfig.DECAY_PER_DAY.get();
        int wantedDecay = SunlitMarketConfig.WANTED_DECAY_PER_DAY.get();
        boolean changed = false;
        for (Entry e : values.values()) {
            if (e.lastDecayDay == day) continue;
            e.lastDecayDay = day;
            changed = true;
            if (decay > 0) {
                for (Map.Entry<String, Integer> a : e.alignment.entrySet()) {
                    int v = a.getValue();
                    if (v > 0) a.setValue(Math.max(0, v - decay));
                    else if (v < 0) a.setValue(Math.min(0, v + decay));
                }
            }
            if (wantedDecay > 0 && e.wanted > 0) {
                e.wanted = Math.max(0, e.wanted - wantedDecay);
            }
        }
        if (changed) setDirty();
    }

    private static String normalize(String faction) {
        return faction == null ? "" : faction;
    }

    private static int clamp(int value) {
        int min = SunlitMarketConfig.ALIGNMENT_MIN.get();
        int max = SunlitMarketConfig.ALIGNMENT_MAX.get();
        return Math.max(min, Math.min(max, value));
    }

    /** Copies the legacy reputation table once, so existing worlds keep their progress. */
    private void ensureMigrated(MinecraftServer server) {
        if (migrated) return;
        migrated = true;
        try {
            ReputationData legacy = ReputationData.get(server);
            int copied = 0;
            for (Map.Entry<UUID, Map<String, Integer>> row : legacy.snapshotAll().entrySet()) {
                Entry e = entry(row.getKey());
                for (Map.Entry<String, Integer> a : row.getValue().entrySet()) {
                    if (a.getValue() == null || a.getValue() == 0) continue;
                    e.alignment.put(a.getKey(), clamp(a.getValue()));
                    copied++;
                }
            }
            if (copied > 0) {
                setDirty();
                SunlitMarketMod.LOG.info("[SunlitMarket] migrated {} alignment rows from reputation", copied);
            }
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] alignment migration failed", t);
        }
    }

    public static AlignmentData load(CompoundTag tag) {
        AlignmentData data = new AlignmentData();
        data.migrated = tag.getBoolean("migrated");
        CompoundTag owners = tag.getCompound("owners");
        for (String key : owners.getAllKeys()) {
            try {
                UUID owner = UUID.fromString(key);
                CompoundTag raw = owners.getCompound(key);
                Entry e = new Entry();
                CompoundTag align = raw.getCompound("alignment");
                for (String faction : align.getAllKeys()) {
                    e.alignment.put(faction, align.getInt(faction));
                }
                e.wanted = raw.getInt("wanted");
                e.lastDecayDay = raw.contains("lastDecayDay") ? raw.getLong("lastDecayDay") : Long.MIN_VALUE;
                data.values.put(owner, e);
            } catch (Throwable ignored) {
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag owners = new CompoundTag();
        for (Map.Entry<UUID, Entry> owner : values.entrySet()) {
            CompoundTag raw = new CompoundTag();
            CompoundTag align = new CompoundTag();
            for (Map.Entry<String, Integer> a : owner.getValue().alignment.entrySet()) {
                align.putInt(a.getKey(), a.getValue());
            }
            raw.put("alignment", align);
            raw.putInt("wanted", owner.getValue().wanted);
            if (owner.getValue().lastDecayDay != Long.MIN_VALUE) {
                raw.putLong("lastDecayDay", owner.getValue().lastDecayDay);
            }
            owners.put(owner.getKey().toString(), raw);
        }
        tag.put("owners", owners);
        tag.putBoolean("migrated", true);
        return tag;
    }

}
