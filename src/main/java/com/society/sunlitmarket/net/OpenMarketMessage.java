package com.society.sunlitmarket.net;

import com.society.sunlitmarket.SunlitMarketMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record OpenMarketMessage(String json) {
    public static void encode(OpenMarketMessage msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.json() == null ? "{}" : msg.json(), 32767);
    }

    public static OpenMarketMessage decode(FriendlyByteBuf buf) {
        return new OpenMarketMessage(buf.readUtf(32767));
    }

    public static void handle(OpenMarketMessage msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.society.sunlitmarket.client.ClientPacketHandlers.openMarket(msg.json()));
        ctx.get().setPacketHandled(true);
    }
}
