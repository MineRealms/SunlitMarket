package com.society.sunlitmarket.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/** Gameplay policy; item pools and jurisdiction definitions live in reloadable JSON. */
public final class CoreConfig {
    public enum WantedScope { PERSONAL, TEAM }
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue TEAM_ALIGNMENT, PREVENT_ARBITRAGE;
    public static final ForgeConfigSpec.EnumValue<WantedScope> WANTED_SCOPE;
    public static final ForgeConfigSpec.BooleanValue COUNTER_ENABLED, CONFISCATION, WANTED_PRICES, ENFORCEMENT;
    public static final ForgeConfigSpec.IntValue REFUSE_BELOW, COUNTER_RANGE, REQUEST_INTERVAL, REQUEST_LIFETIME;
    public static final ForgeConfigSpec.IntValue MAX_REQUESTS, NPC_COOLDOWN, NPC_DAILY_LIMIT, FAIR_REWARD_DIVISOR;
    public static final ForgeConfigSpec.IntValue HEAT_DECAY, INSPECTION_THRESHOLD, INSPECTION_COOLDOWN, INSPECTION_RANGE;
    public static final ForgeConfigSpec.IntValue CONFISCATION_LIMIT, FINE_PER_TIER;
    public static final ForgeConfigSpec.DoubleValue WANTED_SURCHARGE, HAGGLE_CHANCE, REGION_RADIUS;
    public static final ForgeConfigSpec.BooleanValue INSPECT_COUNTER, BRIBE_ENABLED, KILL_REWARDS;
    public static final ForgeConfigSpec.IntValue BRIBE_PRICE, BRIBE_REDUCTION, HAGGLE_FAILURE_LOSS, STOLEN_ALIGNMENT_LOSS, CONTRABAND_WANTED, KILL_LOSS, ENEMY_KILL_GAIN;
    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("teams");
        TEAM_ALIGNMENT = b.define("sharedAlignment", true);
        WANTED_SCOPE = b.comment("PERSONAL keeps wanted records on the player; TEAM uses the FTB party owner. Switching scope preserves both record sets without copying them.")
                .defineEnum("wantedScope", WantedScope.PERSONAL);
        b.pop().push("trade");
        REFUSE_BELOW = b.defineInRange("refuseBelow", -100, -100000, 100000);
        PREVENT_ARBITRAGE = b.define("preventSameQuoteArbitrage", true);
        b.pop().push("counter");
        COUNTER_ENABLED = b.define("enabled", true);
        COUNTER_RANGE = b.defineInRange("range", 8, 1, 32);
        REQUEST_INTERVAL = b.defineInRange("intervalTicks", 200, 20, 24000);
        REQUEST_LIFETIME = b.defineInRange("lifetimeTicks", 1200, 20, 240000);
        MAX_REQUESTS = b.defineInRange("maxRequests", 3, 1, 12);
        NPC_COOLDOWN = b.defineInRange("npcCooldownTicks", 1200, 20, 240000);
        NPC_DAILY_LIMIT = b.defineInRange("npcDailyRequests", 12, 1, 1000);
        FAIR_REWARD_DIVISOR = b.defineInRange("fairTradeRewardDivisor", 200, 1, 1000000);
        HAGGLE_CHANCE = b.defineInRange("haggleBaseChance", 0.5D, 0D, 1D);
        b.pop().push("crime");
        CONFISCATION = b.define("confiscation", true);
        WANTED_PRICES = b.define("pricePenalty", true);
        ENFORCEMENT = b.define("enforcement", true);
        HEAT_DECAY = b.defineInRange("heatDecayPerDay", 18, 0, 1000);
        INSPECTION_THRESHOLD = b.defineInRange("inspectionThreshold", 20, 0, 10000);
        INSPECTION_COOLDOWN = b.defineInRange("inspectionCooldownTicks", 1200, 20, 240000);
        INSPECTION_RANGE = b.defineInRange("inspectionRange", 8, 1, 32);
        CONFISCATION_LIMIT = b.defineInRange("confiscationItemLimit", 64, 1, 2304);
        FINE_PER_TIER = b.comment("Zero disables fines.").defineInRange("finePerTier", 0, 0, 1000000);
        WANTED_SURCHARGE = b.defineInRange("pricePenaltyPerWanted", 0.005D, 0D, 1D);
        b.pop().push("region");
        REGION_RADIUS = b.defineInRange("radius", 512D, 1D, 100000D);
        b.pop().push("consequences");
        INSPECT_COUNTER = b.define("inspectOwnedCounter", true);
        BRIBE_ENABLED = b.define("bribeEnabled", false);
        BRIBE_PRICE = b.defineInRange("bribePrice", 100, 1, 10000000);
        BRIBE_REDUCTION = b.defineInRange("bribeWantedReduction", 10, 1, 10000);
        HAGGLE_FAILURE_LOSS = b.defineInRange("haggleFailureAlignmentLoss", 1, 0, 10000);
        STOLEN_ALIGNMENT_LOSS = b.defineInRange("stolenAlignmentLoss", 2, 0, 10000);
        CONTRABAND_WANTED = b.defineInRange("contrabandWantedPerTier", 5, 0, 10000);
        KILL_REWARDS = b.comment("Explicit opt-in; territorial rewards need valid dimension/region data.").define("killAlignmentEnabled", false);
        KILL_LOSS = b.defineInRange("friendlyKillLoss", 25, 0, 10000);
        ENEMY_KILL_GAIN = b.defineInRange("regionalEnemyKillGain", 5, 0, 10000);
        b.pop();
        SPEC = b.build();
    }
    private CoreConfig() {}
    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, "sunlitmarket-core.toml");
    }
}
