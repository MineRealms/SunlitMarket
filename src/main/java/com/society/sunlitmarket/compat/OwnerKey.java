package com.society.sunlitmarket.compat;

import java.util.UUID;

public record OwnerKey(boolean team, UUID id) {
    public String key() { return (team ? "team:" : "player:") + id; }
    public static OwnerKey player(UUID id) { return new OwnerKey(false, id); }
    public static OwnerKey parse(String key) {
        if (key.startsWith("team:")) return new OwnerKey(true, UUID.fromString(key.substring(5)));
        return player(UUID.fromString(key.startsWith("player:") ? key.substring(7) : key));
    }
}
