package com.society.sunlitmarket.counter;

import com.society.sunlitmarket.config.CoreConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Global per-NPC issuance quota prevents multiple counters duplicating a customer. */
public final class CustomerLedger extends SavedData {
    private final Map<UUID,long[]> rows=new HashMap<>();
    public static CustomerLedger get(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(CustomerLedger::load,CustomerLedger::new,"sunlitmarket_customers");
    }
    public boolean reserve(UUID npc,long tick,long day) {
        long[] row=rows.computeIfAbsent(npc,k->new long[]{Long.MIN_VALUE,day,0});
        if(day>row[1]) { row[1]=day;row[2]=0; }
        if(tick<row[0] || row[2]>=CoreConfig.NPC_DAILY_LIMIT.get()) return false;
        row[0]=tick+Math.max(CoreConfig.NPC_COOLDOWN.get(),CoreConfig.REQUEST_LIFETIME.get());row[2]++;setDirty();return true;
    }
    @Override public CompoundTag save(CompoundTag t) {
        CompoundTag all=new CompoundTag();rows.forEach((id,row)->all.putLongArray(id.toString(),row));t.put("rows",all);return t;
    }
    public static CustomerLedger load(CompoundTag t) {
        CustomerLedger d=new CustomerLedger();CompoundTag all=t.getCompound("rows");
        for(String key:all.getAllKeys()) try { long[] row=all.getLongArray(key);if(row.length==3)d.rows.put(UUID.fromString(key),row); }catch(IllegalArgumentException ignored){}
        return d;
    }
}
