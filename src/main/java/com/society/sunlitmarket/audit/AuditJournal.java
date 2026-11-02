package com.society.sunlitmarket.audit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded nonblocking producer; only the daemon writer touches audit files. */
public final class AuditJournal implements AutoCloseable {
    @FunctionalInterface public interface Sink extends AutoCloseable {
        void append(String line) throws Exception;
        @Override default void close() throws Exception {}
    }
    public record Receipt(ObservedMoneyEvent event, boolean written) {}
    private static final Gson JSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
    private final ArrayBlockingQueue<ObservedMoneyEvent> queue;
    private final Queue<Receipt> receipts = new ConcurrentLinkedQueue<>();
    private final AtomicLong ioErrors = new AtomicLong();
    private final AtomicLong outstanding = new AtomicLong();
    private final Thread worker;
    private volatile boolean accepting = true;
    private volatile boolean failed;

    public AuditJournal(Path file, int capacity) {
        this(capacity, new Sink() {
            private BufferedWriter out;
            public void append(String line) throws IOException {
                if (out == null) {
                    Files.createDirectories(file.getParent());
                    out = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                }
                out.write(line); out.newLine(); out.flush();
            }
            public void close() throws IOException { if (out != null) out.close(); }
        });
    }
    public AuditJournal(int capacity, Sink sink) {
        queue = new ArrayBlockingQueue<>(capacity);
        worker = new Thread(() -> writeLoop(sink), "sunlitmarket-audit");
        worker.setDaemon(true);
        worker.start();
    }
    public boolean offer(ObservedMoneyEvent event) {
        // Include unconsumed receipts in the bound, not just events waiting for disk.
        if (!accepting || failed || outstanding.get() >= queue.remainingCapacity() + queue.size()) return false;
        outstanding.incrementAndGet();
        if (queue.offer(event)) return true;
        outstanding.decrementAndGet(); return false;
    }
    private void writeLoop(Sink sink) {
        try {
            while (accepting || !queue.isEmpty()) {
                ObservedMoneyEvent event = queue.poll(100, TimeUnit.MILLISECONDS);
                if (event == null) continue;
                boolean ok = false;
                if (!failed) try { sink.append(JSON.toJson(event.json())); ok = true; }
                catch (Exception | LinkageError ex) { failed = true; ioErrors.incrementAndGet(); }
                receipts.offer(new Receipt(event, ok));
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            try { sink.close(); } catch (Exception ex) { failed = true; ioErrors.incrementAndGet(); }
        }
    }
    public Receipt pollReceipt() {
        Receipt receipt = receipts.poll();
        if (receipt != null) outstanding.decrementAndGet();
        return receipt;
    }
    public long pending() { return outstanding.get(); }
    public long ioErrors() { return ioErrors.get(); }
    public boolean failed() { return failed; }
    public boolean alive() { return worker.isAlive(); }
    public void close(long timeoutMillis) {
        accepting = false;
        try { worker.join(Math.max(1, timeoutMillis)); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
    }
    @Override public void close() { close(2000); }
}
