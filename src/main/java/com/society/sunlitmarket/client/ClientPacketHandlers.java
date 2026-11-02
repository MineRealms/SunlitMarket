package com.society.sunlitmarket.client;

import net.minecraft.client.Minecraft;

/** Client-only packet effects. This class must never be loaded by a dedicated server. */
public final class ClientPacketHandlers {
    private ClientPacketHandlers() {}

    public static void openMarket(String json) {
        MarketClient.open(json);
    }

    public static void updateMarket(String json, String error) {
        MarketClient.update(json, error);
    }

    public static void openAlignment(String json) {
        Minecraft.getInstance().setScreen(new AlignmentScreen(json));
    }

    public static void updateCounter(int menu, String json) {
        CounterScreen.update(menu, json);
    }
}
