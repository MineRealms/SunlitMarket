package com.society.sunlitmarket.net;

import com.society.sunlitmarket.market.MarketService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record MarketActionMessage(String merchantId, int offerIndex, int action, int quantity,
                                  String session, long sequence, String offerId, int buy, int sell) {
    public static void encode(MarketActionMessage m, FriendlyByteBuf b) {
        b.writeUtf(m.merchantId,128); b.writeVarInt(m.offerIndex); b.writeVarInt(m.action); b.writeVarInt(m.quantity);
        b.writeUtf(m.session,64); b.writeVarLong(m.sequence); b.writeUtf(m.offerId,128); b.writeVarInt(m.buy); b.writeVarInt(m.sell);
    }
    public static MarketActionMessage decode(FriendlyByteBuf b) {
        return new MarketActionMessage(b.readUtf(128), b.readVarInt(), b.readVarInt(), b.readVarInt(),
                b.readUtf(64), b.readVarLong(), b.readUtf(128), b.readVarInt(), b.readVarInt());
    }
    public static void handle(MarketActionMessage m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var p = ctx.get().getSender();
            if (p != null) MarketService.networkAction(p,m.merchantId,m.offerIndex,m.action,m.quantity,
                    m.session,m.sequence,m.offerId,m.buy,m.sell);
        });
        ctx.get().setPacketHandled(true);
    }
}
