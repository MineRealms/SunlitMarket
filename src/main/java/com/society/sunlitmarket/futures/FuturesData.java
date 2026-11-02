package com.society.sunlitmarket.futures;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.society.sunlitmarket.market.FillMath;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Peer-to-peer futures book for one world: resting offers, matched contracts, escrow, unpaid
 * claims and settlement history. Pure bookkeeping (no player or world access) so the escrow
 * invariant can be unit-tested: {@link #escrow()} always equals the remaining offer collateral and
 * fees plus the full band value of every open contract.
 *
 * <p>Money only ever moves in via the caller's payment before {@link #post}/{@link #take}, and
 * out via {@link #claims}; this class never mints anything.
 */
public final class FuturesData extends SavedData {
    public static final String NAME = "sunlitmarket_futures";
    public static final int HISTORY = 256;

    /** Terms an offer or contract was opened under. */
    public record Terms(String index, long expiryDay, int price, long low, long high, int multiplier, double band,
                        int minTraders, int minBuckets, long windowTicks) {
        public long expiryTick() {
            return expiryDay * FuturesMath.DAY;
        }

        public long closeTick() {
            return expiryTick() - windowTicks;
        }

        public String series() {
            return FuturesMath.series(index, expiryDay);
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("index", index);
            t.putLong("expiryDay", expiryDay);
            t.putInt("price", price);
            t.putLong("low", low);
            t.putLong("high", high);
            t.putInt("mult", multiplier);
            t.putDouble("band", band);
            t.putInt("minTraders", minTraders);
            t.putInt("minBuckets", minBuckets);
            t.putLong("window", windowTicks);
            return t;
        }

        static Terms load(CompoundTag t) {
            return new Terms(t.getString("index"), t.getLong("expiryDay"), t.getInt("price"), t.getLong("low"),
                    t.getLong("high"), t.getInt("mult"), t.getDouble("band"), t.getInt("minTraders"),
                    t.getInt("minBuckets"), t.getLong("window"));
        }

        void json(JsonObject o) {
            o.addProperty("series", series());
            o.addProperty("price", price);
            o.addProperty("low", low);
            o.addProperty("high", high);
            o.addProperty("multiplier", multiplier);
            o.addProperty("expiryDay", expiryDay);
        }
    }

    public static final class Offer {
        public int id;
        public UUID maker;
        public String makerOwner;
        public String makerName;
        public boolean isLong;
        public int lots;
        public long collateralPerLot;
        public long feePerLot;
        public Terms terms;
        public long created;

        long escrowed() {
            return lots * (collateralPerLot + feePerLot);
        }

        public JsonObject json() {
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.addProperty("maker", makerName);
            o.addProperty("side", isLong ? "long" : "short");
            o.addProperty("lots", lots);
            o.addProperty("collateralPerLot", collateralPerLot);
            o.addProperty("feePerLot", feePerLot);
            terms.json(o);
            return o;
        }
    }

    public static final class Contract {
        public int id;
        public UUID longId;
        public String longOwner;
        public UUID shortId;
        public String shortOwner;
        public int lots;
        public Terms terms;
        public long opened;

        long escrowed() {
            return (terms.high() - terms.low()) * terms.multiplier() * lots;
        }

        public JsonObject json() {
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.addProperty("lots", lots);
            o.addProperty("longOwner", longOwner);
            o.addProperty("shortOwner", shortOwner);
            terms.json(o);
            return o;
        }
    }

    /** What a settlement pass did; history entries are per series. */
    public record SettleResult(int series, int contracts, long paid, long refunded) {
    }

    private int nextId = 1;
    private final Map<Integer, Offer> offers = new LinkedHashMap<>();
    private final List<Contract> contracts = new ArrayList<>();
    private final Map<UUID, Long> claims = new HashMap<>();
    private final ArrayDeque<JsonObject> history = new ArrayDeque<>();
    private long escrow;
    private long feesBurned;

    // ------------------------------------------------------------ queries

    public long escrow() {
        return escrow;
    }

    public long feesBurned() {
        return feesBurned;
    }

    public Offer offer(int id) {
        return offers.get(id);
    }

    public List<Offer> offers() {
        return new ArrayList<>(offers.values());
    }

    public List<Contract> contracts() {
        return new ArrayList<>(contracts);
    }

    public long claim(UUID player) {
        return claims.getOrDefault(player, 0L);
    }

    public long claimsTotal() {
        long sum = 0;
        for (long v : claims.values()) sum += v;
        return sum;
    }

    public List<JsonObject> history() {
        return new ArrayList<>(history);
    }

    /** Open lots of one owner in one series: resting offers plus either side of matched contracts. */
    public int position(String owner, String index, long expiryDay) {
        int lots = 0;
        for (Offer o : offers.values()) {
            if (o.makerOwner.equals(owner) && o.terms.index().equals(index) && o.terms.expiryDay() == expiryDay) lots += o.lots;
        }
        for (Contract c : contracts) {
            if (!c.terms.index().equals(index) || c.terms.expiryDay() != expiryDay) continue;
            if (c.longOwner.equals(owner)) lots += c.lots;
            if (c.shortOwner.equals(owner)) lots += c.lots;
        }
        return lots;
    }

    /** The escrow invariant; false means the book is corrupt. */
    public boolean consistent() {
        long expected = 0;
        for (Offer o : offers.values()) expected += o.escrowed();
        for (Contract c : contracts) expected += c.escrowed();
        return expected == escrow && escrow >= 0;
    }

    // ------------------------------------------------------------ mutations

    /** Records an offer whose collateral and fee the maker has already paid. */
    public Offer post(UUID maker, String owner, String name, boolean isLong, int lots, Terms terms, long feePerLot, long now) {
        Offer o = new Offer();
        o.id = nextId++;
        o.maker = maker;
        o.makerOwner = owner;
        o.makerName = name;
        o.isLong = isLong;
        o.lots = lots;
        o.terms = terms;
        o.collateralPerLot = FuturesMath.collateralPerLot(isLong, terms.price(), terms.low(), terms.high(), terms.multiplier());
        o.feePerLot = feePerLot;
        o.created = now;
        offers.put(o.id, o);
        escrow += o.escrowed();
        setDirty();
        return o;
    }

    /** What a taker must pay for {@code lots} of this offer: opposite-side collateral plus the fee. */
    public static long takeCost(Offer o, int lots) {
        long collateral = FuturesMath.collateralPerLot(!o.isLong, o.terms.price(), o.terms.low(), o.terms.high(), o.terms.multiplier());
        return lots * (collateral + o.feePerLot);
    }

    /**
     * Matches {@code lots} of an offer against a taker who has already paid {@link #takeCost}.
     * Both sides' fees for these lots are burned; the rest of the money stays in escrow.
     *
     * @return fees burned by this match
     */
    public long take(Offer o, int lots, UUID taker, String takerOwner, long now) {
        if (lots < 1 || lots > o.lots) throw new IllegalArgumentException("lots");
        long takerCollateral = FuturesMath.collateralPerLot(!o.isLong, o.terms.price(), o.terms.low(), o.terms.high(), o.terms.multiplier());
        escrow += lots * takerCollateral;
        escrow -= lots * o.feePerLot;
        long burned = 2L * lots * o.feePerLot;
        feesBurned += burned;
        o.lots -= lots;
        if (o.lots == 0) offers.remove(o.id);
        Contract c = new Contract();
        c.id = nextId++;
        c.lots = lots;
        c.terms = o.terms;
        c.opened = now;
        if (o.isLong) {
            c.longId = o.maker;
            c.longOwner = o.makerOwner;
            c.shortId = taker;
            c.shortOwner = takerOwner;
        } else {
            c.longId = taker;
            c.longOwner = takerOwner;
            c.shortId = o.maker;
            c.shortOwner = o.makerOwner;
        }
        contracts.add(c);
        setDirty();
        return burned;
    }

    /** Withdraws a resting offer; its collateral and unused fee become a claim. Returns the refund. */
    public long cancel(Offer o) {
        long refund = o.escrowed();
        offers.remove(o.id);
        escrow -= refund;
        credit(o.maker, refund);
        return refund;
    }

    public void credit(UUID player, long amount) {
        if (amount <= 0) return;
        claims.merge(player, amount, Long::sum);
        setDirty();
    }

    /** Marks {@code amount} of a player's claim as delivered. */
    public void delivered(UUID player, long amount) {
        long left = claim(player) - amount;
        if (left > 0) claims.put(player, left); else claims.remove(player);
        setDirty();
    }

    /**
     * Settles every contract and offer whose expiry has passed. Contracts pay out on a credible
     * price, otherwise both sides get their collateral back; resting offers are refunded either way.
     */
    public SettleResult settleExpired(long now, java.util.function.Function<Terms, FillMath.Settlement> pricer) {
        Map<String, JsonObject> touched = new LinkedHashMap<>();
        Map<Terms, FillMath.Settlement> prices = new HashMap<>();
        long paid = 0, refunded = 0;
        int settledContracts = 0;
        for (Iterator<Contract> it = contracts.iterator(); it.hasNext(); ) {
            Contract c = it.next();
            if (c.terms.expiryTick() > now) continue;
            FillMath.Settlement s = prices.computeIfAbsent(c.terms, pricer);
            JsonObject row = row(touched, c.terms, s, now);
            if (s.credible()) {
                long toLong = FuturesMath.payout(true, s.price(), c.terms.low(), c.terms.high(), c.terms.multiplier(), c.lots);
                long toShort = FuturesMath.payout(false, s.price(), c.terms.low(), c.terms.high(), c.terms.multiplier(), c.lots);
                credit(c.longId, toLong);
                credit(c.shortId, toShort);
                paid += toLong + toShort;
                add(row, "paid", toLong + toShort);
            } else {
                long backLong = FuturesMath.collateralPerLot(true, c.terms.price(), c.terms.low(), c.terms.high(), c.terms.multiplier()) * c.lots;
                long backShort = FuturesMath.collateralPerLot(false, c.terms.price(), c.terms.low(), c.terms.high(), c.terms.multiplier()) * c.lots;
                credit(c.longId, backLong);
                credit(c.shortId, backShort);
                refunded += backLong + backShort;
                add(row, "refunded", backLong + backShort);
            }
            escrow -= c.escrowed();
            add(row, "contracts", 1);
            add(row, "lots", c.lots);
            settledContracts++;
            it.remove();
        }
        for (Iterator<Offer> it = offers.values().iterator(); it.hasNext(); ) {
            Offer o = it.next();
            if (o.terms.expiryTick() > now) continue;
            long refund = o.escrowed();
            escrow -= refund;
            credit(o.maker, refund);
            refunded += refund;
            JsonObject row = touched.computeIfAbsent(o.terms.series(), k -> baseRow(o.terms, now));
            add(row, "offersRefunded", refund);
            it.remove();
        }
        for (JsonObject row : touched.values()) {
            history.addLast(row);
            while (history.size() > HISTORY) history.removeFirst();
        }
        if (!touched.isEmpty()) setDirty();
        return new SettleResult(touched.size(), settledContracts, paid, refunded);
    }

    private static JsonObject row(Map<String, JsonObject> touched, Terms terms, FillMath.Settlement s, long now) {
        JsonObject row = touched.computeIfAbsent(terms.series(), k -> baseRow(terms, now));
        if (!row.has("reason") || row.get("reason").getAsString().equals("NO_CONTRACTS")) {
            row.addProperty("credible", s.credible());
            row.addProperty("price", s.price());
            row.addProperty("reason", s.credible() ? "SETTLED" : "VOID_" + s.reason());
            row.addProperty("traders", s.traders());
            row.addProperty("buckets", s.buckets());
        }
        return row;
    }

    private static JsonObject baseRow(Terms terms, long now) {
        JsonObject row = new JsonObject();
        row.addProperty("series", terms.series());
        row.addProperty("index", terms.index());
        row.addProperty("expiryDay", terms.expiryDay());
        row.addProperty("settledTick", now);
        row.addProperty("reason", "NO_CONTRACTS");
        return row;
    }

    private static void add(JsonObject row, String key, long amount) {
        row.addProperty(key, (row.has(key) ? row.get(key).getAsLong() : 0L) + amount);
    }

    public JsonObject status() {
        JsonObject o = new JsonObject();
        o.addProperty("escrow", escrow);
        o.addProperty("claims", claimsTotal());
        o.addProperty("feesBurned", feesBurned);
        o.addProperty("offers", offers.size());
        o.addProperty("contracts", contracts.size());
        o.addProperty("consistent", consistent());
        return o;
    }

    public JsonArray historyJson() {
        JsonArray a = new JsonArray();
        history.forEach(a::add);
        return a;
    }

    // ------------------------------------------------------------ persistence

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("nextId", nextId);
        tag.putLong("escrow", escrow);
        tag.putLong("feesBurned", feesBurned);
        ListTag offerTags = new ListTag();
        for (Offer o : offers.values()) {
            CompoundTag t = new CompoundTag();
            t.putInt("id", o.id);
            t.putUUID("maker", o.maker);
            t.putString("owner", o.makerOwner);
            t.putString("name", o.makerName);
            t.putBoolean("long", o.isLong);
            t.putInt("lots", o.lots);
            t.putLong("collateral", o.collateralPerLot);
            t.putLong("fee", o.feePerLot);
            t.putLong("created", o.created);
            t.put("terms", o.terms.save());
            offerTags.add(t);
        }
        tag.put("offers", offerTags);
        ListTag contractTags = new ListTag();
        for (Contract c : contracts) {
            CompoundTag t = new CompoundTag();
            t.putInt("id", c.id);
            t.putUUID("longId", c.longId);
            t.putString("longOwner", c.longOwner);
            t.putUUID("shortId", c.shortId);
            t.putString("shortOwner", c.shortOwner);
            t.putInt("lots", c.lots);
            t.putLong("opened", c.opened);
            t.put("terms", c.terms.save());
            contractTags.add(t);
        }
        tag.put("contracts", contractTags);
        CompoundTag claimTag = new CompoundTag();
        claims.forEach((id, amount) -> claimTag.putLong(id.toString(), amount));
        tag.put("claims", claimTag);
        ListTag historyTags = new ListTag();
        for (JsonObject row : history) historyTags.add(StringTag.valueOf(row.toString()));
        tag.put("history", historyTags);
        return tag;
    }

    /** Replaces this instance's state with a saved snapshot; used by isolated test fixtures. */
    public void restore(CompoundTag tag) {
        FuturesData other = load(tag);
        nextId = other.nextId;
        escrow = other.escrow;
        feesBurned = other.feesBurned;
        offers.clear();
        offers.putAll(other.offers);
        contracts.clear();
        contracts.addAll(other.contracts);
        claims.clear();
        claims.putAll(other.claims);
        history.clear();
        history.addAll(other.history);
        setDirty();
    }

    public static FuturesData load(CompoundTag tag) {
        FuturesData data = new FuturesData();
        data.nextId = Math.max(1, tag.getInt("nextId"));
        data.escrow = tag.getLong("escrow");
        data.feesBurned = tag.getLong("feesBurned");
        for (Tag raw : tag.getList("offers", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            Offer o = new Offer();
            o.id = t.getInt("id");
            o.maker = t.getUUID("maker");
            o.makerOwner = t.getString("owner");
            o.makerName = t.getString("name");
            o.isLong = t.getBoolean("long");
            o.lots = t.getInt("lots");
            o.collateralPerLot = t.getLong("collateral");
            o.feePerLot = t.getLong("fee");
            o.created = t.getLong("created");
            o.terms = Terms.load(t.getCompound("terms"));
            data.offers.put(o.id, o);
        }
        for (Tag raw : tag.getList("contracts", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            Contract c = new Contract();
            c.id = t.getInt("id");
            c.longId = t.getUUID("longId");
            c.longOwner = t.getString("longOwner");
            c.shortId = t.getUUID("shortId");
            c.shortOwner = t.getString("shortOwner");
            c.lots = t.getInt("lots");
            c.opened = t.getLong("opened");
            c.terms = Terms.load(t.getCompound("terms"));
            data.contracts.add(c);
        }
        CompoundTag claimTag = tag.getCompound("claims");
        for (String key : claimTag.getAllKeys()) {
            try {
                data.claims.put(UUID.fromString(key), claimTag.getLong(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
        for (Tag raw : tag.getList("history", Tag.TAG_STRING)) {
            try {
                data.history.addLast(JsonParser.parseString(raw.getAsString()).getAsJsonObject());
            } catch (RuntimeException ignored) {
            }
        }
        return data;
    }
}
