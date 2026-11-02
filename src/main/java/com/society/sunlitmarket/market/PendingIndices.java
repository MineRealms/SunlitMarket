package com.society.sunlitmarket.market;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Index definitions declared by KubeJS before a world exists.
 *
 * <p>Server scripts are evaluated during resource reload, which on a client happens before the
 * integrated server is created - {@code ServerLifecycleHooks.getCurrentServer()} is null there, so
 * writing straight into {@link MarketData} silently loses every index. Definitions are buffered
 * here instead and replayed on {@code ServerStartedEvent}, which also covers leaving and
 * re-entering a world (the saved data is per-world).
 */
public final class PendingIndices {
    public record Definition(String id, String name, List<MarketData.Member> members) {
    }

    private static final Map<String, Definition> DEFINITIONS = new LinkedHashMap<>();

    private PendingIndices() {
    }

    /** Records (or replaces) one index definition. */
    public static synchronized void put(String id, String name, List<MarketData.Member> members) {
        if (id == null || id.isBlank() || members == null || members.isEmpty()) return;
        DEFINITIONS.put(id, new Definition(id, name == null || name.isBlank() ? id : name, List.copyOf(members)));
    }

    public static synchronized int size() {
        return DEFINITIONS.size();
    }

    public static synchronized void clear() {
        DEFINITIONS.clear();
    }

    /** Writes every buffered definition into the world's market data; returns how many. */
    public static synchronized int applyTo(MinecraftServer server) {
        if (server == null || DEFINITIONS.isEmpty()) return 0;
        MarketData data = MarketData.get(server);
        for (Definition definition : new ArrayList<>(DEFINITIONS.values())) {
            data.defineIndex(definition.id(), definition.name(), definition.members());
        }
        return DEFINITIONS.size();
    }
}
