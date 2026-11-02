package com.society.sunlitmarket.deposit;

import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Attaches all five intake capabilities to the bin. Thaumcraft and Botania are matched by
 * capability identity, so a pack without them simply never asks.
 */
public final class DepositCapabilities implements ICapabilityProvider {
    private final LazyOptional<IItemHandler> items;
    private final LazyOptional<IFluidHandler> fluids;
    private final LazyOptional<IEnergyStorage> energy;
    private final LazyOptional<Object> essentia;
    private final LazyOptional<Object> mana;

    public DepositCapabilities(DepositBinBlockEntity bin) {
        this.items = LazyOptional.of(bin::items);
        this.fluids = LazyOptional.of(bin::tank);
        this.energy = LazyOptional.of(bin::energy);
        this.essentia = LazyOptional.of(() -> DepositCompat.essentiaCapability(bin.essentia()));
        this.mana = LazyOptional.of(() -> DepositCompat.manaCapability(bin.mana()));
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return items.cast();
        if (cap == ForgeCapabilities.FLUID_HANDLER) return fluids.cast();
        if (cap == ForgeCapabilities.ENERGY) return energy.cast();
        if (DepositCompat.matchesEssentia(cap)) return essentia.cast();
        if (DepositCompat.matchesMana(cap)) return mana.cast();
        return LazyOptional.empty();
    }

    public void invalidate() {
        items.invalidate();
        fluids.invalidate();
        energy.invalidate();
        essentia.invalidate();
        mana.invalidate();
    }
}
