package com.society.sunlitmarket.core;

import net.minecraft.server.MinecraftServer;

/**
 * The economy's day counter. It runs on {@code getGameTime()}, which only advances one per server
 * tick: {@code /time set}, {@code /time add} and sleeping move {@code getDayTime()} but never this,
 * so nobody can fast-forward a budget reset or a contract expiry.
 */
public final class EconomyClock {
    public static final long DAY = 24000L;

    private EconomyClock() {
    }

    public static long tick(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    public static long day(MinecraftServer server) {
        return tick(server) / DAY;
    }
}
