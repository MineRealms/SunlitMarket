package com.society.sunlitmarket.market;

import com.society.sunlitmarket.SunlitMarketMod;
import com.society.sunlitmarket.alignment.AlignmentData;
import com.society.sunlitmarket.config.SunlitMarketConfig;
import com.society.sunlitmarket.currency.Currency;
import com.society.sunlitmarket.net.MarketUpdateMessage;
import com.society.sunlitmarket.net.OpenMarketMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

public final class MarketService {
    public static final int ACTION_BUY = 0;
    public static final int ACTION_SELL = 1;

    private MarketService() {
    }

    private static String stockKey(Merchant merchant, int index) {
        return merchant.id + '#' + index;
    }

    private static int alignment(ServerPlayer player, Merchant merchant) {
        return AlignmentData.get(player.server).get(player, merchant.faction);
    }

    private static int buyPrice(ServerPlayer player, Merchant merchant, Offer offer) {
        int base = PriceModifiers.apply(offer.buy, PriceModifiers.get(merchant.id, offer.item));
        double multiplier = SunlitMarketConfig.tierFor(alignment(player, merchant)).buy;
        return Math.max(1, (int) Math.round(base * multiplier));
    }

    private static int sellPrice(ServerPlayer player, Merchant merchant, Offer offer) {
        int base = PriceModifiers.apply(offer.sell, PriceModifiers.get(merchant.id, offer.item));
        double multiplier = SunlitMarketConfig.tierFor(alignment(player, merchant)).sell;
        return Math.max(1, (int) Math.round(base * multiplier));
    }

    private static void grantReputation(ServerPlayer player, Merchant merchant, Offer offer, int qty) {
        if (offer.repGain <= 0) return;
        AlignmentData.get(player.server).add(player, merchant.faction, offer.repGain * qty);
    }

    public static MarketView view(ServerPlayer player, Merchant merchant) {
        MarketView view = new MarketView();
        view.merchantId = merchant.id;
        view.merchantName = merchant.name;
        view.faction = merchant.faction;
        view.balance = Currency.balance(player);
        view.reputation = AlignmentData.get(player.server).get(player, merchant.faction);
        view.title = SunlitMarketConfig.tierFor(view.reputation).title;
        long day = player.server.overworld().getDayTime() / 24000L;
        StockData stockData = StockData.get(player.server);
        stockData.roll(day);
        LimitData limitData = LimitData.get(player.server);
        limitData.roll(day);
        for (int i = 0; i < merchant.offers.size(); i++) {
            Offer offer = merchant.offers.get(i);
            MarketView.Line line = new MarketView.Line();
            line.item = offer.item;
            line.count = offer.count;
            line.buy = buyPrice(player, merchant, offer);
            line.sell = sellPrice(player, merchant, offer);
            line.reputation = offer.reputation;
            line.stock = stockData.get(stockKey(merchant, i), offer.stock);
            line.limit = offer.limit;
            line.used = limitData.used(player, stockKey(merchant, i));
            line.unlock = offer.unlock == null ? "" : offer.unlock;
            line.locked = !UnlockData.get(player.server).has(player, offer.unlock);
            view.offers.add(line);
        }
        return view;
    }

    public static void open(ServerPlayer player, Merchant merchant) {
        SunlitMarketMod.LOG.info("[SunlitMarket] opening {} for {}", merchant.id, player.getGameProfile().getName());
        SunlitMarketMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenMarketMessage(view(player, merchant).toJson()));
    }

    public static boolean handleAction(ServerPlayer player, String merchantId, int index, int action, int quantity) {
        Merchant merchant = MarketRegistry.get(merchantId);
        if (merchant == null) return false;
        if (index < 0 || index >= merchant.offers.size()) return false;
        Offer offer = merchant.offers.get(index);
        int qty = Math.max(1, Math.min(64, quantity));
        String error = null;
        int reputation = AlignmentData.get(player.server).get(player, merchant.faction);
        long day = player.server.overworld().getDayTime() / 24000L;
        StockData stockData = StockData.get(player.server);
        stockData.roll(day);
        LimitData limitData = LimitData.get(player.server);
        limitData.roll(day);
        String key = stockKey(merchant, index);
        if (!UnlockData.get(player.server).has(player, offer.unlock)) {
            error = "尚未解锁：" + offer.unlock;
        } else if (reputation < offer.reputation) {
            error = "声望不足：需要 " + offer.reputation;
        } else if (action == ACTION_BUY) {
            int unit = buyPrice(player, merchant, offer);
            if (unit <= 0) {
                error = "这个商人不出售该物品";
            } else if (offer.stock >= 0 && stockData.get(key, offer.stock) < qty) {
                error = "库存不足";
            } else if (!limitData.canBuy(player, key, offer.limit, qty)) {
                error = "已达到每日限购";
            } else {
                int total = unit * qty;
                if (!Currency.pay(player, total)) {
                    error = "余额不足：需要 " + total + " spur";
                } else if (!Currency.giveItem(player, offer.item, offer.count * qty)) {
                    Currency.give(player, total);
                    error = "物品不存在：" + offer.item;
                } else {
                    stockData.take(key, offer.stock, qty);
                    limitData.add(player, key, qty);
                    grantReputation(player, merchant, offer, qty);
                }
            }
        } else if (action == ACTION_SELL) {
            int unit = sellPrice(player, merchant, offer);
            if (unit <= 0) {
                error = "这个商人不收购该物品";
            } else {
                int count = offer.count * qty;
                if (!Currency.removeItems(player, offer.item, count)) {
                    error = "物品不足：需要 " + count + " 个 " + offer.item;
                } else {
                    Currency.give(player, unit * qty);
                    grantReputation(player, merchant, offer, qty);
                }
            }
        } else {
            error = "未知操作";
        }
        if (error != null) {
            SunlitMarketMod.LOG.info("[SunlitMarket] {} rejected {}#{} {}x{}: {}",
                    player.getGameProfile().getName(), merchant.id, index,
                    action == ACTION_BUY ? "buy" : "sell", qty, error);
        }
        String message = error == null ? "" : error;
        SunlitMarketMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new MarketUpdateMessage(view(player, merchant).toJson(), message));
        return error == null;
    }
}
