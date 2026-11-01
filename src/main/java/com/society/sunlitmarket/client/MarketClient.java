package com.society.sunlitmarket.client;

import com.society.sunlitmarket.market.MarketView;
import net.minecraft.client.Minecraft;

public final class MarketClient {
    private MarketClient() {
    }

    public static void init() {
    }

    public static void open(String json) {
        Minecraft.getInstance().setScreen(new MarketScreen(MarketView.fromJson(json), ""));
    }

    public static void update(String json, String error) {
        if (Minecraft.getInstance().screen instanceof MarketScreen screen) {
            screen.update(MarketView.fromJson(json), error);
        }
    }
}
