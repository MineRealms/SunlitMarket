package com.society.sunlitmarket.client;

import com.society.sunlitmarket.market.ChartView;
import com.society.sunlitmarket.market.MarketView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

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

    /**
     * Opens the chart, or refreshes it in place when one is already on screen (index/period tab
     * clicks answer with another payload). The market screen is kept as the parent so closing the
     * chart returns to the shop instead of dropping the player to the world.
     */
    public static void openChart(String json) {
        Minecraft minecraft = Minecraft.getInstance();
        ChartView view = ChartView.fromJson(json);
        if (minecraft.screen instanceof ChartScreen screen) {
            screen.update(view);
            return;
        }
        Screen parent = minecraft.screen;
        minecraft.setScreen(new ChartScreen(view, parent));
    }
}
