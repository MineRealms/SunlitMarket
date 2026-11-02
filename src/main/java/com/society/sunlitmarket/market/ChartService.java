package com.society.sunlitmarket.market;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/** Builds the chart payload. Server side only; never references a client class. */
public final class ChartService {
    /** Most candles one reply may carry; the client only draws about 120 anyway. */
    public static final int MAX_CANDLES = 120;

    private ChartService() {
    }

    public static ChartView view(MinecraftServer server, String requestedId, int requestedPeriod) {
        ChartView view = new ChartView();
        if (server == null) return view;
        MarketData data = MarketData.get(server);
        List<String> ids = data.ids();
        for (String key : ids) {
            ChartView.Entry entry = new ChartView.Entry();
            entry.id = key;
            entry.name = data.name(key);
            entry.value = data.value(key);
            view.indices.add(entry);
        }
        int period = requestedPeriod == MarketData.PERIODS[1] ? MarketData.PERIODS[1] : MarketData.PERIODS[0];
        String id = requestedId == null ? "" : requestedId.trim();
        if (id.isEmpty() || !ids.contains(id)) {
            id = ids.isEmpty() ? "" : ids.get(0);
        }
        view.id = id;
        view.name = id.isEmpty() ? "" : data.name(id);
        view.period = period;
        if (id.isEmpty()) return view;
        view.lastBucket = data.lastBucket(id, period);
        view.current = data.value(id);
        view.candles.addAll(withBuckets(data.candles(id, period, MAX_CANDLES), view.lastBucket));
        return view;
    }

    /**
     * MarketData stores bare OHLC quads; the wire format is {bucket, open, high, low, close} so the
     * client can label the x axis without knowing the sampling rule. The newest candle sits at
     * {@code lastBucket}.
     */
    static List<int[]> withBuckets(List<int[]> raw, long lastBucket) {
        List<int[]> result = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            int[] candle = raw.get(i);
            long bucket = lastBucket - (raw.size() - 1 - i);
            result.add(new int[]{(int) bucket, candle[0], candle[1], candle[2], candle[3]});
        }
        return result;
    }

    public static String json(MinecraftServer server, String id, int period) {
        return view(server, id, period).toJson();
    }
}
