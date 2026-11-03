package com.society.sunlitmarket.counter;

import com.society.sunlitmarket.alignment.AlignmentData;
import com.society.sunlitmarket.compat.FtbTeamsCompat;
import com.society.sunlitmarket.compat.OwnerKey;
import com.society.sunlitmarket.config.CoreConfig;
import com.society.sunlitmarket.core.MarketRules;
import com.society.sunlitmarket.core.TradeMath;
import com.society.sunlitmarket.currency.Currency;
import com.society.sunlitmarket.crime.CrimeService;
import com.society.sunlitmarket.treasury.Treasury;
import com.society.sunlitmarket.treasury.TreasuryData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import java.util.*;

public final class CounterBlockEntity extends BlockEntity implements net.minecraft.world.WorldlyContainer, MenuProvider {
    @Override public int[] getSlotsForFace(net.minecraft.core.Direction side) { return new int[0]; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, net.minecraft.core.Direction side) { return false; }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, net.minecraft.core.Direction side) { return false; }
    private final NonNullList<ItemStack> items=NonNullList.withSize(9,ItemStack.EMPTY);
    public final List<CustomerRequest> requests=new ArrayList<>();
    private UUID counterId=UUID.randomUUID();
    private OwnerKey owner;
    private long nextScan;
    public CounterBlockEntity(BlockPos p,BlockState s) { super(CounterRegistry.ENTITY.get(),p,s); }
    public UUID id() { return counterId; }
    public OwnerKey owner() { return owner; }
    public void assign(Player p) { owner=FtbTeamsCompat.owner(p);counterId=UUID.randomUUID();requests.clear();setChanged(); }
    public boolean access(Player p) { return owner!=null && FtbTeamsCompat.canAccess(p,owner); }
    public void transfer(ServerPlayer p) { assign(p); }
    public static void tick(Level level,BlockPos pos,BlockState state,CounterBlockEntity be) {
        if(!(level instanceof ServerLevel sl)||!CoreConfig.COUNTER_ENABLED.get()||be.owner==null)return;
        long now=sl.getServer().overworld().getGameTime();
        if(now<be.nextScan)return;be.nextScan=now+CoreConfig.REQUEST_INTERVAL.get();
        if(be.requests.removeIf(r->!be.valid(r,now)))be.setChanged();
        if(be.requests.size()>=CoreConfig.MAX_REQUESTS.get())return;
        for(LivingEntity npc:sl.getEntitiesOfClass(LivingEntity.class,new AABB(pos).inflate(CoreConfig.COUNTER_RANGE.get()),e->!(e instanceof Player)&&e.isAlive())) {
            if(be.requests.stream().anyMatch(r->r.npc.equals(npc.getUUID())))continue;
            var identity=MarketRules.identity(npc);if(!identity.customer())continue;
            var pools=MarketRules.pools(identity.faction());if(pools.isEmpty())continue;
            var pool=pools.get(sl.random.nextInt(pools.size()));
            boolean buy=pool.buys()&&(!pool.sells()||sl.random.nextBoolean());
            // A buying NPC only shows up while today's NPC budget could still pay its bid.
            if(buy&&Treasury.enabled()&&Treasury.data(sl.getServer()).npcRemaining(Treasury.policy())<pool.bidPrice())continue;
            CustomerRequest request;
            try { request=CustomerRequest.create(npc.getUUID(),identity.faction(),pool,now+CoreConfig.REQUEST_LIFETIME.get(),buy); }
            catch(IllegalArgumentException ex) { continue; }
            if(request.goods.isEmpty())continue;
            var hook=com.society.sunlitmarket.core.MarketHooks.fire(new com.society.sunlitmarket.core.MarketHooks.Event(
                    "beforeRequest",null,npc,identity.faction(),request.id.toString(),request.goods,request.ask));
            if(hook.cancelled||hook.price<1||hook.price>10000000)continue;
            if(!CustomerLedger.get(sl.getServer()).reserve(npc.getUUID(),now,com.society.sunlitmarket.core.EconomyClock.day(sl.getServer())))continue;
            request.ask=hook.price;
            be.requests.add(request);be.setChanged();
            com.society.sunlitmarket.core.MarketHooks.fire(new com.society.sunlitmarket.core.MarketHooks.Event(
                    "afterRequest",null,npc,identity.faction(),request.id.toString(),request.goods,request.ask));
            if(be.requests.size()>=CoreConfig.MAX_REQUESTS.get())break;
        }
    }
    private boolean valid(CustomerRequest r,long now) {
        if(!(level instanceof ServerLevel sl)||now>=r.expires||!MarketRules.poolExists(r.pool,r.faction))return false;
        var e=sl.getEntity(r.npc);if(e==null||!e.isAlive()||e.distanceToSqr(worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5)>Math.pow(CoreConfig.COUNTER_RANGE.get(),2))return false;
        var identity=MarketRules.identity(e);return identity.customer()&&identity.faction().equals(r.faction);
    }
    public String act(ServerPlayer p,UUID requestId,int action) {
        if(com.society.sunlitmarket.core.MarketHooks.dispatching())return "禁止递归交易";
        if(!access(p)||!stillValid(p))return "无权操作柜台";
        CustomerRequest r=requests.stream().filter(q->q.id.equals(requestId)).findFirst().orElse(null);
        if(r==null)return "请求已处理";
        if(!valid(r,p.server.overworld().getGameTime())) {requests.remove(r);setChanged();return "顾客已离开或请求失效";}
        int alignment=AlignmentData.get(p.server).get(p,r.faction);
        if(alignment<CoreConfig.REFUSE_BELOW.get())return "该派系拒绝交易";
        if(action==1) {requests.remove(r);setChanged();return "已拒绝";}
        if(r.buy)return sellToNpc(p,r,action,alignment);
        if(action==2||action==3) {
            if(r.negotiated)return "本请求已经还价";
            r.negotiated=true;int percent=action==2?90:80;
            int proposal=Math.max(1,(int)((long)r.ask*percent/100));
            double chance=Math.max(.1,Math.min(.9,CoreConfig.HAGGLE_CHANCE.get()+alignment/1000D-(r.ask-r.market)/(double)r.market));
            if(level.random.nextDouble()<chance) {r.ask=proposal;setChanged();return "还价成功，请确认收购";}
            requests.remove(r);AlignmentData.get(p.server).add(p,r.faction,-CoreConfig.HAGGLE_FAILURE_LOSS.get());setChanged();return "还价失败，顾客撤回请求";
        }
        if(action!=0)return "无效操作";
        ItemStack goods=r.goods.copy();
        if(r.heat>0)CrimeService.mark(goods,r.heat,p.server.overworld().getDayTime()/24000L,r.faction);
        var npc=((ServerLevel)level).getEntity(r.npc);
        var hook=com.society.sunlitmarket.core.MarketHooks.fire(new com.society.sunlitmarket.core.MarketHooks.Event(
                "beforeTrade",p,npc,r.faction,r.id.toString(),goods,r.ask));
        if(hook.cancelled)return "收购被脚本取消";
        if(hook.price<1||hook.price>10000000)return "脚本报价无效";
        if(hook.price!=r.ask){r.ask=hook.price;setChanged();return "报价已更新，请重新确认";}
        String result=Currency.settle(p,null,this,goods,goods.getCount(),-r.ask,"COUNTER_PURCHASE");
        if(!result.isEmpty())return result;
        // Money paid to an NPC leaves player circulation and returns to the issuance reserve.
        Treasury.receive(p.server,TreasuryData.Account.RESERVE,r.ask,"COUNTER_PURCHASE");
        requests.remove(r);setChanged();
        if(r.heat>0) {AlignmentData.get(p.server).addWanted(p,Math.max(0,r.wanted));AlignmentData.get(p.server).add(p,r.faction,-CoreConfig.STOLEN_ALIGNMENT_LOSS.get());}
        else if((long)r.ask*100>= (long)r.market*90) AlignmentData.get(p.server).add(p,r.faction,
                TradeMath.clamp((long)Math.max(0,r.reward)*Math.max(1,TradeMath.amount(((long)Math.min(r.ask,r.market)+CoreConfig.FAIR_REWARD_DIVISOR.get()-1)/CoreConfig.FAIR_REWARD_DIVISOR.get())),0,100));
        else if((long)r.ask*100<(long)r.market*60)AlignmentData.get(p.server).add(p,r.faction,-Math.max(1,(r.market-r.ask)/100));
        int tier=MarketRules.contraband(goods,r.faction,p.level().dimension().location().toString());
        if(tier>0)AlignmentData.get(p.server).addWanted(p,tier*CoreConfig.CONTRABAND_WANTED.get());
        com.society.sunlitmarket.core.MarketHooks.fire(new com.society.sunlitmarket.core.MarketHooks.Event(
                "afterTrade",p,npc,r.faction,r.id.toString(),goods,r.ask));
        return "收购完成，货物已入柜台";
    }
    /**
     * An NPC buying from the counter: 0 sells, 2/3 asks 10%/20% more. Payment comes from the NPC
     * daily budget in the issuance reserve; the goods leave the counter and are consumed.
     */
    private String sellToNpc(ServerPlayer p,CustomerRequest r,int action,int alignment) {
        if(action==2||action==3) {
            if(r.negotiated)return "本请求已经议价";
            r.negotiated=true;int percent=action==2?110:120;
            int proposal=(int)Math.min(10000000L,Math.max(r.ask+1L,(long)r.ask*percent/100));
            double chance=Math.max(.1,Math.min(.9,CoreConfig.HAGGLE_CHANCE.get()+alignment/1000D-(proposal-r.market)/(double)r.market));
            if(level.random.nextDouble()<chance) {r.ask=proposal;setChanged();return "顾客接受加价，请确认出售";}
            requests.remove(r);AlignmentData.get(p.server).add(p,r.faction,-CoreConfig.HAGGLE_FAILURE_LOSS.get());setChanged();return "加价失败，顾客离开";
        }
        if(action!=0)return "无效操作";
        ItemStack goods=r.goods.copy();
        var npc=((ServerLevel)level).getEntity(r.npc);
        var hook=com.society.sunlitmarket.core.MarketHooks.fire(new com.society.sunlitmarket.core.MarketHooks.Event(
                "beforeTrade",p,npc,r.faction,r.id.toString(),goods,r.ask));
        if(hook.cancelled)return "出售被脚本取消";
        if(hook.price<1||hook.price>10000000)return "脚本报价无效";
        if(hook.price!=r.ask){r.ask=hook.price;setChanged();return "报价已更新，请重新确认";}
        int price=r.ask;
        String result=Treasury.payout(p,TreasuryData.Account.RESERVE,true,price,"NPC_DEMAND",
                ()->Currency.settle(p,this,null,goods,goods.getCount(),price,"NPC_DEMAND"));
        if(!result.isEmpty())return result;
        requests.remove(r);setChanged();
        if((long)price*100<=(long)r.market*110) AlignmentData.get(p.server).add(p,r.faction,
                TradeMath.clamp((long)Math.max(0,r.reward)*Math.max(1,TradeMath.amount(((long)Math.min(price,r.market)+CoreConfig.FAIR_REWARD_DIVISOR.get()-1)/CoreConfig.FAIR_REWARD_DIVISOR.get())),0,100));
        else if((long)price*100>(long)r.market*140)AlignmentData.get(p.server).add(p,r.faction,-Math.max(1,(price-r.market)/100));
        com.society.sunlitmarket.core.MarketHooks.fire(new com.society.sunlitmarket.core.MarketHooks.Event(
                "afterTrade",p,npc,r.faction,r.id.toString(),goods,price));
        return "出售完成，顾客带走了货物";
    }
    @Override public Component getDisplayName(){return Component.translatable("block.sunlitmarket.counter");}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player p){return access(p)?new CounterMenu(id,inventory,this):null;}
    @Override public int getContainerSize(){return items.size();}
    @Override public boolean isEmpty(){return items.stream().allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getItem(int i){return items.get(i);}
    @Override public ItemStack removeItem(int i,int n){ItemStack s=ContainerHelper.removeItem(items,i,n);if(!s.isEmpty())setChanged();return s;}
    @Override public ItemStack removeItemNoUpdate(int i){return ContainerHelper.takeItem(items,i);}
    @Override public void setItem(int i,ItemStack s){items.set(i,s);setChanged();}
    @Override public boolean stillValid(Player p){return level!=null&&level.getBlockEntity(worldPosition)==this&&p.distanceToSqr(worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5)<=64&&access(p);}
    @Override public void clearContent(){items.clear();setChanged();}
    @Override protected void saveAdditional(CompoundTag t){super.saveAdditional(t);ContainerHelper.saveAllItems(t,items);t.putUUID("counterId",counterId);if(owner!=null)t.putString("owner",owner.key());ListTag list=new ListTag();requests.forEach(r->list.add(r.save()));t.put("requests",list);}
    @Override public void load(CompoundTag t){super.load(t);ContainerHelper.loadAllItems(t,items);if(t.hasUUID("counterId"))counterId=t.getUUID("counterId");try{owner=OwnerKey.parse(t.getString("owner"));}catch(IllegalArgumentException ex){owner=null;}requests.clear();for(Tag tag:t.getList("requests",Tag.TAG_COMPOUND))try{requests.add(CustomerRequest.load((CompoundTag)tag));}catch(IllegalArgumentException ignored){}}
}
