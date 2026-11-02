package com.society.sunlitmarket.core;

import com.society.sunlitmarket.currency.Currency;
import com.society.sunlitmarket.market.PriceModifiers;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReloadStagingTest {
    @Test void failedScriptsKeepPreviousPriceSnapshot() {
        PriceModifiers.clear();
        PriceModifiers.set("shop", "minecraft:stone", 2);
        PriceModifiers.beginScripts();
        PriceModifiers.set("shop", "minecraft:stone", 3);
        assertEquals(2, PriceModifiers.get("shop", "minecraft:stone"));
        PriceModifiers.endScripts(false);
        assertEquals(2, PriceModifiers.get("shop", "minecraft:stone"));
        PriceModifiers.beginScripts();
        PriceModifiers.set("shop", "minecraft:stone", 3);
        PriceModifiers.endScripts(true);
        assertEquals(3, PriceModifiers.get("shop", "minecraft:stone"));
        PriceModifiers.clear();
    }

    @Test void failedScriptsKeepPreviousCoinSnapshot() {
        try {
            Currency.resetValues();
            Currency.setCoinValue("minecraft:emerald", 2);
            Currency.beginScripts();
            Currency.setCoinValue("minecraft:emerald", 0);
            Currency.endScripts(false);
            assertEquals(2, Currency.coinValue("minecraft:emerald"));
            Currency.beginScripts();
            Currency.setCoinValue("minecraft:emerald", 0);
            Currency.endScripts(true);
            assertEquals(0, Currency.coinValue("minecraft:emerald"));
        } finally {
            Currency.resetValues();
        }
    }

    @Test void emeraldIsNotMoneyByDefault() {
        try {
            Currency.resetValues();
            assertEquals(0, Currency.coinValue("minecraft:emerald"));
            assertEquals(1, Currency.coinValue("numismatics:spur"));
        } finally {
            Currency.resetValues();
        }
    }
}
