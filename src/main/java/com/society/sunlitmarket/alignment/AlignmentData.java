package com.society.sunlitmarket.alignment;

import com.society.sunlitmarket.SunlitMarketMod;
import com.society.sunlitmarket.compat.FtbTeamsCompat;
import com.society.sunlitmarket.compat.OwnerKey;
import com.society.sunlitmarket.config.SunlitMarketConfig;
import com.society.sunlitmarket.core.TradeMath;
import com.society.sunlitmarket.reputation.ReputationData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Versioned player/team alignment. Wanted ownership is independently configurable. */
public final class AlignmentData extends SavedData {
    private static final String NAME = "sunlitmarket_alignment";
    private final Map<String, Entry> values = new HashMap<>();
    private boolean migrated;
    private long currentDay = Long.MIN_VALUE;
    public static final class Entry {
        public final Map<String, Integer> alignment = new HashMap<>();
        public int wanted;
        public long lastDecayDay = Long.MIN_VALUE;
    }
    public static AlignmentData get(MinecraftServer server) {
        AlignmentData data = server.overworld().getDataStorage().computeIfAbsent(AlignmentData::load, AlignmentData::new, NAME);
        // Same clock as the daily decay roll, so a new entry never starts days "in the past".
        data.currentDay = com.society.sunlitmarket.core.EconomyClock.day(server);
        data.ensureMigrated(server);
        return data;
    }
    public static UUID ownerOf(Player player) { return OwnerKey.parse(ownerKey(player)).id(); }
    public static String ownerKey(Player player) {
        return com.society.sunlitmarket.config.CoreConfig.TEAM_ALIGNMENT.get() ? FtbTeamsCompat.owner(player).key() : OwnerKey.player(player.getUUID()).key();
    }
    private Entry entry(String key) {
        return values.computeIfAbsent(key, k -> { Entry e = new Entry(); e.lastDecayDay = currentDay; return e; });
    }
    public int get(Player player, String faction) { return getKey(ownerKey(player), faction); }
    public int getByOwner(UUID owner, String faction) { return getKey(OwnerKey.player(owner).key(), faction); }
    private int getKey(String owner, String faction) {
        if (!SunlitMarketConfig.ALIGNMENT_ENABLED.get()) return 0;
        Entry e = values.get(owner);
        return e == null ? 0 : clamp(e.alignment.getOrDefault(faction, 0));
    }
    public void set(Player player, String faction, int value) {
        if (!FtbTeamsCompat.resolve(player).available()) throw new IllegalStateException("Team ownership unavailable");
        setKey(ownerKey(player), faction, value);
    }
    public void setByOwner(UUID owner, String faction, int value) { setKey(OwnerKey.player(owner).key(), faction, value); }
    private void setKey(String owner, String faction, int value) {
        if (faction == null || faction.isBlank()) return;
        entry(owner).alignment.put(faction, clamp(value)); setDirty();
    }
    public void add(Player player, String faction, int delta) {
        if (faction != null && !faction.isBlank()) addMany(player, Map.of(faction, delta));
    }
    public void addMany(Player player, Map<String, Integer> deltas) {
        if (!FtbTeamsCompat.resolve(player).available()) throw new IllegalStateException("Team ownership unavailable");
        Entry e = entry(ownerKey(player));
        Map<String, Long> applied = new HashMap<>();
        for (var d : deltas.entrySet()) {
            if (d.getKey() == null || d.getKey().isBlank() || d.getValue() == null) continue;
            applied.merge(d.getKey(), (long) d.getValue(), Long::sum);
            if (d.getValue() > 0 && SunlitMarketConfig.DOUBLE_DROP_ENABLED.get()) {
                long actualGain = Math.max(0L, (long) clamp((long) e.alignment.getOrDefault(d.getKey(), 0) + d.getValue())
                        - e.alignment.getOrDefault(d.getKey(), 0));
                long loss = (long) Math.ceil(actualGain * SunlitMarketConfig.DOUBLE_DROP_RATIO.get());
                for (String enemy : FactionRelations.hostilesOf(d.getKey()))
                    if (!enemy.equals(d.getKey())) applied.merge(enemy, -loss, Long::sum);
            }
        }
        applied.forEach((f, delta) -> e.alignment.put(f, clamp((long) e.alignment.getOrDefault(f, 0) + delta)));
        setDirty();
    }
    private String wantedOwner(Player player) {
        return com.society.sunlitmarket.config.CoreConfig.WANTED_SCOPE.get() == com.society.sunlitmarket.config.CoreConfig.WantedScope.TEAM
                ? FtbTeamsCompat.owner(player).key() : OwnerKey.player(player.getUUID()).key();
    }
    public int wanted(Player player) {
        Entry e = values.get(wantedOwner(player));
        return e == null ? 0 : TradeMath.clamp(e.wanted, 0, SunlitMarketConfig.WANTED_MAX.get());
    }
    public void addWanted(Player player, int delta) {
        setWanted(player, TradeMath.clamp((long) wanted(player) + delta, 0, SunlitMarketConfig.WANTED_MAX.get()));
    }
    public void setWanted(Player player, int value) {
        entry(wantedOwner(player)).wanted = TradeMath.clamp(value, 0, SunlitMarketConfig.WANTED_MAX.get()); setDirty();
    }
    public Map<String, Integer> snapshot(Player player) {
        Map<String, Integer> result = new HashMap<>();
        if (!SunlitMarketConfig.ALIGNMENT_ENABLED.get()) return result;
        Entry e = values.get(ownerKey(player));
        if (e != null) e.alignment.forEach((k, v) -> { if (v != 0) result.put(k, clamp(v)); });
        return result;
    }
    public void roll(long day, MinecraftServer server) {
        currentDay = day;
        boolean changed = false;
        for (Entry e : values.values()) {
            if (e.lastDecayDay != Long.MIN_VALUE && day <= e.lastDecayDay) continue;
            long elapsed = TradeMath.days(e.lastDecayDay, day);
            e.lastDecayDay = day; changed = true;
            if (SunlitMarketConfig.DECAY_ENABLED.get()) {
                e.alignment.replaceAll((k, v) -> TradeMath.decay(v, elapsed, SunlitMarketConfig.DECAY_PER_DAY.get()));
                e.wanted = TradeMath.decay(e.wanted, elapsed, SunlitMarketConfig.WANTED_DECAY_PER_DAY.get());
            }
        }
        if (changed) setDirty();
    }
    private static int clamp(long value) {
        return TradeMath.clamp(value, SunlitMarketConfig.ALIGNMENT_MIN.get(), SunlitMarketConfig.ALIGNMENT_MAX.get());
    }
    private void ensureMigrated(MinecraftServer server) {
        if (migrated) return;
        try {
            Map<String, Map<String, Integer>> candidate = new HashMap<>();
            for (var row : ReputationData.get(server).snapshotAll().entrySet()) {
                Map<String, Integer> copy = new HashMap<>();
                row.getValue().forEach((f, v) -> { if (v != null && v != 0) copy.put(f, clamp(v)); });
                candidate.put(OwnerKey.player(row.getKey()).key(), copy);
            }
            candidate.forEach((owner, row) -> row.forEach((f, v) -> entry(owner).alignment.putIfAbsent(f, v)));
            migrated = true; setDirty();
            SunlitMarketMod.LOG.info("[SunlitMarket] legacy migration completed ({} owners)", candidate.size());
        } catch (RuntimeException ex) {
            SunlitMarketMod.LOG.error("[SunlitMarket] legacy migration incomplete; source preserved", ex);
        }
    }
    public static AlignmentData load(CompoundTag tag) {
        AlignmentData data = new AlignmentData(); data.migrated = tag.getBoolean("migrated");
        CompoundTag owners = tag.getCompound("owners");
        for (String rawKey : owners.getAllKeys()) {
            try {
                String key = OwnerKey.parse(rawKey).key();
                CompoundTag raw = owners.getCompound(rawKey), align = raw.getCompound("alignment");
                Entry e = new Entry();
                for (String faction : align.getAllKeys()) e.alignment.put(faction, align.getInt(faction));
                e.wanted = Math.max(0, raw.getInt("wanted"));
                e.lastDecayDay = raw.contains("lastDecayDay") ? raw.getLong("lastDecayDay") : Long.MIN_VALUE;
                data.values.put(key, e);
            } catch (IllegalArgumentException ex) { SunlitMarketMod.LOG.warn("[SunlitMarket] Invalid alignment owner {}", rawKey); }
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        CompoundTag owners = new CompoundTag();
        values.forEach((key, e) -> {
            CompoundTag raw = new CompoundTag(), align = new CompoundTag();
            e.alignment.forEach(align::putInt); raw.put("alignment", align); raw.putInt("wanted", e.wanted);
            raw.putLong("lastDecayDay", e.lastDecayDay); owners.put(key, raw);
        });
        tag.put("owners", owners); tag.putBoolean("migrated", migrated); tag.putInt("version", 2); return tag;
    }
}
