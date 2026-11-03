package com.society.sunlitmarket.core;

import com.google.gson.*;
import com.society.sunlitmarket.SunlitMarketMod;
import com.society.sunlitmarket.alignment.FactionRelations;
import com.society.sunlitmarket.market.MarketRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.Files;
import java.util.*;

/** Explicit, reloadable rules. Empty defaults impose no pack-specific economy or laws. */
public final class MarketRules {
    public record Identity(String faction,String rank,boolean enforcer) {
        public boolean customer() { return !enforcer && rank.equals("CIVILIAN") && !faction.isBlank(); }
    }
    /**
     * One kind of counter request. {@code direction} is "sell" (the NPC sells goods to the player,
     * the original behaviour), "buy" (the NPC consumes goods from the counter and pays out of the
     * NPC daily budget at {@code bidPrice}) or "both".
     */
    public record Pool(String id,String faction,String item,int count,int baseValue,int marketValue,int askPrice,
                       int heat,int wanted,int reward,String nbt,String direction,int bidPrice) {
        public boolean sells() { return !direction.equals("buy"); }
        public boolean buys() { return direction.equals("buy")||direction.equals("both"); }
    }
    public record Law(String id,String faction,String dimension,String item,String tag,int tier) {
        public boolean matches(ItemStack stack,String f,String dim) {
            if (!faction.isBlank() && !faction.equals(f) || !dimension.isBlank() && !dimension.equals(dim)) return false;
            return !item.isBlank() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(item)
                    || !tag.isBlank() && stack.is(TagKey.create(Registries.ITEM,new ResourceLocation(tag)));
        }
    }
    private static Map<String,JsonObject> json = Map.of();
    private static final Map<String,JsonObject> scripts = new LinkedHashMap<>();
    private static Map<String,JsonObject> staging;
    public static synchronized void beginScripts() { staging=new LinkedHashMap<>(); }
    public static synchronized void endScripts(boolean valid) { if(staging!=null&&valid){scripts.clear();scripts.putAll(staging);} staging=null; }
    private static Map<String,JsonObject> scriptTarget() { return staging==null?scripts:staging; }
    private MarketRules() {}
    public static synchronized void loadJson() {
        var path=FMLPaths.CONFIGDIR.get().resolve("sunlitmarket/rules.json");
        Map<String,JsonObject> candidate=new LinkedHashMap<>();
        try {
            if (Files.isRegularFile(path)) for (JsonElement raw:JsonParser.parseString(Files.readString(path)).getAsJsonArray()) {
                JsonObject o=raw.getAsJsonObject(); validate(o); String id=o.get("id").getAsString();
                if(candidate.put(id,o.deepCopy())!=null) throw new IllegalArgumentException("Duplicate rule "+id);
            }
            json=Map.copyOf(candidate); MarketRegistry.changed();
        } catch(Exception ex) { SunlitMarketMod.LOG.error("[SunlitMarket] Invalid rules; previous snapshot retained",ex); }
    }
    public static synchronized void add(String text) {
        JsonObject o=JsonParser.parseString(text).getAsJsonObject(); validate(o);
        scriptTarget().put(o.get("id").getAsString(),o.deepCopy()); if(staging==null)MarketRegistry.changed();
    }
    public static synchronized void clearScripts() { scriptTarget().clear(); if(staging==null)MarketRegistry.changed(); }
    public static synchronized void removeScript(String id) { scripts.remove(id); MarketRegistry.changed(); }
    private static void validate(JsonObject o) {
        if(text(o,"id").isBlank()) throw new IllegalArgumentException("Rule id required");
        String type=text(o,"type");
        if(!Set.of("pool","npc","law").contains(type)) throw new IllegalArgumentException("Unknown rule type");
        if(type.equals("pool")) {
            if(ResourceLocation.tryParse(text(o,"item"))==null || number(o,"count",1)<1 || number(o,"count",1)>64
                    || number(o,"baseValue",0)<1 || number(o,"baseValue",0)>10000000
                    || number(o,"marketValue",number(o,"baseValue",0))<1 || number(o,"askPrice",number(o,"baseValue",0))<1)
                throw new IllegalArgumentException("Invalid pool");
        }
        if(type.equals("npc") && (text(o,"faction").isBlank() || (text(o,"tag").isBlank()&&text(o,"npcType").isBlank()
                &&text(o,"entity").isBlank()&&text(o,"profession").isBlank())))
            throw new IllegalArgumentException("NPC selector and faction required");
        if(type.equals("law") && (text(o,"item").isBlank()&&text(o,"tag").isBlank())) throw new IllegalArgumentException("Law selector required");
        if(type.equals("law") && (number(o,"tier",1)<1 || number(o,"tier",1)>4)) throw new IllegalArgumentException("Law tier must be 1..4");
        for (String key : new String[]{"item","dimension","faction","entity"}) if (!text(o,key).isBlank()
                && ResourceLocation.tryParse(key.equals("entity")?normalizeId(text(o,key)):text(o,key)) == null)
            throw new IllegalArgumentException("Invalid resource id: " + key);
        if(!text(o,"profession").isBlank() && ResourceLocation.tryParse(normalizeId(text(o,"profession"))) == null)
            throw new IllegalArgumentException("Invalid profession id");
        if(type.equals("law") && !text(o,"tag").isBlank() && ResourceLocation.tryParse(text(o,"tag")) == null)
            throw new IllegalArgumentException("Invalid law tag");
        if(type.equals("pool")) {
            int market=number(o,"marketValue",number(o,"baseValue",0)), ask=number(o,"askPrice",market);
            if(market>10000000 || ask>10000000 || number(o,"heat",0)<0 || number(o,"heat",0)>10000
                    || number(o,"repGain",1)<0 || number(o,"repGain",1)>100 || number(o,"wanted",5)<0 || number(o,"wanted",5)>10000)
                throw new IllegalArgumentException("Pool value out of range");
            if(!Set.of("","sell","buy","both").contains(text(o,"direction"))) throw new IllegalArgumentException("Pool direction must be sell, buy or both");
            int bid=number(o,"bidPrice",market);
            if(bid<1||bid>10000000) throw new IllegalArgumentException("Pool bidPrice out of range");
            if(!text(o,"nbt").isBlank()) try { net.minecraft.nbt.TagParser.parseTag(text(o,"nbt")); }
            catch(com.mojang.brigadier.exceptions.CommandSyntaxException ex) { throw new IllegalArgumentException("Invalid pool NBT",ex); }
        }
        if(o.has("radius") && (!Double.isFinite(o.get("radius").getAsDouble()) || o.get("radius").getAsDouble()<=0))
            throw new IllegalArgumentException("Invalid jurisdiction radius");
    }
    private static synchronized List<JsonObject> all() {
        Map<String,JsonObject> result=new LinkedHashMap<>(json); result.putAll(scripts); return List.copyOf(result.values());
    }
    public static Identity identity(Entity entity) {
        String type=MarketRegistry.readNpcType(entity);
        var nativeIdentity=FactionRelations.identity(type);
        Identity result=new Identity(nativeIdentity.faction(),nativeIdentity.rank(),false);
        for(JsonObject o:all()) if(text(o,"type").equals("npc")&&matchesNpc(o,entity,type))
            result=new Identity(text(o,"faction"),text(o,"rank"),o.has("enforcer")&&o.get("enforcer").getAsBoolean());
        return result;
    }
    private static String entityId(Entity entity){return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();}
    private static String professionId(Entity entity){
        return entity instanceof Villager villager
                ? net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession()).toString() : "";
    }
    private static String normalizeId(String value){return value.indexOf(':')<0?"minecraft:"+value:value;}
    private static boolean matchesNpc(JsonObject o,Entity entity,String npcType){
        String tag=text(o,"tag"),type=text(o,"npcType"),kind=text(o,"entity"),profession=text(o,"profession"),key=text(o,"npcKey");
        boolean selector=false,matched=false;
        if(!tag.isBlank()){selector=true;matched|=entity.getTags().contains(tag);}
        if(!key.isBlank()){selector=true;matched|=key.equals(MarketRegistry.readNpcKey(entity));}
        if(!type.isBlank()){selector=true;matched|=type.equals(npcType);}
        if(!kind.isBlank()){selector=true;matched|=normalizeId(kind).equals(entityId(entity));}
        if(selector&&!matched)return false;
        if(!profession.isBlank()&&!normalizeId(profession).equals(professionId(entity)))return false;
        return selector||!profession.isBlank();
    }
    public static boolean inJurisdiction(Entity npc, Entity target) {
        String npcType=MarketRegistry.readNpcType(npc);
        for(JsonObject o:all())             if(text(o,"type").equals("npc") && o.has("enforcer") && o.get("enforcer").getAsBoolean()
                && matchesNpc(o,npc,npcType)) {
            String dimension=text(o,"dimension");
            if(dimension.isBlank() || !dimension.equals(target.level().dimension().location().toString())) continue;
            if(o.has("x")&&o.has("z")) {
                double dx=target.getX()-o.get("x").getAsDouble(), dz=target.getZ()-o.get("z").getAsDouble();
                double radius=o.has("radius")?o.get("radius").getAsDouble():512;
                if(dx*dx+dz*dz>radius*radius)continue;
            }
            return true;
        }
        return false;
    }
    public static List<Pool> pools(String faction) {
        List<Pool> result=new ArrayList<>();
        for(JsonObject o:all()) if(text(o,"type").equals("pool") && (text(o,"faction").isBlank()||text(o,"faction").equals(faction))) {
            int base=number(o,"baseValue",1),market=number(o,"marketValue",base);
            result.add(new Pool(text(o,"id"),text(o,"faction"),text(o,"item"),number(o,"count",1),base,market,
                    number(o,"askPrice",market),number(o,"heat",0),number(o,"wanted",5),number(o,"repGain",1),text(o,"nbt"),
                    text(o,"direction").isBlank()?"sell":text(o,"direction"),number(o,"bidPrice",market)));
        }
        for (var merchant : MarketRegistry.all()) if (merchant.faction.equals(faction)) {
            for (int i=0;i<merchant.offers.size();i++) {
                var offer=merchant.offers.get(i);
                if (!offer.kind.equals("offer")) continue;
                String id="merchant:"+merchant.id+":"+(offer.id.isBlank()?"legacy:"+i:offer.id);
                result.add(new Pool(id,faction,offer.item,offer.count,offer.baseValue,offer.marketValue,offer.askPrice,
                        offer.heat,5,offer.repGain,offer.nbt,"sell",offer.marketValue));
            }
        }
        return result;
    }
    public static boolean poolExists(String id,String faction) { return pools(faction).stream().anyMatch(p->p.id.equals(id)); }
    public static int contraband(ItemStack stack,String faction,String dimension) {
        int tier=0;
        for(JsonObject o:all()) if(text(o,"type").equals("law")) {
            Law law=new Law(text(o,"id"),text(o,"faction"),text(o,"dimension"),text(o,"item"),text(o,"tag"),number(o,"tier",1));
            if(law.matches(stack,faction,dimension)) tier=Math.max(tier,law.tier);
        }
        return tier;
    }
    public static String text(JsonObject o,String k) { return o.has(k)?o.get(k).getAsString():""; }
    public static int number(JsonObject o,String k,int fallback) { return o.has(k)?o.get(k).getAsInt():fallback; }
}
