package com.society.sunlitmarket.deposit;

import java.util.Map;

/**
 * Bridge to Thaumcraft's essentia network. The concrete implementation lives in
 * {@code compat/tc4} and is only touched when Thaumcraft is loaded, so the mod still starts on a
 * pack without it.
 */
public interface EssentiaPort {
    EssentiaPort NONE = new EssentiaPort() {
        @Override public boolean available() { return false; }
        @Override public int total() { return 0; }
        @Override public Map<String, Integer> view() { return Map.of(); }
        @Override public void restore(Map<String, Integer> saved) { }
        @Override public void clear() { }
    };

    boolean available();

    int total();

    /** aspect id -> amount, for the screen. */
    Map<String, Integer> view();

    void restore(Map<String, Integer> saved);

    void clear();
}
