package com.society.sunlitmarket.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record MarketUpdateMessage(String json, String error) {
    public static void encode(MarketUpdateMessage msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.json() == null ? "{}" : msg.json(), 32767);
        buf.writeUtf(msg.error() == null ? "" : msg.error(), 1024);
    }

    public static MarketUpdateMessage decode(FriendlyByteBuf buf) {
        return new MarketUpdateMessage(buf.readUtf(32767), buf.readUtf(1024));
    }

    public static void handle(MarketUpdateMessage msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.society.sunlitmarket.client.ClientPacketHandlers.updateMarket(msg.json(), msg.error()));
        ctx.get().setPacketHandled(true);
    }
}
