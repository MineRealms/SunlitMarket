package com.society.sunlitmarket.kjs;

import com.society.sunlitmarket.core.MarketHooks;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.script.ScriptType;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MarketScriptEvents {
    private static final EventGroup GROUP=EventGroup.of("SunlitMarketEvents");
    private static final Map<String,EventHandler> EVENTS=new LinkedHashMap<>();
    static {for(String name:new String[]{"beforeTrade","afterTrade","beforeRequest","afterRequest","beforeInspection","afterInspection"})
        EVENTS.put(name,GROUP.server(name,()->MarketEventJS.class));}
    private MarketScriptEvents(){}
    public static void register(){GROUP.register();MarketHooks.publisher(e->{var handler=EVENTS.get(e.kind);if(handler!=null)handler.post(ScriptType.SERVER,new MarketEventJS(e));});}
}
