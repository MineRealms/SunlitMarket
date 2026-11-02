package com.society.sunlitmarket.deposit;

import com.google.gson.JsonObject;
import com.society.sunlitmarket.SunlitMarketMod;
import com.society.sunlitmarket.currency.Currency;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The acceptance bin: five intake ports (item, fluid, FE, essentia, mana) that all drain into one
 * payout. Everything here is server-authoritative; the screen only reads {@link #state()}.
 */
public final class DepositBinBlockEntity extends BlockEntity implements net.minecraft.world.MenuProvider {
    public static final int ITEM_SLOTS = 1;
    public static final int TANK_CAPACITY = 8_000;
    public static final int ENERGY_CAPACITY = 2_000_000;

    private final ItemStackHandler items = new ItemStackHandler(ITEM_SLOTS) {
        @Override protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final FluidTank tank = new FluidTank(TANK_CAPACITY) {
        @Override protected void onContentsChanged() {
            setChanged();
        }
    };

    private final EnergyStorage energy = new EnergyStorage(ENERGY_CAPACITY, ENERGY_CAPACITY, ENERGY_CAPACITY);

    private final EssentiaPort essentia;
    private final ManaPort mana;

    private int lastPayout;
    private String lastMessage = "";

    private LazyOptional<IItemHandler> itemsCap = LazyOptional.of(() -> items);
    private LazyOptional<IFluidHandler> fluidCap = LazyOptional.of(() -> tank);
    private LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> energy);

    public DepositBinBlockEntity(BlockPos pos, BlockState state) {
        super(DepositRegistry.ENTITY.get(), pos, state);
        this.essentia = DepositCompat.essentia(this);
        this.mana = DepositCompat.mana(this);
    }

    // ------------------------------------------------------------ capabilities

    private DepositCapabilities capabilities;

    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable net.minecraft.core.Direction side) {
        if (capabilities == null) capabilities = new DepositCapabilities(this);
        LazyOptional<T> found = capabilities.getCapability(cap, side);
        if (found.isPresent()) return found;
        return super.getCapability(cap, side);
    }

    // ------------------------------------------------------------ menu

    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.translatable("block.sunlitmarket.deposit_bin");
    }

    @Nullable
    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id,
            net.minecraft.world.entity.player.Inventory inventory, net.minecraft.world.entity.player.Player player) {
        return new DepositBinMenu(id, inventory, this);
    }

    public boolean stillValid(net.minecraft.world.entity.player.Player player) {
        if (level == null || level.getBlockEntity(worldPosition) != this) return false;
        return player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    public ItemStackHandler items() {
        return items;
    }

    public FluidTank tank() {
        return tank;
    }

    public IEnergyStorage energy() {
        return energy;
    }

    public EssentiaPort essentia() {
        return essentia;
    }

    public ManaPort mana() {
        return mana;
    }

    /** Value of everything currently stored, in spur. */
    public int value() {
        return DepositValues.itemValue(items.getStackInSlot(0))
                + DepositValues.fluidValue(tank.getFluid())
                + DepositValues.energyValue(energy.getEnergyStored())
                + DepositValues.essentiaValue(essentia.total())
                + DepositValues.manaValue(mana.stored());
    }

    /**
     * Pays out everything stored. The wallet is credited first: if the player cannot be paid, no
     * goods are destroyed.
     */
    public String collect(ServerPlayer player) {
        int total = value();
        if (total <= 0) return "没有可回收的内容";
        String error = Currency.settle(player, null, null, ItemStack.EMPTY, 0, total);
        if (error != null && !error.isEmpty()) return error;
        items.setStackInSlot(0, ItemStack.EMPTY);
        tank.setFluid(FluidStack.EMPTY);
        energy.extractEnergy(energy.getEnergyStored(), false);
        essentia.clear();
        mana.clear();
        lastPayout = total;
        lastMessage = "回收 +" + total + " spur";
        setChanged();
        return lastMessage;
    }

    /** Drops the held stack back to the player; used when the block is broken. */
    public void dropContents(net.minecraft.world.level.Level level, BlockPos pos) {
        ItemStack held = items.getStackInSlot(0);
        if (!held.isEmpty()) net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), held);
        items.setStackInSlot(0, ItemStack.EMPTY);
    }

    // ------------------------------------------------------------ state

    public JsonObject state() {
        JsonObject o = new JsonObject();
        ItemStack held = items.getStackInSlot(0);
        if (!held.isEmpty()) {
            o.addProperty("itemId", BuiltInRegistries.ITEM.getKey(held.getItem()).toString());
            o.addProperty("itemCount", held.getCount());
            o.addProperty("itemName", held.getHoverName().getString());
        }
        o.addProperty("itemValue", DepositValues.itemValue(held));

        FluidStack fluid = tank.getFluid();
        if (!fluid.isEmpty()) {
            o.addProperty("fluidId", BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString());
            o.addProperty("fluidAmount", fluid.getAmount());
            o.addProperty("fluidName", fluid.getDisplayName().getString());
        }
        o.addProperty("fluidCapacity", tank.getCapacity());
        o.addProperty("fluidValue", DepositValues.fluidValue(fluid));

        o.addProperty("energy", energy.getEnergyStored());
        o.addProperty("energyCapacity", energy.getMaxEnergyStored());
        o.addProperty("energyValue", DepositValues.energyValue(energy.getEnergyStored()));

        o.addProperty("essentia", essentia.total());
        o.addProperty("essentiaValue", DepositValues.essentiaValue(essentia.total()));
        o.addProperty("essentiaReady", essentia.available());
        JsonObject aspects = new JsonObject();
        for (Map.Entry<String, Integer> entry : essentia.view().entrySet()) {
            aspects.addProperty(entry.getKey(), entry.getValue());
        }
        o.add("aspects", aspects);

        o.addProperty("mana", mana.stored());
        o.addProperty("manaCapacity", mana.capacity());
        o.addProperty("manaValue", DepositValues.manaValue(mana.stored()));
        o.addProperty("manaReady", mana.available());

        o.addProperty("total", value());
        o.addProperty("lastPayout", lastPayout);
        o.addProperty("message", lastMessage);
        return o;
    }

    // ------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", items.serializeNBT());
        tag.put("Tank", tank.writeToNBT(new CompoundTag()));
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putInt("Mana", mana.stored());
        tag.putInt("LastPayout", lastPayout);
        tag.putString("LastMessage", lastMessage);
        ListTag list = new ListTag();
        for (Map.Entry<String, Integer> entry : essentia.view().entrySet()) {
            CompoundTag aspect = new CompoundTag();
            aspect.putString("Aspect", entry.getKey());
            aspect.putInt("Amount", entry.getValue());
            list.add(aspect);
        }
        tag.put("Essentia", list);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.deserializeNBT(tag.getCompound("Items"));
        tank.readFromNBT(tag.getCompound("Tank"));
        energy.receiveEnergy(tag.getInt("Energy"), false);
        mana.restore(tag.getInt("Mana"));
        lastPayout = tag.getInt("LastPayout");
        lastMessage = tag.getString("LastMessage");
        Map<String, Integer> aspects = new LinkedHashMap<>();
        ListTag list = tag.getList("Essentia", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag aspect = list.getCompound(i);
            aspects.put(aspect.getString("Aspect"), aspect.getInt("Amount"));
        }
        essentia.restore(aspects);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemsCap.invalidate();
        fluidCap.invalidate();
        energyCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        itemsCap = LazyOptional.of(() -> items);
        fluidCap = LazyOptional.of(() -> tank);
        energyCap = LazyOptional.of(() -> energy);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    public String lastMessage() {
        return lastMessage;
    }
}
