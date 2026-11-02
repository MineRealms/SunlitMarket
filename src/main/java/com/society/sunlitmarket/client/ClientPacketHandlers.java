package com.society.sunlitmarket.client;

import net.minecraft.client.Minecraft;

/**
 * Client-only packet effects. Every S2C handler funnels through this class so a dedicated server
 * never resolves {@code net.minecraft.client.*} from common code — that used to crash the server
 * with {@code NoClassDefFoundError: net/minecraft/client/gui/screens/Screen}.
 */
public final class ClientPacketHandlers {
    private ClientPacketHandlers() {
    }

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

    public static void openChart(String json) {
        MarketClient.openChart(json);
    }
}
