package com.society.sunlitmarket.market;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public final class Merchant {
    public String id = "";
    public String name = "Merchant";
    /** Reputation bucket, e.g. me:gondor or a custom id. */
    public String faction = "sunlitmarket:default";
    /** Optional Middle-earth NpcData.Type, e.g. me:shire.peasant. */
    public String npcType = "";
    /** Optional entity tag, e.g. sunlitmarket.shire_farmer. */
    public String tag = "";
    public List<Offer> offers = new ArrayList<>();

    public Merchant() {
    }

    public static Merchant fromJson(JsonObject o) {
        Merchant m = new Merchant();
        m.id = o.has("id") ? o.get("id").getAsString() : "";
        m.name = o.has("name") ? o.get("name").getAsString() : m.id;
        m.faction = o.has("faction") ? o.get("faction").getAsString() : "sunlitmarket:default";
        m.npcType = o.has("npcType") ? o.get("npcType").getAsString() : "";
        m.tag = o.has("tag") ? o.get("tag").getAsString() : "";
        if (o.has("offers") && o.get("offers").isJsonArray()) {
            JsonArray array = o.getAsJsonArray("offers");
            for (JsonElement e : array) {
                if (e.isJsonObject()) {
                    Offer offer = Offer.fromJson(e.getAsJsonObject());
                    if (!offer.valid()) throw new IllegalArgumentException("Invalid offer in " + m.id);
                    if (!offer.id.isBlank() && m.offers.stream().anyMatch(existing -> existing.id.equals(offer.id)))
                        throw new IllegalArgumentException("Duplicate offer id: " + offer.id);
                    m.offers.add(offer);
                }
            }
        }
        return m;
    }

    public boolean valid() {
        return id != null && !id.isBlank() && (!tag.isBlank() || !npcType.isBlank()) && !offers.isEmpty();
    }
}
