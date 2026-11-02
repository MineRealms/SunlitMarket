package com.society.sunlitmarket.market;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Wire payload for the read-only price chart. Lives in {@code market} (not {@code client}) so a
 * dedicated server can build and serialise it without ever loading a client class.
 *
 * <p>Candles are {@code {bucket, open, high, low, close}} with {@code bucket = dayTime / period},
 * so the client can label the x axis without a second round trip.
 */
public final class ChartView {
    public String id = "";
    public String name = "";
    public int period = 24000;
    /** Bucket of the newest candle, so {@code bucket} values can be turned into day numbers. */
    public long lastBucket;
    public int current = -1;
    public List<Entry> indices = new ArrayList<>();
    public List<int[]> candles = new ArrayList<>();

    public static final class Entry {
        public String id = "";
        public String name = "";
        public int value = -1;
    }

    private static final Gson GSON = new GsonBuilder().create();

    public String toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("name", name);
        o.addProperty("period", period);
        o.addProperty("lastBucket", lastBucket);
        o.addProperty("current", current);
        JsonArray list = new JsonArray();
        for (Entry entry : indices) {
            JsonObject e = new JsonObject();
            e.addProperty("id", entry.id);
            e.addProperty("name", entry.name);
            e.addProperty("value", entry.value);
            list.add(e);
        }
        o.add("indices", list);
        JsonArray array = new JsonArray();
        for (int[] candle : candles) {
            JsonArray c = new JsonArray();
            for (int value : candle) {
                c.add(value);
            }
            array.add(c);
        }
        o.add("candles", array);
        return GSON.toJson(o);
    }

    public static ChartView fromJson(String json) {
        ChartView view = new ChartView();
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return view;
            JsonObject o = root.getAsJsonObject();
            view.id = o.has("id") ? o.get("id").getAsString() : "";
            view.name = o.has("name") ? o.get("name").getAsString() : "";
            view.period = o.has("period") ? o.get("period").getAsInt() : 24000;
            view.lastBucket = o.has("lastBucket") ? o.get("lastBucket").getAsLong() : 0L;
            view.current = o.has("current") ? o.get("current").getAsInt() : -1;
            if (o.has("indices") && o.get("indices").isJsonArray()) {
                for (JsonElement e : o.getAsJsonArray("indices")) {
                    if (!e.isJsonObject()) continue;
                    JsonObject f = e.getAsJsonObject();
                    Entry entry = new Entry();
                    entry.id = f.has("id") ? f.get("id").getAsString() : "";
                    entry.name = f.has("name") ? f.get("name").getAsString() : entry.id;
                    entry.value = f.has("value") ? f.get("value").getAsInt() : -1;
                    view.indices.add(entry);
                }
            }
            if (o.has("candles") && o.get("candles").isJsonArray()) {
                for (JsonElement e : o.getAsJsonArray("candles")) {
                    if (!e.isJsonArray()) continue;
                    JsonArray a = e.getAsJsonArray();
                    if (a.size() < 5) continue;
                    int[] candle = new int[5];
                    for (int i = 0; i < 5; i++) {
                        candle[i] = a.get(i).getAsInt();
                    }
                    view.candles.add(candle);
                }
            }
        } catch (Throwable ignored) {
        }
        return view;
    }
}
