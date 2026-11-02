package com.society.sunlitmarket.market;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IndexMathTest {

    @Test void flatRatiosStartAt1000() {
        assertEquals(1000.0D, IndexMath.value(new double[]{1, 1, 1, 1}), 1e-9);
    }

    @Test void doublingMemberDoublesSingleMemberIndex() {
        assertEquals(2000.0D, IndexMath.value(new double[]{2.0D}), 1e-9);
    }

    @Test void doublingAndHalvingCancel() {
        assertEquals(1000.0D, IndexMath.value(new double[]{2.0D, 0.5D}), 1e-9);
    }

    @Test void geometricMeanIsNotArithmeticMean() {
        // Arithmetic mean of (2, 0.125) is 1.06; geometric is 0.5.
        assertEquals(500.0D, IndexMath.value(new double[]{2.0D, 0.125D}), 1e-9);
    }

    @Test void extremeRatiosAreClamped() {
        assertEquals(1000.0D * Math.exp(Math.log(20.0D)), IndexMath.value(new double[]{1000.0D}), 1e-6);
        assertEquals(1000.0D * Math.exp(Math.log(0.05D)), IndexMath.value(new double[]{0.0001D}), 1e-6);
    }

    @Test void rejectsEmptyAndInvalid() {
        assertThrows(IllegalArgumentException.class, () -> IndexMath.value(new double[0]));
        assertThrows(IllegalArgumentException.class, () -> IndexMath.value(new double[]{0}));
        assertThrows(IllegalArgumentException.class, () -> IndexMath.value(new double[]{-1}));
        assertThrows(IllegalArgumentException.class, () -> IndexMath.value(null));
    }

    @Test void candleFoldsHighLowClose() {
        int[] candle = IndexMath.seedCandle(10);
        IndexMath.updateCandle(candle, 12);
        IndexMath.updateCandle(candle, 8);
        IndexMath.updateCandle(candle, 11);
        assertArrayEquals(new int[]{10, 12, 8, 11}, candle);
    }
}
