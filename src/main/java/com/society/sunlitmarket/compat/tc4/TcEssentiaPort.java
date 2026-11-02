package com.society.sunlitmarket.compat.tc4;

import com.society.sunlitmarket.deposit.DepositBinBlockEntity;
import com.society.sunlitmarket.deposit.EssentiaPort;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransport;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Accepts essentia pushed in by Thaumcraft tubes. This class is only instantiated behind a
 * {@code ModList.isLoaded("thaumcraft")} check, so a pack without Thaumcraft never loads it.
 */
public final class TcEssentiaPort implements EssentiaTransport, EssentiaPort {
    public static final int CAPACITY = 128;

    private final LinkedHashMap<AspectId, Integer> stored = new LinkedHashMap<>();
    private final DepositBinBlockEntity bin;

    public TcEssentiaPort(DepositBinBlockEntity bin) {
        this.bin = bin;
    }

    // ------------------------------------------------------------ EssentiaPort

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public int total() {
        int total = 0;
        for (int amount : stored.values()) total += amount;
        return total;
    }

    @Override
    public Map<String, Integer> view() {
        Map<String, Integer> out = new LinkedHashMap<>();
        stored.forEach((aspect, amount) -> {
            if (amount > 0) out.put(aspect.toString(), amount);
        });
        return out;
    }

    @Override
    public void restore(Map<String, Integer> saved) {
        stored.clear();
        if (saved == null) return;
        saved.forEach((id, amount) -> {
            ResourceLocation key = ResourceLocation.tryParse(id);
            if (key != null && amount != null && amount > 0) stored.put(AspectId.of(key), amount);
        });
    }

    @Override
    public void clear() {
        stored.clear();
    }

    // ------------------------------------------------------------ EssentiaTransport

    @Override
    public boolean isConnectable(Direction side) {
        return true;
    }

    @Override
    public boolean canInputFrom(Direction side) {
        return true;
    }

    @Override
    public boolean canOutputTo(Direction side) {
        return false;
    }

    @Override
    public AspectId suctionType(Direction side) {
        return null;
    }

    @Override
    public int suctionAmount(Direction side) {
        return 0;
    }

    @Override
    public int takeEssentia(AspectId aspect, int amount, Direction side, EssentiaTransferMode mode) {
        return 0;
    }

    @Override
    public int addEssentia(AspectId aspect, int amount, Direction side, EssentiaTransferMode mode) {
        if (aspect == null || amount <= 0) return 0;
        int free = CAPACITY - total();
        int accepted = Math.max(0, Math.min(amount, free));
        if (accepted > 0 && mode.executes()) {
            stored.merge(aspect, accepted, Integer::sum);
            bin.setChanged();
        }
        return accepted;
    }

    @Override
    public AspectId essentiaType(Direction side) {
        return null;
    }

    @Override
    public int essentiaAmount(Direction side) {
        return total();
    }

    @Override
    public int minimumSuction() {
        return 0;
    }

    @Override
    public boolean renderExtendedTube() {
        return false;
    }
}
