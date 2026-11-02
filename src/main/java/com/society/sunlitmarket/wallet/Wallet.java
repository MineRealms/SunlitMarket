package com.society.sunlitmarket.wallet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * A per-player virtual account, stored in the player's Forge persistent data.
 *
 * <p>Deliberately not a capability: the balance must survive death, dimension changes and
 * datapack reloads without a registration pass, and {@code ForgeData} already does all three.
 * Server-side only.
 */
public final class Wallet {
    private static final String TAG = "sunlitmarket.wallet";

    private Wallet() {
    }

    public static long balance(Player player) {
        CompoundTag data = player.getPersistentData();
        return data.contains(TAG) ? Math.max(0L, data.getLong(TAG)) : 0L;
    }

    public static boolean set(Player player, long value) {
        if (value < 0) return false;
        player.getPersistentData().putLong(TAG, value);
        return true;
    }

    /** Adds to the balance, saturating instead of wrapping. */
    public static boolean add(Player player, long amount) {
        if (amount <= 0) return false;
        long next = balance(player);
        if (Long.MAX_VALUE - next < amount) next = Long.MAX_VALUE;
        else next += amount;
        return set(player, next);
    }

    /** Removes from the balance; false and no change when the player cannot cover it. */
    public static boolean take(Player player, long amount) {
        if (amount <= 0) return false;
        long current = balance(player);
        if (current < amount) return false;
        return set(player, current - amount);
    }

    /** Whether the wallet takes part in payments at all; see {@code wallet.enabled}. */
    public static boolean enabled() {
        return com.society.sunlitmarket.deposit.DepositConfig.walletEnabled();
    }
}
