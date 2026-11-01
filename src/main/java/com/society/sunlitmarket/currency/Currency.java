package com.society.sunlitmarket.currency;

import com.society.sunlitmarket.SunlitMarketMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Numismatics bank when present; otherwise coin items (emerald by default for the dev harness).
 * No compile-time dependency on Numismatics.
 */
public final class Currency {
    private static final Map<String, Integer> VALUES = new LinkedHashMap<>();

    static {
        VALUES.put("numismatics:spur", 1);
        VALUES.put("numismatics:bevel", 8);
        VALUES.put("numismatics:sprocket", 16);
        VALUES.put("numismatics:cog", 64);
        VALUES.put("numismatics:crown", 512);
        VALUES.put("numismatics:sun", 4096);
        VALUES.put("numismatics:neptunium_coin", 32768);
        VALUES.put("numismatics:ancient_coin", 262144);
        VALUES.put("numismatics:prismatic_coin", 16777216);
        // Dev harness fallback; KubeJS can override or remove this.
        VALUES.put("minecraft:emerald", 1);
    }

    private Currency() {
    }

    public static synchronized void setCoinValue(String item, int value) {
        if (item == null || item.isBlank()) return;
        if (value <= 0) VALUES.remove(item);
        else VALUES.put(item, value);
    }

    public static synchronized int coinValue(String item) {
        return VALUES.getOrDefault(item, 0);
    }

    public static synchronized List<Map.Entry<String, Integer>> denominations() {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(VALUES.entrySet());
        list.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        return list;
    }

    private static Object bankAccount(Player player) {
        if (!ModList.get().isLoaded("numismatics")) return null;
        try {
            Class<?> numismatics = Class.forName("dev.ithundxr.createnumismatics.Numismatics");
            Field bankField = numismatics.getField("BANK");
            Object bank = bankField.get(null);
            if (bank == null) return null;
            for (Method method : bank.getClass().getMethods()) {
                if (!method.getName().equals("getAccount") || method.getParameterCount() != 1) continue;
                Class<?> parameter = method.getParameterTypes()[0];
                if (parameter == Player.class) return method.invoke(bank, player);
                if (parameter == java.util.UUID.class) return method.invoke(bank, player.getUUID());
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static int accountBalance(Object account) {
        try {
            return ((Number) account.getClass().getMethod("getBalance").invoke(account)).intValue();
        } catch (Throwable t) {
            return -1;
        }
    }

    /** @return true when the bank accepted the new balance. */
    private static boolean setAccountBalance(Object account, int value) {
        try {
            account.getClass().getMethod("setBalance", int.class).invoke(account, value);
            return true;
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] bank setBalance failed, falling back to coin items", t);
            return false;
        }
    }

    public static int balance(Player player) {
        Object account = bankAccount(player);
        if (account != null) {
            int balance = accountBalance(account);
            if (balance >= 0) return balance;
        }
        return coinBalance(player);
    }

    public static boolean pay(Player player, int amount) {
        if (amount <= 0) return true;
        Object account = bankAccount(player);
        if (account != null) {
            int balance = accountBalance(account);
            if (balance < amount) return false;
            if (setAccountBalance(account, balance - amount)) return true;
        }
        return payCoins(player, amount);
    }

    public static void give(Player player, int amount) {
        if (amount <= 0) return;
        Object account = bankAccount(player);
        if (account != null) {
            int balance = accountBalance(account);
            if (balance >= 0 && setAccountBalance(account, balance + amount)) return;
        }
        giveCoins(player, amount);
    }

    public static int coinBalance(Player player) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            int value = coinValue(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            if (value > 0) total += value * stack.getCount();
        }
        return total;
    }

    private static boolean payCoins(Player player, int amount) {
        if (coinBalance(player) < amount) return false;
        int paid = 0;
        for (int i = 0; i < player.getInventory().getContainerSize() && paid < amount; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            int value = coinValue(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            if (value <= 0 || stack.isEmpty()) continue;
            while (!stack.isEmpty() && paid < amount) {
                int need = amount - paid;
                int take = Math.min(stack.getCount(), Math.max(1, (need + value - 1) / value));
                stack.shrink(take);
                paid += take * value;
            }
        }
        if (paid > amount) giveCoins(player, paid - amount);
        return paid >= amount;
    }

    private static void giveCoins(Player player, int amount) {
        for (Map.Entry<String, Integer> denomination : denominations()) {
            int value = denomination.getValue();
            if (value <= 0 || amount < value) continue;
            // Resolve the item before spending the amount: a denomination that is not
            // installed (numismatics:spur in a dev instance) would otherwise eat the
            // change and the player receives nothing.
            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(denomination.getKey()));
            if (item == null || item == net.minecraft.world.item.Items.AIR) continue;
            int count = amount / value;
            if (count <= 0) continue;
            amount -= count * value;
            while (count > 0) {
                int give = Math.min(64, count);
                count -= give;
                ItemStack stack = new ItemStack(item, give);
                if (!player.getInventory().add(stack)) player.drop(stack, false);
            }
        }
        if (amount > 0) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] cannot make change for {} spur(s): no installed denomination",
                    amount);
        }
    }

    public static int countItems(Player player, String itemId) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)) count += stack.getCount();
        }
        return count;
    }

    public static boolean removeItems(Player player, String itemId, int count) {
        if (count <= 0) return true;
        if (countItems(player, itemId) < count) return false;
        int left = count;
        for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)) continue;
            int take = Math.min(left, stack.getCount());
            stack.shrink(take);
            left -= take;
        }
        return left == 0;
    }

    public static boolean giveItem(Player player, String itemId, int count) {
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(itemId));
        if (item == null || item == net.minecraft.world.item.Items.AIR) return false;
        ItemStack stack = new ItemStack(item, Math.max(1, count));
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        return true;
    }
}
