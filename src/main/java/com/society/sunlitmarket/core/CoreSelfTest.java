package com.society.sunlitmarket.core;

import com.society.sunlitmarket.SunlitMarketMod;
import com.society.sunlitmarket.alignment.AlignmentData;
import com.society.sunlitmarket.config.CoreConfig;
import com.society.sunlitmarket.counter.*;
import com.society.sunlitmarket.currency.Currency;
import com.society.sunlitmarket.crime.CrimeService;
import com.society.sunlitmarket.market.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.List;

/** Explicit opt-in integration probe; restores inventory, alignment and temporary fixtures. */
public final class CoreSelfTest {
    private CoreSelfTest(){}
    public static String run(ServerPlayer p){
        if(Currency.balance(p)<0)return "FAIL bank unavailable";
        List<String> passed=new ArrayList<>();
        var inventory=Currency.snapshot(p.getInventory());
        int originalAlignment=AlignmentData.get(p.server).get(p,"sunlitmarket:test");
        int originalWanted=AlignmentData.get(p.server).wanted(p);
        int originalFine=CoreConfig.FINE_PER_TIER.get();
        BlockPos pos=p.blockPosition().above(2);var oldState=p.level().getBlockState(pos);
        if(!oldState.isAir())return "FAIL test position must be air two blocks above player";
        Villager npc=null;
        String migrationId="__core_migration_"+p.server.overworld().getGameTime();
        int emerald=Currency.coinValue("minecraft:emerald");
        try{
            Currency.setCoinValue("minecraft:emerald",1);
            // This probe uses item currency only; installed-bank settlement is separately verified.
            if(net.minecraftforge.fml.ModList.get().isLoaded("numismatics"))return "FAIL run coin fixture without Numismatics";
            p.getInventory().clearContent();p.getInventory().setItem(0,new ItemStack(Items.EMERALD,64));
            AlignmentData data=AlignmentData.get(p.server);data.set(p,"sunlitmarket:test",0);data.setWanted(p,0);CoreConfig.FINE_PER_TIER.set(0);
            MarketRegistry.addScriptMerchant("{\"id\":\"__core_test\",\"tag\":\"sunlitmarket.test.merchant\",\"faction\":\"sunlitmarket:test\",\"offers\":[{\"id\":\"stone\",\"item\":\"minecraft:stone\",\"buy\":4,\"sell\":2},{\"id\":\"sellonly\",\"item\":\"minecraft:dirt\",\"sell\":5}]}");
            Merchant merchant=MarketRegistry.get("__core_test");
            check(MarketService.view(p,merchant).offers.get(1).buy==0,"disabled direction",passed);
            data.set(p,"sunlitmarket:test",-1);
            check(MarketService.handleAction(p,merchant.id,0,MarketService.ACTION_BUY,1),"negative alignment trade",passed);
            check(Currency.countItems(p,"minecraft:stone")==1,"goods delivered",passed);
            check(!MarketService.handleAction(p,merchant.id,1,MarketService.ACTION_BUY,1),"disabled trade refused",passed);
            int balance=Currency.balance(p);
            check(!MarketService.handleAction(p,merchant.id,0,MarketService.ACTION_BUY,0)&&Currency.balance(p)==balance,"invalid quantity unchanged",passed);
            p.getInventory().setItem(0,new ItemStack(Items.EMERALD,64));
            for(int i=1;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.DIRT,64));
            balance=Currency.balance(p);
            check(!MarketService.handleAction(p,merchant.id,0,MarketService.ACTION_BUY,1)&&Currency.balance(p)==balance,"full inventory unchanged",passed);
            p.getInventory().clearContent();p.getInventory().setItem(0,new ItemStack(Items.EMERALD,64));
            data.set(p,"sunlitmarket:test",3000);
            var inversion=new Offer();inversion.item="minecraft:bread";inversion.buy=90;inversion.sell=70;
            var quote=MarketService.quote(p,merchant,inversion);check(quote.sell()<=quote.buy(),"noninverted quote",passed);
            CompoundTag saved=data.save(new CompoundTag());var restored=AlignmentData.load(saved);
            check(restored.get(p,"sunlitmarket:test")==3000,"alignment serialization",passed);
            data.set(p,"sunlitmarket:test",0);
            MarketRegistry.addScriptMerchant("{\"id\":\""+migrationId+"\",\"tag\":\"sunlitmarket.test.migration\",\"faction\":\"sunlitmarket:test\",\"offers\":[{\"id\":\"dirt\",\"legacyIndex\":0,\"item\":\"minecraft:dirt\",\"buy\":1,\"stock\":10,\"limit\":5}]}");
            StockData stock=StockData.get(p.server);LimitData limits=LimitData.get(p.server);
            long migrationDay=p.server.overworld().getDayTime()/24000L;stock.roll(migrationDay);limits.roll(migrationDay);
            stock.take(migrationId+"#0",10,4);limits.add(p,migrationId+"#0",2);
            var migrated=MarketService.view(p,MarketRegistry.get(migrationId)).offers.get(0);
            check(migrated.stock==6&&migrated.used==2,"legacy offer migration stock="+migrated.stock+",used="+migrated.used,passed);
            check(MarketService.handleAction(p,migrationId,0,MarketService.ACTION_BUY,1)
                    &&MarketService.view(p,MarketRegistry.get(migrationId)).offers.get(0).stock==5
                    &&MarketService.view(p,MarketRegistry.get(migrationId)).offers.get(0).used==3,"migrated stock and limit persist",passed);
            MarketRules.add("{\"type\":\"npc\",\"id\":\"__core_npc\",\"tag\":\"sunlitmarket.test.customer\",\"faction\":\"sunlitmarket:test\",\"rank\":\"CIVILIAN\"}");
            MarketRules.add("{\"type\":\"pool\",\"id\":\"__core_pool\",\"faction\":\"sunlitmarket:test\",\"item\":\"minecraft:apple\",\"count\":2,\"baseValue\":5,\"marketValue\":5,\"askPrice\":5,\"repGain\":1}");
            p.level().setBlock(pos,CounterRegistry.BLOCK.get().defaultBlockState(),3);
            CounterBlockEntity be=(CounterBlockEntity)p.level().getBlockEntity(pos);be.assign(p);
            npc=EntityType.VILLAGER.create(p.serverLevel());if(npc==null)throw new IllegalStateException("No villager");
            npc.setPos(p.getX()+1,p.getY(),p.getZ());npc.addTag("sunlitmarket.test.customer");npc.setNoAi(true);p.serverLevel().addFreshEntity(npc);
            check(MarketRules.identity(npc).customer(),"civilian identity",passed);
            CounterBlockEntity.tick(p.level(),pos,p.level().getBlockState(pos),be);
            check(!be.requests.isEmpty(),"request generated",passed);
            var request=be.requests.get(0);int before=Currency.balance(p);
            String result=be.act(p,request.id,0);
            check(result.contains("完成")&&Currency.balance(p)==before-5&&be.getItem(0).getCount()==2,"counter settlement",passed);
            check(be.act(p,request.id,0).contains("已处理")&&Currency.balance(p)==before-5,"request replay refused",passed);
            check(data.get(p,"sunlitmarket:test")>0,"trade alignment reward",passed);
            var pool=MarketRules.pools("sunlitmarket:test").get(0);
            CustomerRequest haggle=CustomerRequest.create(npc.getUUID(),"sunlitmarket:test",pool,p.server.overworld().getGameTime()+1000);
            be.requests.add(haggle);int oldAsk=haggle.ask;String haggleResult=be.act(p,haggle.id,2);
            if(be.requests.contains(haggle))check(haggle.negotiated&&haggle.ask<oldAsk&&be.act(p,haggle.id,3).contains("已经还价"),"haggle success invariant",passed);
            else check(haggleResult.contains("失败"),"haggle failure invariant",passed);
            CustomerRequest expired=CustomerRequest.create(npc.getUUID(),"sunlitmarket:test",pool,p.server.overworld().getGameTime()-1);
            be.requests.add(expired);before=Currency.balance(p);result=be.act(p,expired.id,0);
            check(result.contains("失效")&&!be.requests.contains(expired)&&Currency.balance(p)==before,"expired request refused",passed);
            check(be.getSlotsForFace(net.minecraft.core.Direction.DOWN).length==0,"hopper denied",passed);
            ItemStack stolen=new ItemStack(Items.DIAMOND,2);long day=p.server.overworld().getDayTime()/24000;
            CrimeService.mark(stolen,25,day,"sunlitmarket:test");CrimeService.cool(stolen,day+1);check(CrimeService.heat(stolen)==7,"heat decay",passed);
            CrimeService.cool(stolen,day+2);check(CrimeService.heat(stolen)==0&&!stolen.hasTag(),"heat clean",passed);
            p.getInventory().setItem(10,new ItemStack(Items.DIAMOND,2));p.getInventory().setItem(11,new ItemStack(Items.DIAMOND,1));
            CrimeService.mark(p.getInventory().getItem(10),50,day,"sunlitmarket:test");data.setWanted(p,25);
            CrimeService.inspect(p,npc,"sunlitmarket:test");
            check(p.getInventory().getItem(10).isEmpty()&&p.getInventory().getItem(11).getCount()==1,"confiscation provenance",passed);
            ItemStack counterStolen=new ItemStack(Items.DIAMOND,1);CrimeService.mark(counterStolen,50,day,"sunlitmarket:test");be.setItem(0,counterStolen);
            CoreConfig.FINE_PER_TIER.set(2);before=Currency.balance(p);CrimeService.inspect(p,npc,"sunlitmarket:test");
            check(be.getItem(0).isEmpty()&&Currency.balance(p)==before-2,"counter confiscation and fine",passed);
            return "PASS "+passed.size()+" integration assertions: "+String.join(", ",passed);
        }catch(Exception ex){SunlitMarketMod.LOG.error("[SM-CORE-TEST] FAIL after {}",passed,ex);return "FAIL after "+passed.size()+": "+ex;}
        finally{
            if(npc!=null)npc.discard();
            if(p.level().getBlockEntity(pos) instanceof CounterBlockEntity be){be.clearContent();be.requests.clear();}
            p.level().setBlock(pos,oldState,3);
            for(int i=0;i<inventory.size();i++)p.getInventory().setItem(i,inventory.get(i));p.getInventory().setChanged();
            AlignmentData.get(p.server).set(p,"sunlitmarket:test",originalAlignment);AlignmentData.get(p.server).setWanted(p,originalWanted);CoreConfig.FINE_PER_TIER.set(originalFine);
            Currency.setCoinValue("minecraft:emerald",emerald);
            MarketRegistry.removeScript("__core_test");MarketRegistry.removeScript(migrationId);MarketRules.removeScript("__core_npc");MarketRules.removeScript("__core_pool");
            SunlitMarketMod.LOG.info("[SM-CORE-TEST] completed assertions {}",passed);
        }
    }
    private static void check(boolean condition,String name,List<String> passed){if(!condition)throw new IllegalStateException(name);passed.add(name);}
}
