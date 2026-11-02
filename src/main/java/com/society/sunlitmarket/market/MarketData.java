package com.society.sunlitmarket.market;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Index membership, fill-built candles and the recent trade tape, per world.
 *
 * <p>Indices move only when a member (merchantId, item) actually trades. Each fill sets that
 * member's last impersonal price ratio ({@link TradeFill#ratio()}); members that never traded
 * count as 1.0. The index is {@link IndexMath#value} over those ratios, folded into 1-day and
 * 4-hour candle rings ({@link #PERIODS}) keyed by economy ticks, so {@code /time} cannot shift a
 * bar. A period with no trades has no candle: nothing is backfilled or sampled from quotes.
 *
 * <p>Version 1 worlds stored candles sampled from shelf quotes. Those are discarded on load
 * (membership is kept) because they never described real trades.
 */
public final class MarketData extends SavedData {
    public static final String NAME = "sunlitmarket_market";
    public static final int VERSION = 2;
    public static final int[] PERIODS = {24000, 4000};
    public static final int RING = 240;
    public static final int FILL_RING = 512;
    private static final Logger LOG = LogUtils.getLogger();

    public static final class Member {
        public final String merchant;
        public final String item;

        public Member(String merchant, String item) {
            this.merchant = merchant;
            this.item = item;
        }

        String key() {
            return merchant + '|' + item;
        }
    }

    public static final class IndexState {
        public String name = "";
        public final List<Member> members = new ArrayList<>();
        /** member key → last traded impersonal ratio. */
        public final Map<String, Double> ratios = new LinkedHashMap<>();
        public int value = -1;
        public final Map<Integer, Candle> current = new LinkedHashMap<>();
        public final Map<Integer, ArrayDeque<Candle>> ring = new LinkedHashMap<>();

        IndexState() {
            for (int period : PERIODS) ring.put(period, new ArrayDeque<>());
        }
    }

    private final Map<String, IndexState> indices = new LinkedHashMap<>();
    private final ArrayDeque<TradeFill> fills = new ArrayDeque<>();

    public static MarketData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(MarketData::load, MarketData::new, NAME);
    }

    /** (Re)defines an index from KubeJS; candles and the ratios of members still listed are kept. */
    public synchronized void defineIndex(String id, String name, List<Member> members) {
        IndexState state = indices.computeIfAbsent(id, k -> new IndexState());
        state.name = name == null ? id : name;
        state.members.clear();
        state.members.addAll(members);
        state.ratios.keySet().retainAll(members.stream().map(Member::key).toList());
        setDirty();
    }

    public synchronized List<String> ids() {
        return new ArrayList<>(indices.keySet());
    }

    public synchronized boolean has(String id) {
        return indices.containsKey(id);
    }

    /** Index value after the most recent fill, or -1 before any trade. */
    public synchronized int value(String id) {
        IndexState state = indices.get(id);
        return state == null ? -1 : state.value;
    }

    public synchronized String name(String id) {
        IndexState state = indices.get(id);
        return state == null ? id : state.name;
    }

    public synchronized List<Member> members(String id) {
        IndexState state = indices.get(id);
        return state == null ? List.of() : List.copyOf(state.members);
    }

    /** Up to {@code max} traded candles of one period, oldest first, as wire arrays. */
    public synchronized List<int[]> candles(String id, int period, int max) {
        List<int[]> result = new ArrayList<>();
        for (Candle c : bars(id, period)) result.add(c.wire());
        return result.size() <= max ? result : new ArrayList<>(result.subList(result.size() - max, result.size()));
    }

    /** Copies of every stored candle of one period, oldest first, live bar included. */
    public synchronized List<Candle> bars(String id, int period) {
        IndexState state = indices.get(id);
        List<Candle> result = new ArrayList<>();
        if (state == null) return result;
        ArrayDeque<Candle> ring = state.ring.get(period);
        if (ring != null) for (Candle c : ring) result.add(c.copy());
        Candle live = state.current.get(period);
        if (live != null) result.add(live.copy());
        return result;
    }

    /** 4-hour bars whose start lies in [fromTick, toTick). */
    public synchronized List<Candle> window(String id, long fromTick, long toTick) {
        int period = PERIODS[1];
        List<Candle> result = new ArrayList<>();
        for (Candle c : bars(id, period)) {
            long start = c.bucket * period;
            if (start >= fromTick && start < toTick) result.add(c);
        }
        return result;
    }

    /** Close of the newest bar that started within {@code windowTicks} of {@code nowTick}, or -1. */
    public synchronized int recentClose(String id, long nowTick, long windowTicks) {
        List<Candle> bars = bars(id, PERIODS[1]);
        if (bars.isEmpty()) return -1;
        Candle last = bars.get(bars.size() - 1);
        return last.bucket * PERIODS[1] >= nowTick - windowTicks ? last.close : -1;
    }

    /**
     * Books one executed trade and moves every index that lists the (merchant, item).
     *
     * @return how many indices moved
     */
    public synchronized int recordFill(TradeFill fill, long ownerCap) {
        fills.addLast(fill);
        while (fills.size() > FILL_RING) fills.removeFirst();
        int moved = 0;
        String key = fill.merchant() + '|' + fill.item();
        for (IndexState state : indices.values()) {
            double[] ratios = new double[state.members.size()];
            boolean member = false;
            for (int i = 0; i < ratios.length; i++) {
                String k = state.members.get(i).key();
                if (k.equals(key)) {
                    state.ratios.put(k, fill.ratio());
                    member = true;
                }
                ratios[i] = state.ratios.getOrDefault(k, 1.0D);
            }
            if (!member) continue;
            int price = (int) Math.round(Math.max(1, IndexMath.value(ratios)));
            state.value = price;
            for (int period : PERIODS) {
                fold(state, period, fill.tick() / period, price, fill.quantity(), fill.owner(), ownerCap);
            }
            moved++;
        }
        setDirty();
        return moved;
    }

    public synchronized List<TradeFill> fills() {
        return new ArrayList<>(fills);
    }

    synchronized IndexState state(String id) {
        return indices.get(id);
    }

    static void fold(IndexState state, int period, long bucket, int price, long quantity, String owner, long ownerCap) {
        Candle live = state.current.get(period);
        if (live == null || bucket > live.bucket) {
            if (live != null) {
                ArrayDeque<Candle> ring = state.ring.get(period);
                ring.addLast(live);
                while (ring.size() > RING) ring.removeFirst();
            }
            live = new Candle(bucket, price);
            state.current.put(period, live);
        }
        live.add(price, quantity, owner, ownerCap);
    }

    /**
     * Quote-based reference for display only: the old shelf-quote basket (modifiers x scarcity).
     * It is never stored or used for settlement; -1 when a member cannot be priced.
     */
    public int quoteReference(String id, MinecraftServer server) {
        List<Member> members = members(id);
        if (members.isEmpty()) return -1;
        double[] ratios = new double[members.size()];
        StockData stock = StockData.get(server);
        for (int i = 0; i < members.size(); i++) {
            Member member = members.get(i);
            Merchant merchant = MarketRegistry.get(member.merchant);
            Offer offer = null;
            if (merchant != null) {
                for (Offer candidate : merchant.offers) {
                    if (candidate.item.equals(member.item)) {
                        offer = candidate;
                        break;
                    }
                }
            }
            if (offer == null || offer.buy <= 0) return -1;
            double scarcity = 1.0D;
            if (offer.stock >= 0) {
                String key = MarketService.stockKey(merchant, merchant.offers.indexOf(offer));
                scarcity = PriceEngine.scarcity(stock.get(key, offer.stock), offer.stock,
                        com.society.sunlitmarket.config.CoreConfig.INVENTORY_ELASTICITY.get(),
                        com.society.sunlitmarket.config.CoreConfig.PRICE_MIN_MULT.get(),
                        com.society.sunlitmarket.config.CoreConfig.PRICE_MAX_MULT.get());
            }
            ratios[i] = PriceModifiers.get(member.merchant, member.item) * scarcity;
        }
        return (int) Math.round(Math.max(1, IndexMath.value(ratios)));
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag) {
        tag.putInt("version", VERSION);
        CompoundTag indexTags = new CompoundTag();
        for (Map.Entry<String, IndexState> entry : indices.entrySet()) {
            IndexState state = entry.getValue();
            CompoundTag indexTag = new CompoundTag();
            indexTag.putString("name", state.name);
            ListTag members = new ListTag();
            for (Member member : state.members) {
                CompoundTag memberTag = new CompoundTag();
                memberTag.putString("merchant", member.merchant);
                memberTag.putString("item", member.item);
                members.add(memberTag);
            }
            indexTag.put("members", members);
            CompoundTag ratios = new CompoundTag();
            state.ratios.forEach(ratios::putDouble);
            indexTag.put("ratios", ratios);
            indexTag.putInt("value", state.value);
            CompoundTag periodTags = new CompoundTag();
            for (int period : PERIODS) {
                CompoundTag periodTag = new CompoundTag();
                ListTag ring = new ListTag();
                for (Candle c : state.ring.get(period)) ring.add(c.save());
                periodTag.put("ring", ring);
                Candle live = state.current.get(period);
                if (live != null) periodTag.put("live", live.save());
                periodTags.put(Integer.toString(period), periodTag);
            }
            indexTag.put("periods", periodTags);
            indexTags.put(entry.getKey(), indexTag);
        }
        tag.put("indices", indexTags);
        ListTag fillTags = new ListTag();
        for (TradeFill fill : fills) fillTags.add(fill.save());
        tag.put("fills", fillTags);
        return tag;
    }

    /** Replaces this instance's state with a saved snapshot; used by isolated test fixtures. */
    public synchronized void restore(CompoundTag tag) {
        MarketData other = load(tag);
        indices.clear();
        indices.putAll(other.indices);
        fills.clear();
        fills.addAll(other.fills);
        setDirty();
    }

    public static MarketData load(CompoundTag tag) {
        MarketData data = new MarketData();
        boolean legacy = tag.getInt("version") < VERSION;
        CompoundTag indexTags = tag.getCompound("indices");
        for (String id : indexTags.getAllKeys()) {
            CompoundTag indexTag = indexTags.getCompound(id);
            IndexState state = new IndexState();
            state.name = indexTag.getString("name");
            ListTag members = indexTag.getList("members", Tag.TAG_COMPOUND);
            for (int i = 0; i < members.size(); i++) {
                CompoundTag memberTag = members.getCompound(i);
                state.members.add(new Member(memberTag.getString("merchant"), memberTag.getString("item")));
            }
            if (!legacy) {
                CompoundTag ratios = indexTag.getCompound("ratios");
                for (String key : ratios.getAllKeys()) state.ratios.put(key, ratios.getDouble(key));
                state.value = indexTag.contains("value") ? indexTag.getInt("value") : -1;
                CompoundTag periodTags = indexTag.getCompound("periods");
                for (int period : PERIODS) {
                    CompoundTag periodTag = periodTags.getCompound(Integer.toString(period));
                    for (Tag raw : periodTag.getList("ring", Tag.TAG_COMPOUND)) {
                        try {
                            state.ring.get(period).addLast(Candle.load((CompoundTag) raw));
                        } catch (IllegalArgumentException ignored) {
                            // A damaged bar is dropped; the rest of the history stays usable.
                        }
                    }
                    if (periodTag.contains("live")) {
                        try {
                            state.current.put(period, Candle.load(periodTag.getCompound("live")));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
            }
            data.indices.put(id, state);
        }
        if (!legacy) {
            for (Tag raw : tag.getList("fills", Tag.TAG_COMPOUND)) data.fills.addLast(TradeFill.load((CompoundTag) raw));
        } else if (!indexTags.isEmpty()) {
            LOG.info("[SunlitMarket] discarded quote-sampled candles from {} legacy indices; charts now start from real trades",
                    indexTags.size());
            data.setDirty();
        }
        return data;
    }
}
