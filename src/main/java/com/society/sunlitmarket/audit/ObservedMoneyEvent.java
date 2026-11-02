package com.society.sunlitmarket.audit;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.UUID;

/** One observation, not a recoverable transaction or a total-money measurement. */
public record ObservedMoneyEvent(
        UUID eventId, UUID operationId, String epoch, long sequence, Instant timestamp,
        long gameTime, long dayTime, String source, String backend, String outcome,
        long requestedDelta, Long confirmedDelta, UUID player, String valuationVersion,
        Long grossCoinCreated, Long grossCoinRemoved, String note) {
    public String utcDay() { return timestamp.toString().substring(0, 10); }

    public JsonObject json() {
        JsonObject o = new JsonObject();
        o.addProperty("schema", 2);
        o.addProperty("eventId", eventId.toString());
        o.addProperty("operationId", operationId == null ? null : operationId.toString());
        o.addProperty("epoch", epoch);
        o.addProperty("sequence", sequence);
        o.addProperty("timestamp", timestamp.toString());
        o.addProperty("utcDay", utcDay());
        o.addProperty("gameTime", gameTime);
        o.addProperty("dayTime", dayTime);
        o.addProperty("source", source);
        o.addProperty("backend", backend);
        o.addProperty("outcome", outcome);
        o.addProperty("requestedDelta", requestedDelta);
        o.addProperty("confirmedDelta", confirmedDelta);
        o.addProperty("player", player == null ? null : player.toString());
        o.addProperty("valuationVersion", valuationVersion);
        o.addProperty("grossCoinCreated", grossCoinCreated);
        o.addProperty("grossCoinRemoved", grossCoinRemoved);
        o.addProperty("note", note);
        return o;
    }
}
