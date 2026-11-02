package com.society.sunlitmarket.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record CounterStateMessage(int menu,String json){
    public static void encode(CounterStateMessage m,FriendlyByteBuf b){b.writeVarInt(m.menu);b.writeUtf(m.json,32767);}
    public static CounterStateMessage decode(FriendlyByteBuf b){return new CounterStateMessage(b.readVarInt(),b.readUtf(32767));}
    public static void handle(CounterStateMessage m,Supplier<NetworkEvent.Context> ctx){
        ctx.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->com.society.sunlitmarket.client.ClientPacketHandlers.updateCounter(m.menu,m.json)));
        ctx.get().setPacketHandled(true);
    }
}
