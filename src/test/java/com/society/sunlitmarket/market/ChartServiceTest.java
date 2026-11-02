package com.society.sunlitmarket.market;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChartServiceTest {

    @Test
    void candlesGainABucketSoTheClientCanLabelTheAxis() {
        List<int[]> raw = List.of(
                new int[]{1000, 1010, 995, 1004},
                new int[]{1004, 1050, 1001, 1043});
        List<int[]> wire = ChartService.withBuckets(raw, 421L);
        assertEquals(2, wire.size());
        assertEquals(5, wire.get(0).length, "wire candles are {bucket, open, high, low, close}");
        assertEquals(420, wire.get(0)[0]);
        assertEquals(1000, wire.get(0)[1]);
        assertEquals(1043, wire.get(1)[4]);
        assertEquals(421, wire.get(1)[0]);
    }

    @Test
    void theNewestCandleSitsOnTheLastBucket() {
        List<int[]> wire = ChartService.withBuckets(List.of(new int[]{1, 2, 3, 4}), 7L);
        assertEquals(7, wire.get(0)[0]);
    }

    @Test
    void emptyInputStaysEmpty() {
        assertEquals(0, ChartService.withBuckets(List.of(), 3L).size());
    }

    @Test
    void wireCandlesSurviveTheClientParser() {
        ChartView view = new ChartView();
        view.id = "sv.ore";
        view.candles.addAll(ChartService.withBuckets(List.of(new int[]{1000, 1010, 995, 1004}), 12L));
        ChartView parsed = ChartView.fromJson(view.toJson());
        assertEquals(1, parsed.candles.size(), "a 4-wide candle would be dropped by ChartView.fromJson");
        assertEquals(12, parsed.candles.get(0)[0]);
        assertEquals(1004, parsed.candles.get(0)[4]);
    }
}
