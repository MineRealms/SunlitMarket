package com.society.sunlitmarket.audit;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class AuditJournalTest {
    @TempDir Path dir;
    @Test void writesRealJsonLinesAndReceipts() throws Exception {
        Path file=dir.resolve("world/audit/events.jsonl");
        AuditJournal j=new AuditJournal(file,8);
        assertTrue(j.offer(AuditEventTest.event(1,"CONFIRMED",20L)));
        assertTrue(j.offer(AuditEventTest.event(2,"FAILED",0L)));j.close();
        assertFalse(j.alive());assertEquals(2,Files.readAllLines(file).size());
        assertEquals("2026-11-02",JsonParser.parseString(Files.readAllLines(file).get(0)).getAsJsonObject().get("utcDay").getAsString());
        assertTrue(j.pollReceipt().written());assertTrue(j.pollReceipt().written());assertEquals(0,j.pending());
    }
    @Test void failedDiskDoesNotThrowOnProducer() {
        AuditJournal j=new AuditJournal(8,line->{throw new IOException("injected disk failure");});
        assertDoesNotThrow(()->j.offer(AuditEventTest.event(1,"CONFIRMED",20L)));j.close();
        assertTrue(j.failed());assertEquals(1,j.ioErrors());assertFalse(j.pollReceipt().written());
        assertFalse(j.offer(AuditEventTest.event(2,"CONFIRMED",30L)));
    }
    @Test void fullQueueAndSlowDiskDoNotWaitForTransactionThread() throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        AuditJournal j=new AuditJournal(1,line->{entered.countDown();release.await();});
        try {
            assertTrue(j.offer(AuditEventTest.event(1,"CONFIRMED",1L)));assertTrue(entered.await(2,TimeUnit.SECONDS));
            assertFalse(j.offer(AuditEventTest.event(2,"CONFIRMED",1L)));
            j.close(5);assertTrue(j.alive());
        } finally {release.countDown();j.close();}
        assertFalse(j.alive());assertTrue(j.pollReceipt().written());
    }
    @Test void unavailableDirectoryIsReportedNotThrown() throws Exception {
        Path blocker=dir.resolve("not-directory");Files.writeString(blocker,"keep");
        AuditJournal j=new AuditJournal(blocker.resolve("events.jsonl"),2);
        assertTrue(j.offer(AuditEventTest.event(1,"CONFIRMED",10L)));j.close();assertTrue(j.failed());assertFalse(j.pollReceipt().written());
        assertEquals("keep",Files.readString(blocker));
    }
    @Test void separateEpochFilesDoNotOverwritePreviousRun() throws Exception {
        Path first=dir.resolve("events-a.jsonl"),second=dir.resolve("events-b.jsonl");
        try(var j=new AuditJournal(first,2)){assertTrue(j.offer(AuditEventTest.event(1,"CONFIRMED",1L)));}
        String original=Files.readString(first);
        try(var j=new AuditJournal(second,2)){assertTrue(j.offer(AuditEventTest.event(1,"CONFIRMED",2L)));}
        assertEquals(original,Files.readString(first));assertEquals(1,Files.readAllLines(second).size());
    }
}
