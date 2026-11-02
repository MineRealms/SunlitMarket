package com.society.sunlitmarket.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record AlignmentMessage(String json){
    public static void encode(AlignmentMessage m,FriendlyByteBuf b){b.writeUtf(m.json,32767);}
    public static AlignmentMessage decode(FriendlyByteBuf b){return new AlignmentMessage(b.readUtf(32767));}
    public static void handle(AlignmentMessage m,Supplier<NetworkEvent.Context> ctx){ctx.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->net.minecraft.client.Minecraft.getInstance().setScreen(new com.society.sunlitmarket.client.AlignmentScreen(m.json))));ctx.get().setPacketHandled(true);}
}
