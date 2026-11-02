package com.society.sunlitmarket.audit;

import com.google.gson.JsonObject;
import com.society.sunlitmarket.SunlitMarketMod;
import com.society.sunlitmarket.currency.Currency;
import com.society.sunlitmarket.wallet.Wallet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/** Server-thread observations only; audit failure cannot veto or repeat an economic action. */
public final class EconomyAudit {
    private record Cause(String reason, UUID operation) {}
    private static final ThreadLocal<Cause> CAUSE = new ThreadLocal<>();
    private static MinecraftServer owner;
    private static AuditJournal journal;
    private static AuditCheckpoint checkpoint;
    private static String epoch = "";
    private static String mapping = "UNKNOWN";
    private static long sequence, observerErrors;
    private static boolean stopping;
    private EconomyAudit() {}

    public static final class Scope implements AutoCloseable {
        private final Cause previous;
        private Scope(Cause previous) { this.previous = previous; }
        @Override public void close() { try { if (previous == null) CAUSE.remove(); else CAUSE.set(previous); } catch (RuntimeException ignored) {} }
    }
    public static Scope scope(String reason) {
        Cause previous = CAUSE.get();
        try { CAUSE.set(new Cause(reason, previous == null ? UUID.randomUUID() : previous.operation)); }
        catch (RuntimeException ignored) {}
        return new Scope(previous);
    }
    public static String reason(String fallback) { Cause c = CAUSE.get(); return c == null ? fallback : c.reason; }
    public static UUID operation() { Cause c = CAUSE.get(); return c == null ? UUID.randomUUID() : c.operation; }

    public static void start(MinecraftServer server) {
        try {
            if (server == owner) return;
            if (owner != null) stop(owner);
            owner = server; stopping = false; observerErrors = 0; sequence = 0;
            epoch = UUID.randomUUID().toString(); mapping = "UNKNOWN";
            checkpoint = AuditCheckpoint.get(server); checkpoint.beginEpoch(epoch);
            if (!Boolean.getBoolean("sunlitmarket.audit.disabled"))
                journal = new AuditJournal(server.getWorldPath(LevelResource.ROOT).resolve("sunlitmarket/audit/events-" + epoch + ".jsonl"), 2048);
            emit(server, "SESSION_START", "NONE", "CONTROL", 0, null, null, null, null, null,
                    "best effort observations; no inventory census or transaction replay");
            mappingChanged();
        } catch (Throwable ex) { observerError(); }
    }
    public static void tick(MinecraftServer server) {
        if (server != owner || stopping) return;
        try { drain(); } catch (Throwable ex) { observerError(); }
    }
    private static void drain() {
        if (journal == null || checkpoint == null) return;
        AuditJournal.Receipt receipt;
        while ((receipt = journal.pollReceipt()) != null) {
            if (receipt.written()) checkpoint.written(receipt.event());
            else checkpoint.dropped(receipt.event());
        }
    }
    public static void stop(MinecraftServer server) {
        if (owner != server || stopping) return;
        try {
            emit(server, "SESSION_STOP", "NONE", "CONTROL", 0, null, null, null, null, null, "normal stop requested");
            stopping = true;
            if (journal != null) journal.close(2000);
            drain();
            boolean clean = journal != null && !journal.alive() && !journal.failed() && journal.pending() == 0;
            if (checkpoint != null) checkpoint.finishEpoch(clean);
            if (!clean) SunlitMarketMod.LOG.warn("[SunlitMarket audit] Incomplete shutdown; audit tail may be missing");
        } catch (Throwable ex) { observerError(); }
        finally { journal = null; checkpoint = null; owner = null; CAUSE.remove(); }
    }
    private static void observerError() {
        observerErrors++;
        if (observerErrors == 1) try { SunlitMarketMod.LOG.warn("[SunlitMarket audit] Observation failure; economic action is unchanged"); }
        catch (Throwable ignored) {}
    }
    public static void emit(MinecraftServer server, String source, String backend, String outcome,
                            long requested, Long confirmed, UUID player, UUID operation,
                            Long created, Long removed, String note) {
        try {
            if (server != owner || stopping || !server.isSameThread() || checkpoint == null
                    || Boolean.getBoolean("sunlitmarket.audit.disabled")) return;
            ObservedMoneyEvent event = new ObservedMoneyEvent(UUID.randomUUID(), operation, epoch, ++sequence,
                    Instant.now(), server.overworld().getGameTime(), server.overworld().getDayTime(), source,
                    backend, outcome, requested, confirmed, player, mapping, created, removed, note);
            checkpoint.attempt(event);
            if (journal == null || !journal.offer(event)) checkpoint.dropped(event);
        } catch (Throwable ex) { observerError(); }
    }
    public static void mappingChanged() {
        try {
            MinecraftServer server = owner;
            if (server == null || stopping) return;
            if (!server.isSameThread()) { server.execute(EconomyAudit::mappingChanged); return; }
            Map<String, Integer> values = new TreeMap<>();
            for (var entry : Currency.denominations()) values.put(entry.getKey(), entry.getValue());
            String canonical = values.toString();
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
            if (hash.equals(mapping)) return;
            String old = mapping; mapping = hash;
            emit(server, "VALUATION_MAPPING_CHANGED", "NONE", "CONTROL", 0, null, null, null, null, null,
                    "previous=" + old + ";denominations=" + canonical);
        } catch (Throwable ex) { observerError(); }
    }

