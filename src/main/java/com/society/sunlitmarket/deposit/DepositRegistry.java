package com.society.sunlitmarket.deposit;

import com.society.sunlitmarket.SunlitMarketMod;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class DepositRegistry {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, SunlitMarketMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SunlitMarketMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SunlitMarketMod.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, SunlitMarketMod.MOD_ID);

    public static final RegistryObject<Block> BLOCK = BLOCKS.register("deposit_bin", DepositBinBlock::new);
    public static final RegistryObject<Item> ITEM = ITEMS.register("deposit_bin",
            () -> new BlockItem(BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<DepositBinBlockEntity>> ENTITY = ENTITIES.register("deposit_bin",
            () -> BlockEntityType.Builder.of(DepositBinBlockEntity::new, BLOCK.get()).build(null));
    public static final RegistryObject<MenuType<DepositBinMenu>> MENU = MENUS.register("deposit_bin",
            () -> IForgeMenuType.create((id, inv, buf) -> new DepositBinMenu(id, inv, buf.readBlockPos())));

    private DepositRegistry() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        MENUS.register(bus);
        bus.addListener(DepositRegistry::onCommonSetup);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(DepositValues::refresh);
    }

}
