package com.society.sunlitmarket.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ChartServiceTest {

    @Test
    void wireCandlesCarryTheirOwnBucketAndVolume() {
        Candle c = new Candle(421, 1000);
        c.add(1000, 2, "a", 64);
        c.add(1043, 1, "b", 64);
        assertArrayEquals(new int[]{421, 1000, 1043, 1000, 1043, 3, 3, 2}, c.wire());
    }

    @Test
    void eightWideCandlesSurviveTheClientParser() {
        ChartView view = new ChartView();
        view.id = "sv.ore";
        view.quote = 990;
        Candle c = new Candle(12, 1000);
        c.add(1004, 5, "a", 64);
        view.candles.add(c.wire());
        ChartView parsed = ChartView.fromJson(view.toJson());
        assertEquals(1, parsed.candles.size());
        assertEquals(12, parsed.candles.get(0)[0]);
        assertEquals(1004, parsed.candles.get(0)[4]);
        assertEquals(5, parsed.candles.get(0)[5]);
        assertEquals(1, parsed.candles.get(0)[7]);
        assertEquals(990, parsed.quote);
    }

    @Test
    void fiveWideCandlesFromOlderServersStillParse() {
        ChartView parsed = ChartView.fromJson("{\"id\":\"x\",\"candles\":[[3,1000,1010,995,1004]]}");
        assertEquals(1, parsed.candles.size());
        assertEquals(5, parsed.candles.get(0).length);
        assertEquals(-1, parsed.quote);
    }
}
