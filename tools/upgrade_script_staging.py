from pathlib import Path
root=Path(__file__).resolve().parents[1]/'src/main/java/com/society/sunlitmarket'
p=root/'market/MarketRegistry.java';s=p.read_text(encoding='utf-8')
s=s.replace('    private static final Map<String, Merchant> SCRIPTS = new LinkedHashMap<>();','''    private static final Map<String, Merchant> SCRIPTS = new LinkedHashMap<>();
    private static Map<String, Merchant> staging;
    private static boolean stagingFailed;
    public static synchronized void beginScripts() { staging = new LinkedHashMap<>(); stagingFailed = false; }
    public static synchronized void endScripts(boolean valid) {
        if (staging != null && valid && !stagingFailed) { SCRIPTS.clear(); SCRIPTS.putAll(staging); }
        staging = null; changed();
    }
    private static Map<String,Merchant> scriptTarget() { return staging == null ? SCRIPTS : staging; }''')
s=s.replace('if (merchant.valid()) { SCRIPTS.put(merchant.id, merchant); changed(); }','if (!merchant.valid()) throw new IllegalArgumentException("Invalid script merchant");\n            scriptTarget().put(merchant.id, merchant); if (staging == null) changed();')
s=s.replace('            SunlitMarketMod.LOG.warn("[SunlitMarket] bad script merchant JSON", t);','            stagingFailed = true;\n            SunlitMarketMod.LOG.warn("[SunlitMarket] bad script merchant JSON", t);')
s=s.replace('            Merchant merchant = SCRIPTS.get(merchantId);','            Merchant merchant = scriptTarget().get(merchantId);')
s=s.replace('                SCRIPTS.put(merchantId, merchant);','                scriptTarget().put(merchantId, merchant);')
s=s.replace('            merchant.offers.add(offer); changed();','            merchant.offers.add(offer); if (staging == null) changed();')
s=s.replace('            SunlitMarketMod.LOG.warn("[SunlitMarket] bad script offer JSON", t);','            stagingFailed = true;\n            SunlitMarketMod.LOG.warn("[SunlitMarket] bad script offer JSON", t);')
s=s.replace('        SCRIPTS.clear(); changed();','        scriptTarget().clear(); if (staging == null) changed();')
p.write_text(s,encoding='utf-8')
p=root/'core/MarketRules.java';s=p.read_text(encoding='utf-8')
s=s.replace('    private static final Map<String,JsonObject> scripts = new LinkedHashMap<>();','''    private static final Map<String,JsonObject> scripts = new LinkedHashMap<>();
    private static Map<String,JsonObject> staging;
    public static synchronized void beginScripts() { staging=new LinkedHashMap<>(); }
    public static synchronized void endScripts(boolean valid) { if(staging!=null&&valid){scripts.clear();scripts.putAll(staging);} staging=null; }
    private static Map<String,JsonObject> scriptTarget() { return staging==null?scripts:staging; }''')
s=s.replace('scripts.put(o.get("id").getAsString(),o.deepCopy()); MarketRegistry.changed();','scriptTarget().put(o.get("id").getAsString(),o.deepCopy()); if(staging==null)MarketRegistry.changed();')
s=s.replace('public static synchronized void clearScripts() { scripts.clear(); MarketRegistry.changed(); }','public static synchronized void clearScripts() { scriptTarget().clear(); if(staging==null)MarketRegistry.changed(); }')
p.write_text(s,encoding='utf-8')
p=root/'kjs/SunlitMarketPlugin.java';s=p.read_text(encoding='utf-8')
s=s.replace('        event.add("SunlitMarket", new SunlitMarketBinding());','''        if (event.getType() != dev.latvian.mods.kubejs.script.ScriptType.SERVER) return;
        MarketRegistry.beginScripts();
        com.society.sunlitmarket.core.MarketRules.beginScripts();
        com.society.sunlitmarket.market.PriceModifiers.clear();
        com.society.sunlitmarket.currency.Currency.resetValues();
        event.add("SunlitMarket", new SunlitMarketBinding());''')
s=s.replace('        try {\n            MarketRegistry.loadJson();','''        try {
            boolean valid = dev.latvian.mods.kubejs.script.ScriptType.SERVER.console.errors.isEmpty();
            MarketRegistry.endScripts(valid);
            com.society.sunlitmarket.core.MarketRules.endScripts(valid);
            MarketRegistry.loadJson();''')
p.write_text(s,encoding='utf-8')
