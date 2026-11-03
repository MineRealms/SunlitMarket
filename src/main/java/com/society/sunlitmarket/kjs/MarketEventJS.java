package com.society.sunlitmarket.kjs;

import com.society.sunlitmarket.core.MarketHooks;
import dev.latvian.mods.kubejs.event.EventJS;

public final class MarketEventJS extends EventJS {
    public final MarketHooks.Event data;
    public MarketEventJS(MarketHooks.Event data){this.data=data;}
    public void cancelTrade(){data.cancelled=true;}
    public void setPrice(int price){data.price=price;}
    // Read accessors: Rhino reaches these as event.player / event.npc / event.price.
    public net.minecraft.server.level.ServerPlayer getPlayer(){return data.player;}
    public net.minecraft.world.entity.Entity getNpc(){return data.npc;}
    public String getFaction(){return data.faction;}
    public String getKind(){return data.kind;}
    public String getRequestId(){return data.requestId;}
    public int getPrice(){return data.price;}
    public boolean isCancelled(){return data.cancelled;}
}
