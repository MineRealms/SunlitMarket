package com.society.sunlitmarket.compat.botania;

import com.society.sunlitmarket.deposit.DepositBinBlockEntity;
import com.society.sunlitmarket.deposit.ManaPort;
import net.minecraftforge.common.capabilities.Capability;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Accepts Botania mana without a compile-time dependency on Botania: the {@code ManaReceiver}
 * interface is implemented with a dynamic proxy, and the capability is looked up by reflection.
 * Only constructed behind a {@code ModList.isLoaded("botania")} check.
 */
public final class BotaniaManaPort implements ManaPort, InvocationHandler {
    public static final int CAPACITY = 1_000_000;

    private static Capability<?> capability;
    private static boolean probed;

    private final DepositBinBlockEntity bin;
    private final Object proxy;
    private int mana;

    public BotaniaManaPort(DepositBinBlockEntity bin) {
        this.bin = bin;
        this.proxy = Proxy.newProxyInstance(BotaniaManaPort.class.getClassLoader(),
                new Class<?>[]{receiverInterface()}, this);
    }

    private static Class<?> receiverInterface() {
        try {
            return Class.forName("vazkii.botania.api.mana.ManaReceiver");
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("Botania is loaded but ManaReceiver is missing", ex);
        }
    }

    /** The Forge capability, or null when Botania is absent. */
    public static Capability<?> capability() {
        if (!probed) {
            probed = true;
            try {
                Class<?> holder = Class.forName("vazkii.botania.api.BotaniaForgeCapabilities");
                capability = (Capability<?>) holder.getField("MANA_RECEIVER").get(null);
            } catch (ReflectiveOperationException | RuntimeException ex) {
                capability = null;
            }
        }
        return capability;
    }

    public Object proxy() {
        return proxy;
    }

    @Override
    public Object invoke(Object self, Method method, Object[] args) {
        switch (method.getName()) {
            case "getManaReceiverLevel":
                return bin.getLevel();
            case "getManaReceiverPos":
                return bin.getBlockPos();
            case "getCurrentMana":
                return mana;
            case "isFull":
                return mana >= CAPACITY;
            case "receiveMana":
                if (args != null && args.length > 0 && args[0] instanceof Integer amount) {
                    int accepted = Math.max(0, Math.min(amount, CAPACITY - mana));
                    if (accepted > 0) {
                        mana += accepted;
                        bin.setChanged();
                    }
                }
                return null;
            case "canReceiveManaFromBursts":
                return true;
            case "hashCode":
                return System.identityHashCode(this);
            case "equals":
                return self == (args == null ? null : args[0]);
            case "toString":
                return "SunlitMarketManaReceiver";
            default:
                return null;
        }
    }

    // ------------------------------------------------------------ ManaPort

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public int stored() {
        return mana;
    }

    @Override
    public int capacity() {
        return CAPACITY;
    }

    @Override
    public void restore(int amount) {
        mana = Math.max(0, Math.min(CAPACITY, amount));
    }

    @Override
    public void clear() {
        mana = 0;
    }
}
