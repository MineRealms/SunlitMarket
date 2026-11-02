package com.society.sunlitmarket.counter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.society.sunlitmarket.SunlitMarketMod;
import com.society.sunlitmarket.market.MarketRegistry;
import com.society.sunlitmarket.net.CounterStateMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import java.util.UUID;

public final class CounterMenu extends AbstractContainerMenu {
    public final BlockPos pos;
    private final CounterBlockEntity counter;
    private final Player player;
    private final UUID session=UUID.randomUUID();
    private final long version=MarketRegistry.version();
    private long sequence;
    private int ticks;
    private String message="";
    public CounterMenu(int id,Inventory inventory,BlockPos pos){this(id,inventory,null,new SimpleContainer(9),pos);}
    public CounterMenu(int id,Inventory inventory,CounterBlockEntity be){this(id,inventory,be,be,be.getBlockPos());}
    private CounterMenu(int id,Inventory inventory,CounterBlockEntity be,Container storage,BlockPos pos){
        super(CounterRegistry.MENU.get(),id);this.pos=pos;counter=be;player=inventory.player;
        for(int i=0;i<9;i++)addSlot(new Slot(storage,i,67+i*18,126));
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,col+row*9+9,67+col*18,150+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,67+col*18,208));
    }
    @Override public boolean stillValid(Player p){return counter==null||counter.stillValid(p)&&version==MarketRegistry.version();}
    @Override public ItemStack quickMoveStack(Player p,int index){
        if(index<0||index>=slots.size()||!stillValid(p))return ItemStack.EMPTY;
        Slot slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
        ItemStack stack=slot.getItem(),copy=stack.copy();
        if(!moveItemStackTo(stack,index<9?9:0,index<9?slots.size():9,index<9))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();return copy;
    }
    public void action(ServerPlayer p,String sessionId,long seq,String request,int action){
        if(p!=player||counter==null||!stillValid(p)||!session.toString().equals(sessionId)||seq!=sequence)return;
        sequence++;
        try{message=counter.act(p,UUID.fromString(request),action);}catch(IllegalArgumentException ex){message="无效请求";}
        sync();
    }
    @Override public void broadcastChanges(){super.broadcastChanges();if(counter!=null&&++ticks%20==0)sync();}
    public void sync(){
        if(!(player instanceof ServerPlayer p)||counter==null)return;
        JsonObject o=new JsonObject();o.addProperty("session",session.toString());o.addProperty("sequence",sequence);
        o.addProperty("owner",counter.owner()==null?"":counter.owner().key());o.addProperty("message",message);
        JsonArray rows=new JsonArray();
        for(CustomerRequest r:counter.requests){JsonObject x=new JsonObject();x.addProperty("id",r.id.toString());x.addProperty("item",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(r.goods.getItem()).toString());
            x.addProperty("count",r.goods.getCount());x.addProperty("faction",r.faction);x.addProperty("base",r.base);x.addProperty("market",r.market);x.addProperty("ask",r.ask);x.addProperty("negotiated",r.negotiated);x.addProperty("dir",r.buy?"buy":"sell");x.addProperty("seconds",Math.max(0,(r.expires-p.server.overworld().getGameTime())/20));rows.add(x);}
        o.add("requests",rows);SunlitMarketMod.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new CounterStateMessage(containerId,o.toString()));
    }
}
