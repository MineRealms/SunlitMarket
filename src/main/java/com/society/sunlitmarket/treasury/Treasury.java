package com.society.sunlitmarket.treasury;

import com.google.gson.JsonObject;
import com.society.sunlitmarket.SunlitMarketMod;
import com.society.sunlitmarket.config.CoreConfig;
import com.society.sunlitmarket.core.EconomyClock;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;

import java.util.function.Supplier;

/**
 * Server entry point for {@link TreasuryData}. Every system payout goes through {@link #payout}:
 * the account is debited first, then the player is paid, and the debit is reversed only when the
 * payment demonstrably did not happen. A bank write that may or may not have landed keeps the
 * debit - the system may underpay, but never pays twice.
 *
 * <p>Server thread only; all callers are trade handlers or commands.
 */
public final class Treasury {
    private static final String BANK_UNCERTAIN = "sunlitmarket.bankUncertain";

    private Treasury() {
    }

    public static boolean enabled() {
        return CoreConfig.TREASURY_ENABLED.get();
    }

    public static TreasuryData.Policy policy() {
        return new TreasuryData.Policy(CoreConfig.INITIAL_CASH.get(), CoreConfig.INITIAL_RESERVE.get(),
                CoreConfig.CASH_TARGET.get(), CoreConfig.CASH_CEILING.get(),
                CoreConfig.DAILY_REFILL.get(), CoreConfig.NPC_DAILY_BUDGET.get());
    }

    /** The world's treasury, rolled forward to the current economy day. */
    public static TreasuryData data(MinecraftServer server) {
        TreasuryData.Policy policy = policy();
        TreasuryData data = server.overworld().getDataStorage()
                .computeIfAbsent(TreasuryData::load, () -> TreasuryData.create(policy), TreasuryData.NAME);
        data.roll(EconomyClock.day(server), policy);
        return data;
    }

    /** Called from the server tick so the daily refill also happens on quiet days. */
    public static void tick(MinecraftServer server) {
        // Cadence on real server ticks; the economy clock decides only which day it is.
        if (enabled() && server.getTickCount() % 200 == 0) data(server);
    }

    /**
     * Pays {@code amount} to a player out of {@code account}. {@code settle} performs the actual
     * payment and returns an error message, or an empty string on success.
     *
     * @param npcBudget also charge today's NPC budget (account must be RESERVE)
     * @return an error message, or empty on success
     */
    public static String payout(Player player, TreasuryData.Account account, boolean npcBudget, long amount,
                                String source, Supplier<String> settle) {
        if (!enabled() || amount <= 0 || player.getServer() == null) return nullToEmpty(settle.get());
        MinecraftServer server = player.getServer();
        TreasuryData data = data(server);
        if (npcBudget) {
            if (!data.spendNpc(amount, source, policy())) return "NPC 今日消费预算不足";
        } else if (!data.withdraw(account, amount, source)) {
            return account == TreasuryData.Account.CASH
                    ? "当铺现金不足（剩 " + data.cash() + "，需 " + amount + "）"
                    : "发行准备金不足";
        }
        boolean uncertainBefore = player.getPersistentData().getBoolean(BANK_UNCERTAIN);
        String error;
        try {
            error = nullToEmpty(settle.get());
        } catch (RuntimeException ex) {
            undo(player, data, account, npcBudget, amount, source, uncertainBefore);
            throw ex;
        }
        if (!error.isEmpty()) undo(player, data, account, npcBudget, amount, source, uncertainBefore);
        return error;
    }

    private static void undo(Player player, TreasuryData data, TreasuryData.Account account, boolean npcBudget,
                             long amount, String source, boolean uncertainBefore) {
        if (!uncertainBefore && player.getPersistentData().getBoolean(BANK_UNCERTAIN)) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] treasury kept {} {} debit for {}: bank write uncertain for {}",
                    amount, account, source, player.getUUID());
            return;
        }
        if (npcBudget) data.reverseNpc(amount, source);
        else data.reverse(account, amount, source);
    }

    /** Books money a player has already paid to the system. No-op when the treasury is disabled. */
    public static void receive(MinecraftServer server, TreasuryData.Account account, long amount, String source) {
        if (enabled() && server != null && amount > 0) data(server).deposit(account, amount, source);
    }

    /** Records money destroyed by a fee. Counted even with the treasury off, since the fee still applies. */
    public static void burn(MinecraftServer server, long amount, String source) {
        if (server != null && amount > 0) data(server).burn(amount, source);
    }

    public static JsonObject status(MinecraftServer server) {
        JsonObject o = data(server).status(policy());
        o.addProperty("enabled", enabled());
        return o;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
