package com.society.sunlitmarket.market;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Market-maker state: index membership and the OHLC candle rings, per world.
 *
 * <p>Index members bind to a merchant offer by (merchantId, item). The member mid is the
 * offer's buy price times the price modifiers times the E1 shelf-scarcity factor, so the
 * index measures exactly what players can move by trading with the shelves. A member
 * ratio is measured against the offer's static script base, which makes the index a
 * pure basket of (modifier x scarcity) signals.
 *
 * <p>Sampling runs every {@link #SAMPLE_EVERY} server ticks from the mod tick hook and
 * folds into 1-day and 4-hour candle rings ({@link #PERIODS}). Missed buckets after
 * downtime are backfilled flat at the last close, so charts never show gaps.
 */
public final class MarketData extends SavedData {
    public static final String NAME = "sunlitmarket_market";
    public static final int[] PERIODS = {24000, 4000};
    public static final int RING = 240;
    public static final int SAMPLE_EVERY = 100;
    private static final long MAX_BACKFILL = 240;

    public static final class Member {
        public final String merchant;
        public final String item;

        public Member(String merchant, String item) {
            this.merchant = merchant;
            this.item = item;
        }
    }

    public static final class IndexState {
        public String name = "";
        public final List<Member> members = new ArrayList<>();
        public final Map<Integer, int[]> current = new LinkedHashMap<>();
        public final Map<Integer, Long> bucket = new LinkedHashMap<>();
        public final Map<Integer, ArrayDeque<int[]>> ring = new LinkedHashMap<>();
    }

    private final Map<String, IndexState> indices = new LinkedHashMap<>();
    private long lastSampleTick = Long.MIN_VALUE;

    public static MarketData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(MarketData::load, MarketData::new, NAME);
    }

    /** (Re)defines an index from KubeJS; existing candles are kept, membership is replaced. */
    public synchronized void defineIndex(String id, String name, List<Member> members) {
        IndexState state = indices.computeIfAbsent(id, k -> {
            IndexState s = new IndexState();
            for (int period : PERIODS) {
                s.ring.put(period, new ArrayDeque<>());
            }
            return s;
        });
        state.name = name == null ? id : name;
        state.members.clear();
        state.members.addAll(members);
        setDirty();
    }

    public synchronized List<String> ids() {
        return new ArrayList<>(indices.keySet());
    }

    /** Current index value in points, or -1 when unknown. */
    public synchronized int value(String id) {
        IndexState state = indices.get(id);
        int[] candle = state == null ? null : state.current.get(PERIODS[0]);
        return candle == null ? -1 : candle[3];
    }

    public synchronized String name(String id) {
        IndexState state = indices.get(id);
        return state == null ? id : state.name;
    }

    /** Up to {@code max} candles of one period, oldest first; empty when unknown. */
    public synchronized List<int[]> candles(String id, int period, int max) {
        IndexState state = indices.get(id);
        ArrayDeque<int[]> ring = state == null ? null : state.ring.get(period);
        List<int[]> result = new ArrayList<>();
        if (ring != null) {
            result.addAll(ring);
        }
        int[] current = state == null ? null : state.current.get(period);
        if (current != null) {
            result.add(current);
        }
        return result.size() <= max ? result : result.subList(result.size() - max, result.size());
    }

    /** One sampling pass; called from the server tick hook every SAMPLE_EVERY ticks. */
    public synchronized void sample(long dayTime, MinecraftServer server) {
        for (Map.Entry<String, IndexState> entry : indices.entrySet()) {
            IndexState state = entry.getValue();
            if (state.members.isEmpty()) {
                continue;
            }
            double[] ratios = new double[state.members.size()];
            StockData stock = StockData.get(server);
            for (int i = 0; i < state.members.size(); i++) {
                Member member = state.members.get(i);
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
                if (offer == null || offer.buy <= 0) {
                    ratios = null;
                    break;
                }
                double scarcity = 1.0D;
                if (offer.stock >= 0) {
                    int index = merchant.offers.indexOf(offer);
                    String key = MarketService.stockKey(merchant, index);
                    int shelf = stock.get(key, offer.stock);
                    scarcity = PriceEngine.scarcity(shelf, offer.stock,
                            com.society.sunlitmarket.config.CoreConfig.INVENTORY_ELASTICITY.get(),
                            com.society.sunlitmarket.config.CoreConfig.PRICE_MIN_MULT.get(),
                            com.society.sunlitmarket.config.CoreConfig.PRICE_MAX_MULT.get());
                }
                ratios[i] = PriceModifiers.get(member.merchant, member.item) * scarcity;
            }
            if (ratios == null) {
                continue;
            }
            int price = (int) Math.round(Math.max(1, IndexMath.value(ratios)));
            for (int period : PERIODS) {
                fold(state, period, dayTime / period, price);
            }
            lastSampleTick = dayTime;
            setDirty();
        }
    }

    synchronized IndexState state(String id) {
        return indices.get(id);
    }

    static void fold(IndexState state, int period, long bucket, int price) {
        int[] current = state.current.get(period);
        Long last = state.bucket.get(period);
        if (current == null) {
            state.current.put(period, IndexMath.seedCandle(price));
            state.bucket.put(period, bucket);
            return;
        }
        if (bucket <= last) {
            IndexMath.updateCandle(current, price);
            return;
        }
        state.ring.get(period).addLast(current);
        while (state.ring.get(period).size() > RING) {
            state.ring.get(period).removeFirst();
        }
        long missed = Math.min(MAX_BACKFILL, bucket - last - 1);
        for (long i = 0; i < missed; i++) {
            state.ring.get(period).addLast(new int[]{current[3], current[3], current[3], current[3]});
        }
        state.current.put(period, IndexMath.seedCandle(price));
        state.bucket.put(period, bucket);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong("lastSampleTick", lastSampleTick);
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
            CompoundTag periodTags = new CompoundTag();
            for (Map.Entry<Integer, int[]> e : state.current.entrySet()) {
                CompoundTag periodTag = new CompoundTag();
                periodTag.putLong("bucket", state.bucket.getOrDefault(e.getKey(), Long.MIN_VALUE));
                int[] c = e.getValue();
                periodTag.putIntArray("current", c);
                List<Integer> flat = new ArrayList<>();
                for (int[] candle : state.ring.get(e.getKey())) {
                    for (int value : candle) {
                        flat.add(value);
                    }
                }
                int[] flatArray = new int[flat.size()];
                for (int i = 0; i < flatArray.length; i++) {
                    flatArray[i] = flat.get(i);
                }
                periodTag.putIntArray("ring", flatArray);
                periodTags.put(Integer.toString(e.getKey()), periodTag);
            }
            indexTag.put("periods", periodTags);
            indexTags.put(entry.getKey(), indexTag);
        }
        tag.put("indices", indexTags);
        return tag;
    }


    public static MarketData load(CompoundTag tag) {
        MarketData data = new MarketData();
        data.lastSampleTick = tag.getLong("lastSampleTick");
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
            CompoundTag periodTags = indexTag.getCompound("periods");
            for (int period : PERIODS) {
                CompoundTag periodTag = periodTags.getCompound(Integer.toString(period));
                if (!periodTag.contains("current")) {
                    state.ring.put(period, new ArrayDeque<>());
                    continue;
                }
                state.ring.put(period, new ArrayDeque<>());
                int[] flat = periodTag.getIntArray("ring");
                for (int i = 0; i + 3 < flat.length; i += 4) {
                    state.ring.get(period).addLast(new int[]{flat[i], flat[i + 1], flat[i + 2], flat[i + 3]});
                }
                state.current.put(period, periodTag.getIntArray("current"));
                state.bucket.put(period, periodTag.getLong("bucket"));
            }
            data.indices.put(id, state);
        }
        return data;
    }
}
