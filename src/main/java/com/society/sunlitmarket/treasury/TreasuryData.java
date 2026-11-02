package com.society.sunlitmarket.treasury;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;
import java.util.TreeMap;

/**
 * The system side of every payment: pawn cash C, issuance reserve R and the NPC daily budget.
 *
 * <p>C is the house's till. Shelf purchases pay into it; the acceptance bin and shelf buybacks pay
 * out of it, and refuse once it is empty. R is the only place new money comes from: once per
 * economy day it tops C up toward its target (and takes back anything above the ceiling), and it
 * funds what NPCs spend on goods. Money paid to NPCs, fines and bribes return to R; fees are burned.
 * Net issuance is therefore bounded by what R started with plus explicit administrator injections.
 *
 * <p>All amounts are long spur. Pure bookkeeping, no Minecraft lookups, so it unit-tests directly.
 */
public final class TreasuryData extends SavedData {
    public static final String NAME = "sunlitmarket_treasury";

    public enum Account { CASH, RESERVE }

    public record Policy(long initialCash, long initialReserve, long cashTarget, long cashCeiling,
                         long dailyRefill, long npcDailyBudget) {
    }

    private long cash;
    private long reserve;
    private long burned;
    private long injectedCash;
    private long injectedReserve;
    private long npcSpent;
    private long lastRollDay = Long.MIN_VALUE;
    /** source → {paid into the system, paid out of the system}. */
    private final Map<String, long[]> flows = new TreeMap<>();

    public static TreasuryData create(Policy policy) {
        TreasuryData data = new TreasuryData();
        data.cash = Math.max(0L, policy.initialCash());
        data.reserve = Math.max(0L, policy.initialReserve());
        data.setDirty();
        return data;
    }

    /**
     * Applies the daily refill/sweep once for a new economy day. Days missed while the server was
     * down collapse into this single roll; there is no catch-up issuance.
     */
    public void roll(long day, Policy policy) {
        if (day == lastRollDay) return;
        boolean first = lastRollDay == Long.MIN_VALUE;
        lastRollDay = day;
        npcSpent = 0;
        if (!first) {
            if (cash < policy.cashTarget()) {
                long move = Math.min(Math.min(policy.cashTarget() - cash, Math.max(0L, policy.dailyRefill())), reserve);
                if (move > 0) {
                    cash += move;
                    reserve -= move;
                    flow("DAILY_REFILL", move, 0);
                }
            }
            if (policy.cashCeiling() > 0 && cash > policy.cashCeiling()) {
                long excess = cash - policy.cashCeiling();
                cash -= excess;
                reserve = saturatingAdd(reserve, excess);
                flow("DAILY_SWEEP", 0, excess);
            }
        }
        setDirty();
    }

    public long balance(Account account) {
        return account == Account.CASH ? cash : reserve;
    }

    public long cash() {
        return cash;
    }

    public long reserve() {
        return reserve;
    }

    public long burned() {
        return burned;
    }

    public long npcSpent() {
        return npcSpent;
    }

    /** What NPCs may still pay out today: the smaller of the unused budget and the reserve. */
    public long npcRemaining(Policy policy) {
        return Math.max(0L, Math.min(policy.npcDailyBudget() - npcSpent, reserve));
    }

    /** Takes money out of an account to pay a player; false (and no change) when it cannot cover it. */
    public boolean withdraw(Account account, long amount, String source) {
        if (amount < 0) throw new IllegalArgumentException("negative withdrawal");
        if (balance(account) < amount) return false;
        if (account == Account.CASH) cash -= amount; else reserve -= amount;
        flow(source, 0, amount);
        setDirty();
        return true;
    }

    /** Undoes a {@link #withdraw} whose payment did not happen. */
    public void reverse(Account account, long amount, String source) {
        if (amount <= 0) return;
        if (account == Account.CASH) cash = saturatingAdd(cash, amount); else reserve = saturatingAdd(reserve, amount);
        flow(source, 0, -amount);
        setDirty();
    }

    /** Books money a player has already paid to the system. */
    public void deposit(Account account, long amount, String source) {
        if (amount <= 0) return;
        if (account == Account.CASH) cash = saturatingAdd(cash, amount); else reserve = saturatingAdd(reserve, amount);
        flow(source, amount, 0);
        setDirty();
    }

    /** Draws on today's NPC budget as well as the reserve; false when either is short. */
    public boolean spendNpc(long amount, String source, Policy policy) {
        if (amount < 0) throw new IllegalArgumentException("negative spend");
        if (npcRemaining(policy) < amount) return false;
        reserve -= amount;
        npcSpent += amount;
        flow(source, 0, amount);
        setDirty();
        return true;
    }

