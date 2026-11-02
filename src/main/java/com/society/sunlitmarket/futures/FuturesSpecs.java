package com.society.sunlitmarket.futures;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.society.sunlitmarket.SunlitMarketMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which indices have futures, from {@code config/sunlitmarket/futures.json} plus KubeJS. Scripts
 * win over JSON for the same index. Script reloads are staged like the other script registries: a
 * failed reload keeps the previous set.
 */
public final class FuturesSpecs {
    private static Map<String, FuturesSpec> json = Map.of();
    private static final Map<String, FuturesSpec> SCRIPTS = new LinkedHashMap<>();
    private static Map<String, FuturesSpec> staging;

    private FuturesSpecs() {
    }

    public static synchronized void beginScripts() {
        staging = new LinkedHashMap<>();
    }

    public static synchronized void endScripts(boolean valid) {
        if (staging != null && valid) {
            SCRIPTS.clear();
            SCRIPTS.putAll(staging);
        }
        staging = null;
    }

    private static Map<String, FuturesSpec> scriptTarget() {
        return staging == null ? SCRIPTS : staging;
    }

    public static synchronized void loadJson() {
        var path = FMLPaths.CONFIGDIR.get().resolve("sunlitmarket/futures.json");
        Map<String, FuturesSpec> candidate = new LinkedHashMap<>();
        try {
            if (Files.isRegularFile(path)) {
                for (JsonElement raw : JsonParser.parseString(Files.readString(path)).getAsJsonArray()) {
                    FuturesSpec spec = FuturesSpec.parse(raw.getAsJsonObject());
                    candidate.put(spec.index(), spec);
                }
            }
            json = Map.copyOf(candidate);
        } catch (Exception ex) {
            SunlitMarketMod.LOG.error("[SunlitMarket] Invalid futures.json; previous specs retained", ex);
        }
    }

    public static synchronized FuturesSpec add(String text) {
        FuturesSpec spec = FuturesSpec.parse(JsonParser.parseString(text).getAsJsonObject());
        scriptTarget().put(spec.index(), spec);
        return spec;
    }

    public static synchronized void clearScripts() {
        scriptTarget().clear();
    }

    public static synchronized void removeScript(String index) {
        SCRIPTS.remove(index);
    }

    public static synchronized FuturesSpec get(String index) {
        FuturesSpec spec = SCRIPTS.get(index);
        return spec != null ? spec : json.get(index);
    }

    public static synchronized List<FuturesSpec> all() {
        Map<String, FuturesSpec> merged = new LinkedHashMap<>(json);
        merged.putAll(SCRIPTS);
        return new ArrayList<>(merged.values());
    }

    public static JsonObject json() {
        JsonObject o = new JsonObject();
        for (FuturesSpec spec : all()) o.add(spec.index(), spec.json());
        return o;
    }
}
