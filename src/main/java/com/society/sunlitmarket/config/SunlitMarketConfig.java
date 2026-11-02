package com.society.sunlitmarket.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * All tunables live here so a pack author can rebalance without a rebuild.
 * Everything the alignment system needs is configurable; KubeJS only supplies data.
 */
public final class SunlitMarketConfig {
    public static final ForgeConfigSpec SPEC;

    // ---- alignment ----
    public static final ForgeConfigSpec.BooleanValue ALIGNMENT_ENABLED;
    public static final ForgeConfigSpec.IntValue ALIGNMENT_MIN;
    public static final ForgeConfigSpec.IntValue ALIGNMENT_MAX;
    public static final ForgeConfigSpec.BooleanValue DECAY_ENABLED;
    public static final ForgeConfigSpec.IntValue DECAY_PER_DAY;
    public static final ForgeConfigSpec.IntValue WANTED_DECAY_PER_DAY;
    public static final ForgeConfigSpec.IntValue WANTED_MAX;

    // ---- diplomacy ----
    public static final ForgeConfigSpec.BooleanValue DOUBLE_DROP_ENABLED;
    public static final ForgeConfigSpec.DoubleValue DOUBLE_DROP_RATIO;
    public static final ForgeConfigSpec.BooleanValue USE_ME_FACTION_TABLE;

    // ---- price ladder ----
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PRICE_TIERS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Player x faction alignment. Replaces the old flat reputation value.").push("alignment");
        ALIGNMENT_ENABLED = b.comment("Master switch. When false every faction reads as 0.")
                .define("enabled", true);
        ALIGNMENT_MIN = b.comment("Lower clamp.")
                .defineInRange("min", -1000, -100000, 0);
        ALIGNMENT_MAX = b.comment("Upper clamp. The pack runs a slow curve, so this is deliberately high.")
                .defineInRange("max", 3000, 1, 100000);
        DECAY_ENABLED = b.comment("Drift alignment back toward 0 once per in-game day.")
                .define("decayEnabled", true);
        DECAY_PER_DAY = b.comment("Points moved toward 0 per day (sign preserved).")
                .defineInRange("decayPerDay", 1, 0, 1000);
        WANTED_DECAY_PER_DAY = b.comment("Wanted level lost per day.")
                .defineInRange("wantedDecayPerDay", 2, 0, 1000);
        WANTED_MAX = b.comment("Upper clamp for wanted level.")
                .defineInRange("wantedMax", 100, 1, 10000);
        b.pop();

        b.comment("Cross-faction consequences.").push("diplomacy");
        DOUBLE_DROP_ENABLED = b.comment("Gaining with faction A costs you with A's enemies.")
                .define("doubleDropEnabled", true);
        DOUBLE_DROP_RATIO = b.comment("Fraction of the gain subtracted from each hostile faction.")
                .defineInRange("doubleDropRatio", 0.5D, 0.0D, 2.0D);
        USE_ME_FACTION_TABLE = b.comment("Read data/me/me/faction/*.json for the hostile table. Falls back to empty when Middle-earth is absent.")
                .define("useMiddleEarthFactionTable", true);
        b.pop();

        b.comment("Price ladder. Each entry is \"threshold|buyMultiplier|sellMultiplier|title\".",
                "The highest threshold that is <= the player's alignment wins.",
                "buyMultiplier multiplies what the player PAYS (1.35 = 35% surcharge).",
                "sellMultiplier multiplies what the player RECEIVES (0.85 = 15% less).")
                .push("price");
        PRICE_TIERS = b.comment("Ladder entries, sorted ascending by threshold at load time.")
                .defineList("tiers", List.of(
                        "-1000|1.50|0.70|死敌",
                        "-500|1.35|0.85|敌对",
                        "-100|1.20|0.90|冷淡",
                        "0|1.00|1.00|中立",
                        "100|0.95|1.05|常客",
                        "500|0.90|1.10|盟友",
                        "1000|0.85|1.15|挚友",
                        "3000|0.80|1.20|传奇"
                ), o -> o instanceof String);
        b.pop();

        SPEC = b.build();
    }

    private SunlitMarketConfig() {
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC);
    }

    /** Parsed ladder, rebuilt whenever the config reloads. */
    public static final class Tier {
        public final int threshold;
        public final double buy;
        public final double sell;
        public final String title;

        Tier(int threshold, double buy, double sell, String title) {
            this.threshold = threshold;
            this.buy = buy;
            this.sell = sell;
            this.title = title;
        }
    }

    private static List<Tier> tiers = new ArrayList<>();
    private static boolean baked;

    public static synchronized List<Tier> tiers() {
        if (!baked) {
            List<Tier> parsed = new ArrayList<>();
            for (Object raw : PRICE_TIERS.get()) {
                String s = String.valueOf(raw);
                String[] parts = s.split("\\|");
                if (parts.length < 4) continue;
                try {
                    double buy = Double.parseDouble(parts[1].trim());
                    double sell = Double.parseDouble(parts[2].trim());
                    if (!Double.isFinite(buy) || !Double.isFinite(sell) || buy <= 0 || sell <= 0 || buy > 100 || sell > 100)
                        throw new NumberFormatException("Invalid tier multiplier");
                    parsed.add(new Tier(Integer.parseInt(parts[0].trim()), buy, sell, parts[3].trim()));
                } catch (NumberFormatException ignored) {
                }
            }
            parsed.sort((a, c) -> Integer.compare(a.threshold, c.threshold));
            if (parsed.isEmpty()) parsed.add(new Tier(0, 1.0D, 1.0D, ""));
            tiers = parsed;
            baked = true;
        }
        return tiers;
    }

    public static synchronized void invalidate() {
        baked = false;
    }

    public static Tier tierFor(int alignment) {
        List<Tier> list = tiers();
        Tier picked = list.get(0);
        for (Tier t : list) {
            if (alignment >= t.threshold) picked = t;
            else break;
        }
        return picked;
    }
}
