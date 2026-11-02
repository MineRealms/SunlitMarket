package com.society.sunlitmarket.core;

import com.google.gson.JsonParser;
import com.society.sunlitmarket.market.Offer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OfferTest {
    private Offer parse(String json){return Offer.fromJson(JsonParser.parseString(json).getAsJsonObject());}
    @Test void missingThresholdIsDistinctFromExplicitZero(){assertFalse(parse("{\"item\":\"minecraft:stone\",\"buy\":1}").hasReputation);assertTrue(parse("{\"item\":\"minecraft:stone\",\"buy\":1,\"reputation\":0}").hasReputation);}
    @Test void signedThresholdIsPreserved(){assertEquals(-50,parse("{\"item\":\"minecraft:stone\",\"buy\":1,\"reputation\":-50}").reputation);}
    @Test void oldSchemaDefaultsToStock(){Offer o=parse("{\"item\":\"minecraft:stone\",\"sell\":4}");assertEquals("stock",o.kind);assertEquals(0,o.buy);assertEquals(-1,o.stock);assertTrue(o.valid());}
    @Test void explicitIdentityIsRetained(){assertEquals("iron",parse("{\"id\":\"iron\",\"item\":\"minecraft:iron_ingot\",\"buy\":4}").id);}
    @Test void legacyIndexIsExplicitAndOptional(){assertEquals(-1,parse("{\"id\":\"iron\",\"item\":\"minecraft:iron_ingot\",\"buy\":4}").legacyIndex);assertEquals(2,parse("{\"id\":\"iron\",\"legacyIndex\":2,\"item\":\"minecraft:iron_ingot\",\"buy\":4}").legacyIndex);}
}
