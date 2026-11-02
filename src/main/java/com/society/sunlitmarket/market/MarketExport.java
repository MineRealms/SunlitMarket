package com.society.sunlitmarket.market;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.society.sunlitmarket.SunlitMarketMod;
import com.society.sunlitmarket.core.EconomyClock;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Function;

/**
 * Writes the world's index history as plain JSON for external tools, under
 * {@code <world>/sunlitmarket/export/}. The SavedData stays authoritative; these files are
 * snapshots rewritten on server stop and by {@code /sunlitmarket market export}.
 */
public final class MarketExport {
    /** Extra JSON files contributed by other modules (futures history), keyed by file name. */
    private static final java.util.Map<String, Function<MinecraftServer, JsonElement>> EXTRA = new java.util.LinkedHashMap<>();

    private MarketExport() {
    }

    public static synchronized void register(String fileName, Function<MinecraftServer, JsonElement> source) {
        EXTRA.put(fileName, source);
    }

    public static JsonObject indices(MinecraftServer server) {
        MarketData data = MarketData.get(server);
        JsonObject root = new JsonObject();
        root.addProperty("schema", 1);
        root.addProperty("economyTick", EconomyClock.tick(server));
        root.addProperty("note", "candles: [bucket, open, high, low, close, volume, weight, traders]; bucket = economy tick / period; real fills only");
        JsonArray list = new JsonArray();
        for (String id : data.ids()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.addProperty("name", data.name(id));
            o.addProperty("value", data.value(id));
            JsonArray members = new JsonArray();
            for (MarketData.Member member : data.members(id)) {
                JsonObject m = new JsonObject();
                m.addProperty("merchant", member.merchant);
                m.addProperty("item", member.item);
                members.add(m);
            }
            o.add("members", members);
            JsonObject periods = new JsonObject();
            for (int period : MarketData.PERIODS) {
                JsonArray candles = new JsonArray();
                for (Candle c : data.bars(id, period)) candles.add(c.json());
                periods.add(Integer.toString(period), candles);
            }
            o.add("periods", periods);
            list.add(o);
        }
        root.add("indices", list);
        JsonArray fills = new JsonArray();
        for (TradeFill fill : data.fills()) fills.add(fill.json());
        root.add("recentFills", fills);
        return root;
    }

    /** Writes every export file; returns the directory. */
    public static Path write(MinecraftServer server) throws IOException {
        Path dir = server.getWorldPath(LevelResource.ROOT).resolve("sunlitmarket").resolve("export");
        Files.createDirectories(dir);
        writeAtomic(dir.resolve("indices.json"), indices(server));
        java.util.Map<String, Function<MinecraftServer, JsonElement>> extra;
        synchronized (MarketExport.class) {
            extra = new java.util.LinkedHashMap<>(EXTRA);
        }
        for (var entry : extra.entrySet()) writeAtomic(dir.resolve(entry.getKey()), entry.getValue().apply(server));
        return dir;
    }

    /** Stop-time variant: logs instead of throwing, so a full disk cannot block shutdown. */
    public static void writeQuietly(MinecraftServer server) {
        try {
            write(server);
        } catch (IOException | RuntimeException ex) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] market export failed", ex);
        }
    }

    private static void writeAtomic(Path target, JsonElement json) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, new GsonBuilder().setPrettyPrinting().create().toJson(json), StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
