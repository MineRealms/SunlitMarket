package com.society.sunlitmarket.market;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarketDataFoldTest {
    private static final int DAY = MarketData.PERIODS[0];
    private static final int BAR = MarketData.PERIODS[1];

    private static MarketData dataWithMembers() {
        MarketData data = new MarketData();
        data.defineIndex("sv.ore", "矿指", List.of(new MarketData.Member("m", "minecraft:iron_ingot"),
                new MarketData.Member("m", "minecraft:gold_ingot")));
        return data;
    }

    private static TradeFill fill(long tick, String item, double ratio, int qty, String owner) {
        return new TradeFill(tick, "m", item, "BUY", 10, qty, ratio, owner);
    }

    @Test void noTradesMeansNoValueAndNoCandles() {
        MarketData data = dataWithMembers();
        assertEquals(-1, data.value("sv.ore"));
        assertTrue(data.candles("sv.ore", DAY, 100).isEmpty());
    }

    @Test void fillMovesOnlyItsMemberAndOthersStayNeutral() {
        MarketData data = dataWithMembers();
        assertEquals(1, data.recordFill(fill(100, "minecraft:iron_ingot", 1.21, 2, "a"), 64));
        // sqrt(1.21 * 1.0) = 1.1
        assertEquals(1100, data.value("sv.ore"));
        int[] bar = data.candles("sv.ore", DAY, 100).get(0);
        assertArrayEquals(new int[]{0, 1100, 1100, 1100, 1100, 2, 2, 1}, bar);
    }

    @Test void nonMemberFillsOnlyReachTheTape() {
        MarketData data = dataWithMembers();
        assertEquals(0, data.recordFill(fill(100, "minecraft:dirt", 3.0, 1, "a"), 64));
        assertEquals(-1, data.value("sv.ore"));
        assertEquals(1, data.fills().size());
    }

    @Test void sameBucketBuildsOhlcAndCountsTraders() {
        MarketData data = dataWithMembers();
        data.recordFill(fill(10, "minecraft:iron_ingot", 1.0, 1, "a"), 64);
        data.recordFill(fill(20, "minecraft:iron_ingot", 1.44, 1, "b"), 64);
        data.recordFill(fill(30, "minecraft:iron_ingot", 0.81, 1, "a"), 64);
        int[] bar = data.candles("sv.ore", BAR, 100).get(0);
        assertArrayEquals(new int[]{0, 1000, 1200, 900, 900, 3, 3, 2}, bar);
    }

    @Test void ownerCapLimitsWeightNotVolume() {
        MarketData data = dataWithMembers();
        data.recordFill(fill(10, "minecraft:iron_ingot", 1.0, 50, "whale"), 64);
        data.recordFill(fill(11, "minecraft:iron_ingot", 1.0, 50, "whale"), 64);
        data.recordFill(fill(12, "minecraft:iron_ingot", 1.0, 5, "minnow"), 64);
        int[] bar = data.candles("sv.ore", BAR, 100).get(0);
        assertEquals(105, bar[5], "volume is raw");
        assertEquals(69, bar[6], "whale weight capped at 64");
    }

    @Test void quietPeriodsLeaveGapsInsteadOfFlatBars() {
        MarketData data = dataWithMembers();
        data.recordFill(fill(5 * BAR, "minecraft:iron_ingot", 1.0, 1, "a"), 64);
        data.recordFill(fill(9 * BAR, "minecraft:iron_ingot", 1.21, 1, "a"), 64);
        List<int[]> bars = data.candles("sv.ore", BAR, 100);
        assertEquals(2, bars.size());
        assertEquals(5, bars.get(0)[0]);
        assertEquals(9, bars.get(1)[0]);
    }

    @Test void ringIsBounded() {
        MarketData data = dataWithMembers();
        for (long bucket = 0; bucket < MarketData.RING + 50L; bucket++) {
            data.recordFill(fill(bucket * BAR, "minecraft:iron_ingot", 1.0, 1, "a"), 64);
        }
        assertEquals(MarketData.RING + 1, data.bars("sv.ore", BAR).size(), "ring plus the live bar");
        for (int i = 0; i < MarketData.FILL_RING; i++) data.recordFill(fill(0, "minecraft:dirt", 1.0, 1, "a"), 64);
        assertEquals(MarketData.FILL_RING, data.fills().size(), "trade tape is bounded");
    }

    @Test void windowAndRecentClose() {
        MarketData data = dataWithMembers();
        data.recordFill(fill(BAR, "minecraft:iron_ingot", 1.0, 1, "a"), 64);
        data.recordFill(fill(3 * BAR, "minecraft:iron_ingot", 1.21, 1, "b"), 64);
        assertEquals(1, data.window("sv.ore", 2L * BAR, 4L * BAR).size());
        assertEquals(1100, data.recentClose("sv.ore", 3L * BAR + 10, DAY));
        assertEquals(-1, data.recentClose("sv.ore", 3L * BAR + DAY + 1, DAY), "stale bars are not a reference");
    }

    @Test void saveLoadRoundTripsCandlesRatiosAndFills() {
        MarketData data = dataWithMembers();
        data.recordFill(fill(10, "minecraft:iron_ingot", 1.21, 2, "a"), 64);
        data.recordFill(fill(BAR + 1, "minecraft:gold_ingot", 0.81, 1, "b"), 64);
        MarketData restored = MarketData.load(data.save(new CompoundTag()));
        assertEquals("矿指", restored.name("sv.ore"));
        assertEquals(data.value("sv.ore"), restored.value("sv.ore"));
        assertEquals(data.candles("sv.ore", BAR, 100).size(), restored.candles("sv.ore", BAR, 100).size());
        assertArrayEquals(data.candles("sv.ore", DAY, 100).get(0), restored.candles("sv.ore", DAY, 100).get(0));
        assertEquals(2, restored.fills().size());
        restored.recordFill(fill(BAR + 2, "minecraft:iron_ingot", 1.0, 1, "a"), 64);
        assertEquals(900, restored.value("sv.ore"), "gold's ratio survived the reload: sqrt(1.0 * 0.81)");
    }

    @Test void legacyQuoteCandlesAreDiscardedButMembersKept() {
        CompoundTag legacy = new CompoundTag();
        CompoundTag indices = new CompoundTag();
        CompoundTag index = new CompoundTag();
        index.putString("name", "旧");
        ListTag members = new ListTag();
        CompoundTag member = new CompoundTag();
        member.putString("merchant", "m");
        member.putString("item", "minecraft:iron_ingot");
        members.add(member);
        index.put("members", members);
        CompoundTag periods = new CompoundTag();
        CompoundTag period = new CompoundTag();
        period.putLong("bucket", 7);
        period.putIntArray("current", new int[]{1000, 1000, 1000, 1000});
        period.putIntArray("ring", new int[]{1000, 1000, 1000, 1000});
        periods.put("24000", period);
        index.put("periods", periods);
        indices.put("old", index);
        legacy.put("indices", indices);
        MarketData loaded = MarketData.load(legacy);
        assertEquals(1, loaded.members("old").size());
        assertTrue(loaded.candles("old", DAY, 100).isEmpty());
        assertEquals(-1, loaded.value("old"));
    }

    @Test void redefiningKeepsRatiosOfRemainingMembers() {
        MarketData data = dataWithMembers();
        data.recordFill(fill(10, "minecraft:iron_ingot", 1.21, 1, "a"), 64);
        data.defineIndex("sv.ore", "矿指", List.of(new MarketData.Member("m", "minecraft:iron_ingot")));
        data.recordFill(fill(11, "minecraft:dirt", 2.0, 1, "a"), 64);
        data.recordFill(fill(12, "minecraft:iron_ingot", 1.21, 1, "a"), 64);
        assertEquals(1210, data.value("sv.ore"));
    }
}
