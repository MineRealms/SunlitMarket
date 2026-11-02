package com.society.sunlitmarket.market;

import com.society.sunlitmarket.alignment.AlignmentData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Network authorization is separate from trusted server-side script trades. */
public final class TradeSessions {
    public static final class Session {
        public final UUID id = UUID.randomUUID();
        public final String merchant, dimension, owner;
        public final UUID entity;
        public final long expires, version;
        public long sequence;
        Session(ServerPlayer p, String merchant, Entity entity) {
            this.merchant = merchant; this.entity = entity == null ? null : entity.getUUID();
            dimension = p.level().dimension().location().toString(); owner = AlignmentData.ownerKey(p);
            expires = p.server.overworld().getGameTime() + 1200; version = MarketRegistry.version();
        }
    }
    private static final Map<UUID,Session> OPEN = new HashMap<>();
    private TradeSessions() {}
    public static Session open(ServerPlayer p, String merchant, Entity entity) {
        Session session = new Session(p, merchant, entity); OPEN.put(p.getUUID(), session); return session;
    }
    public static Session get(ServerPlayer p) { return OPEN.get(p.getUUID()); }
    public static void clear() { OPEN.clear(); }
    public static void close(ServerPlayer p) { OPEN.remove(p.getUUID()); }
    public static boolean authorize(ServerPlayer p, String id, long sequence, String merchant) {
        Session s = get(p);
        if (s == null || !s.id.toString().equals(id) || s.sequence != sequence || !s.merchant.equals(merchant)
                || s.version != MarketRegistry.version() || p.server.overworld().getGameTime() > s.expires
                || !p.isAlive() || !s.dimension.equals(p.level().dimension().location().toString())
                || !s.owner.equals(AlignmentData.ownerKey(p))) { close(p); return false; }
        if (s.entity != null) {
            Entity e = p.serverLevel().getEntity(s.entity);
            Merchant m = e == null ? null : MarketRegistry.resolve(e);
            if (e == null || !e.isAlive() || p.distanceToSqr(e) > 64 || m == null || !m.id.equals(merchant)) {
                close(p); return false;
            }
        }
        s.sequence++;
        return true;
    }
}
