package com.society.sunlitmarket.deposit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Daily buyback budget for the acceptance bin: how much spur the world, and one player, may still
 * be paid today.
 *
 * <p>Without it the bin is a printing press - Minecraft production has no ceiling, so "sell
 * everything you can make" has to run into a wall. The wall is a day counter plus two caps, kept
 * in the overworld's SavedData so a restart cannot hand out a second budget for the same day.
 * The day is {@link com.society.sunlitmarket.core.EconomyClock}, never the wall clock or the
 * visible day: restarts, time zones, {@code /time} and sleeping would otherwise open a cross-day hole.
 * The bin's payouts themselves come out of pawn cash; this ledger only spreads them across players.
 *
 * <p>Server-side only.
 */
public final class BuybackLedger extends SavedData {
    public static final String NAME = "sunlitmarket_buyback";

    private long day = Long.MIN_VALUE;
    private long spent;
    private final Map<UUID, Long> perPlayer = new HashMap<>();

    public static BuybackLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(BuybackLedger::load, BuybackLedger::new, NAME);
    }

    /** Spur still payable to this player right now, or {@link Long#MAX_VALUE} when uncapped. */
    public long remaining(ServerPlayer player, long today) {
        int daily = DepositConfig.buybackDailyCap();
        int personal = DepositConfig.buybackPlayerCap();
        if (daily <= 0 && personal <= 0) return Long.MAX_VALUE;
        roll(today);
        long world = daily <= 0 ? Long.MAX_VALUE : Math.max(0L, daily - spent);
        long mine = personal <= 0 ? Long.MAX_VALUE : Math.max(0L, personal - perPlayer.getOrDefault(player.getUUID(), 0L));
        return Math.min(world, mine);
    }

    /** Books a payout; call only after the money actually moved. */
    public void record(ServerPlayer player, long today, int amount) {
        if (amount <= 0) return;
        roll(today);
        spent += amount;
        perPlayer.merge(player.getUUID(), (long) amount, Long::sum);
        setDirty();
    }

    /** Today's world-wide payout, for the admin view. */
    public long spentToday(long today) {
        roll(today);
        return spent;
    }

    private void roll(long today) {
        if (day != Long.MIN_VALUE && today <= day) return;
        day = today;
        spent = 0;
        perPlayer.clear();
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong("Day", day);
        tag.putLong("Spent", spent);
        CompoundTag players = new CompoundTag();
        perPlayer.forEach((id, amount) -> players.putLong(id.toString(), amount));
        tag.put("Players", players);
        return tag;
    }

    private static BuybackLedger load(CompoundTag tag) {
        BuybackLedger ledger = new BuybackLedger();
        ledger.day = tag.contains("Day") ? tag.getLong("Day") : Long.MIN_VALUE;
        ledger.spent = tag.getLong("Spent");
        CompoundTag players = tag.getCompound("Players");
        for (String key : players.getAllKeys()) {
            try {
                ledger.perPlayer.put(UUID.fromString(key), players.getLong(key));
            } catch (IllegalArgumentException ignored) {
                // A hand-edited or foreign key is dropped rather than failing the whole ledger.
            }
        }
        return ledger;
    }
}
