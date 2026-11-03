package com.society.sunlitmarket.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persisted state behind {@link EconomyClock}: a tick counter that only moves forward.
 *
 * <p>Each observation advances the counter by the larger forward step of the two vanilla clocks.
 * Sleeping jumps {@code dayTime} ahead, so the economy day follows the day Sunlit Valley shows;
 * a frozen daylight cycle still lets {@code gameTime} move it; and {@code /time set} backwards is
 * a negative step for {@code dayTime} while {@code gameTime} keeps its one tick, so no day
 * boundary is ever replayed. Pure arithmetic, unit-tested without a world.
 */
public final class EconomyClockData extends SavedData {
    public static final String NAME = "sunlitmarket_clock";

    private long eco = Long.MIN_VALUE;
    private long lastDayTime;
    private long lastGameTime;

    /** Feeds the current vanilla clocks in and returns the economy tick. */
    public long observe(long dayTime, long gameTime) {
        if (eco == Long.MIN_VALUE) {
            // Same value the previous max(dayTime, gameTime) clock had, so switching is seamless.
            eco = Math.max(dayTime, gameTime);
            lastDayTime = dayTime;
            lastGameTime = gameTime;
            setDirty();
            return eco;
        }
        if (dayTime == lastDayTime && gameTime == lastGameTime) return eco;
        long step = Math.max(0L, Math.max(dayTime - lastDayTime, gameTime - lastGameTime));
        eco = eco > Long.MAX_VALUE - step ? Long.MAX_VALUE : eco + step;
        lastDayTime = dayTime;
        lastGameTime = gameTime;
        setDirty();
        return eco;
    }

    public long now() {
        return eco == Long.MIN_VALUE ? 0L : eco;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong("Eco", eco);
        tag.putLong("LastDayTime", lastDayTime);
        tag.putLong("LastGameTime", lastGameTime);
        return tag;
    }

    public static EconomyClockData load(CompoundTag tag) {
        EconomyClockData data = new EconomyClockData();
        data.eco = tag.contains("Eco") ? tag.getLong("Eco") : Long.MIN_VALUE;
        data.lastDayTime = tag.getLong("LastDayTime");
        data.lastGameTime = tag.getLong("LastGameTime");
        return data;
    }
}
