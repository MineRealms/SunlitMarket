package com.society.sunlitmarket.alignment;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.society.sunlitmarket.SunlitMarketMod;
import com.society.sunlitmarket.config.SunlitMarketConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Middle-earth's faction table, read straight out of the data pack.
 *
 * <p>Nothing here links against {@code net.jukoz.me}; the mod only publishes JSON under
 * {@code data/me/me/faction/}. Reading it means faction ids and the hostile graph stay in
 * sync with whatever Middle-earth version the pack ships.</p>
 */
public final class FactionRelations {
    private static final Set<String> FACTIONS = new HashSet<>();
    private static final Map<String, Set<String>> HOSTILE = new HashMap<>();
    private static final Map<String, String> PARENT = new HashMap<>();
    private static volatile boolean loaded;

    private FactionRelations() {
    }

    public static void reload(MinecraftServer server) {
        FACTIONS.clear();
        HOSTILE.clear();
        PARENT.clear();
        loaded = true;
        if (!SunlitMarketConfig.USE_ME_FACTION_TABLE.get()) {
            SunlitMarketMod.LOG.info("[SunlitMarket] faction table disabled in config");
            return;
        }
        try {
            Map<ResourceLocation, Resource> files = server.getResourceManager()
                    .listResources("me/faction", loc -> loc.getPath().endsWith(".json"));
            for (Map.Entry<ResourceLocation, Resource> file : files.entrySet()) {
                readOne(file.getKey(), file.getValue());
            }
            symmetrize();
            SunlitMarketMod.LOG.info("[SunlitMarket] faction table: {} factions, {} with hostile entries",
                    FACTIONS.size(), HOSTILE.size());
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] could not read the Middle-earth faction table", t);
        }
    }

    private static void readOne(ResourceLocation id, Resource resource) {
        try (InputStreamReader reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) return;
            JsonObject obj = root.getAsJsonObject();
            String self = obj.has("id") && obj.get("id").isJsonPrimitive()
                    ? obj.get("id").getAsString()
                    : id.getNamespace() + ":" + id.getPath().substring("me/faction/".length(), id.getPath().length() - ".json".length());
            FACTIONS.add(self);
            if (obj.has("subFactions") && obj.get("subFactions").isJsonArray()) {
                for (JsonElement sub : obj.getAsJsonArray("subFactions")) {
                    if (sub.isJsonPrimitive()) PARENT.put(sub.getAsString(), self);
                }
            }
            if (!obj.has("initial_diplomacy") || !obj.get("initial_diplomacy").isJsonArray()) return;
            JsonArray diplomacy = obj.getAsJsonArray("initial_diplomacy");
            for (JsonElement entry : diplomacy) {
                if (!entry.isJsonObject()) continue;
                JsonObject rel = entry.getAsJsonObject();
                if (!rel.has("affinity") || !rel.has("faction")) continue;
                String affinity = rel.get("affinity").getAsString();
                String other = rel.get("faction").getAsString();
                FACTIONS.add(other);
                if ("HOSTILE".equalsIgnoreCase(affinity)) {
                    HOSTILE.computeIfAbsent(self, k -> new HashSet<>()).add(other);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** Middle-earth only declares one direction; make the graph undirected. */
    private static void symmetrize() {
        List<String> keys = new ArrayList<>(HOSTILE.keySet());
        for (String a : keys) {
            for (String b : new ArrayList<>(HOSTILE.getOrDefault(a, Collections.emptySet()))) {
                HOSTILE.computeIfAbsent(b, k -> new HashSet<>()).add(a);
            }
        }
    }

    public static Set<String> hostilesOf(String faction) {
        if (faction == null) return Collections.emptySet();
        return HOSTILE.getOrDefault(faction, Collections.emptySet());
    }

    public static boolean isHostile(String a, String b) {
        return hostilesOf(a).contains(b);
    }

    public static Set<String> factions() {
        return Collections.unmodifiableSet(FACTIONS);
    }

    public static String parentOf(String faction) {
        return PARENT.getOrDefault(faction, "");
    }

    public static boolean isLoaded() {
        return loaded;
    }

    /**
     * Resolves an ME npc type such as {@code me:longbeards.erebor.miner} to its faction
     * ({@code me:longbeards.erebor}) by longest prefix match, so sub-factions win over
     * their parents.
     */
    public static String factionOfNpcType(String npcType) {
        if (npcType == null || npcType.isBlank()) return "";
        String best = "";
        for (String faction : FACTIONS) {
            if (faction.isEmpty() || faction.equals(npcType)) continue;
            if (npcType.startsWith(faction + ".") && faction.length() > best.length()) {
                best = faction;
            }
        }
        return best;
    }
}
