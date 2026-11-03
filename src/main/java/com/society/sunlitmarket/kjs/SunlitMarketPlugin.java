package com.society.sunlitmarket.kjs;

import com.society.sunlitmarket.SunlitMarketMod;
import com.society.sunlitmarket.market.MarketRegistry;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;

public final class SunlitMarketPlugin extends KubeJSPlugin {

    @Override
    public void registerEvents() { MarketScriptEvents.register(); }

    @Override
    public void init() {
        SunlitMarketMod.LOG.info("[SunlitMarket] KubeJS plugin loaded");
    }

    @Override
    public void registerBindings(BindingsEvent event) {
        if (event.getType() != dev.latvian.mods.kubejs.script.ScriptType.SERVER) return;
        MarketRegistry.beginScripts();
        com.society.sunlitmarket.core.MarketRules.beginScripts();
        com.society.sunlitmarket.market.PriceModifiers.beginScripts();
        com.society.sunlitmarket.currency.Currency.beginScripts();
        com.society.sunlitmarket.futures.FuturesSpecs.beginScripts();
        event.add("SunlitMarket", new SunlitMarketBinding());
    }

    /**
     * Syntax/evaluation failures are logged by KubeJS without entering console.errors,
     * but the failed ScriptFile keeps its source lines while a successful load clears them.
     */
    private static boolean scriptsLoaded() {
        if (!dev.latvian.mods.kubejs.script.ScriptType.SERVER.console.errors.isEmpty()) return false;
        var manager = dev.latvian.mods.kubejs.server.ServerScriptManager.instance;
        if (manager == null) return false;
        for (var pack : manager.packs.values())
            for (var file : pack.scripts)
                if (file.info.lines != null && file.info.lines.length > 0) return false;
        return true;
    }

    /**
     * Commits staged script data when no onServerReload follows. Mohist blocks /reload and
     * kubejs reload does not always fire the reload callback, so staging would stay pending forever.
     */
    public static void commitScripts() {
        if (!MarketRegistry.stagingOpen()) return;
        try {
            boolean valid = scriptsLoaded();
            MarketRegistry.endScripts(valid);
            com.society.sunlitmarket.core.MarketRules.endScripts(valid);
            com.society.sunlitmarket.market.PriceModifiers.endScripts(valid);
            com.society.sunlitmarket.currency.Currency.endScripts(valid);
            com.society.sunlitmarket.futures.FuturesSpecs.endScripts(valid);
            SunlitMarketMod.LOG.info("[SunlitMarket] committed staged script data (valid={})", valid);
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] staging commit failed", t);
        }
    }

    @Override
    public void onServerReload() {
        try {
            boolean valid = scriptsLoaded();
            MarketRegistry.endScripts(valid);
            com.society.sunlitmarket.core.MarketRules.endScripts(valid);
            com.society.sunlitmarket.market.PriceModifiers.endScripts(valid);
            com.society.sunlitmarket.currency.Currency.endScripts(valid);
            com.society.sunlitmarket.futures.FuturesSpecs.endScripts(valid);
            MarketRegistry.loadJson();
            com.society.sunlitmarket.core.MarketRules.loadJson();
            com.society.sunlitmarket.config.SunlitMarketConfig.invalidate();
        } catch (Throwable t) {
            SunlitMarketMod.LOG.warn("[SunlitMarket] reload failed", t);
        }
    }
}
