package com.society.sunlitmarket.core;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EconomyClockDataTest {
    private static final long DAY = EconomyClock.DAY;

    @Test void startsAtTheLaterOfTheTwoClocks() {
        assertEquals(90_000, new EconomyClockData().observe(90_000, 40_000), "slept-through world: dayTime ahead");
        assertEquals(70_000, new EconomyClockData().observe(10_000, 70_000), "rewound world: gameTime ahead");
    }

    @Test void normalTicksAdvanceByOne() {
        EconomyClockData clock = new EconomyClockData();
        clock.observe(1000, 1000);
        assertEquals(1001, clock.observe(1001, 1001));
        assertEquals(1001, clock.observe(1001, 1001), "same tick observed twice");
    }

    @Test void sleepingAdvancesTheEconomyDay() {
        EconomyClockData clock = new EconomyClockData();
        clock.observe(13_000, 13_000);
        long eco = clock.observe(DAY, 13_001);
        assertEquals(DAY, eco);
        assertEquals(1, eco / DAY);
    }

    @Test void timeSetBackwardsNeverRewinds() {
        EconomyClockData clock = new EconomyClockData();
        clock.observe(10 * DAY + 5000, 3 * DAY);
        long before = clock.observe(10 * DAY + 5001, 3 * DAY + 1);
        // "/time set day" puts dayTime back to 1000; only the game tick still counts.
        long after = clock.observe(1000, 3 * DAY + 2);
        assertEquals(before + 1, after);
        assertEquals(10, after / DAY, "the day did not go back");
        // Daylight resumes from the new low value; progress continues from there.
        assertEquals(after + 1, clock.observe(1001, 3 * DAY + 3));
    }

    @Test void frozenDaylightStillMovesOnGameTime() {
        EconomyClockData clock = new EconomyClockData();
        clock.observe(6000, 100);
        for (int i = 1; i <= 50; i++) clock.observe(6000, 100 + i);
        assertEquals(6050, clock.now());
    }

    @Test void timeAddJumpsForward() {
        EconomyClockData clock = new EconomyClockData();
        clock.observe(500, 500);
        assertEquals(500 + DAY, clock.observe(500 + DAY, 501));
    }

    @Test void survivesASaveAndContinuesWithoutAJump() {
        EconomyClockData clock = new EconomyClockData();
        clock.observe(5 * DAY, 2 * DAY);
        clock.observe(5 * DAY + 10, 2 * DAY + 10);
        EconomyClockData loaded = EconomyClockData.load(clock.save(new CompoundTag()));
        assertEquals(clock.now(), loaded.now());
        assertEquals(clock.now() + 1, loaded.observe(5 * DAY + 11, 2 * DAY + 11));
    }
}
