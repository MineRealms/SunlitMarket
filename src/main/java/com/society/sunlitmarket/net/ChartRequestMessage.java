package com.society.sunlitmarket.net;

import com.society.sunlitmarket.SunlitMarketMod;
import com.society.sunlitmarket.market.ChartService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** C2S: "show me the candles for this index / period". Read-only, no session required. */
public record ChartRequestMessage(String id, int period) {
    public static void encode(ChartRequestMessage m, FriendlyByteBuf b) {
        b.writeUtf(m.id == null ? "" : m.id, 64);
        b.writeVarInt(m.period);
    }

    public static ChartRequestMessage decode(FriendlyByteBuf b) {
        return new ChartRequestMessage(b.readUtf(64), b.readVarInt());
    }

    public static void handle(ChartRequestMessage m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            String json = ChartService.json(player.server, m.id(), m.period());
            SunlitMarketMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ChartDataMessage(json));
        });
        ctx.get().setPacketHandled(true);
    }
}
