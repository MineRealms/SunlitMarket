package com.society.sunlitmarket.consignment;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.society.sunlitmarket.SunlitMarketMod;
import com.society.sunlitmarket.core.EconomyClock;
import com.society.sunlitmarket.currency.Currency;
import com.society.sunlitmarket.treasury.Treasury;
import com.society.sunlitmarket.treasury.TreasuryData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Player-facing consignment operations. The house never mints: the buyer pays the seller directly,
 * the treasury only holds the money between the sale and the seller collecting it, and the fee is
 * moved from the till into the issuance reserve instead of being destroyed.
 *
 * <p>Only plain items can be consigned: a listing stores an item id and a count, so anything with
 * NBT (enchanted gear, filled tanks, named books) would come back as a different item. Those are
 * refused rather than silently downgraded.
 *
 * <p>Server thread only.
 */
public final class ConsignmentService {
    /** Share of the sale price the house keeps, in basis points. Script-settable. */
    private static volatile int feeBps = 200;

    private ConsignmentService() {
    }

    public static void feeBps(int bps) {
        feeBps = Math.max(0, Math.min(2000, bps));
    }

    public static int feeBps() {
        return feeBps;
    }

    public static long feeFor(long total) {
        if (total <= 0 || feeBps <= 0) return 0;
        return Math.max(0L, Math.min(total, total * feeBps / 10_000L));
    }

    public static ConsignmentData data(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(ConsignmentData::load, ConsignmentData::new, ConsignmentData.NAME);
    }

    /** Lists the item in the player's main hand. */
    public static String list(ServerPlayer p, int unitPrice, int count) {
        ItemStack held = p.getMainHandItem();
        if (held.isEmpty()) return "主手没有物品";
        if (count < 1 || count > held.getCount()) return "数量必须在 1.." + held.getCount();
        if (held.getTag() != null) return "带 NBT 的物品不能寄售（附魔 / 命名 / 装填过的都不行）";
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        ConsignmentData data = data(p.server);
        if (!Currency.removeItems(p, id, count)) return "扣除物品失败，请重试";
        ConsignmentData.Listing listing;
        try {
            listing = data.post(p.getUUID(), p.getGameProfile().getName(), id, count, unitPrice, EconomyClock.tick(p.server));
        } catch (IllegalArgumentException ex) {
            Currency.giveItem(p, id, count);
            return ex.getMessage();
        }
        SunlitMarketMod.LOG.info("[SunlitMarket] consignment #{} {} x{} @{} by {}", listing.id, id, count, unitPrice, p.getUUID());
        return "已挂单 #" + listing.id + "：" + id + " x" + count + " @ " + unitPrice + " spur/件";
    }

    public static String buy(ServerPlayer p, int id, int count) {
        ConsignmentData data = data(p.server);
        ConsignmentData.Listing listing = data.listing(id);
        if (listing == null) return "挂单不存在或已售罄";
        if (listing.seller.equals(p.getUUID())) return "不能买自己的挂单";
        if (count < 1 || count > listing.remaining) return "数量必须在 1.." + listing.remaining;
        long total = (long) listing.unitPrice * count;
        if (total > Integer.MAX_VALUE) return "金额过大";
        // Take the units off the board first: a crash between the two steps then loses a sale
        // instead of duplicating one.
        data.take(listing, count);
        String error = Currency.settle(p, null, p.getInventory(), Currency.item(listing.item), count,
                (int) -total, "CONSIGNMENT_BUY");
        if (!error.isEmpty()) {
            data.restore(listing, count);
            return error;
        }
        Treasury.receive(p.server, TreasuryData.Account.CASH, total, "CONSIGNMENT_BUY");
        long fee = feeFor(total);
        long net = total - fee;
        if (fee > 0 && Treasury.enabled()) {
            TreasuryData till = Treasury.data(p.server);
            if (till.withdraw(TreasuryData.Account.CASH, fee, "CONSIGNMENT_FEE"))
                till.deposit(TreasuryData.Account.RESERVE, fee, "CONSIGNMENT_FEE");
        }
        data.credit(listing.seller, listing.sellerName, net, fee);
        ServerPlayer seller = p.server.getPlayerList().getPlayer(listing.seller);
        String paid = "";
        if (seller != null) {
            paid = deliver(seller);
            if (!paid.isEmpty()) paid = "；" + paid;
        }
        return "买入 " + listing.item + " x" + count + "，支付 " + total + " spur（卖家得 " + net
                + "，手续费 " + fee + "）" + paid;
    }

    public static String cancel(ServerPlayer p, int id) {
        ConsignmentData data = data(p.server);
        ConsignmentData.Listing listing = data.listing(id);
        if (listing == null) return "挂单不存在或已售罄";
        if (!listing.seller.equals(p.getUUID())) return "只能撤自己的挂单";
        if (!Currency.giveItem(p, listing.item, listing.remaining)) return "背包放不下，先腾出空间";
        int back = data.cancel(listing);
        return "已撤单 #" + id + "，退回 " + listing.item + " x" + back;
    }

    /** Pays out whatever the board owes this player; empty when nothing is owed. */
    public static String deliver(ServerPlayer p) {
        ConsignmentData data = data(p.server);
        long owed = data.claim(p.getUUID());
        if (owed <= 0) return "";
        if (owed > Integer.MAX_VALUE) owed = Integer.MAX_VALUE;
        TreasuryData till = Treasury.enabled() ? Treasury.data(p.server) : null;
        if (till != null && !till.withdraw(TreasuryData.Account.CASH, owed, "CONSIGNMENT_SALE"))
            return "寄售款 " + owed + " 暂存：当铺现金不足（剩 " + till.cash() + "）";
        try {
            Currency.give(p, (int) owed);
        } catch (RuntimeException ex) {
            if (till != null) till.reverse(TreasuryData.Account.CASH, owed, "CONSIGNMENT_SALE");
            SunlitMarketMod.LOG.warn("[SunlitMarket] consignment payout failed for {}", p.getUUID(), ex);
            return "寄售款 " + owed + " 暂存：发放失败（背包或银行问题）";
        }
        data.delivered(p.getUUID(), owed);
        return "寄售款 +" + owed + " spur";
    }

    public static JsonObject mine(ServerPlayer p) {
        ConsignmentData data = data(p.server);
        JsonObject o = new JsonObject();
        JsonArray array = new JsonArray();
        for (ConsignmentData.Listing l : data.listingsOf(p.getUUID())) array.add(l.json());
        o.add("listings", array);
        o.addProperty("claim", data.claim(p.getUUID()));
        return o;
    }

    public static JsonArray browse(MinecraftServer server) {
        return data(server).browse();
    }

    public static JsonObject status(MinecraftServer server) {
        JsonObject o = data(server).status();
        o.addProperty("feeBps", feeBps);
        return o;
    }
}
