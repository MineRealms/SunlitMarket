package com.society.sunlitmarket.currency;

import net.minecraft.world.entity.player.Player;

/**
 * Optional pack-side diversion for money SunlitMarket is about to pay a player: pawn sales,
 * NPC demand orders and the acceptance-bin payout all pass through {@link Currency#settle}
 * with a positive money delta.
 *
 * <p>A pack registers one sink from a KubeJS script, for example to settle hospital debt
 * before the player sees the money — the same order the shipping bin uses. Whatever the sink
 * returns is not credited to the player; when the settlement then fails, {@link #rollback}
 * receives the same amount so the sink can put the diversion back.
 *
 * <p>Both methods run on the server thread, inside the settlement, and must not call back
 * into {@link Currency}.
 */
public interface PayoutSink {

    /**
     * @param amount the full amount SunlitMarket is about to credit
     * @return how much of it to divert (clamped to {@code 0..amount}); 0 leaves the payout intact
     */
    long divert(Player player, long amount, String reason);

    /** Called when a payout the sink diverted money from did not complete. */
    default void rollback(Player player, long diverted, String reason) {
    }

    /** Two-function form, because Rhino converts a JS function to a single-method interface. */
    @FunctionalInterface
    interface Divert {
        long apply(Player player, long amount, String reason);
    }

    @FunctionalInterface
    interface Rollback {
        void apply(Player player, long diverted, String reason);
    }

    static PayoutSink of(Divert divert, Rollback rollback) {
        return new PayoutSink() {
            @Override public long divert(Player player, long amount, String reason) {
                return divert.apply(player, amount, reason);
            }
            @Override public void rollback(Player player, long diverted, String reason) {
                if (rollback != null) rollback.apply(player, diverted, reason);
            }
        };
    }
}
