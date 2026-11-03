package com.society.sunlitmarket.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/** Gameplay policy; item pools and jurisdiction definitions live in reloadable JSON. */
public final class CoreConfig {
    public enum WantedScope { PERSONAL, TEAM }
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue TEAM_ALIGNMENT, PREVENT_ARBITRAGE, DYNAMIC_PRICING;
    public static final ForgeConfigSpec.DoubleValue INVENTORY_ELASTICITY, PRICE_MIN_MULT, PRICE_MAX_MULT, SELL_OVERSTOCK;
    public static final ForgeConfigSpec.EnumValue<WantedScope> WANTED_SCOPE;
    public static final ForgeConfigSpec.BooleanValue COUNTER_ENABLED, CONFISCATION, WANTED_PRICES, ENFORCEMENT;
    public static final ForgeConfigSpec.IntValue REFUSE_BELOW, COUNTER_RANGE, REQUEST_INTERVAL, REQUEST_LIFETIME;
    public static final ForgeConfigSpec.IntValue MAX_REQUESTS, NPC_COOLDOWN, NPC_DAILY_LIMIT, FAIR_REWARD_DIVISOR;
    public static final ForgeConfigSpec.IntValue HEAT_DECAY, INSPECTION_THRESHOLD, INSPECTION_COOLDOWN, INSPECTION_RANGE;
    public static final ForgeConfigSpec.IntValue CONFISCATION_LIMIT, FINE_PER_TIER;
    public static final ForgeConfigSpec.DoubleValue WANTED_SURCHARGE, HAGGLE_CHANCE, REGION_RADIUS;
    public static final ForgeConfigSpec.BooleanValue INSPECT_COUNTER, BRIBE_ENABLED, KILL_REWARDS;
    public static final ForgeConfigSpec.IntValue BRIBE_PRICE, BRIBE_REDUCTION, HAGGLE_FAILURE_LOSS, STOLEN_ALIGNMENT_LOSS, CONTRABAND_WANTED, KILL_LOSS, ENEMY_KILL_GAIN;
    public static final ForgeConfigSpec.BooleanValue TREASURY_ENABLED;
    public static final ForgeConfigSpec.IntValue ME_FACTION_SEED;
    public static final ForgeConfigSpec.IntValue INDEX_OWNER_CAP;
    public static final ForgeConfigSpec.LongValue INITIAL_CASH, INITIAL_RESERVE, CASH_TARGET, CASH_CEILING, DAILY_REFILL, NPC_DAILY_BUDGET;
    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("teams");
        TEAM_ALIGNMENT = b.define("sharedAlignment", true);
        WANTED_SCOPE = b.comment("PERSONAL keeps wanted records on the player; TEAM uses the FTB party owner. Switching scope preserves both record sets without copying them.")
                .defineEnum("wantedScope", WantedScope.PERSONAL);
        b.pop().push("integration");
        ME_FACTION_SEED = b.comment("Starting alignment granted to a player's own Middle-earth faction on first login.",
                "Read from the Middle-earth player data (me:gondor, me:rohan, ...); 0 disables it.")
                .defineInRange("meFactionSeedAlignment", 50, 0, 1000);
        b.pop().push("trade");
        REFUSE_BELOW = b.defineInRange("refuseBelow", -100, -100000, 100000);
        PREVENT_ARBITRAGE = b.define("preventSameQuoteArbitrage", true);
        DYNAMIC_PRICING = b.comment("Stocked shelves price by inventory: buying drains the shelf and raises prices, selling refills it and lowers them. Unlimited offers keep fixed prices.")
                .define("dynamicPricing", true);
        INVENTORY_ELASTICITY = b.defineInRange("inventoryElasticity", 0.2D, 0D, 2D);
        PRICE_MIN_MULT = b.defineInRange("priceMultiplierMin", 0.5D, 0.05D, 4D);
        PRICE_MAX_MULT = b.defineInRange("priceMultiplierMax", 2.0D, 0.1D, 8D);
        SELL_OVERSTOCK = b.comment("Merchant shelf may be filled to this multiple of the restock level before further sales stop lowering prices.")
                .defineInRange("sellOverstockCapacity", 3D, 1D, 16D);
        INDEX_OWNER_CAP = b.comment("Most lots one owner (player or FTB party) can contribute to a single index candle's weight.",
                "Volume beyond the cap still shows on the chart but cannot move a settlement price.").defineInRange("indexOwnerCapPerCandle", 64, 0, 1000000);
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
        b.pop().push("treasury");
        TREASURY_ENABLED = b.comment("Pawn cash (C) pays the acceptance bin and shelf buybacks; the issuance reserve (R) refills C and funds NPC demand.",
                "Disabling returns to the old behaviour where every system payout is minted from nothing.").define("enabled", true);
        INITIAL_CASH = b.comment("Seeded once, when a world first creates its treasury.").defineInRange("initialCash", 200_000L, 0L, Long.MAX_VALUE / 4);
        INITIAL_RESERVE = b.defineInRange("initialReserve", 5_000_000L, 0L, Long.MAX_VALUE / 4);
        CASH_TARGET = b.comment("Each economy day the reserve tops pawn cash up toward this level.").defineInRange("cashTarget", 200_000L, 0L, Long.MAX_VALUE / 4);
        CASH_CEILING = b.comment("Pawn cash above this level is swept back into the reserve each economy day.").defineInRange("cashCeiling", 400_000L, 0L, Long.MAX_VALUE / 4);
        DAILY_REFILL = b.defineInRange("dailyRefill", 100_000L, 0L, Long.MAX_VALUE / 4);
        NPC_DAILY_BUDGET = b.comment("Spur the NPC economy may pay players per economy day for goods it consumes.").defineInRange("npcDailyBudget", 50_000L, 0L, Long.MAX_VALUE / 4);
        b.pop();
        SPEC = b.build();
    }
    private CoreConfig() {}
    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, "sunlitmarket-core.toml");
    }
}
