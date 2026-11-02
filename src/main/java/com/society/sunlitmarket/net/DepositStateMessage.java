package com.society.sunlitmarket.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S2C: serialised state of the open acceptance bin. */
public record DepositStateMessage(String json) {
    public static void encode(DepositStateMessage m, FriendlyByteBuf b) {
        b.writeUtf(m.json == null ? "{}" : m.json, 32767);
    }

    public static DepositStateMessage decode(FriendlyByteBuf b) {
        return new DepositStateMessage(b.readUtf(32767));
    }

    public static void handle(DepositStateMessage m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.society.sunlitmarket.client.ClientPacketHandlers.depositState(m.json()));
        ctx.get().setPacketHandled(true);
    }
}
