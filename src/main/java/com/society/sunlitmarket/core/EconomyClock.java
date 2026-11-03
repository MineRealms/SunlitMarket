package com.society.sunlitmarket.core;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * The economy's day counter. It follows the clock the pack shows the player - sleeping advances
 * it, which is how Sunlit Valley settles its shipping bins and seasons - but it never runs
 * backwards: {@code /time set} to an earlier time cannot replay a daily refill, a budget reset or a
 * contract expiry. See {@link EconomyClockData} for the rule. Server thread only.
 */
public final class EconomyClock {
    public static final long DAY = 24000L;

    private EconomyClock() {
    }

    public static long tick(MinecraftServer server) {
        if (server == null) return 0L;
        ServerLevel overworld = server.overworld();
        if (overworld == null) return 0L;
        EconomyClockData data = overworld.getDataStorage()
                .computeIfAbsent(EconomyClockData::load, EconomyClockData::new, EconomyClockData.NAME);
        return data.observe(overworld.getDayTime(), overworld.getGameTime());
    }

    public static long day(MinecraftServer server) {
        return tick(server) / DAY;
    }
}
