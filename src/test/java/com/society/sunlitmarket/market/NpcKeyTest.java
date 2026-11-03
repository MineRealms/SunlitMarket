package com.society.sunlitmarket.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The pack names its NPCs by translation key; that key is the only locale-stable identity. */
class NpcKeyTest {

    @Test
    void readsTheKeyOutOfATranslatedName() {
        assertEquals("banker", MarketRegistry.npcKeyFromJson("{\"translate\":\"dialog.npc.banker.name\"}"));
    }

    @Test
    void readsTheKeyOutOfAPlainTextName() {
        assertEquals("wise_oak", MarketRegistry.npcKeyFromJson("{\"text\":\"dialog.npc.wise_oak.name\"}"));
    }

    @Test
    void ignoresNamesWithoutAKey() {
        assertEquals("", MarketRegistry.npcKeyFromJson("{\"text\":\"Bob\"}"));
        assertEquals("", MarketRegistry.npcKeyFromJson(""));
        assertEquals("", MarketRegistry.npcKeyFromJson(null));
    }

    @Test
    void doesNotMatchAPartialKey() {
        assertEquals("", MarketRegistry.npcKeyFromJson("{\"text\":\"dialog.npc..name\"}"));
    }
}