    /** Local persistent data only, never queries Numismatics or changes any balance. */
    public static Long walletBefore(Player player) {
        try { return Wallet.balance(player); } catch (Throwable ex) { observerError(); return null; }
    }
    public static Long walletDelta(Long before, Long after) {
        return before == null || after == null ? null : Math.subtractExact(after, before);
    }
    public static void walletAfter(Player player, Long before, long requested, boolean success, String fallback) {
        try {
            if (!(player instanceof ServerPlayer sp)) return;
            Long delta = walletDelta(before, walletBefore(player));
            emit(sp.server, reason(fallback), "WALLET", delta == null ? "UNKNOWN" : success ? "CONFIRMED" : "FAILED",
                    requested, delta, player.getUUID(), operation(), null, null,
                    "direct wallet leg; companion cash legs are counted separately; no C/R backing is implied");
        } catch (Throwable ex) { observerError(); }
    }
    public static String status(MinecraftServer server) {
        if (server != owner || checkpoint == null) return "audit unavailable; coverage UNKNOWN";
        JsonObject o = checkpoint.status();
        o.addProperty("pending", journal == null ? 0 : journal.pending());
        o.addProperty("ioErrors", journal == null ? 0 : journal.ioErrors());
        o.addProperty("observerErrors", observerErrors); o.addProperty("valuationVersion", mapping);
        o.addProperty("fileSinkFailed", journal == null || journal.failed());
        o.addProperty("enabled", !Boolean.getBoolean("sunlitmarket.audit.disabled"));
        try {
            JsonObject treasury = com.society.sunlitmarket.treasury.Treasury.status(server);
            o.add("C", treasury.get("C")); o.add("R", treasury.get("R")); o.add("burned", treasury.get("burned"));
            o.add("treasury", treasury);
        } catch (RuntimeException ex) { o.addProperty("C", "UNAVAILABLE"); o.addProperty("R", "UNAVAILABLE"); }
        return o.toString();
    }
    public static String day(MinecraftServer server, String date) {
        LocalDate.parse(date);
        return server == owner && checkpoint != null ? checkpoint.day(date).toString() : "audit unavailable";
    }
    public static String day(MinecraftServer server) { return day(server, LocalDate.now(ZoneOffset.UTC).toString()); }
    public static String gaps(MinecraftServer server) {
        return "coverage=settlements+tagged_direct_wallet_legs; excluded=external_bank_writes,external_coin_creation,containers,offline_players,unhooked_Wallet.set; "
                + "no_world_total; recentMissing=" + (server == owner && checkpoint != null ? checkpoint.recentGaps() : "UNKNOWN")
                + "; crash tails can be undetectable; IO/pending details: " + status(server);
    }
}
