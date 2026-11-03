package com.society.sunlitmarket.core;

import net.minecraft.server.MinecraftServer;

/**
 * The economy's day counter. It follows the clock the pack shows the player: {@code getDayTime()}
 * advances when someone sleeps, which is how Sunlit Valley settles its shipping bins and seasons.
 * {@code getGameTime()} is the monotonic floor, so {@code /time set} backwards can never replay a
 * day boundary (a reset would be free money).
 */
public final class EconomyClock {
    public static final long DAY = 24000L;

    private EconomyClock() {
    }

    public static long tick(MinecraftServer server) {
        if (server == null) return 0L;
        net.minecraft.server.level.ServerLevel overworld = server.overworld();
        if (overworld == null) return 0L;
        return Math.max(overworld.getDayTime(), overworld.getGameTime());
    }

    public static long day(MinecraftServer server) {
        return tick(server) / DAY;
    }
}