    public void reverseNpc(long amount, String source) {
        if (amount <= 0) return;
        reserve = saturatingAdd(reserve, amount);
        npcSpent = Math.max(0L, npcSpent - amount);
        flow(source, 0, -amount);
        setDirty();
    }

    /** Money that left a player and was destroyed rather than kept by any account. */
    public void burn(long amount, String source) {
        if (amount <= 0) return;
        burned = saturatingAdd(burned, amount);
        flow(source, amount, 0);
        setDirty();
    }

    /** Administrator issuance (positive) or retirement (negative); never takes an account below zero. */
    public long inject(Account account, long delta) {
        long before = balance(account);
        long after = delta >= 0 ? saturatingAdd(before, delta) : Math.max(0L, before + delta);
        long applied = after - before;
        if (account == Account.CASH) {
            cash = after;
            injectedCash += applied;
        } else {
            reserve = after;
            injectedReserve += applied;
        }
        // Issuance enters the accounts from outside ("in"); retirement leaves them ("out").
        flow("ADMIN_INJECT_" + account.name(), Math.max(0L, applied), Math.max(0L, -applied));
        setDirty();
        return applied;
    }

    public JsonObject status(Policy policy) {
        JsonObject o = new JsonObject();
        o.addProperty("C", cash);
        o.addProperty("R", reserve);
        o.addProperty("burned", burned);
        o.addProperty("injectedCash", injectedCash);
        o.addProperty("injectedReserve", injectedReserve);
        o.addProperty("npcSpentToday", npcSpent);
        o.addProperty("npcRemainingToday", npcRemaining(policy));
        o.addProperty("economyDay", lastRollDay);
        o.addProperty("cashTarget", policy.cashTarget());
        o.addProperty("cashCeiling", policy.cashCeiling());
        o.addProperty("dailyRefill", policy.dailyRefill());
        o.addProperty("npcDailyBudget", policy.npcDailyBudget());
        JsonObject sources = new JsonObject();
        flows.forEach((source, v) -> {
            JsonObject row = new JsonObject();
            row.addProperty("in", v[0]);
            row.addProperty("out", v[1]);
            sources.add(source, row);
        });
        o.add("flows", sources);
        return o;
    }

    private void flow(String source, long in, long out) {
        long[] v = flows.computeIfAbsent(source == null || source.isBlank() ? "UNSPECIFIED" : source, k -> new long[2]);
        v[0] = saturatingAdd(v[0], in);
        v[1] = v[1] + out;
    }

    static long saturatingAdd(long a, long b) {
        long r = a + b;
        if (((a ^ r) & (b ^ r)) < 0) return a < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong("Cash", cash);
        tag.putLong("Reserve", reserve);
        tag.putLong("Burned", burned);
        tag.putLong("InjectedCash", injectedCash);
        tag.putLong("InjectedReserve", injectedReserve);
        tag.putLong("NpcSpent", npcSpent);
        tag.putLong("LastRollDay", lastRollDay);
        CompoundTag flowTag = new CompoundTag();
        flows.forEach((source, v) -> flowTag.putLongArray(source, new long[]{v[0], v[1]}));
        tag.put("Flows", flowTag);
        return tag;
    }

    /** Replaces this instance's state with a saved snapshot; used by isolated test fixtures. */
    public void restore(CompoundTag tag) {
        TreasuryData other = load(tag);
        cash = other.cash;
        reserve = other.reserve;
        burned = other.burned;
        injectedCash = other.injectedCash;
        injectedReserve = other.injectedReserve;
        npcSpent = other.npcSpent;
        lastRollDay = other.lastRollDay;
        flows.clear();
        flows.putAll(other.flows);
        setDirty();
    }

    public static TreasuryData load(CompoundTag tag) {
        TreasuryData data = new TreasuryData();
        data.cash = tag.getLong("Cash");
        data.reserve = tag.getLong("Reserve");
        data.burned = tag.getLong("Burned");
        data.injectedCash = tag.getLong("InjectedCash");
        data.injectedReserve = tag.getLong("InjectedReserve");
        data.npcSpent = tag.getLong("NpcSpent");
        data.lastRollDay = tag.contains("LastRollDay") ? tag.getLong("LastRollDay") : Long.MIN_VALUE;
        CompoundTag flowTag = tag.getCompound("Flows");
        for (String source : flowTag.getAllKeys()) {
            long[] v = flowTag.getLongArray(source);
            if (v.length == 2) data.flows.put(source, v);
        }
        return data;
    }
}
