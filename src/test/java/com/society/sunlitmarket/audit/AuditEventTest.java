package com.society.sunlitmarket.audit;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AuditEventTest {
    static ObservedMoneyEvent event(long sequence, String outcome, Long delta) {
        return new ObservedMoneyEvent(UUID.randomUUID(), UUID.randomUUID(), "epoch", sequence,
                Instant.parse("2026-11-02T23:30:00Z"), 12, 24000,
                "TEST", "COINS", outcome, 100, delta, UUID.randomUUID(), "mapping", null, null, null);
    }
    @Test void nullsAndUtcTimeAreExplicit() {
        var e=event(7,"UNKNOWN",null); var json=e.json();
        assertEquals(7,json.get("sequence").getAsLong());
        assertTrue(json.get("confirmedDelta").isJsonNull());
        assertTrue(json.get("note").isJsonNull());
        assertEquals("2026-11-02",e.utcDay());
        assertEquals("2026-11-02T23:30:00Z",json.get("timestamp").getAsString());
    }
    @Test void eventIdsAreUnique(){assertNotEquals(event(1,"CONFIRMED",1L).eventId(),event(2,"CONFIRMED",1L).eventId());}
    @Test void changeIsNotIssuance(){
        SettlementObservation o=new SettlementObservation();o.backend("COINS");o.removed(100);o.created(95);o.success(-5);
        assertEquals(-5L,o.delta());assertEquals(100L,o.grossRemoved());assertEquals(95L,o.grossCreated());
    }
    @Test void failedAttemptDoesNotCountSimulatedMint(){
        SettlementObservation o=new SettlementObservation();o.backend("COINS");o.created(100);o.restored();
        assertEquals("FAILED",o.outcome());assertEquals(0L,o.delta());assertNull(o.grossCreated());
    }
    @Test void uncertainBankHasNoInventedDelta(){SettlementObservation o=new SettlementObservation();o.backend("BANK");o.uncertain();assertNull(o.delta());assertEquals("UNKNOWN",o.outcome());}
    @Test void backendUsesChosenValueNotBalanceAfterPayment(){SettlementObservation o=new SettlementObservation();o.backend("WALLET");o.success(-50);assertEquals("WALLET",o.backend());assertEquals(-50L,o.delta());}
    @Test void saturatedWalletUsesActualDelta(){assertEquals(3L,EconomyAudit.walletDelta(Long.MAX_VALUE-3,Long.MAX_VALUE));assertEquals(0L,EconomyAudit.walletDelta(Long.MAX_VALUE,Long.MAX_VALUE));}
    @Test void missingWalletReadIsUnknown(){assertNull(EconomyAudit.walletDelta(null,10L));}
    @Test void exchangeCountsTwoLegsNotFeeTwice(){
        long cash=-1000, wallet=EconomyAudit.walletDelta(100L,1050L);
        assertEquals(-50,cash+wallet);
        long payout=950, debit=EconomyAudit.walletDelta(1000L,0L);
        assertEquals(-50,payout+debit);
    }
}
