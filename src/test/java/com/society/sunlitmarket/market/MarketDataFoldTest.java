package com.society.sunlitmarket.market;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MarketDataFoldTest {

    private static MarketData dataWithMember() {
        MarketData data = new MarketData();
        data.defineIndex("sv.ore", "矿指", java.util.List.of(new MarketData.Member("m", "minecraft:iron_ingot")));
        return data;
    }

    @Test void firstSampleSeedsAllPeriods() {
        MarketData data = dataWithMember();
        MarketData.IndexState state = data.state("sv.ore");
        MarketData.fold(state, MarketData.PERIODS[0], 5, 1000);
        assertEquals(1000, data.value("sv.ore"));
        assertEquals(5L, state.bucket.get(MarketData.PERIODS[0]));
    }

    @Test void sameBucketUpdatesCurrentCandle() {
        MarketData data = dataWithMember();
        MarketData.IndexState state = data.state("sv.ore");
        MarketData.fold(state, MarketData.PERIODS[0], 5, 1000);
        MarketData.fold(state, MarketData.PERIODS[0], 5, 1100);
        MarketData.fold(state, MarketData.PERIODS[0], 5, 900);
        assertArrayEquals(new int[]{1000, 1100, 900, 900}, state.current.get(MarketData.PERIODS[0]));
        assertEquals(0, state.ring.get(MarketData.PERIODS[0]).size());
    }

    @Test void bucketRollArchivesCandle() {
        MarketData data = dataWithMember();
        MarketData.IndexState state = data.state("sv.ore");
        MarketData.fold(state, MarketData.PERIODS[0], 5, 1000);
        MarketData.fold(state, MarketData.PERIODS[0], 6, 1050);
        assertEquals(1, state.ring.get(MarketData.PERIODS[0]).size());
        assertArrayEquals(new int[]{1000, 1000, 1000, 1000}, state.ring.get(MarketData.PERIODS[0]).peekFirst());
        assertEquals(1050, data.value("sv.ore"));
    }

    @Test void missedBucketsBackfillFlatAtLastClose() {
        MarketData data = dataWithMember();
        MarketData.IndexState state = data.state("sv.ore");
        MarketData.fold(state, MarketData.PERIODS[0], 5, 1000);
        MarketData.fold(state, MarketData.PERIODS[0], 8, 1100);
        // buckets 6 and 7 are flat candles at 1000, bucket 8 is the new live candle.
        assertEquals(3, state.ring.get(MarketData.PERIODS[0]).size());
        int[] last = state.ring.get(MarketData.PERIODS[0]).peekLast();
        assertArrayEquals(new int[]{1000, 1000, 1000, 1000}, last);
    }

    @Test void ringIsBounded() {
        MarketData data = dataWithMember();
        MarketData.IndexState state = data.state("sv.ore");
        for (long bucket = 0; bucket < MarketData.RING + 50L; bucket++) {
            MarketData.fold(state, MarketData.PERIODS[0], bucket, 1000);
        }
        assertEquals(MarketData.RING, state.ring.get(MarketData.PERIODS[0]).size());
    }

    @Test void saveLoadRoundTripsCandles() {
        MarketData data = dataWithMember();
        MarketData.IndexState state = data.state("sv.ore");
        MarketData.fold(state, MarketData.PERIODS[0], 5, 1000);
        MarketData.fold(state, MarketData.PERIODS[0], 5, 1200);
        MarketData.fold(state, MarketData.PERIODS[0], 6, 1300);

        MarketData restored = MarketData.load(data.save(new CompoundTag()));
        MarketData.IndexState restoredState = restored.state("sv.ore");
        assertEquals("矿指", restoredState.name);
        assertEquals(1, restoredState.members.size());
        assertEquals(1300, restored.value("sv.ore"));
        assertEquals(1, restoredState.ring.get(MarketData.PERIODS[0]).size());
        assertArrayEquals(new int[]{1000, 1200, 1000, 1200}, restoredState.ring.get(MarketData.PERIODS[0]).peekFirst());
    }
}
