package com.society.sunlitmarket.market;

import com.society.sunlitmarket.SunlitMarketMod;
import com.society.sunlitmarket.alignment.AlignmentData;
import com.society.sunlitmarket.compat.FtbTeamsCompat;
import com.society.sunlitmarket.config.CoreConfig;
import com.society.sunlitmarket.config.SunlitMarketConfig;
import com.society.sunlitmarket.core.TradeMath;
import com.society.sunlitmarket.currency.Currency;
import com.society.sunlitmarket.net.MarketUpdateMessage;
import com.society.sunlitmarket.net.OpenMarketMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.PacketDistributor;

public final class MarketService {
    public static final int ACTION_BUY = 0, ACTION_SELL = 1;
    private MarketService() {}
    static String stockKey(Merchant merchant, int index) {
        Offer offer = merchant.offers.get(index);
        return merchant.id + '#' + (offer.id.isBlank() ? Integer.toString(index) : "id:" + offer.id);
    }
    private static String legacyStockKey(Merchant merchant, int index) {
        Offer offer = merchant.offers.get(index);
        return offer.id.isBlank() || offer.legacyIndex < 0 ? null : merchant.id + '#' + offer.legacyIndex;
    }
    public record Quote(int buy, int sell) {}
    public static Quote quote(ServerPlayer p, Merchant m, Offer offer) { return quote(p, m, offer, -1); }
    public static Quote quote(ServerPlayer p, Merchant m, Offer offer, int index) {
        int alignment = AlignmentData.get(p.server).get(p, m.faction);
        var tier = SunlitMarketConfig.tierFor(alignment);
        double scale = PriceModifiers.get(m.id, offer.item);
        boolean fixed = offer.fixedPrice || Currency.coinValue(offer.item) > 0;
        double penalty = !fixed && CoreConfig.WANTED_PRICES.get()
                ? 1D + AlignmentData.get(p.server).wanted(p) * CoreConfig.WANTED_SURCHARGE.get() : 1D;
        double scarcity = 1.0D;
        if (!fixed && index >= 0 && offer.stock >= 0 && CoreConfig.DYNAMIC_PRICING.get()) {
            int shelf = StockData.get(p.server).get(stockKey(m, index), legacyStockKey(m, index), offer.stock);
            scarcity = PriceEngine.scarcity(shelf, offer.stock, CoreConfig.INVENTORY_ELASTICITY.get(),
                    CoreConfig.PRICE_MIN_MULT.get(), CoreConfig.PRICE_MAX_MULT.get());
        }
        int buy = TradeMath.price(PriceModifiers.apply(offer.buy, fixed ? 1D : scale), fixed ? 1D : tier.buy * penalty * scarcity);
        int sell = TradeMath.price(PriceModifiers.apply(offer.sell, fixed ? 1D : scale), fixed ? 1D : tier.sell * scarcity / penalty);
        if (CoreConfig.PREVENT_ARBITRAGE.get() && !offer.allowArbitrage && buy > 0) sell = Math.min(sell, buy);
        return new Quote(buy, sell);
    }
    public static MarketView view(ServerPlayer p, Merchant m) {
        MarketView v = new MarketView(); v.merchantId = m.id; v.merchantName = m.name; v.faction = m.faction;
        v.balance = Currency.balance(p); v.reputation = AlignmentData.get(p.server).get(p, m.faction);
        v.title = SunlitMarketConfig.tierFor(v.reputation).title;
        TradeSessions.Session session = TradeSessions.get(p);
        if (session != null && session.merchant.equals(m.id)) { v.session = session.id.toString(); v.sequence = session.sequence; }
        long day = p.server.overworld().getDayTime() / 24000L;
        StockData stock = StockData.get(p.server); stock.roll(day);
        LimitData limits = LimitData.get(p.server); limits.roll(day);
        for (int i = 0; i < m.offers.size(); i++) {
            Offer offer = m.offers.get(i); MarketView.Line line = new MarketView.Line();
            line.item = offer.item; line.count = offer.count; line.offerId = offer.id.isBlank() ? "legacy:" + i : offer.id;
            Quote q;
            try { q = quote(p, m, offer, i); } catch (IllegalArgumentException ex) { q = new Quote(0, 0); }
            line.buy = q.buy; line.sell = q.sell; line.reputation = offer.reputation;
            line.hasReputation = offer.hasReputation;
            line.stock = stock.get(stockKey(m, i), legacyStockKey(m, i), offer.stock); line.limit = offer.limit;
            line.used = limits.used(p, stockKey(m, i), legacyStockKey(m, i)); line.unlock = offer.unlock == null ? "" : offer.unlock;
            line.locked = !UnlockData.get(p.server).has(p, offer.unlock) || v.reputation < CoreConfig.REFUSE_BELOW.get()
                    || (offer.hasReputation && v.reputation < offer.reputation);
            v.offers.add(line);
        }
        return v;
    }
    /** Trusted scripts/admin commands may open a virtual shop. Clients cannot request this path. */
    public static void open(ServerPlayer p, Merchant m) { open(p, m, null); }
    public static void open(ServerPlayer p, Merchant m, Entity entity) {
        TradeSessions.open(p, m.id, entity);
        SunlitMarketMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new OpenMarketMessage(view(p, m).toJson()));
    }
    public static void networkAction(ServerPlayer p, String merchant, int index, int action, int quantity,
                                     String session, long sequence, String offerId, int expectedBuy, int expectedSell) {
        if (action == -1) { if (TradeSessions.get(p) != null && TradeSessions.get(p).id.toString().equals(session)) TradeSessions.close(p); return; }
        if (!TradeSessions.authorize(p, session, sequence, merchant)) return;
        Merchant m = MarketRegistry.get(merchant);
        if (m == null || index < 0 || index >= m.offers.size()) return;
        Offer offer = m.offers.get(index);
        String id = offer.id.isBlank() ? "legacy:" + index : offer.id;
        Quote q;
        try { q = quote(p, m, offer, index); } catch (IllegalArgumentException ex) { update(p,m,"报价超出范围"); return; }
        if (!id.equals(offerId) || q.buy != expectedBuy || q.sell != expectedSell) { update(p, m, "报价已变化，请重新确认"); return; }
        handleAction(p, merchant, index, action, quantity);
    }
    public static boolean handleAction(ServerPlayer p, String merchantId, int index, int action, int quantity) {
        if (com.society.sunlitmarket.core.MarketHooks.dispatching()) return false;
        Merchant m = MarketRegistry.get(merchantId);
        if (m == null || index < 0 || index >= m.offers.size()) return false;
        String error;
        try { error = transact(p, m, index, action, quantity); }
        catch (IllegalArgumentException | IllegalStateException ex) { error = "交易参数无效或后端不可用"; SunlitMarketMod.LOG.warn("[SunlitMarket] Trade refused", ex); }
        update(p, m, error); return error.isEmpty();
    }
    private static String transact(ServerPlayer p, Merchant m, int index, int action, int qty) {
        if (qty < 1 || qty > 64 || (action != ACTION_BUY && action != ACTION_SELL)) return "无效交易数量或方向";
        if (!FtbTeamsCompat.resolve(p).available()) return "队伍服务不可用";
        Offer offer = m.offers.get(index);
        if (!offer.kind.equals("stock")) return "此条目不是货架商品";
        int alignment = AlignmentData.get(p.server).get(p, m.faction);
        if (!UnlockData.get(p.server).has(p, offer.unlock)) return "尚未解锁：" + offer.unlock;
        if (alignment < CoreConfig.REFUSE_BELOW.get()) return "该派系拒绝交易";
        if (offer.hasReputation && alignment < offer.reputation) return "结盟值不足：需要 " + offer.reputation;
        Quote q = quote(p, m, offer, index); int unit = action == ACTION_BUY ? q.buy : q.sell;
        if (unit <= 0) return "该交易方向未开放";
        int total = TradeMath.amount((long) unit * qty), count = TradeMath.amount((long) offer.count * qty);
        int reward = TradeMath.amount((long) offer.repGain * qty);
        long day = p.server.overworld().getDayTime() / 24000L;
        StockData stock = StockData.get(p.server); stock.roll(day); LimitData limits = LimitData.get(p.server); limits.roll(day);
        String key = stockKey(m, index), legacyKey = legacyStockKey(m, index);
        if (action == ACTION_BUY && offer.stock >= 0 && stock.get(key, legacyKey, offer.stock) < qty) return "库存不足";
        if (action == ACTION_BUY) limits.used(p, key, legacyKey);
        if (action == ACTION_BUY && !limits.canBuy(p, key, offer.limit, qty)) return "已达到每日限购";
        var hook = com.society.sunlitmarket.core.MarketHooks.fire(new com.society.sunlitmarket.core.MarketHooks.Event(
                "beforeTrade",p,null,m.faction,key,Currency.item(offer.item),total));
        if (hook.cancelled) return "交易被脚本取消";
        if (hook.price != total) return "货架价格变更请使用价格倍率接口并重新确认";
        String result = Currency.settle(p, action == ACTION_SELL ? p.getInventory() : null,
                action == ACTION_BUY ? p.getInventory() : null, Currency.item(offer.item), count,
                action == ACTION_BUY ? -total : total);
        if (!result.isEmpty()) return result;
        if (action == ACTION_BUY) { stock.take(key, offer.stock, qty); limits.add(p, key, qty); }
        if (action == ACTION_SELL && offer.stock >= 0) { stock.add(key, offer.stock, qty, CoreConfig.SELL_OVERSTOCK.get()); }
        if (reward > 0) AlignmentData.get(p.server).add(p, m.faction, reward);
        int tier=com.society.sunlitmarket.core.MarketRules.contraband(Currency.item(offer.item),m.faction,p.level().dimension().location().toString());
        if(tier>0)AlignmentData.get(p.server).addWanted(p,tier*CoreConfig.CONTRABAND_WANTED.get());
        com.society.sunlitmarket.core.MarketHooks.fire(new com.society.sunlitmarket.core.MarketHooks.Event(
                "afterTrade",p,null,m.faction,key,Currency.item(offer.item),total));
        return "";
    }
    private static void update(ServerPlayer p, Merchant m, String error) {
        SunlitMarketMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new MarketUpdateMessage(view(p, m).toJson(), error));
    }
}
