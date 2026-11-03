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
    private static Map<String, Merchant> staging;
    private static boolean stagingFailed;
    public static synchronized void beginScripts() { staging = new LinkedHashMap<>(); stagingFailed = false; }
    /** True while a script load is between beginScripts() and endScripts(). */
    public static synchronized boolean stagingOpen() { return staging != null; }
    public static synchronized void endScripts(boolean valid) {
        if (staging != null && valid && !stagingFailed) { SCRIPTS.clear(); SCRIPTS.putAll(staging); }
        staging = null; changed();
    }
    private static Map<String,Merchant> scriptTarget() { return staging == null ? SCRIPTS : staging; }

    private MarketRegistry() {
    }

    public static Path configPath() {
        return FMLPaths.CONFIGDIR.get().resolve("sunlitmarket").resolve("merchants.json");
    }

    private static long revision;
    public static synchronized long version() { return revision; }
    public static synchronized void changed() { revision++; TradeSessions.clear(); }

    public static synchronized void loadJson() {
        Path path = configPath();
        Map<String, Merchant> candidate = new LinkedHashMap<>();
        try {
            if (Files.isRegularFile(path)) {
                JsonElement root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
                JsonArray array = root.isJsonArray() ? root.getAsJsonArray() : root.getAsJsonObject().getAsJsonArray("merchants");
                if (array == null) throw new IllegalArgumentException("Expected merchants array");
                for (JsonElement element : array) {
                    Merchant merchant = Merchant.fromJson(element.getAsJsonObject());
                    if (!merchant.valid() || candidate.putIfAbsent(merchant.id, merchant) != null)
                        throw new IllegalArgumentException("Invalid/duplicate merchant " + merchant.id);
                }
            }
            JSON.clear(); JSON.putAll(candidate); changed();
            SunlitMarketMod.LOG.info("[SunlitMarket] loaded {} merchants from config", JSON.size());
        } catch (Exception t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] invalid merchant reload; previous snapshot retained", t);
        }
    }

    public static synchronized void addScriptMerchant(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return;
            Merchant merchant = Merchant.fromJson(root.getAsJsonObject());
            if (!merchant.valid()) throw new IllegalArgumentException("Invalid script merchant");
            scriptTarget().put(merchant.id, merchant); if (staging == null) changed();
        } catch (Throwable t) {
            stagingFailed = true;
            SunlitMarketMod.LOG.warn("[SunlitMarket] bad script merchant JSON", t);
        }
    }

    public static synchronized void addScriptOffer(String merchantId, String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return;
            Offer offer = Offer.fromJson(root.getAsJsonObject());
            if (!offer.valid()) return;
            Merchant merchant = scriptTarget().get(merchantId);
            if (merchant == null) {
                merchant = new Merchant();
                merchant.id = merchantId;
                merchant.name = merchantId;
                merchant.faction = "sunlitmarket:default";
                scriptTarget().put(merchantId, merchant);
            }
            merchant.offers.add(offer); if (staging == null) changed();
        } catch (Throwable t) {
            stagingFailed = true;
            SunlitMarketMod.LOG.warn("[SunlitMarket] bad script offer JSON", t);
        }
    }

    public static synchronized void clearScript() {
        scriptTarget().clear(); if (staging == null) changed();
    }

    public static synchronized void removeScript(String id) { SCRIPTS.remove(id); changed(); }

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
        String npcKey = readNpcKey(entity);
        if (!npcKey.isBlank()) {
            for (Merchant merchant : all()) {
                if (npcKey.equals(merchant.npcKey)) return merchant;
            }
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

    private static final java.util.regex.Pattern NPC_KEY =
            java.util.regex.Pattern.compile("dialog\\.npc\\.([A-Za-z0-9_]+)\\.name");

    /** Dialog name key of an entity (banker, blacksmith, ...), empty when its name carries none. */
    public static String readNpcKey(Entity entity) {
        try {
            net.minecraft.network.chat.Component name = entity.getCustomName();
            if (name == null) return "";
            return npcKeyFromJson(net.minecraft.network.chat.Component.Serializer.toJson(name));
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** Pulls the id out of a serialised name component; works for translate and plain text names. */
    public static String npcKeyFromJson(String json) {
        if (json == null || json.isEmpty()) return "";
        java.util.regex.Matcher matcher = NPC_KEY.matcher(json);
        return matcher.find() ? matcher.group(1) : "";
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
            o.addProperty("npcKey", merchant.npcKey);
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
