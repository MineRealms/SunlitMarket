package com.society.sunlitmarket.net;

import com.society.sunlitmarket.counter.CounterMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record CounterActionMessage(int menu,String session,long sequence,String request,int action){
    public static void encode(CounterActionMessage m,FriendlyByteBuf b){b.writeVarInt(m.menu);b.writeUtf(m.session,64);b.writeVarLong(m.sequence);b.writeUtf(m.request,64);b.writeVarInt(m.action);}
    public static CounterActionMessage decode(FriendlyByteBuf b){return new CounterActionMessage(b.readVarInt(),b.readUtf(64),b.readVarLong(),b.readUtf(64),b.readVarInt());}
    public static void handle(CounterActionMessage m,Supplier<NetworkEvent.Context> ctx){ctx.get().enqueueWork(()->{
        var p=ctx.get().getSender();if(p!=null&&p.containerMenu instanceof CounterMenu menu&&menu.containerId==m.menu)menu.action(p,m.session,m.sequence,m.request,m.action);
    });ctx.get().setPacketHandled(true);}
}
