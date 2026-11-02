package com.society.sunlitmarket.crime;

import com.society.sunlitmarket.SunlitMarketMod;
import com.society.sunlitmarket.alignment.AlignmentData;
import com.society.sunlitmarket.config.CoreConfig;
import com.society.sunlitmarket.core.MarketHooks;
import com.society.sunlitmarket.core.MarketRules;
import com.society.sunlitmarket.core.TradeMath;
import com.society.sunlitmarket.counter.CounterBlockEntity;
import com.society.sunlitmarket.currency.Currency;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** Explicit provenance and jurisdiction; unrelated containers are never searched. */
public final class CrimeService {
    private static final String KEY="sunlitmarket:provenance";
    private CrimeService(){}
    public static void mark(ItemStack stack,int heat,long day,String faction){
        if(stack.isEmpty())return;
        CompoundTag tag=new CompoundTag();tag.putInt("heat",TradeMath.clamp(heat,0,10000));tag.putLong("day",day);tag.putString("faction",faction);
        stack.getOrCreateTag().put(KEY,tag);
    }
    public static int heat(ItemStack stack){return stack.hasTag()?Math.max(0,stack.getTag().getCompound(KEY).getInt("heat")):0;}
    public static void cool(ItemStack stack,long day){
        if(!stack.hasTag()||!stack.getTag().contains(KEY))return;
        CompoundTag t=stack.getTag().getCompound(KEY);long last=t.getLong("day");if(day<=last)return;
        int next=TradeMath.decay(t.getInt("heat"),TradeMath.days(last,day),CoreConfig.HEAT_DECAY.get());
        t.putLong("day",day);t.putInt("heat",next);
        if(next==0){stack.getTag().remove(KEY);if(stack.getTag().isEmpty())stack.setTag(null);}
    }
    private static LivingEntity inspector(ServerPlayer p){
        for(LivingEntity npc:p.serverLevel().getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(CoreConfig.INSPECTION_RANGE.get()),e->!(e instanceof Player)&&e.isAlive()))
            if(MarketRules.identity(npc).enforcer()&&MarketRules.inJurisdiction(npc,p))return npc;
        return null;
    }
    public static void tick(MinecraftServer server){
        long now=server.overworld().getGameTime();if(now%200!=0)return;
        long day=server.overworld().getDayTime()/24000L;
        for(ServerPlayer p:server.getPlayerList().getPlayers()){
            for(int i=0;i<p.getInventory().getContainerSize();i++)cool(p.getInventory().getItem(i),day);
            p.getInventory().setChanged();
            if(!CoreConfig.ENFORCEMENT.get()||AlignmentData.get(server).wanted(p)<CoreConfig.INSPECTION_THRESHOLD.get())continue;
            if(now<p.getPersistentData().getLong("sunlitmarket.nextInspection"))continue;
            LivingEntity npc=inspector(p);if(npc!=null)inspect(p,npc,MarketRules.identity(npc).faction());
        }
    }
    private static int tier(ItemStack stack,String faction,String dimension){
        return Math.max(heat(stack)>0?1:0,MarketRules.contraband(stack,faction,dimension));
    }
    public static String bribe(ServerPlayer p){
        if(!CoreConfig.BRIBE_ENABLED.get())return "贿赂功能未启用";
        if(MarketHooks.dispatching())return "禁止递归支付";
        LivingEntity npc=inspector(p);if(npc==null)return "附近没有该辖区检查员";
        long now=p.server.overworld().getGameTime();
        if(now<p.getPersistentData().getLong("sunlitmarket.nextBribe"))return "贿赂冷却中";
        if(AlignmentData.get(p.server).wanted(p)<=0)return "没有通缉记录";
        if(!Currency.pay(p,CoreConfig.BRIBE_PRICE.get()))return "贿赂支付失败";
        AlignmentData.get(p.server).addWanted(p,-CoreConfig.BRIBE_REDUCTION.get());
        p.getPersistentData().putLong("sunlitmarket.nextBribe",now+CoreConfig.INSPECTION_COOLDOWN.get());
        p.getPersistentData().putLong("sunlitmarket.nextInspection",now+CoreConfig.INSPECTION_COOLDOWN.get());
        SunlitMarketMod.LOG.info("[SunlitMarket] bribe player={} paid={}",p.getUUID(),CoreConfig.BRIBE_PRICE.get());return "已支付，通缉度下降";
    }
    public static String inspect(ServerPlayer p,LivingEntity npc,String faction){
        if(MarketHooks.dispatching())return "Recursive inspection denied";
        p.getPersistentData().putLong("sunlitmarket.nextInspection",p.server.overworld().getGameTime()+CoreConfig.INSPECTION_COOLDOWN.get());
        var before=MarketHooks.fire(new MarketHooks.Event("beforeInspection",p,npc,faction,"",ItemStack.EMPTY,0));
        if(before.cancelled)return "检查已取消";
        List<Container> sources=new ArrayList<>();sources.add(p.getInventory());
        if(CoreConfig.INSPECT_COUNTER.get()){
            int range=Math.min(8,CoreConfig.INSPECTION_RANGE.get());
            for(BlockPos pos:BlockPos.betweenClosed(p.blockPosition().offset(-range,-2,-range),p.blockPosition().offset(range,2,range)))
                if(p.level().hasChunkAt(pos)&&p.level().getBlockEntity(pos) instanceof CounterBlockEntity be&&be.access(p))sources.add(be);
        }
        int maxTier=0;
        String dimension=p.level().dimension().location().toString();
        for(Container c:sources)for(int i=0;i<c.getContainerSize();i++)maxTier=Math.max(maxTier,tier(c.getItem(i),faction,dimension));
        if(maxTier==0)return "检查完成：未发现违禁品";
        int fine=TradeMath.amount((long)maxTier*CoreConfig.FINE_PER_TIER.get());
        boolean paid=fine==0||Currency.pay(p,fine);
        int removed=0;
        if(CoreConfig.CONFISCATION.get())for(Container c:sources){
            for(int i=0;i<c.getContainerSize()&&removed<CoreConfig.CONFISCATION_LIMIT.get();i++){
                ItemStack stack=c.getItem(i);
                if(tier(stack,faction,dimension)>0){int n=Math.min(stack.getCount(),CoreConfig.CONFISCATION_LIMIT.get()-removed);stack.shrink(n);removed+=n;}
            }
            c.setChanged();
        }
        String message="检查：没收 "+removed+" 件"+(fine>0?(paid?"，罚金 "+fine:"，罚金未支付"):"");
        p.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
        SunlitMarketMod.LOG.info("[SunlitMarket] inspection player={} faction={} confiscated={} fine={} paid={}",p.getUUID(),faction,removed,fine,paid);
        MarketHooks.fire(new MarketHooks.Event("afterInspection",p,npc,faction,"",ItemStack.EMPTY,paid?fine:0));
        return message;
    }
}
