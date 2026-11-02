package com.society.sunlitmarket.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PoolDirectionTest {
    @AfterEach void clear() {
        MarketRules.clearScripts();
    }

    private static MarketRules.Pool only(String faction) {
        var pools = MarketRules.pools(faction).stream().filter(p -> p.id().startsWith("__dir")).toList();
        assertEquals(1, pools.size());
        return pools.get(0);
    }

    @Test void legacyPoolsOnlySell() {
        MarketRules.add("{\"type\":\"pool\",\"id\":\"__dir_legacy\",\"faction\":\"t:a\",\"item\":\"minecraft:apple\",\"baseValue\":5,\"marketValue\":7}");
        var pool = only("t:a");
        assertTrue(pool.sells());
        assertFalse(pool.buys());
        assertEquals(7, pool.bidPrice(), "bid defaults to the market value");
    }

    @Test void buyAndBothDirections() {
        MarketRules.add("{\"type\":\"pool\",\"id\":\"__dir_buy\",\"faction\":\"t:b\",\"item\":\"minecraft:apple\",\"baseValue\":5,\"direction\":\"buy\",\"bidPrice\":9}");
        var buy = only("t:b");
        assertTrue(buy.buys());
        assertFalse(buy.sells());
        assertEquals(9, buy.bidPrice());
        MarketRules.add("{\"type\":\"pool\",\"id\":\"__dir_both\",\"faction\":\"t:c\",\"item\":\"minecraft:apple\",\"baseValue\":5,\"direction\":\"both\"}");
        var both = only("t:c");
        assertTrue(both.buys());
        assertTrue(both.sells());
    }

    @Test void invalidDirectionOrBidIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> MarketRules.add(
                "{\"type\":\"pool\",\"id\":\"__dir_bad\",\"item\":\"minecraft:apple\",\"baseValue\":5,\"direction\":\"lend\"}"));
        assertThrows(IllegalArgumentException.class, () -> MarketRules.add(
                "{\"type\":\"pool\",\"id\":\"__dir_bad\",\"item\":\"minecraft:apple\",\"baseValue\":5,\"direction\":\"buy\",\"bidPrice\":0}"));
    }
}
