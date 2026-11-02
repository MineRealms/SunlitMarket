package com.society.sunlitmarket.market;

import com.google.gson.JsonObject;

/** One trade line. Prices are in "spur" units (Numismatics base value). */
public final class Offer {
    public String id = "";
    /** Explicit former zero-based offer index used once to migrate stock and purchase limits. */
    public int legacyIndex = -1;
    public String kind = "stock";
    public boolean hasReputation;
    public boolean fixedPrice;
    public boolean allowArbitrage;
    public int baseValue, marketValue, askPrice, heat;
    public String nbt = "";
    public String item;
    public int count = 1;
    /** Price the player pays to receive {@link #count} items. 0 = not sold by merchant. */
    public int buy;
    /** Price the merchant pays for {@link #count} items. 0 = not bought by merchant. */
    public int sell;
    /** Minimum independent reputation with the merchant faction. */
    public int reputation;
    /** Stock for one restock cycle. -1 = unlimited. Resets at the next in-game day. */
    public int stock = -1;
    /** Per-player purchase limit per in-game day. -1 = unlimited. */
    public int limit = -1;
    /** Optional per-player unlock id (quest stage). Blank = always available. */
    public String unlock = "";
    /** Reputation granted with this faction per completed trade. 0 = none. */
    public int repGain = 0;

    public Offer() {
    }

    public static Offer fromJson(JsonObject o) {
        Offer offer = new Offer();
        offer.id = o.has("id") ? o.get("id").getAsString() : "";
        offer.legacyIndex = o.has("legacyIndex") ? o.get("legacyIndex").getAsInt() : -1;
        offer.kind = o.has("kind") ? o.get("kind").getAsString() : "stock";
        offer.hasReputation = o.has("reputation");
        offer.fixedPrice = o.has("fixedPrice") && o.get("fixedPrice").getAsBoolean();
        offer.allowArbitrage = o.has("allowArbitrage") && o.get("allowArbitrage").getAsBoolean();
        offer.item = o.has("item") ? o.get("item").getAsString() : "";
        offer.count = Math.max(1, o.has("count") ? o.get("count").getAsInt() : 1);
        offer.baseValue = o.has("baseValue") ? o.get("baseValue").getAsInt() : 0;
        offer.marketValue = o.has("marketValue") ? o.get("marketValue").getAsInt() : offer.baseValue;
        offer.askPrice = o.has("askPrice") ? o.get("askPrice").getAsInt() : offer.marketValue;
        offer.heat = o.has("heat") ? o.get("heat").getAsInt() : 0;
        offer.nbt = o.has("nbt") ? (o.get("nbt").isJsonPrimitive() ? o.get("nbt").getAsString() : o.get("nbt").toString()) : "";
        offer.buy = Math.max(0, o.has("buy") ? o.get("buy").getAsInt() : 0);
        offer.sell = Math.max(0, o.has("sell") ? o.get("sell").getAsInt() : 0);
        offer.reputation = o.has("reputation") ? o.get("reputation").getAsInt() : 0;
        offer.stock = o.has("stock") ? o.get("stock").getAsInt() : -1;
        offer.limit = o.has("limit") ? o.get("limit").getAsInt() : -1;
        offer.unlock = o.has("unlock") ? o.get("unlock").getAsString() : "";
        offer.repGain = Math.max(0, o.has("repGain") ? o.get("repGain").getAsInt() : 0);
        return offer;
    }

    public boolean valid() {
        return legacyIndex >= -1 && item != null && item.matches("(?:[a-z0-9_.-]+:)?[a-z0-9/._-]+") && count > 0 && count <= 64
                && (kind.equals("stock") && (buy > 0 || sell > 0) || kind.equals("offer") && baseValue > 0 && marketValue > 0 && askPrice > 0);
    }
}
