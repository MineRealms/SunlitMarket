package com.society.sunlitmarket.client;

import com.society.sunlitmarket.deposit.DepositRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Client-only wiring for the acceptance bin. */
public final class DepositClient {
    private DepositClient() {
    }

    public static void init() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(DepositClient::setup);
    }

    private static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(DepositRegistry.MENU.get(), DepositBinScreen::new));
    }

    /** Routes a state payload to whichever bin screen is open. */
    public static void acceptState(String json) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof DepositBinScreen screen) {
            screen.update(json);
        }
    }
}
