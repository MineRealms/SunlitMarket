package com.society.sunlitmarket.audit;

import com.google.gson.JsonObject;
import java.util.UUID;

/** Immutable best-effort observation; it never participates in settlement decisions. */
public record ObservedMoneyEvent(
        UUID eventId, String epoch, long sequence, long gameTime, long dayTime,
        String source, String reason, String backend, String outcome,
        long requestedDelta, Long confirmedDelta, UUID player,
        String valuationVersion, String note) {
    public JsonObject json() {
        JsonObject o = new JsonObject();
        o.addProperty("eventId", eventId.toString());
        o.addProperty("epoch", epoch); o.addProperty("sequence", sequence);
        o.addProperty("gameTime", gameTime); o.addProperty("dayTime", dayTime);
        o.addProperty("source", source); o.addProperty("reason", reason);
        o.addProperty("backend", backend); o.addProperty("outcome", outcome);
        o.addProperty("requestedDelta", requestedDelta);
        if (confirmedDelta == null) o.add("confirmedDelta", null); else o.addProperty("confirmedDelta", confirmedDelta);
        if (player == null) o.add("player", null); else o.addProperty("player", player.toString());
        if (valuationVersion == null) o.add("valuationVersion", null); else o.addProperty("valuationVersion", valuationVersion);
        if (note == null) o.add("note", null); else o.addProperty("note", note);
        return o;
    }
}
