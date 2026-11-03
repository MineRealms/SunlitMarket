package com.society.sunlitmarket.deposit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.society.sunlitmarket.SunlitMarketMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The acceptance bin's price table, loaded from {@code config/sunlitmarket/deposit_values.json}.
 *
 * <p>Nothing here is hard-coded: an item without an entry is priced by the pawnshop's own sell
 * table, and an explicit 0 marks it as not accepted. KubeJS can override any rate at runtime
 * through {@code SunlitMarket.depositPrice} / {@code depositFluidRate} / {@code depositAccept};
 * those overrides live in memory until the file is reloaded.
 *
 * <p>Server-side only.
 */
public final class DepositConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "deposit_values.json";

    private static final Map<String, Integer> ITEMS = new LinkedHashMap<>();
    /**
     * Items a script refuses (SunlitMarket.depositBlock). Kept apart from ITEMS and never written to
     * deposit_values.json: the script re-asserts them on every load, so an item the pack stops
     * pricing is accepted again instead of being refused forever by a stale file entry.
     */
    private static final java.util.Set<String> REFUSED = new java.util.HashSet<>();
    private static java.util.Set<String> refusedStaging;
    private static final Map<String, Integer> FLUIDS = new LinkedHashMap<>();
    private static final Map<String, Integer> ASPECTS = new LinkedHashMap<>();

    private static boolean acceptItem = true;
    private static boolean acceptFluid = true;
    private static boolean acceptEnergy = true;
    private static boolean acceptEssentia = true;
    private static boolean acceptMana = true;
    private static int fluidDefault = 1;
    private static int fePerSpur = 50_000;
    private static int pointsPerSpur = 1;
    private static int manaPerSpur = 2_000;
    private static boolean walletEnabled = true;
    private static int exchangeFeePercent = 5;
    private static int minWithdraw = 10;
    private static boolean buybackEnabled = true;
    private static int buybackDailyCap = 100_000;
    private static int buybackPlayerCap = 20_000;

    private static boolean loaded;
    private static boolean dirty;

    private DepositConfig() {
    }

    public static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve("sunlitmarket").resolve(FILE);
    }

    // ------------------------------------------------------------------ load / save

    public static synchronized void load() {
        Path file = path();
        try {
            if (!Files.exists(file)) {
                defaults();
                save();
                loaded = true;
                return;
            }
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            apply(root);
        } catch (RuntimeException | IOException ex) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] deposit_values.json unreadable; using built-in defaults", ex);
            defaults();
            loaded = true;
        }
    }

    public static synchronized void save() {
        Path file = path();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(write()), StandardCharsets.UTF_8);
            dirty = false;
        } catch (IOException ex) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] could not write {}", file, ex);
        }
    }

    public static synchronized void ensureLoaded() {
        if (!loaded) load();
    }

    private static void defaults() {
        ITEMS.clear();
        FLUIDS.clear();
        ASPECTS.clear();
        acceptItem = acceptFluid = acceptEnergy = acceptEssentia = acceptMana = true;
        fluidDefault = 1;
        fePerSpur = 50_000;
        pointsPerSpur = 1;
        manaPerSpur = 2_000;
        walletEnabled = true;
        exchangeFeePercent = 5;
        minWithdraw = 10;
        buybackEnabled = true;
        buybackDailyCap = 100_000;
        buybackPlayerCap = 20_000;
        // No pack-specific entries here on purpose: which fluids are worth what is pack data,
        // so it belongs in kubejs/server_scripts/sunlitmarket/svDepositRates.js, not in the mod.
    }

    /** Parses one already-read document and marks the config loaded; the test seam for {@link #load()}. */
    static synchronized void apply(JsonObject root) {
        read(root);
        loaded = true;
        dirty = false;
    }

    private static void read(JsonObject root) {
        defaults();
        JsonObject accept = object(root, "accept");
        acceptItem = flag(accept, "item", true);
        acceptFluid = flag(accept, "fluid", true);
        acceptEnergy = flag(accept, "energy", true);
        acceptEssentia = flag(accept, "essentia", true);
        acceptMana = flag(accept, "mana", true);

        JsonObject items = object(root, "items");
        for (Map.Entry<String, JsonElement> entry : items.entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) continue;
            // -1 is an explicit refusal (SunlitMarket.depositBlock); clamping it to 0 here would
            // turn a persisted refusal back into "no override" and quietly restore the price.
            ITEMS.put(entry.getKey(), Math.max(-1, entry.getValue().getAsInt()));
        }

        JsonObject fluids = object(root, "fluids");
        for (Map.Entry<String, JsonElement> entry : fluids.entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) continue;
            if ("default".equals(entry.getKey())) fluidDefault = Math.max(0, entry.getValue().getAsInt());
            else FLUIDS.put(entry.getKey(), Math.max(0, entry.getValue().getAsInt()));
        }

        JsonObject energy = object(root, "energy");
        fePerSpur = Math.max(1, number(energy, "fePerSpur", 50_000));

        JsonObject essentia = object(root, "essentia");
        pointsPerSpur = Math.max(1, number(essentia, "pointsPerSpur", 1));
        JsonObject aspects = object(essentia, "aspects");
        for (Map.Entry<String, JsonElement> entry : aspects.entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) continue;
            ASPECTS.put(entry.getKey(), Math.max(0, entry.getValue().getAsInt()));
        }

        JsonObject mana = object(root, "mana");
        manaPerSpur = Math.max(1, number(mana, "manaPerSpur", 2_000));

        JsonObject wallet = object(root, "wallet");
        walletEnabled = flag(wallet, "enabled", true);
        exchangeFeePercent = Math.max(0, Math.min(50, number(wallet, "exchangeFeePercent", 5)));
        minWithdraw = Math.max(0, number(wallet, "minWithdraw", 10));

        JsonObject buyback = object(root, "buyback");
        buybackEnabled = flag(buyback, "enabled", true);
        buybackDailyCap = Math.max(0, number(buyback, "dailyCap", 100_000));
        buybackPlayerCap = Math.max(0, number(buyback, "playerCap", 20_000));
    }

    /** The document {@link #save()} would write; the test seam for the persisted shape. */
    static synchronized JsonObject toJsonForTest() {
        return write();
    }

    private static JsonObject write() {
        JsonObject root = new JsonObject();
        JsonObject accept = new JsonObject();
        accept.addProperty("item", acceptItem);
        accept.addProperty("fluid", acceptFluid);
        accept.addProperty("energy", acceptEnergy);
        accept.addProperty("essentia", acceptEssentia);
        accept.addProperty("mana", acceptMana);
        root.add("accept", accept);

        JsonObject items = new JsonObject();
        ITEMS.forEach(items::addProperty);
        root.add("items", items);

        JsonObject fluids = new JsonObject();
        fluids.addProperty("default", fluidDefault);
        FLUIDS.forEach(fluids::addProperty);
        root.add("fluids", fluids);

        JsonObject energy = new JsonObject();
        energy.addProperty("fePerSpur", fePerSpur);
        root.add("energy", energy);

        JsonObject essentia = new JsonObject();
        essentia.addProperty("pointsPerSpur", pointsPerSpur);
        JsonObject aspects = new JsonObject();
        ASPECTS.forEach(aspects::addProperty);
        essentia.add("aspects", aspects);
        root.add("essentia", essentia);

        JsonObject mana = new JsonObject();
        mana.addProperty("manaPerSpur", manaPerSpur);
        root.add("mana", mana);

        JsonObject wallet = new JsonObject();
        wallet.addProperty("enabled", walletEnabled);
        wallet.addProperty("exchangeFeePercent", exchangeFeePercent);
        wallet.addProperty("minWithdraw", minWithdraw);
        root.add("wallet", wallet);

        JsonObject buyback = new JsonObject();
        buyback.addProperty("enabled", buybackEnabled);
        buyback.addProperty("dailyCap", buybackDailyCap);
        buyback.addProperty("playerCap", buybackPlayerCap);
        root.add("buyback", buyback);
        return root;
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject()
                ? parent.getAsJsonObject(key) : new JsonObject();
    }

    private static boolean flag(JsonObject parent, String key, boolean fallback) {
        return parent.has(key) && parent.get(key).isJsonPrimitive() ? parent.get(key).getAsBoolean() : fallback;
    }

    private static int number(JsonObject parent, String key, int fallback) {
        return parent.has(key) && parent.get(key).isJsonPrimitive() ? parent.get(key).getAsInt() : fallback;
    }

    // ------------------------------------------------------------------ accessors

    /** Explicit spur per item, or null when the pawnshop table should decide. */
    public static synchronized Integer itemPrice(String id) {
        ensureLoaded();
        if (REFUSED.contains(id)) return -1;
        return ITEMS.get(id);
    }

    /** Script refusal; staged during a script load, live otherwise. */
    public static synchronized void refuse(String id) {
        if (id == null || id.isBlank()) return;
        (refusedStaging != null ? refusedStaging : REFUSED).add(id);
    }

    public static synchronized void beginScripts() {
        refusedStaging = new java.util.HashSet<>();
    }

    /**
     * Commits the staged refusals. Persisted -1 entries the scripts now refuse anyway are dropped
     * from the file (older builds wrote one per shipping-bin item); other -1 entries stay, they
     * are an administrator's explicit refusal.
     */
    public static synchronized void endScripts(boolean valid) {
        if (refusedStaging != null && valid) {
            REFUSED.clear();
            REFUSED.addAll(refusedStaging);
            ensureLoaded();
            if (ITEMS.entrySet().removeIf(e -> e.getValue() < 0 && REFUSED.contains(e.getKey()))) dirty = true;
        }
        refusedStaging = null;
    }

    public static synchronized int fluidRate(String id) {
        ensureLoaded();
        Integer rate = FLUIDS.get(id);
        return rate == null ? fluidDefault : rate;
    }

    public static synchronized int aspectRate(String aspectId) {
        ensureLoaded();
        Integer rate = ASPECTS.get(aspectId);
        return rate == null ? pointsPerSpur : rate;
    }

    public static synchronized boolean accepts(String channel) {
        ensureLoaded();
        return switch (channel) {
            case "item" -> acceptItem;
            case "fluid" -> acceptFluid;
            case "energy" -> acceptEnergy;
            case "essentia" -> acceptEssentia;
            case "mana" -> acceptMana;
            default -> false;
        };
    }

    public static synchronized int fePerSpur() {
        ensureLoaded();
        return fePerSpur;
    }

    public static synchronized int pointsPerSpur() {
        ensureLoaded();
        return pointsPerSpur;
    }

    public static synchronized int manaPerSpur() {
        ensureLoaded();
        return manaPerSpur;
    }

    public static synchronized boolean walletEnabled() {
        ensureLoaded();
        return walletEnabled;
    }

    public static synchronized int exchangeFeePercent() {
        ensureLoaded();
        return exchangeFeePercent;
    }

    public static synchronized int minWithdraw() {
        ensureLoaded();
        return minWithdraw;
    }

    public static synchronized boolean buybackEnabled() {
        ensureLoaded();
        return buybackEnabled;
    }

    /** World-wide daily payout ceiling; 0 means uncapped. */
    public static synchronized int buybackDailyCap() {
        ensureLoaded();
        return buybackDailyCap;
    }

    /** Per-player daily payout ceiling; 0 means uncapped. */
    public static synchronized int buybackPlayerCap() {
        ensureLoaded();
        return buybackPlayerCap;
    }

    // ------------------------------------------------------------------ script overrides

    public static synchronized void setItemPrice(String id, int spur) {
        ensureLoaded();
        if (id == null || id.isBlank()) return;
        ITEMS.put(id, Math.max(-1, spur));
        dirty = true;
    }

    public static synchronized void setFluidRate(String id, int perBucket) {
        ensureLoaded();
        if (id == null || id.isBlank()) return;
        // "default" is the fallback rate for every fluid without its own entry.
        if ("default".equals(id)) fluidDefault = Math.max(0, perBucket);
        else FLUIDS.put(id, Math.max(0, perBucket));
        dirty = true;
    }

    /** Script hook: FE per one spur. */
    public static synchronized void setEnergyRate(int fePerSpur) {
        ensureLoaded();
        DepositConfig.fePerSpur = Math.max(1, fePerSpur);
        dirty = true;
    }

    /** Script hook: mana per one spur. */
    public static synchronized void setManaRate(int manaPerSpur) {
        ensureLoaded();
        DepositConfig.manaPerSpur = Math.max(1, manaPerSpur);
        dirty = true;
    }

    /** Script hook: default essentia points per one spur. */
    public static synchronized void setEssentiaRate(int pointsPerSpur) {
        ensureLoaded();
        DepositConfig.pointsPerSpur = Math.max(1, pointsPerSpur);
        dirty = true;
    }

    /** Script hook: per-aspect override of points per one spur. */
    public static synchronized void setAspectRate(String aspectId, int pointsPerSpur) {
        ensureLoaded();
        if (aspectId == null || aspectId.isBlank()) return;
        ASPECTS.put(aspectId, Math.max(0, pointsPerSpur));
        dirty = true;
    }

    public static synchronized void setAccepted(String channel, boolean allowed) {
        ensureLoaded();
        switch (channel == null ? "" : channel) {
            case "item" -> acceptItem = allowed;
            case "fluid" -> acceptFluid = allowed;
            case "energy" -> acceptEnergy = allowed;
            case "essentia" -> acceptEssentia = allowed;
            case "mana" -> acceptMana = allowed;
            default -> {
                return;
            }
        }
        dirty = true;
    }

    /** Script hook: both caps in one call; 0 disables a cap. */
    public static synchronized void setBuyback(int dailyCap, int playerCap) {
        ensureLoaded();
        buybackDailyCap = Math.max(0, dailyCap);
        buybackPlayerCap = Math.max(0, playerCap);
        dirty = true;
    }

    public static synchronized void setBuybackEnabled(boolean enabled) {
        ensureLoaded();
        buybackEnabled = enabled;
        dirty = true;
    }

    public static synchronized boolean dirty() {
        return dirty;
    }
}
