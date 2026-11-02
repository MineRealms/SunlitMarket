package com.society.sunlitmarket.deposit;

import com.society.sunlitmarket.market.MarketRegistry;
import com.society.sunlitmarket.market.Merchant;
import com.society.sunlitmarket.market.Offer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Converts everything the bin accepts into spur. Server-only: no client class is referenced here.
 *
 * <p>Items are priced with the pawnshop's own buy-back table, so the bin never invents a price the
 * merchants would not pay. Everything else uses a deliberately poor rate - energy especially, since
 * GT electricity is the one resource a late-game player can produce without limit.
 */
public final class DepositValues {
    /** 1 spur per 50,000 FE. A plasma turbine would still need minutes to earn pocket change. */
    private static final int FE_PER_SPUR = 50_000;
    /** 1 spur per 2,000 mana. */
    private static final int MANA_PER_SPUR = 2_000;
    /** 1 spur per point of essentia. */
    private static final int ESSENTIA_PER_SPUR = 1;
    /** Spur per bucket of fluid, by fluid id. Anything unlisted is worth a single spur. */
    private static final Map<String, Integer> FLUID_RATES = Map.of(
            "gtceu:oil", 6,
            "gtceu:heavy_fuel", 6,
            "gtceu:diesel", 8,
            "gtceu:gasoline", 10,
            "gtceu:sulfuric_acid", 3,
            "gtceu:nitric_acid", 3);

    private static Map<String, Integer> itemUnit = Map.of();
    private static long stamp = Long.MIN_VALUE;

    private DepositValues() {
    }

    /** Rebuilds the item table when the merchant registry changes. */
    public static synchronized void refresh() {
        long version = MarketRegistry.version();
        if (version == stamp) return;
        Map<String, Integer> units = new HashMap<>();
        for (Merchant merchant : MarketRegistry.all()) {
            for (Offer offer : merchant.offers) {
                if (offer.sell <= 0 || offer.item == null || offer.item.isEmpty()) continue;
                int unit = Math.max(1, offer.sell / Math.max(1, offer.count));
                units.merge(offer.item, unit, Math::max);
            }
        }
        itemUnit = units;
        stamp = version;
    }

    /** Spur per single item, or 0 when no merchant would buy it. */
    public static int itemUnit(String id) {
        if (id == null || id.isEmpty()) return 0;
        refresh();
        return itemUnit.getOrDefault(id, 0);
    }

    public static int itemValue(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return itemUnit(id) * stack.getCount();
    }

    public static int fluidValue(FluidStack stack) {
        if (stack.isEmpty()) return 0;
        String id = BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString();
        int perBucket = FLUID_RATES.getOrDefault(id.toLowerCase(Locale.ROOT), 1);
        return (int) Math.min(Integer.MAX_VALUE, (long) perBucket * stack.getAmount() / 1000L);
    }

    public static int energyValue(int fe) {
        return Math.max(0, fe) / FE_PER_SPUR;
    }

    public static int manaValue(int mana) {
        return Math.max(0, mana) / MANA_PER_SPUR;
    }

    public static int essentiaValue(int amount) {
        return Math.max(0, amount) / ESSENTIA_PER_SPUR;
    }
}
