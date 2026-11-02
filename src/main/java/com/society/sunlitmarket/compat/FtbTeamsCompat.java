package com.society.sunlitmarket.compat;

import com.society.sunlitmarket.SunlitMarketMod;
import com.society.sunlitmarket.config.CoreConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;
import java.util.Optional;
import java.util.UUID;

/** Optional stable API only; no compile-time FTB dependency. */
public final class FtbTeamsCompat {
    private static boolean warned;
    private FtbTeamsCompat() {}
    public record Resolution(OwnerKey owner, boolean available) {}
    public static Resolution resolve(Player player) {
        OwnerKey fallback = OwnerKey.player(player.getUUID());
        if (!(player instanceof ServerPlayer) || !ModList.get().isLoaded("ftbteams")) return new Resolution(fallback, true);
        try {
            Class<?> apiClass = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
            Object api = apiClass.getMethod("api").invoke(null);
            Class<?> contract = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI$API");
            if (!(Boolean) contract.getMethod("isManagerLoaded").invoke(api)) return new Resolution(fallback, false);
            Object manager = contract.getMethod("getManager").invoke(api);
            Class<?> managers = Class.forName("dev.ftb.mods.ftbteams.api.TeamManager");
            Optional<?> result = (Optional<?>) managers.getMethod("getTeamForPlayer", ServerPlayer.class).invoke(manager, player);
            if (result.isEmpty()) return new Resolution(fallback, true);
            Object team = result.get();
            Class<?> teams = Class.forName("dev.ftb.mods.ftbteams.api.Team");
            if (!(Boolean) teams.getMethod("isPartyTeam").invoke(team)) return new Resolution(fallback, true);
            if (!(Boolean) teams.getMethod("isValid").invoke(team)) return new Resolution(fallback, false);
            return new Resolution(new OwnerKey(true, (UUID) teams.getMethod("getId").invoke(team)), true);
        } catch (ReflectiveOperationException | LinkageError ex) {
            if (!warned) { warned = true; SunlitMarketMod.LOG.warn("[SunlitMarket] FTB ownership unavailable; shared writes denied", ex); }
            return new Resolution(fallback, false);
        }
    }
    public static OwnerKey owner(Player player) { return resolve(player).owner(); }
    public static boolean canAccess(Player player, OwnerKey owner) {
        Resolution r = resolve(player);
        return r.available() && r.owner().equals(owner);
    }
}
