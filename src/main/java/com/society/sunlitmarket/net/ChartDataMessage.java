package com.society.sunlitmarket.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S2C: the serialised {@code ChartView}. Handed to the client through ClientPacketHandlers. */
public record ChartDataMessage(String json) {
    public static void encode(ChartDataMessage m, FriendlyByteBuf b) {
        b.writeUtf(m.json == null ? "{}" : m.json, 32767);
    }

    public static ChartDataMessage decode(FriendlyByteBuf b) {
        return new ChartDataMessage(b.readUtf(32767));
    }

    public static void handle(ChartDataMessage m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.society.sunlitmarket.client.ClientPacketHandlers.openChart(m.json()));
        ctx.get().setPacketHandled(true);
    }
}
