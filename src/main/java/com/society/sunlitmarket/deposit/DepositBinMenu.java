package com.society.sunlitmarket.deposit;

import com.google.gson.JsonObject;
import com.society.sunlitmarket.SunlitMarketMod;
import com.society.sunlitmarket.net.DepositStateMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.PacketDistributor;

/**
 * Slot-less menu: the bin has no inventory to arrange, so this only carries state and the payout
 * action. Adding slots would have forced the screen to a fixed origin for no benefit.
 */
public final class DepositBinMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final Player player;
    private DepositBinBlockEntity bin;
    private int ticks;

    public DepositBinMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, pos, null);
    }

    public DepositBinMenu(int id, Inventory inventory, DepositBinBlockEntity bin) {
        this(id, inventory, bin.getBlockPos(), bin);
    }

    private DepositBinMenu(int id, Inventory inventory, BlockPos pos, DepositBinBlockEntity bin) {
        super(DepositRegistry.MENU.get(), id);
        this.pos = pos;
        this.player = inventory.player;
        if (bin == null) {
            BlockEntity be = inventory.player.level().getBlockEntity(pos);
            bin = be instanceof DepositBinBlockEntity found ? found : null;
        }
        this.bin = bin;
        if (this.bin != null) sync();
    }

    private DepositBinBlockEntity bin() {
        if (bin == null || bin.isRemoved()) {
            BlockEntity be = player.level().getBlockEntity(pos);
            bin = be instanceof DepositBinBlockEntity found ? found : null;
        }
        return bin;
    }

    @Override
    public boolean stillValid(Player p) {
        DepositBinBlockEntity found = bin();
        return found != null && found.stillValid(p);
    }

    /** Screen button 0 = collect. Avoids a dedicated C2S packet. */
    @Override
    public boolean clickMenuButton(Player p, int id) {
        if (id == 0 && p instanceof ServerPlayer serverPlayer) {
            collect(serverPlayer);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        return ItemStack.EMPTY;
    }

    /** Server-side action from the screen. */
    public void collect(ServerPlayer p) {
        if (p != player) return;
        DepositBinBlockEntity found = bin();
        if (found == null) return;
        found.collect(p);
        sync();
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (++ticks % 10 == 0) sync();
    }

    public void sync() {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        DepositBinBlockEntity found = bin();
        if (found == null) return;
        JsonObject state = found.state();
        SunlitMarketMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer),
                new DepositStateMessage(state.toString()));
    }
}
