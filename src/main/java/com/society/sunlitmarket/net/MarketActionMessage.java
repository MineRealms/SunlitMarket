package com.society.sunlitmarket.net;

import com.society.sunlitmarket.SunlitMarketMod;
import com.society.sunlitmarket.market.MarketService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record MarketActionMessage(String merchantId, int offerIndex, int action, int quantity) {
    public static void encode(MarketActionMessage msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.merchantId() == null ? "" : msg.merchantId(), 128);
        buf.writeVarInt(msg.offerIndex());
        buf.writeVarInt(msg.action());
        buf.writeVarInt(msg.quantity());
    }

    public static MarketActionMessage decode(FriendlyByteBuf buf) {
        return new MarketActionMessage(buf.readUtf(128), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(MarketActionMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                MarketService.handleAction(player, msg.merchantId(), msg.offerIndex(), msg.action(), msg.quantity());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
