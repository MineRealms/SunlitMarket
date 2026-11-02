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
 * Turns everything the bin accepts into spur. All rates come from {@link DepositConfig}; the only
 * built-in rule is "an item with no configured price is worth what the pawnshop would pay for it".
 *
 * <p>Server-side only: no client class is referenced here.
 */
public final class DepositValues {
    private static Map<String, Integer> marketUnit = Map.of();
    private static long stamp = Long.MIN_VALUE;

    private DepositValues() {
    }

    /** Rebuilds the merchant price table when the registry changes. */
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
        marketUnit = units;
        stamp = version;
    }

    /** Spur per single item: the config wins, then the pawnshop table, then 0 (not accepted). */
    public static int itemUnit(String id) {
        if (id == null || id.isEmpty()) return 0;
        Integer configured = DepositConfig.itemPrice(id);
        if (configured != null) return configured;
        refresh();
        return marketUnit.getOrDefault(id, 0);
    }

    public static int itemValue(ItemStack stack) {
        if (stack.isEmpty() || !DepositConfig.accepts("item")) return 0;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return itemUnit(id) * stack.getCount();
    }

    public static int fluidValue(FluidStack stack) {
        if (stack.isEmpty() || !DepositConfig.accepts("fluid")) return 0;
        String id = BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString().toLowerCase(Locale.ROOT);
        int perBucket = DepositConfig.fluidRate(id);
        return (int) Math.min(Integer.MAX_VALUE, (long) perBucket * stack.getAmount() / 1000L);
    }

    public static int energyValue(int fe) {
        if (fe <= 0 || !DepositConfig.accepts("energy")) return 0;
        return fe / DepositConfig.fePerSpur();
    }

    public static int manaValue(int mana) {
        if (mana <= 0 || !DepositConfig.accepts("mana")) return 0;
        return mana / DepositConfig.manaPerSpur();
    }

    /** Flat rate, used when the aspect is unknown. */
    public static int essentiaValue(int amount) {
        if (amount <= 0 || !DepositConfig.accepts("essentia")) return 0;
        return amount / DepositConfig.pointsPerSpur();
    }

    /** Per-aspect rate, so a config can make one aspect worth more than another. */
    public static int essentiaValue(Map<String, Integer> aspects) {
        if (aspects == null || aspects.isEmpty() || !DepositConfig.accepts("essentia")) return 0;
        long total = 0;
        for (Map.Entry<String, Integer> entry : aspects.entrySet()) {
            int amount = entry.getValue() == null ? 0 : entry.getValue();
            if (amount <= 0) continue;
            total += (long) amount / Math.max(1, DepositConfig.aspectRate(entry.getKey()));
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }
}
