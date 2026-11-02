package com.society.sunlitmarket.core;

import com.society.sunlitmarket.market.PriceEngine;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PriceEngineTest {
    private static final double E = 0.2D, MIN = 0.5D, MAX = 2.0D;

    @Test void initialStockIsNeutral() {
        assertEquals(1.0D, PriceEngine.scarcity(64, 64, E, MIN, MAX), 1e-9);
    }

    @Test void depletedShelfRaisesPricesToCap() {
        double full = Math.pow(64, E);
        assertEquals(Math.min(MAX, full), PriceEngine.scarcity(1, 64, E, MIN, MAX), 1e-9);
        assertEquals(MAX, PriceEngine.scarcity(1, 64, E, MIN, MAX));
    }

    @Test void overstockedShelfLowersPrices() {
        double expected = Math.pow(64 / 192.0D, E);
        assertEquals(expected, PriceEngine.scarcity(192, 64, E, MIN, MAX), 1e-9);
        assertTrue(PriceEngine.scarcity(192, 64, E, MIN, MAX) < 1.0D);
    }

    @Test void multiplierFallsMonotonicallyWithStock() {
        double previous = Double.MAX_VALUE;
        for (int stock = 1; stock <= 256; stock += 5) {
            double mult = PriceEngine.scarcity(stock, 64, E, 0.05D, 8D);
            assertTrue(mult <= previous);
            previous = mult;
        }
    }

    @Test void unlimitedShelvesAreNeutral() {
        assertEquals(1.0D, PriceEngine.scarcity(5, -1, E, MIN, MAX));
        assertEquals(1.0D, PriceEngine.scarcity(-1, 64, E, MIN, MAX));
        assertEquals(1.0D, PriceEngine.scarcity(5, 0, E, MIN, MAX));
    }

    @Test void degenerateInputsAreNeutral() {
        assertEquals(1.0D, PriceEngine.scarcity(64, 64, Double.NaN, MIN, MAX));
        assertEquals(1.0D, PriceEngine.scarcity(64, 64, 0.0D, MIN, MAX), 1e-9);
    }

    @Test void saleRestockIsCapped() {
        assertEquals(30, PriceEngine.afterSale(10, 10, 20, 3.0D));
        assertEquals(10, PriceEngine.afterSale(10, 10, 20, 1.0D));
        assertEquals(15, PriceEngine.afterSale(10, 10, 5, 3.0D));
    }

    @Test void unlimitedShelvesNeverRestock() {
        assertEquals(-1, PriceEngine.afterSale(-1, -1, 20, 3.0D));
        assertEquals(-1, PriceEngine.afterSale(-1, 64, 20, 3.0D));
    }
}
