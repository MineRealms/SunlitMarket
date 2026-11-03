package com.society.sunlitmarket.core;

import com.society.sunlitmarket.alignment.FactionRelations;
import com.society.sunlitmarket.config.SunlitMarketConfig;
import com.society.sunlitmarket.config.CoreConfig;
import com.society.sunlitmarket.counter.CounterBlockEntity;
import com.society.sunlitmarket.crime.CrimeService;
import com.society.sunlitmarket.market.MarketRegistry;
import com.society.sunlitmarket.market.TradeSessions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

public final class CoreEvents {
    private CoreEvents(){}
    public static void register(IEventBus modBus){
        modBus.addListener(CoreEvents::config);
        MinecraftForge.EVENT_BUS.addListener(CoreCommands::register);
        MinecraftForge.EVENT_BUS.addListener(com.society.sunlitmarket.futures.FuturesCommands::register);
        MinecraftForge.EVENT_BUS.addListener(CoreEvents::login);
        com.society.sunlitmarket.market.MarketExport.register("futures-history.json", com.society.sunlitmarket.futures.FuturesService::history);
        MinecraftForge.EVENT_BUS.addListener(CoreEvents::reload);
        MinecraftForge.EVENT_BUS.addListener(CoreEvents::tick);
        MinecraftForge.EVENT_BUS.addListener(CoreEvents::breakBlock);
        MinecraftForge.EVENT_BUS.addListener(CoreEvents::logout);
        MinecraftForge.EVENT_BUS.addListener(CoreEvents::death);
        MinecraftForge.EVENT_BUS.addListener(CoreEvents::stopped);
        MinecraftForge.EVENT_BUS.addListener(CoreEvents::stopping);
    }
    private static void config(ModConfigEvent e){
        if(e.getConfig().getSpec()!=SunlitMarketConfig.SPEC&&e.getConfig().getSpec()!=CoreConfig.SPEC)return;
        SunlitMarketConfig.invalidate();MinecraftServer s=ServerLifecycleHooks.getCurrentServer();
        if(s!=null)s.execute(()->{MarketRegistry.changed();FactionRelations.reload(s);});
    }
    private static void reload(AddReloadListenerEvent e){
        e.addListener(new SimplePreparableReloadListener<Void>(){
            @Override protected Void prepare(ResourceManager r,ProfilerFiller p){return null;}
            @Override protected void apply(Void ignored,ResourceManager r,ProfilerFiller p){
                SunlitMarketConfig.invalidate();FactionRelations.reload(r);MarketRegistry.loadJson();MarketRules.loadJson();com.society.sunlitmarket.futures.FuturesSpecs.loadJson();MarketRegistry.changed();
            }
        });
    }
    private static void tick(TickEvent.ServerTickEvent e){if(e.phase==TickEvent.Phase.END){CrimeService.tick(e.getServer());com.society.sunlitmarket.audit.EconomyAudit.tick(e.getServer());com.society.sunlitmarket.treasury.Treasury.tick(e.getServer());com.society.sunlitmarket.futures.FuturesService.tick(e.getServer());}}
    private static void breakBlock(BlockEvent.BreakEvent e){
        if(e.getLevel().getBlockEntity(e.getPos()) instanceof CounterBlockEntity be&&!be.access(e.getPlayer())&&!e.getPlayer().hasPermissions(2))e.setCanceled(true);
    }
    private static void death(net.minecraftforge.event.entity.living.LivingDeathEvent e) {
        if(!CoreConfig.KILL_REWARDS.get() || e.getEntity() instanceof net.minecraft.world.entity.player.Player
                || !(e.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer p)) return;
        var identity=MarketRules.identity(e.getEntity()); if(identity.faction().isBlank())return;
        var alignment=com.society.sunlitmarket.alignment.AlignmentData.get(p.server);
        alignment.add(p,identity.faction(),-CoreConfig.KILL_LOSS.get());
        for(String friendly:FactionRelations.hostilesOf(identity.faction())) {
            int reward=(int)Math.floor(CoreConfig.ENEMY_KILL_GAIN.get()*FactionRelations.regionWeight(friendly,e.getEntity()));
            if(reward>0)alignment.add(p,friendly,reward);
        }
    }
    /** Futures settled while a player was offline are paid on their next login. */
    private static void login(PlayerEvent.PlayerLoggedInEvent e){
        if(e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p){
            seedMiddleEarthAlignment(p);
            String line=com.society.sunlitmarket.futures.FuturesService.deliver(p);
            if(!line.isEmpty())p.sendSystemMessage(net.minecraft.network.chat.Component.literal("[期货] "+line));
        }
    }

    /**
     * Middle-earth has its own player faction (me:gondor, me:rohan, ...) and SunlitMarket keys
     * alignment by the same ids, but nothing connected them. On the first login after the player
     * picked a faction, give that faction a starting alignment so "your own people like you" holds.
     * Seed once per player: later gains/losses are the player's own business.
     */
    private static void seedMiddleEarthAlignment(net.minecraft.server.level.ServerPlayer p){
        int seed=CoreConfig.ME_FACTION_SEED.get();
        if(seed<=0)return;
        var data=p.getPersistentData();
        if(data.getBoolean("sunlitmarket.meSeeded"))return;
        String faction=com.society.sunlitmarket.compat.MiddleEarthCompat.savedFaction(p);
        if(faction==null||faction.isBlank()||"UNKNOWN".equals(faction))return;
        String id=faction.startsWith("me:")?faction:"me:"+faction;
        if(!com.society.sunlitmarket.alignment.FactionRelations.factions().contains(id))return;
        var alignment=com.society.sunlitmarket.alignment.AlignmentData.get(p.server);
        if(alignment.get(p,id)==0)alignment.add(p,id,seed);
        data.putBoolean("sunlitmarket.meSeeded",true);
    }
    private static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p)TradeSessions.close(p);}
    private static void stopping(net.minecraftforge.event.server.ServerStoppingEvent e){com.society.sunlitmarket.market.MarketExport.writeQuietly(e.getServer());com.society.sunlitmarket.audit.EconomyAudit.stop(e.getServer());}
    private static void stopped(ServerStoppedEvent e){
        TradeSessions.clear();
        // A compat class that fails to load must never take the shutdown thread with it: the
        // ServerStoppedEvent handler is the last thing the server does, so a throw here leaves a
        // zombie process holding the world lock instead of exiting.
        try{com.society.sunlitmarket.compat.MiddleEarthCompat.clear();}
        catch(Throwable t){com.society.sunlitmarket.SunlitMarketMod.LOG.warn("[SunlitMarket] ME compat clear failed on shutdown",t);}
        com.society.sunlitmarket.audit.EconomyAudit.stop(e.getServer());
    }
}
