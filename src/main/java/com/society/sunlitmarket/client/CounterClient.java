package com.society.sunlitmarket.client;

import com.society.sunlitmarket.counter.CounterRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public final class CounterClient{
    private CounterClient(){}
    public static void init(){FMLJavaModLoadingContext.get().getModEventBus().addListener(CounterClient::setup);}
    private static void setup(FMLClientSetupEvent e){e.enqueueWork(()->MenuScreens.register(CounterRegistry.MENU.get(),CounterScreen::new));}
}
