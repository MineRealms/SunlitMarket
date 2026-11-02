package com.society.sunlitmarket.market;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FillMathTest {
    private static Candle bar(long bucket, int close, long qty, String... owners) {
        Candle c = new Candle(bucket, close);
        for (String owner : owners) c.add(close, qty, owner, 64);
        return c;
    }

    @Test void emptyWindowIsNotCredible() {
        var s = FillMath.settle(List.of(), 2, 2, 0.2);
        assertFalse(s.credible());
        assertEquals("NO_TRADES", s.reason());
    }

    @Test void weightedAverageOfCloses() {
        var s = FillMath.settle(List.of(bar(1, 1000, 1, "a"), bar(2, 1100, 3, "b")), 2, 2, 0.2);
        assertTrue(s.credible());
        assertEquals(1075, s.price());
        assertEquals(2, s.traders());
    }

    @Test void oneTraderCannotSettleWhenTwoAreRequired() {
        var s = FillMath.settle(List.of(bar(1, 1000, 1, "a"), bar(2, 1100, 1, "a")), 2, 2, 0.2);
        assertFalse(s.credible());
        assertEquals("TOO_FEW_TRADERS", s.reason());
    }

    @Test void outlierBarIsDropped() {
        List<Candle> window = new ArrayList<>(List.of(bar(1, 1000, 1, "a"), bar(2, 1010, 1, "b"), bar(3, 990, 1, "c")));
        window.add(bar(4, 5000, 64, "whale"));
        var s = FillMath.settle(window, 2, 2, 0.2);
        assertTrue(s.credible());
        assertEquals(1000, s.price());
        assertEquals(3, s.buckets());
    }

    @Test void tooFewBarsAfterFiltering() {
        var s = FillMath.settle(List.of(bar(1, 1000, 1, "a", "b")), 2, 2, 0.2);
        assertFalse(s.credible());
        assertEquals("TOO_FEW_BUCKETS", s.reason());
    }

    @Test void ownerCapBoundsAWhalesWeight() {
        Candle whale = new Candle(1, 1200);
        whale.add(1200, 1000, "whale", 64);
        var s = FillMath.settle(List.of(bar(2, 1000, 64, "a"), whale), 2, 2, 0.3);
        assertEquals(1100, s.price(), "both bars weigh 64");
    }
}
