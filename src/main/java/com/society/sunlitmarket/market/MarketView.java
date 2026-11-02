package com.society.sunlitmarket.market;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

public final class MarketView {
    public String merchantId = "";
    public String merchantName = "";
    public String faction = "";
    public String session = "";
    public long sequence;
    public int balance;
    public int reputation;
    /** Alignment tier title for the interacting faction, e.g. 常客. */
    public String title = "";
    public List<Line> offers = new ArrayList<>();

    public static final class Line {
        public String offerId = "";
        public boolean hasReputation;
        public String item = "";
        public int count = 1;
        public int buy;
        public int sell;
        public int reputation;
        public int stock = -1;
        public int limit = -1;
        public int used;
        /** Unlock id required to trade this line; blank = none. */
        public String unlock = "";
        /** True when the player is missing {@link #unlock}. */
        public boolean locked;
    }

    private static final Gson GSON = new GsonBuilder().create();

    public String toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("merchantId", merchantId);
        o.addProperty("merchantName", merchantName);
        o.addProperty("faction", faction);
        o.addProperty("session", session);
        o.addProperty("sequence", sequence);
        o.addProperty("balance", balance);
        o.addProperty("reputation", reputation);
        o.addProperty("title", title);
        JsonArray array = new JsonArray();
        for (Line line : offers) {
            JsonObject f = new JsonObject();
            f.addProperty("offerId", line.offerId);
            f.addProperty("hasReputation", line.hasReputation);
            f.addProperty("item", line.item);
            f.addProperty("count", line.count);
            f.addProperty("buy", line.buy);
            f.addProperty("sell", line.sell);
            f.addProperty("reputation", line.reputation);
            f.addProperty("stock", line.stock);
            f.addProperty("limit", line.limit);
            f.addProperty("used", line.used);
            f.addProperty("unlock", line.unlock);
            f.addProperty("locked", line.locked);
            array.add(f);
        }
        o.add("offers", array);
        return GSON.toJson(o);
    }

    public static MarketView fromJson(String json) {
        MarketView view = new MarketView();
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return view;
            JsonObject o = root.getAsJsonObject();
            view.merchantId = o.has("merchantId") ? o.get("merchantId").getAsString() : "";
            view.merchantName = o.has("merchantName") ? o.get("merchantName").getAsString() : "";
            view.faction = o.has("faction") ? o.get("faction").getAsString() : "";
            view.session = o.has("session") ? o.get("session").getAsString() : "";
            view.sequence = o.has("sequence") ? o.get("sequence").getAsLong() : 0;
            view.balance = o.has("balance") ? o.get("balance").getAsInt() : 0;
            view.reputation = o.has("reputation") ? o.get("reputation").getAsInt() : 0;
            view.title = o.has("title") ? o.get("title").getAsString() : "";
            if (o.has("offers") && o.get("offers").isJsonArray()) {
                for (JsonElement e : o.getAsJsonArray("offers")) {
                    if (!e.isJsonObject()) continue;
                    JsonObject f = e.getAsJsonObject();
                    Line line = new Line();
                    line.offerId = f.has("offerId") ? f.get("offerId").getAsString() : "legacy:" + view.offers.size();
                    line.hasReputation = f.has("hasReputation") && f.get("hasReputation").getAsBoolean();
                    line.item = f.has("item") ? f.get("item").getAsString() : "";
                    line.count = f.has("count") ? f.get("count").getAsInt() : 1;
                    line.buy = f.has("buy") ? f.get("buy").getAsInt() : 0;
                    line.sell = f.has("sell") ? f.get("sell").getAsInt() : 0;
                    line.reputation = f.has("reputation") ? f.get("reputation").getAsInt() : 0;
                    line.stock = f.has("stock") ? f.get("stock").getAsInt() : -1;
                    line.limit = f.has("limit") ? f.get("limit").getAsInt() : -1;
                    line.used = f.has("used") ? f.get("used").getAsInt() : 0;
                    line.unlock = f.has("unlock") ? f.get("unlock").getAsString() : "";
                    line.locked = f.has("locked") && f.get("locked").getAsBoolean();
                    view.offers.add(line);
                }
            }
        } catch (Throwable ignored) {
        }
        return view;
    }
}
