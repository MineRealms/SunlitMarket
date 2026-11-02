package com.society.sunlitmarket.market;

import net.minecraft.server.MinecraftServer;

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
        view.current = data.value(id);
        try {
            view.quote = data.quoteReference(id, server);
        } catch (RuntimeException ex) {
            view.quote = -1;
        }
        // Candles carry their own bucket: periods without trades have no bar, so positions are not contiguous.
        view.candles.addAll(data.candles(id, period, MAX_CANDLES));
        view.lastBucket = view.candles.isEmpty() ? 0L : view.candles.get(view.candles.size() - 1)[0];
        return view;
    }

    public static String json(MinecraftServer server, String id, int period) {
        return view(server, id, period).toJson();
    }
}
