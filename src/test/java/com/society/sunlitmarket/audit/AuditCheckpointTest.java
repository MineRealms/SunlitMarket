package com.society.sunlitmarket.audit;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuditCheckpointTest {
    @Test void checkpointSeparatesKnownAndUnknown() {
        AuditCheckpoint c=new AuditCheckpoint();
        c.markAttempted();c.written("PAY",-12,true);c.markAttempted();c.written("BANK",0,false);c.markDropped();c.markFailed();
        assertEquals(2,c.attempted());assertEquals(2,c.written());assertEquals(1,c.confirmed());assertEquals(1,c.unknown());
        assertEquals(-12,c.observedNet());assertEquals(1,c.dropped());assertEquals(1,c.failed());assertEquals(1,c.sources().get("PAY"));
    }
    @Test void checkpointRoundTripsOnlyStatistics() {
        AuditCheckpoint c=new AuditCheckpoint();c.markAttempted();c.written("GIVE",25,true);
        CompoundTag tag=c.save(new CompoundTag());AuditCheckpoint loaded=AuditCheckpoint.load(tag);
        assertEquals(1,loaded.attempted());assertEquals(25,loaded.observedNet());assertEquals(1,loaded.written());
        assertEquals(1,loaded.sources().get("GIVE"));
        assertFalse(tag.contains("transaction")||tag.contains("items")||tag.contains("replay"));
    }
}
