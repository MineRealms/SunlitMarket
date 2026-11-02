package com.society.sunlitmarket.deposit;

import com.society.sunlitmarket.compat.botania.BotaniaManaPort;
import com.society.sunlitmarket.compat.tc4.TcEssentiaPort;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.ModList;

/**
 * Optional-mod plumbing. Every reference to a Thaumcraft or Botania type sits inside a branch that
 * only runs when that mod is loaded, so the bin works on a pack with neither.
 */
public final class DepositCompat {
    private static Capability<?> essentiaCap;
    private static Capability<?> manaCap;
    private static boolean probed;

    private DepositCompat() {
    }

    /** Resolves the foreign capabilities once, at mod construction. */
    public static synchronized void probe() {
        if (probed) return;
        probed = true;
        if (ModList.get().isLoaded("thaumcraft")) {
            try {
                Class<?> holder = Class.forName("dev.tc4port.thaumcraft.api.essentia.EssentiaCapabilities");
                essentiaCap = (Capability<?>) holder.getField("TRANSPORT").get(null);
            } catch (ReflectiveOperationException | RuntimeException ex) {
                essentiaCap = null;
            }
        }
        if (ModList.get().isLoaded("botania")) {
            manaCap = BotaniaManaPort.capability();
        }
    }

    public static boolean matchesEssentia(Capability<?> cap) {
        return essentiaCap != null && essentiaCap == cap;
    }

    public static boolean matchesMana(Capability<?> cap) {
        return manaCap != null && manaCap == cap;
    }

    static EssentiaPort essentia(DepositBinBlockEntity bin) {
        if (!ModList.get().isLoaded("thaumcraft")) return EssentiaPort.NONE;
        try {
            return new TcEssentiaPort(bin);
        } catch (Throwable ex) {
            return EssentiaPort.NONE;
        }
    }

    static ManaPort mana(DepositBinBlockEntity bin) {
        if (!ModList.get().isLoaded("botania")) return ManaPort.NONE;
        try {
            return new BotaniaManaPort(bin);
        } catch (Throwable ex) {
            return ManaPort.NONE;
        }
    }

    /** The raw foreign capabilities, or null when the mod is absent. */
    public static Capability<?> essentiaCapabilityRaw() {
        return essentiaCap;
    }

    public static Capability<?> manaCapabilityRaw() {
        return manaCap;
    }

    static Object essentiaCapability(EssentiaPort port) {
        return port;
    }

    static Object manaCapability(ManaPort port) {
        return port instanceof BotaniaManaPort botania ? botania.proxy() : port;
    }
}
