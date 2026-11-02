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
        MinecraftForge.EVENT_BUS.addListener(CoreEvents::reload);
        MinecraftForge.EVENT_BUS.addListener(CoreEvents::tick);
        MinecraftForge.EVENT_BUS.addListener(CoreEvents::breakBlock);
        MinecraftForge.EVENT_BUS.addListener(CoreEvents::logout);
        MinecraftForge.EVENT_BUS.addListener(CoreEvents::death);
        MinecraftForge.EVENT_BUS.addListener(CoreEvents::stopped);
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
                SunlitMarketConfig.invalidate();FactionRelations.reload(r);MarketRegistry.loadJson();MarketRules.loadJson();MarketRegistry.changed();
            }
        });
    }
    private static void tick(TickEvent.ServerTickEvent e){if(e.phase==TickEvent.Phase.END)CrimeService.tick(e.getServer());}
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
    private static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p)TradeSessions.close(p);}
    private static void stopped(ServerStoppedEvent e){TradeSessions.clear();com.society.sunlitmarket.compat.MiddleEarthCompat.clear();}
}
