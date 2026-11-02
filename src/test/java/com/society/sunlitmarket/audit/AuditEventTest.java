package com.society.sunlitmarket.audit;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AuditEventTest {
    @Test void eventSerializesNullableFieldsWithoutInventingZero() {
        ObservedMoneyEvent e=new ObservedMoneyEvent(UUID.randomUUID(),"epoch",7,12,24000,
                "CURRENCY_SETTLEMENT","PAY","WALLET","CONFIRMED",-50L,-50L,UUID.randomUUID(),"v1",null);
        JsonObject json=e.json();
        assertEquals(7,json.get("sequence").getAsLong());
        assertEquals(-50,json.get("requestedDelta").getAsLong());
        assertEquals(-50,json.get("confirmedDelta").getAsLong());
        assertTrue(json.get("note").isJsonNull());
    }

    @Test void unknownOutcomeKeepsConfirmedDeltaNull() {
        ObservedMoneyEvent e=new ObservedMoneyEvent(UUID.randomUUID(),"epoch",1,1,1,
                "CURRENCY_SETTLEMENT","PAY","BANK","UNKNOWN",100L,null,null,"v1","bank result unknown");
        assertTrue(e.json().get("confirmedDelta").isJsonNull());
        assertEquals("UNKNOWN",e.json().get("outcome").getAsString());
    }
}
