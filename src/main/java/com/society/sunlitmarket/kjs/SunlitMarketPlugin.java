package com.society.sunlitmarket.kjs;

import com.society.sunlitmarket.SunlitMarketMod;
import com.society.sunlitmarket.market.MarketRegistry;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;

public final class SunlitMarketPlugin extends KubeJSPlugin {

    @Override
    public void init() {
        SunlitMarketMod.LOG.info("[SunlitMarket] KubeJS plugin loaded");
    }

    @Override
    public void registerBindings(BindingsEvent event) {
        event.add("SunlitMarket", new SunlitMarketBinding());
    }

    @Override
    public void onServerReload() {
        try {
            MarketRegistry.loadJson();
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] reload failed", t);
        }
    }
}
