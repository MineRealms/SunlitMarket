package com.society.sunlitmarket.currency;

import com.society.sunlitmarket.SunlitMarketMod;
import com.society.sunlitmarket.core.TradeMath;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Prepared inventory settlement. An installed but failing bank never silently switches backend. */
public final class Currency {
    private static final Map<String, Integer> VALUES = new LinkedHashMap<>();
    private static Map<String, Integer> staging;
    static { resetValues(); }
    private Currency() {}
    private static Map<String,Integer> target() { return staging == null ? VALUES : staging; }
    private static void defaults(Map<String,Integer> values) {
        values.put("numismatics:spur", 1); values.put("numismatics:bevel", 8);
        values.put("numismatics:sprocket", 16); values.put("numismatics:cog", 64);
        values.put("numismatics:crown", 512); values.put("numismatics:sun", 4096);
        values.put("numismatics:neptunium_coin", 32768); values.put("numismatics:ancient_coin", 262144);
        values.put("numismatics:prismatic_coin", 16777216); values.put("minecraft:emerald", 1);
    }
    public static synchronized void beginScripts() { staging = new LinkedHashMap<>(); defaults(staging); }
    public static synchronized void endScripts(boolean valid) {
        if (staging != null && valid) { VALUES.clear(); VALUES.putAll(staging); }
        staging = null;
    }
    public static synchronized void resetValues() {
        Map<String,Integer> values=target(); values.clear(); defaults(values);
    }
    public static synchronized void setCoinValue(String id, int value) {
        if (ResourceLocation.tryParse(id) == null) throw new IllegalArgumentException("Invalid coin id");
        if (value <= 0) target().remove(id); else target().put(id, value);
    }
    public static synchronized int coinValue(String id) { return VALUES.getOrDefault(id, 0); }
    public static synchronized List<Map.Entry<String,Integer>> denominations() {
        List<Map.Entry<String,Integer>> result = new ArrayList<>(VALUES.entrySet());
        result.sort(Map.Entry.<String,Integer>comparingByValue(Comparator.reverseOrder())); return result;
    }
    private record Bank(Object account, int balance, Method setter) {}
    private static Bank bank(Player player) throws ReflectiveOperationException {
        if (!ModList.get().isLoaded("numismatics")) return null;
        Class<?> mod = Class.forName("dev.ithundxr.createnumismatics.Numismatics");
        Object bank = mod.getField("BANK").get(null);
        if (bank == null) throw new IllegalStateException("Bank is not ready");
        Object account = null;
        for (Method m : bank.getClass().getMethods()) {
            if (!m.getName().equals("getAccount") || m.getParameterCount() != 1) continue;
            if (m.getParameterTypes()[0] == java.util.UUID.class) { account = m.invoke(bank, player.getUUID()); break; }
            if (m.getParameterTypes()[0] == Player.class) { account = m.invoke(bank, player); break; }
        }
        if (account == null) throw new IllegalStateException("Bank account unavailable");
        int balance = TradeMath.amount(((Number) account.getClass().getMethod("getBalance").invoke(account)).longValue());
        return new Bank(account, balance, account.getClass().getMethod("setBalance", int.class));
    }
    /** Coins in the inventory plus the numismatics bank account; the virtual wallet is not included. */
    public static int cashBalance(Player player) {
        try { Bank b = bank(player); return b == null ? coinBalance(player) : b.balance; }
        catch (ReflectiveOperationException | RuntimeException ex) { return -1; }
    }
    /** Everything the player can spend: cash/bank plus the virtual wallet. */
    public static int balance(Player player) {
        int cash = cashBalance(player);
        if (cash < 0) return cash;
        long wallet = com.society.sunlitmarket.wallet.Wallet.balance(player);
        return (int) Math.min(Integer.MAX_VALUE, cash + wallet);
    }
    public static int coinBalance(Player player) {
        long sum = 0;
        for (ItemStack s : snapshot(player.getInventory())) if (!s.hasTag())
            sum += (long) coinValue(BuiltInRegistries.ITEM.getKey(s.getItem()).toString()) * s.getCount();
        return (int) Math.min(Integer.MAX_VALUE, sum);
    }
    public static List<ItemStack> snapshot(Container container) {
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) result.add(container.getItem(i).copy());
        return result;
    }
    private static void commit(Container c, List<ItemStack> slots) {
        for (int i = 0; i < slots.size(); i++) c.setItem(i, slots.get(i));
        c.setChanged();
    }
    public static boolean insert(List<ItemStack> slots, ItemStack template, int count, int limit) {
        if (template.isEmpty() || count <= 0) return false;
        int max = Math.min(limit, template.getMaxStackSize());
        for (int i = 0; i < slots.size() && count > 0; i++) {
            ItemStack s = slots.get(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameTags(s, template)) {
                int n = Math.min(count, Math.max(0, max - s.getCount())); s.grow(n); count -= n;
            }
        }
        for (int i = 0; i < slots.size() && count > 0; i++) if (slots.get(i).isEmpty()) {
            ItemStack s = template.copy(); int n = Math.min(count, max); s.setCount(n); slots.set(i, s); count -= n;
        }
        return count == 0;
    }
    private static boolean remove(List<ItemStack> slots, ItemStack template, int count) {
        if (count <= 0) return false;
        for (ItemStack s : slots) if (ItemStack.isSameItemSameTags(s, template)) {
            int n = Math.min(count, s.getCount()); s.shrink(n); count -= n; if (count == 0) return true;
        }
        return false;
    }
    private static boolean coins(List<ItemStack> slots, int delta) {
        if (delta < 0) {
            long paid = 0, needed = -(long) delta;
            for (ItemStack s : slots) {
                int value = s.hasTag() ? 0 : coinValue(BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
                if (value <= 0 || paid >= needed) continue;
                int n = (int) Math.min(s.getCount(), (needed - paid + value - 1L) / value);
                s.shrink(n); paid += (long) n * value;
            }
            if (paid < needed) return false;
            return mint(slots, paid - needed);
        }
        return mint(slots, delta);
    }
    private static boolean mint(List<ItemStack> slots, long amount) {
        for (var d : denominations()) {
            ResourceLocation id = ResourceLocation.tryParse(d.getKey());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) continue;
            ItemStack template = new ItemStack(BuiltInRegistries.ITEM.get(id));
            if (template.isEmpty()) continue;
            long n = amount / d.getValue();
            if (n > Integer.MAX_VALUE || (n > 0 && !insert(slots, template, (int) n, 64))) return false;
            amount -= n * d.getValue();
        }
        return amount == 0;
    }
    /** Positive moneyDelta pays the player. null source/target represents virtual stock. */
    public static String settle(Player player, Container source, Container target, ItemStack item, int count, int moneyDelta) {
        return settle(player, source, target, item, count, moneyDelta, true);
    }

    /**
     * @param useWallet when false the virtual wallet is skipped entirely, which is what a
     *                  cash-to-wallet conversion needs: otherwise the wallet would pay for its own
     *                  top-up and the fee would be pure burn.
     */
    private static String settle(Player player, Container source, Container target, ItemStack item,
                                 int count, int moneyDelta, boolean useWallet) {
        if (player.level().isClientSide) return "Server-only transaction";
        if (count < 0 || (count > 0 && item.isEmpty())) return "Invalid goods";
        Container wallet = player.getInventory();
        Map<Container,List<ItemStack>> plans = new java.util.IdentityHashMap<>();
        plans.put(wallet, snapshot(wallet));
        if (source != null) plans.computeIfAbsent(source, Currency::snapshot);
        if (target != null) plans.computeIfAbsent(target, Currency::snapshot);
        if (count > 0 && source != null && !remove(plans.get(source), item, count)) return "物品不足或物品数据不匹配";
        // Only main inventory slots accept newly delivered goods/coins, not armor/offhand.
        List<ItemStack> walletMain = plans.get(wallet).subList(0, Math.min(36, plans.get(wallet).size()));
        Bank b = null;
        boolean bankAttempted = false;
        Map<Container,List<ItemStack>> originals = new java.util.IdentityHashMap<>();
        plans.keySet().forEach(c -> originals.put(c,snapshot(c)));
        // The virtual wallet covers a debit whole or not at all: a payment is never split across
        // two backends, so a half-empty wallet behaves exactly like an absent one.
        long walletBefore = com.society.sunlitmarket.wallet.Wallet.balance(player);
        boolean walletDebit = useWallet && moneyDelta < 0
                && com.society.sunlitmarket.wallet.Wallet.enabled()
                && walletBefore >= -(long) moneyDelta;
        try {
            if (player.getPersistentData().getBoolean("sunlitmarket.bankUncertain")) return "银行需管理员核对后解锁";
            b = (moneyDelta == 0 || walletDebit) ? null : bank(player);
            int next = b == null ? 0 : TradeMath.amount((long) b.balance + moneyDelta);
            if (b == null && !walletDebit && !coins(walletMain, moneyDelta)) return "余额不足、无法精确找零或背包空间不足";
            if (count > 0 && target != null && !insert(target == wallet ? walletMain : plans.get(target), item, count, target.getMaxStackSize()))
                return "目标库存空间不足";
            if (walletDebit && !com.society.sunlitmarket.wallet.Wallet.take(player, -(long) moneyDelta))
                throw new IllegalStateException("Wallet debit was not confirmed");
            if (b != null) {
                bankAttempted = true;
                b.setter.invoke(b.account, next);
                long observed = ((Number) b.account.getClass().getMethod("getBalance").invoke(b.account)).longValue();
                if (observed != next) throw new IllegalStateException("Bank write was not confirmed");
            }
            plans.forEach(Currency::commit);
            player.getInventory().setChanged();
            return "";
        } catch (ReflectiveOperationException | RuntimeException ex) {
            originals.forEach(Currency::commit);
            if (walletDebit) com.society.sunlitmarket.wallet.Wallet.set(player, walletBefore);
            boolean restored = !bankAttempted;
            if (bankAttempted && b != null) try {
                b.setter.invoke(b.account,b.balance);
                restored = ((Number)b.account.getClass().getMethod("getBalance").invoke(b.account)).longValue() == b.balance;
            } catch (ReflectiveOperationException | RuntimeException restoreFailure) { ex.addSuppressed(restoreFailure); }
            if (!restored) player.getPersistentData().putBoolean("sunlitmarket.bankUncertain",true);
            SunlitMarketMod.LOG.error("[SunlitMarket] Settlement aborted; inventory restored, bankRestored={}", restored, ex);
            return restored ? "交易未完成，库存与余额已保留" : "银行结果不明，交易已锁定，请管理员核对";
        }
    }
    public static boolean pay(Player player, int amount) {
        return amount >= 0 && settle(player, null, null, ItemStack.EMPTY, 0, -amount).isEmpty();
    }

    /** Debits coins/bank only, never the virtual wallet. */
    public static boolean payCash(Player player, int amount) {
        return amount >= 0 && settle(player, null, null, ItemStack.EMPTY, 0, -amount, false).isEmpty();
    }
    public static void give(Player player, int amount) {
        if (amount < 0 || !settle(player, null, null, ItemStack.EMPTY, 0, amount).isEmpty())
            throw new IllegalStateException("Payment could not be completed");
    }
    public static ItemStack item(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null || !BuiltInRegistries.ITEM.containsKey(key) ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(key));
    }
    public static int countItems(Player player, String id) {
        long n = 0; ItemStack template = item(id);
        for (ItemStack s : snapshot(player.getInventory())) if (ItemStack.isSameItemSameTags(s, template)) n += s.getCount();
        return (int) Math.min(Integer.MAX_VALUE, n);
    }
    public static boolean removeItems(Player p, String id, int n) { return n > 0 && settle(p, p.getInventory(), null, item(id), n, 0).isEmpty(); }
    public static boolean giveItem(Player p, String id, int n) { return n > 0 && settle(p, null, p.getInventory(), item(id), n, 0).isEmpty(); }
}
