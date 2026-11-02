package com.society.sunlitmarket.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Reads only an on-disk snapshot; UNKNOWN means no authoritative live allegiance is available. */
public final class MiddleEarthCompat {
    private record Snapshot(long tick,String value){}
    private static final Map<UUID,Snapshot> CACHE=new HashMap<>();
    private MiddleEarthCompat(){}
    public static String savedFaction(ServerPlayer p){
        long now=p.server.overworld().getGameTime();Snapshot old=CACHE.get(p.getUUID());
        if(old!=null&&now>=old.tick&&now-old.tick<1200)return old.value;
        String value="UNKNOWN";
        try{
            CompoundTag root=p.server.overworld().getDataStorage().readTagFromDisk("middle_earth_player_datas",net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion());
            CompoundTag data=root.getCompound("data");
            for(Tag raw:data.getList("list",Tag.TAG_COMPOUND)){
                CompoundTag row=(CompoundTag)raw;
                boolean matches=row.hasUUID("uuid")?row.getUUID("uuid").equals(p.getUUID()):row.getString("uuid").equals(p.getUUID().toString());
                if(matches){String faction=row.getCompound("data").getString("faction");value=faction.isBlank()?"UNKNOWN":faction;break;}
            }
        }catch(Exception ignored){}
        CACHE.put(p.getUUID(),new Snapshot(now,value));return value;
    }
    public static void clear(){CACHE.clear();}
}
