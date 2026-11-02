package com.society.sunlitmarket.audit;

/** Audit-only local state copied from values already calculated by the settlement. */
public final class SettlementObservation {
    private String backend = "NONE";
    private String outcome = "FAILED";
    private Long confirmedDelta = 0L;
    private long created, removed;

    public void backend(String name) { backend = name; }
    public void removed(long value) { removed = value < 0 || Long.MAX_VALUE - removed < value ? Long.MAX_VALUE : removed + value; }
    public void created(long value) { created = value < 0 || Long.MAX_VALUE - created < value ? Long.MAX_VALUE : created + value; }
    public void uncertain() { outcome = "UNKNOWN"; confirmedDelta = null; }
    public void success(long delta) { outcome = "CONFIRMED"; confirmedDelta = delta; }
    public void restored() { outcome = "FAILED"; confirmedDelta = 0L; }
    public String backend() { return backend; }
    public String outcome() { return outcome; }
    public Long delta() { return confirmedDelta; }
    public Long grossCreated() { return "COINS".equals(backend) && "CONFIRMED".equals(outcome) ? created : null; }
    public Long grossRemoved() { return "COINS".equals(backend) && "CONFIRMED".equals(outcome) ? removed : null; }
}
