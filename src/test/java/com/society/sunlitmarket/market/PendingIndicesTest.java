package com.society.sunlitmarket.market;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PendingIndicesTest {

    @AfterEach
    void reset() {
        PendingIndices.clear();
    }

    private static List<MarketData.Member> members(String item) {
        return List.of(new MarketData.Member("m", item));
    }

    @Test
    void keepsOneEntryPerId() {
        PendingIndices.put("sv.ore", "矿指", members("minecraft:raw_iron"));
        PendingIndices.put("sv.ore", "矿指2", members("minecraft:raw_gold"));
        assertEquals(1, PendingIndices.size());
    }

    @Test
    void ignoresBlankOrEmptyDefinitions() {
        PendingIndices.put("", "x", members("minecraft:raw_iron"));
        PendingIndices.put("sv.ore", "x", List.of());
        assertEquals(0, PendingIndices.size());
    }

    @Test
    void applyingWithoutAServerIsANoOp() {
        PendingIndices.put("sv.ore", "矿指", members("minecraft:raw_iron"));
        assertEquals(0, PendingIndices.applyTo(null));
        assertEquals(1, PendingIndices.size());
    }

    @Test
    void clearDropsEverything() {
        PendingIndices.put("sv.ore", "矿指", members("minecraft:raw_iron"));
        PendingIndices.clear();
        assertEquals(0, PendingIndices.size());
    }
}
