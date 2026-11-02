package com.society.sunlitmarket.deposit;

/** Bridge to Botania's mana network; same optional-loading rule as {@link EssentiaPort}. */
public interface ManaPort {
    ManaPort NONE = new ManaPort() {
        @Override public boolean available() { return false; }
        @Override public int stored() { return 0; }
        @Override public int capacity() { return 0; }
        @Override public void restore(int amount) { }
        @Override public void clear() { }
    };

    boolean available();

    int stored();

    int capacity();

    void restore(int amount);

    void clear();
}
