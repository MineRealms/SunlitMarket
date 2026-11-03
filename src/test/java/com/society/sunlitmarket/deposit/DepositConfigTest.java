package com.society.sunlitmarket.deposit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** A persisted refusal (-1) must survive a reload; it is not the same as "no override". */
class DepositConfigTest {

    @Test void aRefusalSurvivesAReload() {
        JsonObject root = JsonParser.parseString("{\"items\":{\"minecraft:wheat\":-1,\"minecraft:iron_ingot\":30}}").getAsJsonObject();
        DepositConfig.apply(root);
        assertEquals(-1, DepositConfig.itemPrice("minecraft:wheat"));
        assertEquals(30, DepositConfig.itemPrice("minecraft:iron_ingot"));
        assertNull(DepositConfig.itemPrice("minecraft:dirt"));
    }
}
