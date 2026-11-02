package com.society.sunlitmarket.core;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.society.sunlitmarket.alignment.AlignmentData;
import com.society.sunlitmarket.counter.CounterBlockEntity;
import com.society.sunlitmarket.counter.CounterRegistry;
import com.society.sunlitmarket.market.*;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

public final class CoreCommands {
    private CoreCommands(){}
    public static void register(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("sunlitbribe").executes(c->{var p=c.getSource().getPlayerOrException();String result=com.society.sunlitmarket.crime.CrimeService.bribe(p);c.getSource().sendSuccess(()->Component.literal(result),false);return 1;}));
        e.getDispatcher().register(Commands.literal("sunlitalignment").executes(c->{
            ServerPlayer p=c.getSource().getPlayerOrException();
            com.google.gson.JsonObject o=new com.google.gson.JsonObject();o.addProperty("owner",AlignmentData.ownerKey(p));o.addProperty("wanted",AlignmentData.get(p.server).wanted(p));
            com.google.gson.JsonArray rows=new com.google.gson.JsonArray();
            java.util.Set<String> ids=new java.util.TreeSet<>(com.society.sunlitmarket.alignment.FactionRelations.factions());ids.addAll(AlignmentData.get(p.server).snapshot(p).keySet());
            for(String id:ids){int v=AlignmentData.get(p.server).get(p,id);var row=new com.google.gson.JsonObject();row.addProperty("id",id);row.addProperty("value",v);row.addProperty("title",com.society.sunlitmarket.config.SunlitMarketConfig.tierFor(v).title);rows.add(row);}
            o.add("factions",rows);com.society.sunlitmarket.SunlitMarketMod.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->p),new com.society.sunlitmarket.net.AlignmentMessage(o.toString()));return 1;
        }));
        e.getDispatcher().register(Commands.literal("sunlitmarket").requires(s->s.hasPermission(2))
            .then(Commands.literal("team").then(Commands.literal("on").executes(c->{com.society.sunlitmarket.config.CoreConfig.TEAM_ALIGNMENT.set(true);MarketRegistry.changed();c.getSource().sendSuccess(()->Component.literal("Team alignment enabled; existing data kept separate"),true);return 1;}))
                .then(Commands.literal("off").executes(c->{com.society.sunlitmarket.config.CoreConfig.TEAM_ALIGNMENT.set(false);MarketRegistry.changed();c.getSource().sendSuccess(()->Component.literal("Personal alignment enabled; team data preserved"),true);return 1;})))
            .then(Commands.literal("wantedscope").then(Commands.literal("personal").executes(c->{com.society.sunlitmarket.config.CoreConfig.WANTED_SCOPE.set(com.society.sunlitmarket.config.CoreConfig.WantedScope.PERSONAL);MarketRegistry.changed();c.getSource().sendSuccess(()->Component.literal("Personal wanted records enabled; team records preserved"),true);return 1;}))
                .then(Commands.literal("team").executes(c->{com.society.sunlitmarket.config.CoreConfig.WANTED_SCOPE.set(com.society.sunlitmarket.config.CoreConfig.WantedScope.TEAM);MarketRegistry.changed();c.getSource().sendSuccess(()->Component.literal("FTB party wanted records enabled; personal records preserved"),true);return 1;})))
            .then(Commands.literal("bankunlock").then(Commands.argument("player",EntityArgument.player()).executes(c->{var p=EntityArgument.getPlayer(c,"player");p.getPersistentData().remove("sunlitmarket.bankUncertain");c.getSource().sendSuccess(()->Component.literal("Bank transaction lock cleared after administrator reconciliation"),true);return 1;})))
            .then(Commands.literal("identity").then(Commands.argument("entity",EntityArgument.entity()).executes(c->{
                var entity=EntityArgument.getEntity(c,"entity");var i=MarketRules.identity(entity);
                c.getSource().sendSuccess(()->Component.literal(i.toString()+" customer="+i.customer()),false);return 1;
            })))
            .then(Commands.literal("owner").executes(c->{var p=c.getSource().getPlayerOrException();c.getSource().sendSuccess(()->Component.literal(AlignmentData.ownerKey(p)),false);return 1;}))
            .then(Commands.literal("recovercounter").executes(c->{
                var p=c.getSource().getPlayerOrException();var hit=p.pick(6,0,false);
                if(hit instanceof net.minecraft.world.phys.BlockHitResult b&&p.level().getBlockEntity(b.getBlockPos()) instanceof CounterBlockEntity be){be.transfer(p);c.getSource().sendSuccess(()->Component.literal("Counter ownership recovered"),true);return 1;}return 0;
            }))
            .then(Commands.literal("audit")
                .then(Commands.literal("smoketest").requires(s->Boolean.getBoolean("sunlitmarket.audit.allowSmoke")).executes(c->{
                    String result=com.society.sunlitmarket.audit.AuditSmokeTest.run(c.getSource().getServer());
                    c.getSource().sendSuccess(()->Component.literal(result),false);return result.startsWith("PASS")?1:0;
                }))
                .then(Commands.literal("status").executes(c->{
                    c.getSource().sendSuccess(()->Component.literal(com.society.sunlitmarket.audit.EconomyAudit.status(c.getSource().getServer())),false);return 1;
                }))
                .then(Commands.literal("gaps").executes(c->{
                    c.getSource().sendSuccess(()->Component.literal(com.society.sunlitmarket.audit.EconomyAudit.gaps(c.getSource().getServer())),false);return 1;
                }))
                .then(Commands.literal("day").executes(c->{
                    c.getSource().sendSuccess(()->Component.literal(com.society.sunlitmarket.audit.EconomyAudit.day(c.getSource().getServer())),false);return 1;
                }).then(Commands.argument("date",com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c->{
                    try {
                        String result=com.society.sunlitmarket.audit.EconomyAudit.day(c.getSource().getServer(),com.mojang.brigadier.arguments.StringArgumentType.getString(c,"date"));
                        c.getSource().sendSuccess(()->Component.literal(result),false);return 1;
                    } catch(java.time.DateTimeException ex){c.getSource().sendFailure(Component.literal("Expected UTC date YYYY-MM-DD"));return 0;}
                }))))
            .then(Commands.literal("selftest").executes(c->{
                ServerPlayer p=c.getSource().getPlayerOrException();
                if(!p.server.isSingleplayer()&&!Boolean.getBoolean("sunlitmarket.allowSelfTest")){c.getSource().sendFailure(Component.literal("Self-test is restricted to isolated development worlds"));return 0;}
                String result=CoreSelfTest.run(p);c.getSource().sendSuccess(()->Component.literal(result),false);return result.contains("FAIL")?0:1;
            })));
    }
}
