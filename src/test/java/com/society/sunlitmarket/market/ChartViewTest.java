package com.society.sunlitmarket.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartViewTest {

    private static ChartView sample() {
        ChartView view = new ChartView();
        view.id = "sv.produce";
        view.name = "夏尔物产";
        view.period = 24000;
        view.lastBucket = 421;
        view.current = 1043;
        ChartView.Entry entry = new ChartView.Entry();
        entry.id = "sv.produce";
        entry.name = "夏尔物产";
        entry.value = 1043;
        view.indices.add(entry);
        view.candles.add(new int[]{420, 1000, 1010, 995, 1004});
        view.candles.add(new int[]{421, 1004, 1050, 1001, 1043});
        return view;
    }

    @Test
    void roundTripsEveryField() {
        ChartView parsed = ChartView.fromJson(sample().toJson());
        assertEquals("sv.produce", parsed.id);
        assertEquals("夏尔物产", parsed.name);
        assertEquals(24000, parsed.period);
        assertEquals(421L, parsed.lastBucket);
        assertEquals(1043, parsed.current);
        assertEquals(1, parsed.indices.size());
        assertEquals(1043, parsed.indices.get(0).value);
        assertEquals(2, parsed.candles.size());
        assertEquals(420, parsed.candles.get(0)[0]);
        assertEquals(995, parsed.candles.get(0)[3]);
        assertEquals(1043, parsed.candles.get(1)[4]);
    }

    @Test
    void malformedPayloadDegradesToEmptyView() {
        ChartView parsed = ChartView.fromJson("not json at all");
        assertEquals("", parsed.id);
        assertTrue(parsed.candles.isEmpty());
        assertEquals(-1, parsed.current);
    }

    @Test
    void candlesWithMissingFieldsAreDropped() {
        String json = """
                {"id":"x","candles":[[1,2,3],[4,5,6,7,8]]}
                """;
        ChartView parsed = ChartView.fromJson(json);
        assertEquals(1, parsed.candles.size());
        assertEquals(8, parsed.candles.get(0)[4]);
    }

    @Test
    void emptyViewSerialisesWithoutCandles() {
        ChartView parsed = ChartView.fromJson(new ChartView().toJson());
        assertTrue(parsed.candles.isEmpty());
        assertTrue(parsed.indices.isEmpty());
    }
}
