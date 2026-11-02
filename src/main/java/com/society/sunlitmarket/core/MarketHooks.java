package com.society.sunlitmarket.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import java.util.function.Consumer;

/** Optional scripting bridge; common code never resolves KubeJS classes. */
public final class MarketHooks {
    public static final class Event {
        public final String kind, faction, requestId;
        public final ServerPlayer player;
        public final Entity npc;
        public final ItemStack item;
        public int price;
        public boolean cancelled;
        public Event(String kind, ServerPlayer player, Entity npc, String faction, String requestId, ItemStack item, int price) {
            this.kind=kind;this.player=player;this.npc=npc;this.faction=faction;this.requestId=requestId;this.item=item.copy();this.price=price;
        }
    }
    private static Consumer<Event> publisher=e->{};
    private static boolean dispatching;
    private MarketHooks(){}
    public static void publisher(Consumer<Event> value){publisher=value;}
    public static boolean dispatching(){return dispatching;}
    public static Event fire(Event e){
        if(dispatching){e.cancelled=true;return e;}
        dispatching=true;
        try{publisher.accept(e);}catch(RuntimeException ex){e.cancelled=true;com.society.sunlitmarket.SunlitMarketMod.LOG.error("[SunlitMarket] Script event failed: {}",e.kind,ex);}
        finally{dispatching=false;}
        return e;
    }
}
