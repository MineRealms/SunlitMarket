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

    @Test void scriptRefusalsAreStagedAndNotPersisted() {
        DepositConfig.apply(JsonParser.parseString("{\"items\":{\"minecraft:wheat\":-1,\"minecraft:dirt\":-1,\"minecraft:stone\":2}}").getAsJsonObject());
        DepositConfig.beginScripts();
        DepositConfig.refuse("minecraft:wheat");
        DepositConfig.refuse("minecraft:stone");
        assertNull(DepositConfig.itemPrice("minecraft:carrot"), "nothing applies before the load commits");
        assertEquals(2, DepositConfig.itemPrice("minecraft:stone"));
        DepositConfig.endScripts(true);
        assertEquals(-1, DepositConfig.itemPrice("minecraft:stone"), "a script refusal wins over a configured price");
        assertEquals(-1, DepositConfig.itemPrice("minecraft:wheat"));
        assertEquals(-1, DepositConfig.itemPrice("minecraft:dirt"), "an administrator's -1 stays");
        JsonObject saved = DepositConfig.toJsonForTest();
        JsonObject items = saved.getAsJsonObject("items");
        assertNull(items.get("minecraft:wheat"), "the redundant persisted refusal is dropped from the file");
        assertEquals(-1, items.get("minecraft:dirt").getAsInt());
        assertEquals(2, items.get("minecraft:stone").getAsInt());

        DepositConfig.beginScripts();
        DepositConfig.endScripts(true);
        assertNull(DepositConfig.itemPrice("minecraft:wheat"), "an item the script stops refusing is accepted again");

        DepositConfig.beginScripts();
        DepositConfig.refuse("minecraft:carrot");
        DepositConfig.endScripts(false);
        assertNull(DepositConfig.itemPrice("minecraft:carrot"), "a failed load changes nothing");
    }
}
