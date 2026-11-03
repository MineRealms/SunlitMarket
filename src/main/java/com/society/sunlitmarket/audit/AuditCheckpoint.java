package com.society.sunlitmarket.audit;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.math.BigInteger;
import java.util.*;

/** Server-thread checkpoint of observations, never a source of funds or transaction replay. */
public final class AuditCheckpoint extends SavedData {
    private static final String NAME = "sunlitmarket_audit";
    private long attempted, written, dropped, confirmed, failed, unknown;
    private BigInteger observedNet = BigInteger.ZERO;
    private final Map<String, Long> sources = new TreeMap<>();
    private final Map<String, Day> days = new TreeMap<>();
    private final List<String> gaps = new ArrayList<>();
    private String epoch = "";
    private long sequence, writtenSequence;
    private boolean clean = true, reconciliationRequired;
    /** Latest world money supply reported by a pack script; MIN_VALUE = never reported. */
    private long m0 = Long.MIN_VALUE, m0Debts = Long.MIN_VALUE, m0Tick = Long.MIN_VALUE;

    private static final class Day {
        long attempted, written, dropped, confirmed, failed, unknown;
        BigInteger net = BigInteger.ZERO;
        String first = "", last = "";
    }
    public static AuditCheckpoint get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(AuditCheckpoint::load, AuditCheckpoint::new, NAME);
    }
    public void beginEpoch(String next) {
        if (!clean) reconciliationRequired = true;
        epoch = next; sequence = 0; writtenSequence = 0; clean = false; setDirty();
    }
    public void finishEpoch(boolean drained) { clean = drained; if (!drained) reconciliationRequired = true; setDirty(); }
    public void attempt(ObservedMoneyEvent e) {
        attempted++; sequence = e.sequence();
        Day day = days.computeIfAbsent(e.utcDay(), key -> new Day());
        day.attempted++; if (day.first.isEmpty()) day.first = e.timestamp().toString();
        day.last = e.timestamp().toString(); setDirty();
    }
    public void written(ObservedMoneyEvent e) {
        written++; writtenSequence = Math.max(writtenSequence, e.sequence());
        sources.merge(e.source(), 1L, Long::sum);
        Day day = days.computeIfAbsent(e.utcDay(), key -> new Day()); day.written++;
        if ("FAILED".equals(e.outcome())) { failed++; day.failed++; }
        else if ("CONFIRMED".equals(e.outcome()) && e.confirmedDelta() != null) {
            confirmed++; day.confirmed++;
            BigInteger delta = BigInteger.valueOf(e.confirmedDelta());
            observedNet = observedNet.add(delta); day.net = day.net.add(delta);
        } else if (!"CONTROL".equals(e.outcome())) { unknown++; day.unknown++; }
        setDirty();
    }
    public void dropped(ObservedMoneyEvent e) {
        dropped++; days.computeIfAbsent(e.utcDay(), key -> new Day()).dropped++;
        if (gaps.size() == 64) gaps.remove(0);
        gaps.add(e.epoch() + ":" + e.sequence()); reconciliationRequired = true; setDirty();
    }
    /**
     * Records a world money-supply observation. SunlitMarket never computes this itself: the
     * pack owns the bank and the debt ledger, so the number arrives from a script.
     */
    public void supply(long total, long debts, long tick) {
        if (total < 0) return;
        if (total == m0 && debts == m0Debts) return;
        m0 = total; m0Debts = debts; m0Tick = tick; setDirty();
    }
    public JsonObject status() {
        JsonObject o = new JsonObject(); o.addProperty("scope", "SUNLITMARKET_OBSERVED_FLOWS");
        o.addProperty("epoch", epoch); o.addProperty("attempted", attempted); o.addProperty("written", written);
        o.addProperty("dropped", dropped); o.addProperty("confirmed", confirmed); o.addProperty("failed", failed); o.addProperty("unknown", unknown);
        o.addProperty("observedNet", observedNet.toString()); o.addProperty("attemptSequence", sequence); o.addProperty("writtenSequence", writtenSequence);
        o.addProperty("reconciliationRequired", reconciliationRequired); o.addProperty("cleanStop", clean);
        o.addProperty("M0", m0 == Long.MIN_VALUE ? "UNKNOWN" : Long.toString(m0));
        o.addProperty("M0Debts", m0Debts == Long.MIN_VALUE ? "UNKNOWN" : Long.toString(m0Debts));
        o.addProperty("M0Source", m0 == Long.MIN_VALUE ? "UNREPORTED" : "PACK_SCRIPT@tick" + m0Tick);
        // The pack reports bank balances only; coins held as items are outside this number.
        o.addProperty("M0Scope", "BANK_ACCOUNTS_ONLY");
        o.addProperty("C", "SEE_TREASURY"); o.addProperty("R", "SEE_TREASURY");
        o.addProperty("durability", "BEST_EFFORT; checkpoint and JSONL are not atomic; crashes may lose undetectable tails");
        JsonObject src = new JsonObject(); sources.forEach(src::addProperty); o.add("sources", src);
        return o;
    }
    public JsonObject day(String date) {
        Day d = days.get(date); JsonObject o = new JsonObject(); o.addProperty("utcDay", date);
        o.addProperty("status", d == null ? "NO_OBSERVATIONS" : "PARTIAL_COVERAGE");
        if (d != null) {
            o.addProperty("attempted", d.attempted); o.addProperty("written", d.written); o.addProperty("dropped", d.dropped);
            o.addProperty("confirmed", d.confirmed); o.addProperty("failed", d.failed); o.addProperty("unknown", d.unknown);
            o.addProperty("observedNet", d.net.toString()); o.addProperty("firstObservation", d.first); o.addProperty("lastObservation", d.last);
        }
        o.addProperty("M0", "UNKNOWN"); o.addProperty("C", "NOT_RECORDED_PER_DAY"); o.addProperty("R", "NOT_RECORDED_PER_DAY"); return o;
    }
    public List<String> recentGaps() { return List.copyOf(gaps); }
    public long attempted() { return attempted; }
    public long written() { return written; }
    public long dropped() { return dropped; }
    public long failed() { return failed; }
    public long unknown() { return unknown; }
    public long confirmed() { return confirmed; }
    public BigInteger observedNet() { return observedNet; }
    @Override public CompoundTag save(CompoundTag t) {
        t.putInt("schema", 2); t.putLong("attempted", attempted); t.putLong("written", written); t.putLong("dropped", dropped);
        t.putLong("confirmed", confirmed); t.putLong("failed", failed); t.putLong("unknown", unknown); t.putString("net", observedNet.toString());
        t.putString("epoch", epoch); t.putLong("sequence", sequence); t.putLong("writtenSequence", writtenSequence);
        t.putBoolean("clean", clean); t.putBoolean("reconciliationRequired", reconciliationRequired);
        t.putLong("m0", m0); t.putLong("m0Debts", m0Debts); t.putLong("m0Tick", m0Tick);
        CompoundTag src = new CompoundTag(); sources.forEach(src::putLong); t.put("sources", src);
        CompoundTag dates = new CompoundTag(); days.forEach((date, d) -> {
            CompoundTag row = new CompoundTag(); row.putLong("attempted", d.attempted); row.putLong("written", d.written); row.putLong("dropped", d.dropped);
            row.putLong("confirmed", d.confirmed); row.putLong("failed", d.failed); row.putLong("unknown", d.unknown);
            row.putString("net", d.net.toString()); row.putString("first", d.first); row.putString("last", d.last); dates.put(date, row);
        }); t.put("days", dates);
        net.minecraft.nbt.ListTag lost = new net.minecraft.nbt.ListTag(); gaps.forEach(g -> lost.add(net.minecraft.nbt.StringTag.valueOf(g))); t.put("gaps", lost);
        return t;
    }
    public static AuditCheckpoint load(CompoundTag t) {
        AuditCheckpoint c = new AuditCheckpoint(); c.attempted=t.getLong("attempted"); c.written=t.getLong("written"); c.dropped=t.getLong("dropped");
        c.confirmed=t.getLong("confirmed"); c.failed=t.getLong("failed"); c.unknown=t.getLong("unknown");
        try { c.observedNet=t.contains("net")?new BigInteger(t.getString("net")):BigInteger.valueOf(t.getLong("observedNet")); }
        catch (NumberFormatException ex) { c.reconciliationRequired=true; }
        c.epoch=t.getString("epoch"); c.sequence=t.getLong("sequence"); c.writtenSequence=t.getLong("writtenSequence");
        c.clean=t.getBoolean("clean"); c.reconciliationRequired|=t.getBoolean("reconciliationRequired") || t.getInt("schema")<2;
        c.m0=t.contains("m0")?t.getLong("m0"):Long.MIN_VALUE;
        c.m0Debts=t.contains("m0Debts")?t.getLong("m0Debts"):Long.MIN_VALUE;
        c.m0Tick=t.contains("m0Tick")?t.getLong("m0Tick"):Long.MIN_VALUE;
        CompoundTag src=t.getCompound("sources"); for(String k:src.getAllKeys())c.sources.put(k,src.getLong(k));
        CompoundTag dates=t.getCompound("days"); for(String key:dates.getAllKeys()) {
            CompoundTag row=dates.getCompound(key); Day d=new Day(); d.attempted=row.getLong("attempted"); d.written=row.getLong("written"); d.dropped=row.getLong("dropped");
            d.confirmed=row.getLong("confirmed"); d.failed=row.getLong("failed"); d.unknown=row.getLong("unknown"); d.first=row.getString("first"); d.last=row.getString("last");
            try {d.net=new BigInteger(row.getString("net"));}catch(NumberFormatException ex){c.reconciliationRequired=true;} c.days.put(key,d);
        }
        var lost=t.getList("gaps",net.minecraft.nbt.Tag.TAG_STRING); for(int i=Math.max(0,lost.size()-64);i<lost.size();i++)c.gaps.add(lost.getString(i));
        return c;
    }
}
