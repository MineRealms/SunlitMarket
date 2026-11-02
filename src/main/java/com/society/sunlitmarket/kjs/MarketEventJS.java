package com.society.sunlitmarket.kjs;

import com.society.sunlitmarket.core.MarketHooks;
import dev.latvian.mods.kubejs.event.EventJS;

public final class MarketEventJS extends EventJS {
    public final MarketHooks.Event data;
    public MarketEventJS(MarketHooks.Event data){this.data=data;}
    public void cancelTrade(){data.cancelled=true;}
    public void setPrice(int price){data.price=price;}
}
