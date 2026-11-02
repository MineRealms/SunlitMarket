package com.society.sunlitmarket.alignment;

import com.google.gson.*;
import com.society.sunlitmarket.SunlitMarketMod;
import com.society.sunlitmarket.config.CoreConfig;
import com.society.sunlitmarket.config.SunlitMarketConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Atomically published resource data; never links to Connector-remapped ME classes. */
public final class FactionRelations {
    public record Identity(String faction, String rank) { public boolean civilian() { return "CIVILIAN".equals(rank); } }
    public record Region(String faction, String dimension, double x, double z, double radius) {}
    private record Snapshot(Set<String> factions, Map<String,Set<String>> hostile, Map<String,String> parent,
                            Map<String,Identity> types, List<Region> regions) {}
    private static volatile Snapshot data = new Snapshot(Set.of(),Map.of(),Map.of(),Map.of(),List.of());
    private static boolean loaded;
    private FactionRelations() {}
    public static void reload(MinecraftServer server) { reload(server.getResourceManager()); }
    public static void reload(ResourceManager manager) {
        Set<String> factions = new HashSet<>(); Map<String,Set<String>> hostile = new HashMap<>();
        Map<String,String> parent = new HashMap<>(); Map<String,Identity> types = new HashMap<>(); List<Region> regions = new ArrayList<>();
        try {
            Map<ResourceLocation,net.minecraft.server.packs.resources.Resource> files = new LinkedHashMap<>();
            if (SunlitMarketConfig.USE_ME_FACTION_TABLE.get()) files.putAll(manager.listResources("me/faction", id -> id.getNamespace().equals("me") && id.getPath().endsWith(".json")));
            files.putAll(manager.listResources("sunlitmarket/factions", id -> id.getPath().endsWith(".json")));
            for (var file : files.entrySet()) {
                try (var reader = new InputStreamReader(file.getValue().open(), StandardCharsets.UTF_8)) {
                    JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
                    String self = o.get("id").getAsString(); factions.add(self);
                    if (o.has("parent_faction")) parent.put(self,o.get("parent_faction").getAsString());
                    if (o.has("subfaction")) for (JsonElement sub : o.getAsJsonArray("subfaction")) parent.put(sub.getAsString(),self);
                    if (o.has("initial_diplomacy")) for (JsonElement raw : o.getAsJsonArray("initial_diplomacy")) {
                        JsonObject rel = raw.getAsJsonObject(); String other = rel.get("faction").getAsString(); factions.add(other);
                        if ("HOSTILE".equalsIgnoreCase(rel.get("affinity").getAsString())) {
                            hostile.computeIfAbsent(self,k->new HashSet<>()).add(other);
                            hostile.computeIfAbsent(other,k->new HashSet<>()).add(self);
                        }
                    }
                    if (o.has("npcs") && o.getAsJsonObject("npcs").has("ranks"))
                        for (JsonElement raw : o.getAsJsonObject("npcs").getAsJsonArray("ranks")) {
                            JsonObject rank = raw.getAsJsonObject();
                            for (JsonElement type : rank.getAsJsonArray("pool")) {
                                Identity incoming = new Identity(self,rank.get("rank").getAsString());
                                types.merge(type.getAsString(),incoming,(a,b) -> a.civilian() == b.civilian() ? a : new Identity(self,"UNKNOWN"));
                            }
                        }
                    String dimension = o.has("dimension") ? o.get("dimension").getAsString()
                            : file.getKey().getNamespace().equals("me") ? "me:middle-earth" : "";
                    if (!dimension.isBlank() && o.has("spawns")) for (JsonElement raw : o.getAsJsonObject("spawns").getAsJsonArray("data")) {
                        JsonObject c = raw.getAsJsonObject().getAsJsonObject("coordinates");
                        regions.add(new Region(self,dimension,c.get("x").getAsDouble(),c.get("z").getAsDouble(),CoreConfig.REGION_RADIUS.get()));
                    }
                }
            }
            Map<String,Set<String>> immutable = new HashMap<>(); hostile.forEach((k,v)->immutable.put(k,Set.copyOf(v)));
            data = new Snapshot(Set.copyOf(factions),Map.copyOf(immutable),Map.copyOf(parent),Map.copyOf(types),List.copyOf(regions));
            loaded = true;
            SunlitMarketMod.LOG.info("[SunlitMarket] faction snapshot: {} factions, {} NPC types, {} regions", factions.size(),types.size(),regions.size());
        } catch (Exception ex) { SunlitMarketMod.LOG.error("[SunlitMarket] Invalid faction reload; previous snapshot retained",ex); }
    }
    public static Set<String> hostilesOf(String f) { return data.hostile.getOrDefault(f,Set.of()); }
    public static boolean isHostile(String a,String b) { return hostilesOf(a).contains(b); }
    public static Set<String> factions() { return data.factions; }
    public static String parentOf(String f) { return data.parent.getOrDefault(f,""); }
    public static boolean isLoaded() { return loaded; }
    public static String factionOfNpcType(String type) {
        if (type == null) return "";
        Identity known = data.types.get(type); if (known != null) return known.faction;
        String best = "";
        for (String f : data.factions) if (type.startsWith(f + ".") && f.length() > best.length()) best = f;
        return best;
    }
    public static Identity identity(String type) { return data.types.getOrDefault(type,new Identity(factionOfNpcType(type),"UNKNOWN")); }
    public static double regionWeight(String faction, Entity e) {
        double weight = 0;
        for (Region r : data.regions) if (r.faction.equals(faction) && r.dimension.equals(e.level().dimension().location().toString())) {
            double dx=e.getX()-r.x,dz=e.getZ()-r.z;
            weight=Math.max(weight,Math.max(0,1-Math.sqrt(dx*dx+dz*dz)/r.radius));
        }
        return weight;
    }
}
