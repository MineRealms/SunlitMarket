from pathlib import Path
r=Path(__file__).resolve().parents[1]/'src/main/java/com/society/sunlitmarket'
p=r/'counter/CounterBlockEntity.java';s=p.read_text(encoding='utf-8')
s=s.replace('AlignmentData.get(p.server).add(p,r.faction,-1);','AlignmentData.get(p.server).add(p,r.faction,-CoreConfig.HAGGLE_FAILURE_LOSS.get());')
s=s.replace('AlignmentData.get(p.server).add(p,r.faction,-2);','AlignmentData.get(p.server).add(p,r.faction,-CoreConfig.STOLEN_ALIGNMENT_LOSS.get());')
s=s.replace('        com.society.sunlitmarket.core.MarketHooks.fire(new com.society.sunlitmarket.core.MarketHooks.Event(\n                "afterTrade",p,npc,r.faction,r.id.toString(),goods,r.ask));','''        int tier=MarketRules.contraband(goods,r.faction,p.level().dimension().location().toString());
        if(tier>0)AlignmentData.get(p.server).addWanted(p,tier*CoreConfig.CONTRABAND_WANTED.get());
        com.society.sunlitmarket.core.MarketHooks.fire(new com.society.sunlitmarket.core.MarketHooks.Event(
                "afterTrade",p,npc,r.faction,r.id.toString(),goods,r.ask));''')
p.write_text(s,encoding='utf-8')
p=r/'market/MarketService.java';s=p.read_text(encoding='utf-8')
s=s.replace('        if (reward > 0) AlignmentData.get(p.server).add(p, m.faction, reward);','''        if (reward > 0) AlignmentData.get(p.server).add(p, m.faction, reward);
        int tier=com.society.sunlitmarket.core.MarketRules.contraband(Currency.item(offer.item),m.faction,p.level().dimension().location().toString());
        if(tier>0)AlignmentData.get(p.server).addWanted(p,tier*CoreConfig.CONTRABAND_WANTED.get());''')
p.write_text(s,encoding='utf-8')
p=r/'core/CoreEvents.java';s=p.read_text(encoding='utf-8')
s=s.replace('        MinecraftForge.EVENT_BUS.addListener(CoreEvents::logout);','        MinecraftForge.EVENT_BUS.addListener(CoreEvents::logout);\n        MinecraftForge.EVENT_BUS.addListener(CoreEvents::death);')
s=s.replace('    private static void logout(PlayerEvent.PlayerLoggedOutEvent e)', '''    private static void death(net.minecraftforge.event.entity.living.LivingDeathEvent e) {
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
    private static void logout(PlayerEvent.PlayerLoggedOutEvent e)''')
s=s.replace('private static void stopped(ServerStoppedEvent e){TradeSessions.clear();}', 'private static void stopped(ServerStoppedEvent e){TradeSessions.clear();com.society.sunlitmarket.compat.MiddleEarthCompat.clear();}')
p.write_text(s,encoding='utf-8')
