package com.society.sunlitmarket.audit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.Map;

/** Statistics checkpoint only; never a source of funds or transaction replay. */
public final class AuditCheckpoint extends SavedData {
    private static final String NAME="sunlitmarket_audit";
    private long attempted, written, dropped, unknown, confirmed, failed;
    private final Map<String,Long> sourceCounts=new HashMap<>();
    private long observedNet;
    public static AuditCheckpoint get(MinecraftServer s){return s.overworld().getDataStorage().computeIfAbsent(AuditCheckpoint::load,AuditCheckpoint::new,NAME);}
    public synchronized void markAttempted(){attempted++;setDirty();}
    public synchronized void written(String source,long delta,boolean known){written++;sourceCounts.merge(source,1L,Long::sum);if(known){confirmed++;observedNet+=delta;}else unknown++;setDirty();}
    public synchronized void markFailed(){failed++;setDirty();}
    public synchronized void markDropped(){dropped++;setDirty();}
    public synchronized long attempted(){return attempted;}
    public synchronized long written(){return written;}
    public synchronized long dropped(){return dropped;}
    public synchronized long unknown(){return unknown;}
    public synchronized long confirmed(){return confirmed;}
    public synchronized long failed(){return failed;}
    public synchronized long observedNet(){return observedNet;}
    public synchronized Map<String,Long> sources(){return Map.copyOf(sourceCounts);}
    @Override public synchronized CompoundTag save(CompoundTag t){
        t.putLong("attempted",attempted);t.putLong("written",written);t.putLong("dropped",dropped);t.putLong("unknown",unknown);t.putLong("confirmed",confirmed);t.putLong("failed",failed);t.putLong("observedNet",observedNet);
        CompoundTag sources=new CompoundTag();sourceCounts.forEach(sources::putLong);t.put("sources",sources);return t;
    }
    public static AuditCheckpoint load(CompoundTag t){AuditCheckpoint c=new AuditCheckpoint();c.attempted=t.getLong("attempted");c.written=t.getLong("written");c.dropped=t.getLong("dropped");c.unknown=t.getLong("unknown");c.confirmed=t.getLong("confirmed");c.failed=t.getLong("failed");c.observedNet=t.getLong("observedNet");CompoundTag s=t.getCompound("sources");for(String k:s.getAllKeys())c.sourceCounts.put(k,s.getLong(k));return c;}
}
