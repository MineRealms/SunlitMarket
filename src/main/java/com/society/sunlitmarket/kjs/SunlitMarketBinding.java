package com.society.sunlitmarket.kjs;

import com.society.sunlitmarket.SunlitMarketMod;
import com.society.sunlitmarket.alignment.AlignmentData;
import com.society.sunlitmarket.alignment.FactionRelations;
import com.society.sunlitmarket.config.SunlitMarketConfig;
import com.society.sunlitmarket.currency.Currency;
import com.society.sunlitmarket.market.MarketRegistry;
import com.society.sunlitmarket.market.PriceModifiers;
import com.society.sunlitmarket.market.MarketData;
import com.society.sunlitmarket.market.MarketService;
import com.society.sunlitmarket.market.Merchant;
import com.society.sunlitmarket.market.UnlockData;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.NativeJSON;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Global {@code SunlitMarket} binding for KubeJS server scripts.
 * All entry points swallow exceptions so a broken script cannot take down the server.
 */
public final class SunlitMarketBinding {

    /** SunlitMarket.merchant({ id, name, faction, npcType, tag, offers: [...] }). */
    public void merchant(Object spec) {
        try {
            MarketRegistry.addScriptMerchant(toJson(spec));
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] SunlitMarket.merchant failed", t);
        }
    }

    /** SunlitMarket.offer("merchant_id", { item, count, buy, sell, reputation }). */
    public void offer(String merchantId, Object spec) {
        try {
            if (merchantId != null) MarketRegistry.addScriptOffer(merchantId, toJson(spec));
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] SunlitMarket.offer failed", t);
        }
    }

    /** Remove all script-defined merchants; call at the top of your reload script. */
    public void clear() {
        try {
            MarketRegistry.clearScript();
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] SunlitMarket.clear failed", t);
        }
    }

    /**
     * SunlitMarket.index("sv.ore", "矿指", [["me_erebor_miner", "minecraft:raw_iron"], ...]).
     * Members bind to a merchant offer; the index measures modifier x shelf-scarcity against
     * the static script base. Candles persist across reloads; membership is replaced.
     */
    public boolean index(String id, Object name, Object members) {
        try {
            java.util.List<MarketData.Member> list = new java.util.ArrayList<>();
            if (members instanceof Iterable<?> rows) {
                for (Object row : rows) {
                    if (row instanceof Iterable<?> pair) {
                        java.util.Iterator<?> it = pair.iterator();
                        if (it.hasNext()) {
                            String merchant = String.valueOf(it.next());
                            String item = it.hasNext() ? String.valueOf(it.next()) : "";
                            if (!merchant.isBlank() && !item.isBlank()) {
                                list.add(new MarketData.Member(merchant, item));
                            }
                        }
                    }
                }
            }
            if (id == null || id.isBlank() || list.isEmpty()) {
                SunlitMarketMod.LOG.warn("[SunlitMarket] SunlitMarket.index({}) has no members", id);
                return false;
            }
            com.society.sunlitmarket.market.PendingIndices.put(id, name == null ? id : String.valueOf(name), list);
            net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                SunlitMarketMod.LOG.info("[SunlitMarket] index {} buffered until a world is loaded", id);
                return true;
            }
            com.society.sunlitmarket.market.PendingIndices.applyTo(server);
            return true;
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] SunlitMarket.index failed", t);
        }
        return false;
    }

    /** Drops every buffered index definition; call at the top of a script that defines indices. */
    public void clearIndices() {
        com.society.sunlitmarket.market.PendingIndices.clear();
    }

    // ---- alignment ------------------------------------------------------------------
    // setReputation/addReputation/getReputation are kept as aliases so existing pack
    // scripts keep working; alignment is the same number, with a negative range and a
    // diplomatic backlash applied by addAlignment.

    public void setReputation(Player player, String faction, int value) {
        setAlignment(player, faction, value);
    }

    public void addReputation(Player player, String faction, int delta) {
        addAlignment(player, faction, delta);
    }

    public int getReputation(Player player, String faction) {
        return alignment(player, faction);
    }

    public int alignment(Player player, String faction) {
        try {
            if (player instanceof ServerPlayer serverPlayer) {
                return AlignmentData.get(serverPlayer.server).get(player, faction);
            }
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] alignment failed", t);
        }
        return 0;
    }

    public void setAlignment(Player player, String faction, int value) {
        try {
            if (player instanceof ServerPlayer serverPlayer && faction != null) {
                AlignmentData.get(serverPlayer.server).set(player, faction, value);
            }
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] setAlignment failed", t);
        }
    }

    /** Adds alignment and applies the configured cross-faction backlash. */
    public void addAlignment(Player player, String faction, int delta) {
        try {
            if (player instanceof ServerPlayer serverPlayer && faction != null) {
                AlignmentData.get(serverPlayer.server).add(player, faction, delta);
            }
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] addAlignment failed", t);
        }
    }

    /** Current tier title for a faction, e.g. 常客. Empty when no tier matches. */
    public String title(Player player, String faction) {
        try {
            return SunlitMarketConfig.tierFor(alignment(player, faction)).title;
        } catch (Throwable t) {
            return "";
        }
    }

    public int wanted(Player player) {
        try {
            if (player instanceof ServerPlayer serverPlayer) {
                return AlignmentData.get(serverPlayer.server).wanted(player);
            }
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] wanted failed", t);
        }
        return 0;
    }

    public void addWanted(Player player, int delta) {
        try {
            if (player instanceof ServerPlayer serverPlayer) {
                AlignmentData.get(serverPlayer.server).addWanted(player, delta);
            }
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] addWanted failed", t);
        }
    }

    /** Middle-earth npc type -> faction id, longest prefix match. Blank when unknown. */
    public String factionOf(String npcType) {
        try {
            return FactionRelations.factionOfNpcType(npcType);
        } catch (Throwable t) {
            return "";
        }
    }

    /** Every faction id discovered in the Middle-earth data pack. */
    public String factions() {
        try {
            java.util.List<String> ids = new java.util.ArrayList<>(FactionRelations.factions());
            java.util.Collections.sort(ids);
            return String.join(",", ids);
        } catch (Throwable t) {
            return "";
        }
    }

    /** Grants a per-player unlock id (quest stage / themed merchant access). */
    public boolean unlock(Player player, String id) {
        try {
            if (player instanceof ServerPlayer serverPlayer && id != null && !id.isBlank()) {
                return UnlockData.get(serverPlayer.server).grant(player, id);
            }
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] unlock failed", t);
        }
        return false;
    }

    public boolean revoke(Player player, String id) {
        try {
            if (player instanceof ServerPlayer serverPlayer && id != null && !id.isBlank()) {
                return UnlockData.get(serverPlayer.server).revoke(player, id);
            }
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] revoke failed", t);
        }
        return false;
    }

    public boolean isUnlocked(Player player, String id) {
        try {
            if (player instanceof ServerPlayer serverPlayer) {
                return UnlockData.get(serverPlayer.server).has(player, id);
            }
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] isUnlocked failed", t);
        }
        return false;
    }

    public void open(Player player, String merchantId) {
        try {
            if (player instanceof ServerPlayer serverPlayer) {
                Merchant merchant = MarketRegistry.get(merchantId);
                if (merchant != null) MarketService.open(serverPlayer, merchant);
            }
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] open failed", t);
        }
    }

    /** Merchant id that this entity resolves to (tag / NpcData.Type / ForgeData), or "" if none. */
    public String resolveId(Object entity) {
        try {
            if (entity instanceof net.minecraft.world.entity.Entity target) {
                Merchant merchant = MarketRegistry.resolve(target);
                return merchant == null ? "" : merchant.id;
            }
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] resolveId failed", t);
        }
        return "";
    }

    /** Raw NpcData.Type read from an entity's NBT (empty when absent). */
    public String npcType(Object entity) {
        try {
            if (entity instanceof net.minecraft.world.entity.Entity target) {
                return MarketRegistry.readNpcType(target);
            }
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] npcType failed", t);
        }
        return "";
    }

    /** Current server-side view for a merchant (balance, stock, limits, locks) as JSON. */
    public String view(Player player, String merchantId) {
        try {
            if (player instanceof ServerPlayer serverPlayer) {
                Merchant merchant = MarketRegistry.get(merchantId);
                if (merchant != null) return MarketService.view(serverPlayer, merchant).toJson();
            }
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] view failed", t);
        }
        return "{}";
    }

    /** Script-side purchase, useful for contracts/quests. Returns true on success. */
    public boolean buy(Player player, String merchantId, int offerIndex, int quantity) {
        try {
            if (player instanceof ServerPlayer serverPlayer) {
                return MarketService.handleAction(serverPlayer, merchantId, offerIndex, MarketService.ACTION_BUY, quantity);
            }
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] buy failed", t);
        }
        return false;
    }

    /** Script-side sale, useful for contracts/quests. Returns true on success. */
    public boolean sell(Player player, String merchantId, int offerIndex, int quantity) {
        try {
            if (player instanceof ServerPlayer serverPlayer) {
                return MarketService.handleAction(serverPlayer, merchantId, offerIndex, MarketService.ACTION_SELL, quantity);
            }
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] sell failed", t);
        }
        return false;
    }

    /** Multiplies both buy/sell prices for one merchant item; 1.0 = normal. */
    public void priceMultiplier(String merchantId, String item, double multiplier) {
        try {
            PriceModifiers.set(merchantId, item, multiplier);
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] priceMultiplier failed", t);
        }
    }

    public void clearPriceMultipliers() {
        PriceModifiers.clear();
    }

    /** Override a coin item's value in spurs; 0 removes it. */
    public void coinValue(String item, int value) {
        Currency.setCoinValue(item, value);
    }

    public int balance(Player player) {
        return Currency.balance(player);
    }

    // ---- acceptance bin price table ---------------------------------------------------
    // Everything below writes through DepositConfig, which owns config/sunlitmarket/
    // deposit_values.json. Edits are persisted on the next save tick, so a script stays the
    // single source of truth for as long as it keeps running.

    /** SunlitMarket.depositPrice("minecraft:iron_ingot", 30) - explicit spur each; 0 refuses it. */
    public void depositPrice(String itemId, int spur) {
        try {
            com.society.sunlitmarket.deposit.DepositConfig.setItemPrice(itemId, spur);
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] SunlitMarket.depositPrice failed", t);
        }
    }

    /** SunlitMarket.depositFluidRate("gtceu:diesel", 8) - spur per bucket. */
    public void depositFluidRate(String fluidId, int perBucket) {
        try {
            com.society.sunlitmarket.deposit.DepositConfig.setFluidRate(fluidId, perBucket);
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] SunlitMarket.depositFluidRate failed", t);
        }
    }

    /** SunlitMarket.depositAccept("essentia", false) - channel is item/fluid/energy/essentia/mana. */
    public void depositAccept(String channel, boolean allowed) {
        try {
            com.society.sunlitmarket.deposit.DepositConfig.setAccepted(channel, allowed);
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] SunlitMarket.depositAccept failed", t);
        }
    }

    /** Effective spur value of one item id as the bin prices it right now; -1 when unpriced. */
    public int depositItemValue(String itemId) {
        try {
            Integer price = com.society.sunlitmarket.deposit.DepositConfig.itemPrice(itemId);
            if (price != null) return price;
            net.minecraft.world.item.ItemStack stack = Currency.item(itemId);
            return stack.isEmpty() ? -1 : com.society.sunlitmarket.deposit.DepositValues.itemValue(stack);
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] SunlitMarket.depositItemValue failed", t);
        }
        return -1;
    }

    /** SunlitMarket.depositBudget(100000, 20000) - daily world cap and per-player cap; 0 = uncapped. */
    public void depositBudget(int dailyCap, int playerCap) {
        try {
            com.society.sunlitmarket.deposit.DepositConfig.setBuyback(dailyCap, playerCap);
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] SunlitMarket.depositBudget failed", t);
        }
    }

    /** SunlitMarket.depositBudgetEnabled(false) turns the daily cap off entirely. */
    public void depositBudgetEnabled(boolean enabled) {
        try {
            com.society.sunlitmarket.deposit.DepositConfig.setBuybackEnabled(enabled);
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] SunlitMarket.depositBudgetEnabled failed", t);
        }
    }

    /** Re-reads deposit_values.json; script overrides made before the call are discarded. */
    public void depositReload() {
        try {
            com.society.sunlitmarket.deposit.DepositConfig.load();
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] SunlitMarket.depositReload failed", t);
        }
    }

    // ---- virtual wallet ---------------------------------------------------------------

    /** Wallet balance in spur. */
    public long wallet(Player player) {
        try {
            return com.society.sunlitmarket.wallet.Wallet.balance(player);
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] SunlitMarket.wallet failed", t);
        }
        return 0L;
    }

    /** Credits the wallet directly; use for quest rewards that should not become coins. */
    public boolean walletAdd(Player player, long amount) {
        try {
            Long auditBefore = com.society.sunlitmarket.audit.EconomyAudit.walletBefore(player);
            boolean ok = com.society.sunlitmarket.wallet.Wallet.add(player, amount);
            com.society.sunlitmarket.audit.EconomyAudit.walletAfter(player, auditBefore, amount, ok, "KJS_WALLET_ADD");
            return ok;
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] SunlitMarket.walletAdd failed", t);
        }
        return false;
    }

    /** Debits the wallet; false and no change when the balance is short. */
    public boolean walletTake(Player player, long amount) {
        try {
            Long auditBefore = com.society.sunlitmarket.audit.EconomyAudit.walletBefore(player);
            boolean ok = com.society.sunlitmarket.wallet.Wallet.take(player, amount);
            com.society.sunlitmarket.audit.EconomyAudit.walletAfter(player, auditBefore, -amount, ok, "KJS_WALLET_TAKE");
            return ok;
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] SunlitMarket.walletTake failed", t);
        }
        return false;
    }

    /** Converts the player's cash/bank into the wallet at the configured fee. */
    public String walletDeposit(Player player) {
        try {
            return com.society.sunlitmarket.wallet.WalletExchange.deposit(player);
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] SunlitMarket.walletDeposit failed", t);
        }
        return "钱包存入失败";
    }

    /** Converts wallet balance back into cash/bank at the configured fee. */
    public String walletWithdraw(Player player, long amount) {
        try {
            return com.society.sunlitmarket.wallet.WalletExchange.withdraw(player, amount);
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] SunlitMarket.walletWithdraw failed", t);
        }
        return "钱包取出失败";
    }

    /** Pawn cash, reserve, burned fees and per-source flows as JSON; "{}" with no server running. */
    public String treasury() {
        try {
            var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            return server == null ? "{}" : com.society.sunlitmarket.treasury.Treasury.status(server).toString();
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] SunlitMarket.treasury failed", t);
        }
        return "{}";
    }

    /**
     * Administrator issuance into "cash" or "reserve"; a negative delta retires money but never
     * below zero. Returns the delta actually applied.
     */
    public long treasuryInject(String account, long delta) {
        try {
            var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server == null) return 0L;
            var target = "cash".equalsIgnoreCase(account) ? com.society.sunlitmarket.treasury.TreasuryData.Account.CASH
                    : com.society.sunlitmarket.treasury.TreasuryData.Account.RESERVE;
            if (!"cash".equalsIgnoreCase(account) && !"reserve".equalsIgnoreCase(account)) return 0L;
            long applied = com.society.sunlitmarket.treasury.Treasury.data(server).inject(target, delta);
            SunlitMarketMod.LOG.info("[SunlitMarket] script treasury inject {} {} (applied {})", target, delta, applied);
            return applied;
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] SunlitMarket.treasuryInject failed", t);
        }
        return 0L;
    }

    /**
     * Opens futures on an index: {index, multiplier, tenorDays, band, feeBps, maxLots, minTraders,
     * minBuckets, windowTicks, unlock}; everything but index has a default.
     */
    public boolean futures(Object spec) {
        try {
            com.society.sunlitmarket.futures.FuturesSpecs.add(toJson(spec));
            return true;
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] SunlitMarket.futures rejected", t);
        }
        return false;
    }

    /** Drops every script futures spec; existing offers and contracts keep their terms. */
    public void clearFutures() {
        com.society.sunlitmarket.futures.FuturesSpecs.clearScripts();
    }

    /** Read-only order book for one index (or all when blank) as JSON. */
    public String futuresJson(String index) {
        try {
            var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            return server == null ? "{}" : com.society.sunlitmarket.futures.FuturesService.book(server, index).toString();
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] SunlitMarket.futuresJson failed", t);
        }
        return "{}";
    }

    /** How many of an item id the player carries (scripts, quest checks). */
    public int countItems(Player player, String itemId) {
        try {
            return Currency.countItems(player, itemId);
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] countItems failed", t);
        }
        return 0;
    }

    /** Reload config/sunlitmarket/merchants.json. Script merchants are kept. */
    public void reload() {
        MarketRegistry.loadJson();
    }

    public String merchants() {
        return MarketRegistry.toJson(MarketRegistry.all());
    }

    public void rule(Object spec) { com.society.sunlitmarket.core.MarketRules.add(toJson(spec)); }
    public void clearRules() { com.society.sunlitmarket.core.MarketRules.clearScripts(); }
    public int heat(net.minecraft.world.item.ItemStack stack) { return com.society.sunlitmarket.crime.CrimeService.heat(stack); }
    public void markStolen(net.minecraft.world.item.ItemStack stack, int heat) {
        var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if(server!=null) com.society.sunlitmarket.crime.CrimeService.mark(stack,heat,server.overworld().getDayTime()/24000L,"");
    }
    public String owner(Player player) { return AlignmentData.ownerKey(player); }
    /** Saved snapshot only, not a live authoritative allegiance assertion. */
    public String playerFaction(Player player) { return player instanceof ServerPlayer p ? com.society.sunlitmarket.compat.MiddleEarthCompat.savedFaction(p) : "UNKNOWN"; }
    public String identity(net.minecraft.world.entity.Entity entity) {
        var i=com.society.sunlitmarket.core.MarketRules.identity(entity);
        return i.faction()+" / "+i.rank()+" / customer="+i.customer()+" / enforcer="+i.enforcer();
    }
    public double regionWeight(net.minecraft.world.entity.Entity entity,String faction) { return FactionRelations.regionWeight(faction,entity); }

    private static String toJson(Object value) {
        if (value instanceof CharSequence text) return text.toString();
        Context context = Context.enter();
        Object json = NativeJSON.stringify(value, null, null, context);
        return json == null ? "{}" : json.toString();
    }
}
