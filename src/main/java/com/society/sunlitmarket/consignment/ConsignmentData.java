package com.society.sunlitmarket.consignment;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Player-to-player consignment board: one seller escrows a stack, sets a unit price, and anyone
 * else can buy part of it. The goods are held here (as an item id plus a count) so an offline
 * seller never blocks a sale, and the proceeds wait in {@link #claims} until the seller collects.
 *
 * <p>This class is pure bookkeeping: no registry lookups, no player objects, so it unit-tests
 * directly. Every listing keeps the price it was posted at, so changing the fee or the caps never
 * rewrites a live listing.
 */
public final class ConsignmentData extends SavedData {
    public static final String NAME = "sunlitmarket_consignment";
    /** Listings one player may hold at once. */
    public static final int PER_SELLER = 8;
    /** Listings the whole server may hold at once. */
    public static final int CAPACITY = 400;
    /** Highest unit price a listing may carry, in spur. */
    public static final int MAX_UNIT_PRICE = 1_000_000;

    public static final class Listing {
        public int id;
        public UUID seller;
        public String sellerName = "";
        public String item = "";
        public int count;
        public int remaining;
        public int unitPrice;
        public long created;

        public JsonObject json() {
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.addProperty("seller", sellerName);
            o.addProperty("item", item);
            o.addProperty("count", count);
            o.addProperty("remaining", remaining);
            o.addProperty("unitPrice", unitPrice);
            o.addProperty("created", created);
            return o;
        }
    }

    private int nextId = 1;
    private final Map<Integer, Listing> listings = new LinkedHashMap<>();
    private final Map<UUID, Long> claims = new HashMap<>();
    private final Map<UUID, String> claimNames = new HashMap<>();
    private long itemsSold;
    private long grossSpur;
    private long feesSpur;

    public int nextId() {
        return nextId;
    }

    public Listing listing(int id) {
        return listings.get(id);
    }

    public Collection<Listing> listings() {
        return List.copyOf(listings.values());
    }

    public List<Listing> listingsOf(UUID seller) {
        List<Listing> out = new ArrayList<>();
        for (Listing l : listings.values()) if (l.seller.equals(seller)) out.add(l);
        return out;
    }

    public int listingCount(UUID seller) {
        int n = 0;
        for (Listing l : listings.values()) if (l.seller.equals(seller)) n++;
        return n;
    }

    public long claim(UUID player) {
        return claims.getOrDefault(player, 0L);
    }

    public String claimName(UUID player) {
        return claimNames.getOrDefault(player, "");
    }

    public long claimsTotal() {
        long total = 0;
        for (long amount : claims.values()) total += amount;
        return total;
    }

    public long itemsSold() {
        return itemsSold;
    }

    public long grossSpur() {
        return grossSpur;
    }

    public long feesSpur() {
        return feesSpur;
    }

    /**
     * Escrows a stack. The caller has already taken the items out of the player's inventory.
     *
     * @throws IllegalArgumentException with a player-facing message when the board refuses it
     */
    public Listing post(UUID seller, String sellerName, String item, int count, int unitPrice, long now) {
        if (count < 1) throw new IllegalArgumentException("数量必须至少 1");
        if (unitPrice < 1 || unitPrice > MAX_UNIT_PRICE)
            throw new IllegalArgumentException("单价必须在 1.." + MAX_UNIT_PRICE + " spur");
        if (listingCount(seller) >= PER_SELLER)
            throw new IllegalArgumentException("你已经有 " + PER_SELLER + " 个在售挂单，先撤掉一些");
        if (listings.size() >= CAPACITY)
            throw new IllegalArgumentException("寄售行已满（" + CAPACITY + " 个挂单），稍后再试");
        Listing l = new Listing();
        l.id = nextId++;
        l.seller = seller;
        l.sellerName = sellerName == null ? "" : sellerName;
        l.item = item;
        l.count = count;
        l.remaining = count;
        l.unitPrice = unitPrice;
        l.created = now;
        listings.put(l.id, l);
        setDirty();
        return l;
    }

    /** Takes {@code count} units off a listing; drops the listing when it runs out. */
    public void take(Listing l, int count) {
        if (count < 1 || count > l.remaining) throw new IllegalArgumentException("超出剩余数量");
        l.remaining -= count;
        itemsSold += count;
        if (l.remaining <= 0) listings.remove(l.id);
        setDirty();
    }

    /** Puts units back after a sale that could not be paid for. */
    public void restore(Listing l, int count) {
        if (count < 1) return;
        l.remaining += count;
        itemsSold -= count;
        listings.putIfAbsent(l.id, l);
        setDirty();
    }

    /** Removes a listing and returns what the seller still has to be given back. */
    public int cancel(Listing l) {
        listings.remove(l.id);
        setDirty();
        return l.remaining;
    }

    /** Records the seller's cut of a sale; the money itself stays in the treasury till. */
    public void credit(UUID seller, String sellerName, long amount, long fee) {
        if (amount > 0) {
            claims.merge(seller, amount, Long::sum);
            if (sellerName != null && !sellerName.isBlank()) claimNames.put(seller, sellerName);
        }
        grossSpur += amount + fee;
        feesSpur += fee;
        setDirty();
    }

    public void delivered(UUID player, long amount) {
        long left = claims.getOrDefault(player, 0L) - amount;
        if (left > 0) claims.put(player, left);
        else {
            claims.remove(player);
            claimNames.remove(player);
        }
        setDirty();
    }

    /** Escrowed units plus unpaid claims, for the audit's sanity checks. */
    public boolean consistent() {
        if (listings.size() > CAPACITY) return false;
        long held = 0;
        for (Listing l : listings.values()) {
            if (l.remaining < 1 || l.remaining > l.count) return false;
            if (l.unitPrice < 1 || l.unitPrice > MAX_UNIT_PRICE) return false;
            held += l.remaining;
        }
        return held + itemsSold >= 0 && claims.values().stream().allMatch(v -> v >= 0);
    }

    public JsonObject status() {
        JsonObject o = new JsonObject();
        o.addProperty("listings", listings.size());
        o.addProperty("capacity", CAPACITY);
        o.addProperty("perSeller", PER_SELLER);
        o.addProperty("itemsSold", itemsSold);
        o.addProperty("grossSpur", grossSpur);
        o.addProperty("feesSpur", feesSpur);
        o.addProperty("claimsTotal", claimsTotal());
        o.addProperty("claimants", claims.size());
        return o;
    }

    public JsonArray browse() {
        JsonArray array = new JsonArray();
        for (Listing l : listings.values()) array.add(l.json());
        return array;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("nextId", nextId);
        tag.putLong("itemsSold", itemsSold);
        tag.putLong("grossSpur", grossSpur);
        tag.putLong("feesSpur", feesSpur);
        ListTag listTags = new ListTag();
        for (Listing l : listings.values()) {
            CompoundTag t = new CompoundTag();
            t.putInt("id", l.id);
            t.putUUID("seller", l.seller);
            t.putString("sellerName", l.sellerName);
            t.putString("item", l.item);
            t.putInt("count", l.count);
            t.putInt("remaining", l.remaining);
            t.putInt("unitPrice", l.unitPrice);
            t.putLong("created", l.created);
            listTags.add(t);
        }
        tag.put("listings", listTags);
        CompoundTag claimTag = new CompoundTag();
        claims.forEach((id, amount) -> claimTag.putLong(id.toString(), amount));
        tag.put("claims", claimTag);
        CompoundTag nameTag = new CompoundTag();
        claimNames.forEach((id, name) -> nameTag.putString(id.toString(), name));
        tag.put("claimNames", nameTag);
        return tag;
    }

    public static ConsignmentData load(CompoundTag tag) {
        ConsignmentData data = new ConsignmentData();
        data.nextId = Math.max(1, tag.getInt("nextId"));
        data.itemsSold = tag.getLong("itemsSold");
        data.grossSpur = tag.getLong("grossSpur");
        data.feesSpur = tag.getLong("feesSpur");
        for (Tag raw : tag.getList("listings", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            Listing l = new Listing();
            l.id = t.getInt("id");
            l.seller = t.getUUID("seller");
            l.sellerName = t.getString("sellerName");
            l.item = t.getString("item");
            l.count = t.getInt("count");
            l.remaining = t.getInt("remaining");
            l.unitPrice = t.getInt("unitPrice");
            l.created = t.getLong("created");
            if (l.item.isBlank() || l.remaining < 1) continue;
            data.listings.put(l.id, l);
        }
        CompoundTag claimTag = tag.getCompound("claims");
        for (String key : claimTag.getAllKeys()) {
            try {
                data.claims.put(UUID.fromString(key), claimTag.getLong(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
        CompoundTag nameTag = tag.getCompound("claimNames");
        for (String key : nameTag.getAllKeys()) {
            try {
                data.claimNames.put(UUID.fromString(key), nameTag.getString(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }

    /** Replaces this instance's state with a saved snapshot; used by isolated test fixtures. */
    public void restore(CompoundTag tag) {
        ConsignmentData other = load(tag);
        nextId = other.nextId;
        itemsSold = other.itemsSold;
        grossSpur = other.grossSpur;
        feesSpur = other.feesSpur;
        listings.clear();
        listings.putAll(other.listings);
        claims.clear();
        claims.putAll(other.claims);
        claimNames.clear();
        claimNames.putAll(other.claimNames);
        setDirty();
    }
}
