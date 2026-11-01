package com.society.sunlitmarket.market;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.society.sunlitmarket.SunlitMarketMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON merchants from config plus script merchants registered through KubeJS. */
public final class MarketRegistry {
    private static final Gson GSON = new GsonBuilder().setLenient().create();
    private static final Map<String, Merchant> JSON = new LinkedHashMap<>();
    private static final Map<String, Merchant> SCRIPTS = new LinkedHashMap<>();

    private MarketRegistry() {
    }

    public static Path configPath() {
        return FMLPaths.CONFIGDIR.get().resolve("sunlitmarket").resolve("merchants.json");
    }

    public static synchronized void loadJson() {
        JSON.clear();
        Path path = configPath();
        if (!Files.isRegularFile(path)) return;
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            JsonElement root = JsonParser.parseString(text);
            JsonArray array;
            if (root.isJsonArray()) {
                array = root.getAsJsonArray();
            } else if (root.isJsonObject() && root.getAsJsonObject().has("merchants")) {
                array = root.getAsJsonObject().getAsJsonArray("merchants");
            } else {
                return;
            }
            for (JsonElement element : array) {
                if (!element.isJsonObject()) continue;
                Merchant merchant = Merchant.fromJson(element.getAsJsonObject());
                if (merchant.valid()) JSON.put(merchant.id, merchant);
            }
            SunlitMarketMod.LOG.info("[SunlitMarket] loaded {} merchants from config", JSON.size());
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] failed to load config merchants", t);
        }
    }

    public static synchronized void addScriptMerchant(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return;
            Merchant merchant = Merchant.fromJson(root.getAsJsonObject());
            if (merchant.valid()) SCRIPTS.put(merchant.id, merchant);
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] bad script merchant JSON", t);
        }
    }

    public static synchronized void addScriptOffer(String merchantId, String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return;
            Offer offer = Offer.fromJson(root.getAsJsonObject());
            if (!offer.valid()) return;
            Merchant merchant = SCRIPTS.get(merchantId);
            if (merchant == null) {
                merchant = new Merchant();
                merchant.id = merchantId;
                merchant.name = merchantId;
                merchant.faction = "sunlitmarket:default";
                SCRIPTS.put(merchantId, merchant);
            }
            merchant.offers.add(offer);
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] bad script offer JSON", t);
        }
    }

    public static synchronized void clearScript() {
        SCRIPTS.clear();
    }

    public static synchronized List<Merchant> all() {
        List<Merchant> list = new ArrayList<>(JSON.values());
        for (Merchant script : SCRIPTS.values()) {
            list.removeIf(m -> m.id.equals(script.id));
            list.add(script);
        }
        list.sort(Comparator.comparing(m -> m.id));
        return list;
    }

    public static synchronized Merchant get(String id) {
        Merchant script = SCRIPTS.get(id);
        return script != null ? script : JSON.get(id);
    }

    /** Resolve by entity tag first, then by Middle-earth NpcData.Type. */
    public static synchronized Merchant resolve(Entity entity) {
        java.util.Set<String> tags = entity.getTags();
        for (Merchant merchant : all()) {
            if (!merchant.tag.isBlank() && tags.contains(merchant.tag)) return merchant;
        }
        String npcType = readNpcType(entity);
        if (!npcType.isBlank()) {
            for (Merchant merchant : all()) {
                if (npcType.equals(merchant.npcType)) return merchant;
            }
        }
        String explicit = entity.getPersistentData().getString("sunlitmarket.merchant");
        if (!explicit.isBlank()) return get(explicit);
        return null;
    }

    /** Reads ME's NpcData.Type without linking against the Connector-remapped mod. */
    public static String readNpcType(Entity entity) {
        try {
            CompoundTag root = entity.serializeNBT();
            if (!root.contains("NpcData", Tag.TAG_COMPOUND)) return "";
            CompoundTag data = root.getCompound("NpcData");
            if (data.contains("Type", Tag.TAG_STRING)) return data.getString("Type");
            if (data.contains("Type")) return data.get("Type").getAsString();
        } catch (Throwable ignored) {
        }
        return "";
    }

    public static String toJson(List<Merchant> merchants) {
        JsonArray array = new JsonArray();
        for (Merchant merchant : merchants) {
            JsonObject o = new JsonObject();
            o.addProperty("id", merchant.id);
            o.addProperty("name", merchant.name);
            o.addProperty("faction", merchant.faction);
            o.addProperty("npcType", merchant.npcType);
            o.addProperty("tag", merchant.tag);
            JsonArray offers = new JsonArray();
            for (Offer offer : merchant.offers) {
                JsonObject f = new JsonObject();
                f.addProperty("item", offer.item);
                f.addProperty("count", offer.count);
                f.addProperty("buy", offer.buy);
                f.addProperty("sell", offer.sell);
                f.addProperty("reputation", offer.reputation);
                f.addProperty("stock", offer.stock);
                f.addProperty("limit", offer.limit);
                f.addProperty("unlock", offer.unlock == null ? "" : offer.unlock);
                f.addProperty("repGain", offer.repGain);
                offers.add(f);
            }
            o.add("offers", offers);
            array.add(o);
        }
        return GSON.toJson(array);
    }
}
