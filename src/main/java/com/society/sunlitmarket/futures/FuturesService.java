package com.society.sunlitmarket.futures;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.society.sunlitmarket.SunlitMarketMod;
import com.society.sunlitmarket.audit.EconomyAudit;
import com.society.sunlitmarket.compat.FtbTeamsCompat;
import com.society.sunlitmarket.core.EconomyClock;
import com.society.sunlitmarket.currency.Currency;
import com.society.sunlitmarket.market.FillMath;
import com.society.sunlitmarket.market.MarketData;
import com.society.sunlitmarket.market.UnlockData;
import com.society.sunlitmarket.treasury.Treasury;
import com.society.sunlitmarket.wallet.Wallet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Player-facing futures operations. Players trade with each other: the maker escrows collateral
 * when posting, the taker when matching, and at expiry the escrow is split by the settlement
 * price. The server only holds the escrow and burns fees; it is never a counterparty.
 *
 * <p>Server thread only. Every payment out goes through the claims ledger first, so an offline or
 * inventory-full player keeps their money until {@link #deliver} succeeds.
 */
public final class FuturesService {
    private FuturesService() {
    }

    public static FuturesData data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FuturesData::load, FuturesData::new, FuturesData.NAME);
    }

    // ------------------------------------------------------------ trading

    public static String offer(ServerPlayer p, String index, boolean isLong, int lots, int price) {
        FuturesSpec spec = FuturesSpecs.get(index);
        if (spec == null) return "该指数未开放期货：" + index;
        String gate = gate(p, spec);
        if (!gate.isEmpty()) return gate;
        if (lots < 1 || lots > spec.maxLots()) return "手数必须在 1.." + spec.maxLots();
        long now = EconomyClock.tick(p.server);
        long expiryDay = FuturesMath.expiryDay(now, spec.tenorDays(), spec.windowTicks());
        String band = withinBand(p.server, index, price, spec.band(), now);
        if (!band.isEmpty()) return band;
        long[] bounds = FuturesMath.bounds(price, spec.band());
        if (price - bounds[0] < 1 || bounds[1] - price < 1) return "价格过低，无法形成价带";
        FuturesData data = data(p.server);
        String owner = FtbTeamsCompat.owner(p).key();
        if (data.position(owner, index, expiryDay) + lots > spec.maxLots()) return "超过该系列持仓上限 " + spec.maxLots() + " 手";
        FuturesData.Terms terms = new FuturesData.Terms(index, expiryDay, price, bounds[0], bounds[1], spec.multiplier(),
                spec.band(), spec.minTraders(), spec.minBuckets(), spec.windowTicks());
        long fee = FuturesMath.feePerLot(price, spec.multiplier(), spec.feeBps());
        long cost = lots * (FuturesMath.collateralPerLot(isLong, price, bounds[0], bounds[1], spec.multiplier()) + fee);
        if (cost > Integer.MAX_VALUE) return "金额过大";
        if (!Currency.pay(p, (int) cost, "FUTURES_ESCROW")) return "托管金支付失败（需 " + cost + "）";
        FuturesData.Offer o = data.post(p.getUUID(), owner, p.getGameProfile().getName(), isLong, lots, terms, fee, now);
        SunlitMarketMod.LOG.info("[SunlitMarket] futures offer #{} {} {} {}x@{} by {} escrow {}", o.id, terms.series(),
                isLong ? "long" : "short", lots, price, p.getUUID(), cost);
        return "挂单 #" + o.id + " " + terms.series() + " " + (isLong ? "多" : "空") + " " + lots + " 手 @" + price
                + "，托管 " + cost + "（价带 " + bounds[0] + "–" + bounds[1] + "）";
    }

    public static String take(ServerPlayer p, int id, int lots) {
        FuturesData data = data(p.server);
        FuturesData.Offer o = data.offer(id);
        if (o == null) return "挂单不存在或已成交";
        FuturesSpec spec = FuturesSpecs.get(o.terms.index());
        if (spec == null) return "该指数期货已停止交易";
        String gate = gate(p, spec);
        if (!gate.isEmpty()) return gate;
        long now = EconomyClock.tick(p.server);
        if (now >= o.terms.closeTick()) return "该系列已进入结算窗口，停止成交";
        String owner = FtbTeamsCompat.owner(p).key();
        if (p.getUUID().equals(o.maker) || owner.equals(o.makerOwner)) return "不能与自己或同一队伍成交";
        if (lots < 1 || lots > o.lots) return "手数必须在 1.." + o.lots;
        String band = withinBand(p.server, o.terms.index(), o.terms.price(), o.terms.band(), now);
        if (!band.isEmpty()) return band;
        if (data.position(owner, o.terms.index(), o.terms.expiryDay()) + lots > spec.maxLots())
            return "超过该系列持仓上限 " + spec.maxLots() + " 手";
        long cost = FuturesData.takeCost(o, lots);
        if (cost > Integer.MAX_VALUE) return "金额过大";
        if (!Currency.pay(p, (int) cost, "FUTURES_ESCROW")) return "托管金支付失败（需 " + cost + "）";
        String series = o.terms.series();
        int price = o.terms.price();
        boolean takerLong = !o.isLong;
        long burned = data.take(o, lots, p.getUUID(), owner, now);
        Treasury.burn(p.server, burned, "FUTURES_FEE");
        SunlitMarketMod.LOG.info("[SunlitMarket] futures take #{} {}x by {} escrow {} fees burned {}", id, lots, p.getUUID(), cost, burned);
        return "成交 " + series + " " + (takerLong ? "多" : "空") + " " + lots + " 手 @" + price + "，托管 " + cost;
    }

    public static String cancel(ServerPlayer p, int id) {
        FuturesData data = data(p.server);
        FuturesData.Offer o = data.offer(id);
        if (o == null) return "挂单不存在或已成交";
        if (!p.getUUID().equals(o.maker)) return "只能撤销自己的挂单";
        long refund = data.cancel(o);
        String delivered = deliver(p);
        return "已撤单 #" + id + "，退回 " + refund + (delivered.isEmpty() ? "" : "；" + delivered);
    }

    /** Pays out whatever the claims ledger owes this player; returns a status line, empty if nothing was owed. */
    public static String deliver(ServerPlayer p) {
        FuturesData data = data(p.server);
        long owed = data.claim(p.getUUID());
        if (owed <= 0) return "";
        try (var scope = EconomyAudit.scope("FUTURES_PAYOUT")) {
            if (Wallet.enabled()) {
                long room = Long.MAX_VALUE - Wallet.balance(p);
                long amount = Math.min(owed, room);
                if (amount <= 0) return "钱包已满，期货款 " + owed + " 暂存";
                Long before = EconomyAudit.walletBefore(p);
                Wallet.add(p, amount);
                EconomyAudit.walletAfter(p, before, amount, true, "FUTURES_PAYOUT");
                data.delivered(p.getUUID(), amount);
                return "期货款 +" + amount + " → 钱包";
            }
            int amount = (int) Math.min(owed, Integer.MAX_VALUE);
            boolean uncertainBefore = p.getPersistentData().getBoolean("sunlitmarket.bankUncertain");
            try {
                Currency.give(p, amount);
            } catch (RuntimeException ex) {
                if (!uncertainBefore && p.getPersistentData().getBoolean("sunlitmarket.bankUncertain")) {
                    // The bank may already hold the money; paying again could double it. An admin reconciles.
                    data.delivered(p.getUUID(), amount);
                    SunlitMarketMod.LOG.warn("[SunlitMarket] futures payout {} to {} marked delivered: bank write uncertain", amount, p.getUUID());
                    return "银行写入状态不确定，期货款需管理员核对";
                }
                return "背包放不下期货款 " + owed + "，稍后用 /sunlitfutures claim 领取";
            }
            data.delivered(p.getUUID(), amount);
            return "期货款 +" + amount;
        }
    }

    // ------------------------------------------------------------ settlement

    /** Settles expired series every second and pays whoever is online. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        long now = EconomyClock.tick(server);
        settle(server, now);
    }

    public static FuturesData.SettleResult settle(MinecraftServer server, long now) {
        FuturesData data = data(server);
        if (data.offers().isEmpty() && data.contracts().isEmpty()) return new FuturesData.SettleResult(0, 0, 0, 0);
        MarketData market = MarketData.get(server);
        FuturesData.SettleResult result = data.settleExpired(now, terms -> FillMath.settle(
                market.window(terms.index(), terms.expiryTick() - terms.windowTicks(), terms.expiryTick()),
                terms.minTraders(), terms.minBuckets(), terms.band()));
        if (result.series() > 0) {
            SunlitMarketMod.LOG.info("[SunlitMarket] futures settled {} series, {} contracts, paid {}, refunded {}, consistent={}",
                    result.series(), result.contracts(), result.paid(), result.refunded(), data.consistent());
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                String line = deliver(p);
                if (!line.isEmpty()) p.sendSystemMessage(net.minecraft.network.chat.Component.literal("[期货结算] " + line));
            }
        }
        return result;
    }

    // ------------------------------------------------------------ views

    public static JsonObject book(MinecraftServer server, String index) {
        FuturesData data = data(server);
        JsonObject o = new JsonObject();
        JsonArray offers = new JsonArray();
        for (FuturesData.Offer offer : data.offers()) {
            if (index == null || index.isBlank() || offer.terms.index().equals(index)) offers.add(offer.json());
        }
        o.add("offers", offers);
        if (index != null && !index.isBlank()) {
            o.addProperty("reference", MarketData.get(server).recentClose(index, EconomyClock.tick(server), FuturesMath.DAY));
            FuturesSpec spec = FuturesSpecs.get(index);
            if (spec != null) o.add("spec", spec.json());
        }
        return o;
    }

    public static JsonObject mine(ServerPlayer p) {
        FuturesData data = data(p.server);
        JsonObject o = new JsonObject();
        JsonArray offers = new JsonArray();
        for (FuturesData.Offer offer : data.offers()) if (offer.maker.equals(p.getUUID())) offers.add(offer.json());
        JsonArray contracts = new JsonArray();
        for (FuturesData.Contract c : data.contracts()) {
            if (!c.longId.equals(p.getUUID()) && !c.shortId.equals(p.getUUID())) continue;
            JsonObject row = c.json();
            row.addProperty("mySide", c.longId.equals(p.getUUID()) ? (c.shortId.equals(p.getUUID()) ? "both" : "long") : "short");
            contracts.add(row);
        }
        o.add("offers", offers);
        o.add("contracts", contracts);
        o.addProperty("claim", data.claim(p.getUUID()));
        return o;
    }

    public static JsonObject status(MinecraftServer server) {
        JsonObject o = data(server).status();
        o.add("specs", FuturesSpecs.json());
        return o;
    }

    public static JsonArray history(MinecraftServer server) {
        return data(server).historyJson();
    }

    // ------------------------------------------------------------ checks

    private static String gate(ServerPlayer p, FuturesSpec spec) {
        if (!spec.unlock().isBlank() && !UnlockData.get(p.server).has(p, spec.unlock())) return "尚未解锁期货：" + spec.unlock();
        if (!FtbTeamsCompat.resolve(p).available()) return "队伍服务不可用";
        return "";
    }

    /** Circuit breaker: trading needs a reference traded within the last economy day, and a price inside its band. */
    private static String withinBand(MinecraftServer server, String index, int price, double band, long now) {
        int reference = MarketData.get(server).recentClose(index, now, FuturesMath.DAY);
        if (reference <= 0) return "暂无可信行情（最近一天无成交），暂停期货交易";
        if (Math.abs(price / (double) reference - 1.0D) > band + 1e-9)
            return "价格 " + price + " 超出参考价 " + reference + " 的 ±" + Math.round(band * 100) + "%";
        return "";
    }
}
