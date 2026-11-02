from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
r=root/'src/main/java/com/society/sunlitmarket'
p=r/'SunlitMarketMod.java';s=p.read_text(encoding='utf-8')
s=s.replace('com.society.sunlitmarket.client.MarketClient.init());','{ com.society.sunlitmarket.client.MarketClient.init(); com.society.sunlitmarket.client.CounterClient.init(); });')
s=s.replace('        MarketRegistry.loadJson();\n        FactionRelations.reload', '        MarketRegistry.loadJson();\n        com.society.sunlitmarket.core.MarketRules.loadJson();\n        FactionRelations.reload')
s=s.replace('                    MarketRegistry.loadJson();\n                    ctx.getSource().sendSuccess', '                    MarketRegistry.loadJson();\n                    com.society.sunlitmarket.core.MarketRules.loadJson();\n                    SunlitMarketConfig.invalidate();\n                    FactionRelations.reload(ctx.getSource().getServer());\n                    ctx.getSource().sendSuccess')
p.write_text(s,encoding='utf-8')
p=r/'kjs/SunlitMarketPlugin.java';s=p.read_text(encoding='utf-8');s=s.replace('    @Override\n    public void init()', '    @Override\n    public void registerEvents() { MarketScriptEvents.register(); }\n\n    @Override\n    public void init()');s=s.replace('            MarketRegistry.loadJson();','            MarketRegistry.loadJson();\n            com.society.sunlitmarket.core.MarketRules.loadJson();\n            com.society.sunlitmarket.config.SunlitMarketConfig.invalidate();');p.write_text(s,encoding='utf-8')
p=r/'kjs/SunlitMarketBinding.java';s=p.read_text(encoding='utf-8');s=s.replace('    private static String toJson(Object value)', '''    public void rule(Object spec) { com.society.sunlitmarket.core.MarketRules.add(toJson(spec)); }
    public void clearRules() { com.society.sunlitmarket.core.MarketRules.clearScripts(); }
    public int heat(net.minecraft.world.item.ItemStack stack) { return com.society.sunlitmarket.crime.CrimeService.heat(stack); }
    public void markStolen(net.minecraft.world.item.ItemStack stack, int heat) {
        var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if(server!=null) com.society.sunlitmarket.crime.CrimeService.mark(stack,heat,server.overworld().getDayTime()/24000L,"");
    }
    public String owner(Player player) { return AlignmentData.ownerKey(player); }
    public String identity(net.minecraft.world.entity.Entity entity) {
        var i=com.society.sunlitmarket.core.MarketRules.identity(entity);
        return i.faction()+" / "+i.rank()+" / customer="+i.customer()+" / enforcer="+i.enforcer();
    }
    public double regionWeight(net.minecraft.world.entity.Entity entity,String faction) { return FactionRelations.regionWeight(faction,entity); }

    private static String toJson(Object value)''');p.write_text(s,encoding='utf-8')
resources={
'assets/sunlitmarket/blockstates/counter.json':{'variants':{'':{'model':'sunlitmarket:block/counter'}}},
'assets/sunlitmarket/models/block/counter.json':{'parent':'minecraft:block/cube_bottom_top','textures':{'top':'minecraft:block/crafting_table_top','bottom':'minecraft:block/oak_planks','side':'minecraft:block/barrel_side'}},
'assets/sunlitmarket/models/item/counter.json':{'parent':'sunlitmarket:block/counter'},
'assets/sunlitmarket/lang/en_us.json':{'block.sunlitmarket.counter':'Trading Counter'},
'assets/sunlitmarket/lang/zh_cn.json':{'block.sunlitmarket.counter':'当铺柜台'},
'data/sunlitmarket/recipes/counter.json':{'type':'minecraft:crafting_shaped','pattern':['SSS','P P','PPP'],'key':{'S':{'tag':'minecraft:wooden_slabs'},'P':{'tag':'minecraft:planks'}},'result':{'item':'sunlitmarket:counter'}},
'data/sunlitmarket/loot_tables/blocks/counter.json':{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'sunlitmarket:counter'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]},
'data/minecraft/tags/blocks/mineable/axe.json':{'replace':False,'values':['sunlitmarket:counter']}
}
for rel,data in resources.items():
 p=root/'src/main/resources'/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
