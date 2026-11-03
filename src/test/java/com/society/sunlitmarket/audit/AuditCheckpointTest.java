package com.society.sunlitmarket.audit;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import static org.junit.jupiter.api.Assertions.*;

class AuditCheckpointTest {
    @Test void outcomesAndDropsAreNotDoubleCounted(){
        var c=new AuditCheckpoint();c.beginEpoch("epoch");
        var success=AuditEventTest.event(1,"CONFIRMED",-12L);c.attempt(success);c.written(success);
        var failure=AuditEventTest.event(2,"FAILED",0L);c.attempt(failure);c.written(failure);
        var unknown=AuditEventTest.event(3,"UNKNOWN",null);c.attempt(unknown);c.written(unknown);
        var lost=AuditEventTest.event(4,"CONFIRMED",100L);c.attempt(lost);c.dropped(lost);
        assertEquals(4,c.attempted());assertEquals(3,c.written());assertEquals(1,c.failed());assertEquals(1,c.unknown());assertEquals(1,c.confirmed());assertEquals(1,c.dropped());
        assertEquals(BigInteger.valueOf(-12),c.observedNet());assertEquals(1,c.recentGaps().size());
    }
    @Test void dailyStatisticsSurviveRestartWithoutReplay(){
        var c=new AuditCheckpoint();c.beginEpoch("a");var event=AuditEventTest.event(1,"CONFIRMED",25L);c.attempt(event);c.written(event);c.finishEpoch(true);
        var loaded=AuditCheckpoint.load(c.save(new CompoundTag()));loaded.beginEpoch("b");
        assertEquals(1,loaded.written());assertEquals(BigInteger.valueOf(25),loaded.observedNet());
        assertEquals("25",loaded.day("2026-11-02").get("observedNet").getAsString());
        assertEquals("NO_OBSERVATIONS",loaded.day("2026-11-03").get("status").getAsString());
        assertFalse(loaded.status().get("reconciliationRequired").getAsBoolean());
    }
    @Test void uncleanStopIsVisible(){var c=new AuditCheckpoint();c.beginEpoch("a");var loaded=AuditCheckpoint.load(c.save(new CompoundTag()));loaded.beginEpoch("b");assertTrue(loaded.status().get("reconciliationRequired").getAsBoolean());}
    @Test void aggregateDoesNotOverflowLong(){
        var c=new AuditCheckpoint();for(int i=1;i<=2;i++){var e=AuditEventTest.event(i,"CONFIRMED",Long.MAX_VALUE);c.attempt(e);c.written(e);}
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO),c.observedNet());
    }
    @Test void absentAccountsAreNotZero(){var c=new AuditCheckpoint();assertEquals("UNKNOWN",c.status().get("M0").getAsString());assertEquals("SEE_TREASURY",c.status().get("R").getAsString());}
    @Test void reportedSupplyIsRecordedAndSurvivesRestart(){
        var c=new AuditCheckpoint();c.supply(1_234_567L,890L,24000L);
        assertEquals("1234567",c.status().get("M0").getAsString());
        assertEquals("890",c.status().get("M0Debts").getAsString());
        var loaded=AuditCheckpoint.load(c.save(new CompoundTag()));
        assertEquals("1234567",loaded.status().get("M0").getAsString());
        assertEquals("890",loaded.status().get("M0Debts").getAsString());
        assertEquals("UNKNOWN",new AuditCheckpoint().status().get("M0").getAsString());
    }
    @Test void anUnchangedSupplyDoesNotDirtyTheCheckpoint(){
        var c=new AuditCheckpoint();c.supply(500L,10L,100L);c.supply(500L,10L,200L);
        assertEquals("500",c.status().get("M0").getAsString());
        c.supply(600L,10L,300L);
        assertEquals("600",c.status().get("M0").getAsString());
        c.supply(-1L,0L,400L);
        assertEquals("600",c.status().get("M0").getAsString());
    }
    @Test void mappingControlDoesNotMint(){var c=new AuditCheckpoint();var e=AuditEventTest.event(1,"CONTROL",null);c.attempt(e);c.written(e);assertEquals(0,c.unknown());assertEquals(0,c.confirmed());assertEquals(BigInteger.ZERO,c.observedNet());}
}
