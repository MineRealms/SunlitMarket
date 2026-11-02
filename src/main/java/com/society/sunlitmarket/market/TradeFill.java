package com.society.sunlitmarket.market;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;

/**
 * One executed shelf trade. {@code unitPrice} is what the player actually paid or received per
 * lot; {@code ratio} is the impersonal part of that price (price modifiers x shelf scarcity, with
 * the player's own alignment discount and wanted surcharge removed), which is what indices track.
 */
public record TradeFill(long tick, String merchant, String item, String side, int unitPrice, int quantity,
                        double ratio, String owner) {

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("tick", tick);
        t.putString("merchant", merchant);
        t.putString("item", item);
        t.putString("side", side);
        t.putInt("unit", unitPrice);
        t.putInt("qty", quantity);
        t.putDouble("ratio", ratio);
        t.putString("owner", owner);
        return t;
    }

    public static TradeFill load(CompoundTag t) {
        return new TradeFill(t.getLong("tick"), t.getString("merchant"), t.getString("item"), t.getString("side"),
                t.getInt("unit"), t.getInt("qty"), t.getDouble("ratio"), t.getString("owner"));
    }

    public JsonObject json() {
        JsonObject o = new JsonObject();
        o.addProperty("tick", tick);
        o.addProperty("merchant", merchant);
        o.addProperty("item", item);
        o.addProperty("side", side);
        o.addProperty("unitPrice", unitPrice);
        o.addProperty("quantity", quantity);
        o.addProperty("ratio", ratio);
        o.addProperty("owner", owner);
        return o;
    }
}
