package com.society.sunlitmarket;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.society.sunlitmarket.alignment.AlignmentData;
import com.society.sunlitmarket.alignment.FactionRelations;
import com.society.sunlitmarket.config.SunlitMarketConfig;
import com.society.sunlitmarket.currency.Currency;
import com.society.sunlitmarket.market.MarketData;
import com.society.sunlitmarket.market.MarketRegistry;
import com.society.sunlitmarket.market.MarketService;
import com.society.sunlitmarket.market.MarketView;
import com.society.sunlitmarket.market.Merchant;
import com.society.sunlitmarket.market.UnlockData;
import com.society.sunlitmarket.net.MarketActionMessage;
import com.society.sunlitmarket.net.MarketUpdateMessage;
import com.society.sunlitmarket.net.OpenMarketMessage;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(SunlitMarketMod.MOD_ID)
public final class SunlitMarketMod {
    public static final String MOD_ID = "sunlitmarket";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);
    private static final String PROTOCOL = "3";
    private long lastAlignmentDay = Long.MIN_VALUE;

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    public SunlitMarketMod() {
        SunlitMarketConfig.register();
        com.society.sunlitmarket.config.CoreConfig.register();
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        com.society.sunlitmarket.counter.CounterRegistry.register(bus);
        com.society.sunlitmarket.deposit.DepositRegistry.register(bus);
        com.society.sunlitmarket.deposit.DepositCompat.probe();
        com.society.sunlitmarket.core.CoreEvents.register(bus);
        bus.addListener(this::onCommonSetup);
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(this::onCommands);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, this::onEntityInteract);
        MinecraftForge.EVENT_BUS.addListener(this::onEntityJoin);
        MinecraftForge.EVENT_BUS.addListener(this::onChangeTarget);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> { com.society.sunlitmarket.client.MarketClient.init(); com.society.sunlitmarket.client.CounterClient.init(); com.society.sunlitmarket.client.DepositClient.init(); });
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        CHANNEL.messageBuilder(OpenMarketMessage.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenMarketMessage::encode).decoder(OpenMarketMessage::decode)
                .consumerMainThread(OpenMarketMessage::handle).add();
        CHANNEL.messageBuilder(MarketUpdateMessage.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MarketUpdateMessage::encode).decoder(MarketUpdateMessage::decode)
                .consumerMainThread(MarketUpdateMessage::handle).add();
        CHANNEL.messageBuilder(MarketActionMessage.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MarketActionMessage::encode).decoder(MarketActionMessage::decode)
                .consumerMainThread(MarketActionMessage::handle).add();
        CHANNEL.messageBuilder(com.society.sunlitmarket.net.CounterStateMessage.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.society.sunlitmarket.net.CounterStateMessage::encode).decoder(com.society.sunlitmarket.net.CounterStateMessage::decode)
                .consumerMainThread(com.society.sunlitmarket.net.CounterStateMessage::handle).add();
        CHANNEL.messageBuilder(com.society.sunlitmarket.net.CounterActionMessage.class, 4, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.society.sunlitmarket.net.CounterActionMessage::encode).decoder(com.society.sunlitmarket.net.CounterActionMessage::decode)
                .consumerMainThread(com.society.sunlitmarket.net.CounterActionMessage::handle).add();
        CHANNEL.messageBuilder(com.society.sunlitmarket.net.AlignmentMessage.class, 5, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.society.sunlitmarket.net.AlignmentMessage::encode).decoder(com.society.sunlitmarket.net.AlignmentMessage::decode)
                .consumerMainThread(com.society.sunlitmarket.net.AlignmentMessage::handle).add();
        CHANNEL.messageBuilder(com.society.sunlitmarket.net.ChartRequestMessage.class, 6, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.society.sunlitmarket.net.ChartRequestMessage::encode).decoder(com.society.sunlitmarket.net.ChartRequestMessage::decode)
                .consumerMainThread(com.society.sunlitmarket.net.ChartRequestMessage::handle).add();
        CHANNEL.messageBuilder(com.society.sunlitmarket.net.ChartDataMessage.class, 7, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.society.sunlitmarket.net.ChartDataMessage::encode).decoder(com.society.sunlitmarket.net.ChartDataMessage::decode)
                .consumerMainThread(com.society.sunlitmarket.net.ChartDataMessage::handle).add();
        CHANNEL.messageBuilder(com.society.sunlitmarket.net.DepositStateMessage.class, 8, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.society.sunlitmarket.net.DepositStateMessage::encode).decoder(com.society.sunlitmarket.net.DepositStateMessage::decode)
                .consumerMainThread(com.society.sunlitmarket.net.DepositStateMessage::handle).add();
        LOG.info("[SunlitMarket] ready (numismatics={})", ModList.get().isLoaded("numismatics"));
    }

    private void onServerStarted(ServerStartedEvent event) {
        MarketRegistry.loadJson();
        int indices = com.society.sunlitmarket.market.PendingIndices.applyTo(event.getServer());
        if (indices > 0) {
            LOG.info("[SunlitMarket] applied {} buffered price indices", indices);
        }
        com.society.sunlitmarket.core.MarketRules.loadJson();
        com.society.sunlitmarket.deposit.DepositConfig.load();
        FactionRelations.reload(event.getServer());
        lastAlignmentDay = Long.MIN_VALUE;
        com.society.sunlitmarket.market.TradeSessions.clear();
    }

    /** Once per in-game day: drift alignment toward zero and cool down wanted level. */
    private void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;
        long dayTime = server.overworld().getDayTime();
        if (dayTime % MarketData.SAMPLE_EVERY == 0) {
            MarketData.get(server).sample(dayTime, server);
        }
        // KubeJS can override deposit rates mid-session; persist those edits without blocking.
        if (dayTime % 200L == 0L && com.society.sunlitmarket.deposit.DepositConfig.dirty()) {
            com.society.sunlitmarket.deposit.DepositConfig.save();
            LOG.info("[SunlitMarket] deposit_values.json updated by script");
        }
        long day = dayTime / 24000L;
        if (day == lastAlignmentDay) return;
        lastAlignmentDay = day;
        AlignmentData.get(server).roll(day, server);
    }

    private void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Merchant merchant = MarketRegistry.resolve(event.getTarget());
        if (merchant == null) return;
        event.setCanceled(true);
        MarketService.open(player, merchant, event.getTarget());
    }

    private void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof Mob mob
                && isMerchant(event.getEntity())) {
            mob.setPersistenceRequired();
        }
    }

    /** Tagged merchants neither attack nor are targeted by mob AI. */
    private void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (isMerchant(mob) || (event.getNewTarget() != null && isMerchant(event.getNewTarget()))) {
            event.setNewTarget(null);
        }
    }

    private static boolean isMerchant(net.minecraft.world.entity.Entity entity) {
        return MarketRegistry.resolve(entity) != null;
    }

    /** {@code /sunlitmarket <name> [faction] [value]} — read or set alignment. */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack>
            alignmentCommand(String name) {
        return alignmentCommand(name, false);
    }

    /**
     * @param additive when true the value is a delta applied through
     *                 {@link AlignmentData#add}, so the diplomatic backlash runs.
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack>
            alignmentCommand(String name, boolean additive) {
        return Commands.literal(name)
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    java.util.Map<String, Integer> all = AlignmentData.get(player.server).snapshot(player);
                    if (all.isEmpty()) {
                        ctx.getSource().sendSuccess(() -> Component.literal("Alignment: none"), false);
                        return 1;
                    }
                    java.util.List<String> parts = new java.util.ArrayList<>();
                    for (java.util.Map.Entry<String, Integer> e : all.entrySet()) {
                        parts.add(e.getKey() + " " + (e.getValue() > 0 ? "+" : "") + e.getValue());
                    }
                    java.util.Collections.sort(parts);
                    ctx.getSource().sendSuccess(() -> Component.literal("Alignment: " + String.join(", ", parts)), false);
                    return 1;
                })
                .then(Commands.argument("faction", ResourceLocationArgument.id())
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String faction = ResourceLocationArgument.getId(ctx, "faction").toString();
                            int value = AlignmentData.get(player.server).get(player, faction);
                            ctx.getSource().sendSuccess(() -> Component.literal(faction + " = " + value), false);
                            return 1;
                        })
                        .then(Commands.argument("value", IntegerArgumentType.integer())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String faction = ResourceLocationArgument.getId(ctx, "faction").toString();
                                    int value = IntegerArgumentType.getInteger(ctx, "value");
                                    if (additive) {
                                        AlignmentData.get(player.server).add(player, faction, value);
                                    } else {
                                        AlignmentData.get(player.server).set(player, faction, value);
                                    }
                                    int now = AlignmentData.get(player.server).get(player, faction);
                                    ctx.getSource().sendSuccess(() -> Component.literal(faction + " = " + now), false);
                                    return 1;
                                })));
    }

    /**
     * Opens the market of the closest resolved merchant, so a player (or an operator
     * testing a packed counter) does not have to aim at a specific NPC.
     */
    private static int openNearest(net.minecraft.commands.CommandSourceStack source, int radius)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        java.util.List<net.minecraft.world.entity.Entity> candidates = player.level().getEntitiesOfClass(
                net.minecraft.world.entity.Entity.class, player.getBoundingBox().inflate(radius),
                entity -> !(entity instanceof ServerPlayer) && MarketRegistry.resolve(entity) != null);
        net.minecraft.world.entity.Entity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (net.minecraft.world.entity.Entity entity : candidates) {
            double distance = entity.distanceToSqr(player);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = entity;
            }
        }
        Merchant merchant = nearest == null ? null : MarketRegistry.resolve(nearest);
        if (merchant == null) {
            source.sendFailure(Component.literal("No merchant within " + radius + " blocks; try /sunlitmarket near " + (radius * 2)));
            return 0;
        }
        MarketService.open(player, merchant, nearest);
        String distance = String.format(java.util.Locale.ROOT, "%.1f", Math.sqrt(nearestDistance));
        source.sendSuccess(() -> Component.literal("Opening " + merchant.name + " (" + merchant.id + ") at " + distance + "m"), false);
        return 1;
    }

    /** Sends one chart payload to the caller; the client opens (or refreshes) the panel. */
    private static int openChart(net.minecraft.commands.CommandSourceStack source, String id, int period)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String json = com.society.sunlitmarket.market.ChartService.json(player.server, id, period);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new com.society.sunlitmarket.net.ChartDataMessage(json));
        return 1;
    }

    private void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sunlitmarket")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("near")
                        .executes(ctx -> openNearest(ctx.getSource(), 32))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                                .executes(ctx -> openNearest(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))))
                .then(Commands.literal("open")
                        .then(Commands.argument("merchant", StringArgumentType.string())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    Merchant merchant = MarketRegistry.get(StringArgumentType.getString(ctx, "merchant"));
                                    if (merchant == null) {
                                        ctx.getSource().sendFailure(Component.literal("Unknown merchant"));
                                        return 0;
                                    }
                                    MarketService.open(player, merchant);
                                    return 1;
                                })))
                .then(alignmentCommand("rep"))
                .then(alignmentCommand("align"))
                .then(alignmentCommand("addalign", true))
                .then(Commands.literal("wanted")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            int wanted = AlignmentData.get(player.server).wanted(player);
                            ctx.getSource().sendSuccess(() -> Component.literal("Wanted: " + wanted), false);
                            return 1;
                        })
                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    int value = IntegerArgumentType.getInteger(ctx, "value");
                                    AlignmentData.get(player.server).setWanted(player, value);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Wanted = " + value), false);
                                    return 1;
                                })))
                .then(Commands.literal("index").executes(ctx -> {
                    MarketData data = MarketData.get(ctx.getSource().getServer());
                    java.util.List<String> ids = data.ids();
                    if (ids.isEmpty()) {
                        ctx.getSource().sendSuccess(() -> Component.literal("No indices registered"), false);
                    }
                    for (String id : ids) {
                        int value = data.value(id);
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                id + " [" + data.name(id) + "] = " + (value < 0 ? "n/a" : value)), false);
                    }
                    return ids.size();
                }))
                .then(Commands.literal("chart")
                        .executes(ctx -> openChart(ctx.getSource(), "", MarketData.PERIODS[0]))
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(ctx -> openChart(ctx.getSource(), StringArgumentType.getString(ctx, "id"), MarketData.PERIODS[0]))
                                .then(Commands.argument("period", IntegerArgumentType.integer(1))
                                        .executes(ctx -> openChart(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                                                IntegerArgumentType.getInteger(ctx, "period"))))))
                .then(Commands.literal("factions").executes(ctx -> {
                    java.util.List<String> ids = new java.util.ArrayList<>(FactionRelations.factions());
                    java.util.Collections.sort(ids);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Factions (" + ids.size() + "): " + String.join(", ", ids)), false);
                    return 1;
                }))
                .then(Commands.literal("balance").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ctx.getSource().sendSuccess(() -> Component.literal("Balance: " + Currency.balance(player)), false);
                    return 1;
                }))
                .then(Commands.literal("wallet")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            long wallet = com.society.sunlitmarket.wallet.Wallet.balance(player);
                            int cash = Currency.cashBalance(player);
                            String cashText = cash < 0 ? "不可用" : cash + " spur";
                            ctx.getSource().sendSuccess(() -> Component.literal("钱包 " + wallet + " spur · 现金/银行 "
                                    + cashText + " · 兑换手续费 "
                                    + com.society.sunlitmarket.wallet.WalletExchange.fee() + "%"), false);
                            return 1;
                        })
                        .then(Commands.literal("deposit").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String result = com.society.sunlitmarket.wallet.WalletExchange.deposit(player);
                            ctx.getSource().sendSuccess(() -> Component.literal(result), false);
                            return 1;
                        }))
                        .then(Commands.literal("withdraw")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String result = com.society.sunlitmarket.wallet.WalletExchange.withdrawAll(player);
                                    ctx.getSource().sendSuccess(() -> Component.literal(result), false);
                                    return 1;
                                })
                                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            String result = com.society.sunlitmarket.wallet.WalletExchange.withdraw(player,
                                                    LongArgumentType.getLong(ctx, "amount"));
                                            ctx.getSource().sendSuccess(() -> Component.literal(result), false);
                                            return 1;
                                        }))))
                .then(Commands.literal("reload").executes(ctx -> {
                    MarketRegistry.loadJson();
                    com.society.sunlitmarket.core.MarketRules.loadJson();
                    com.society.sunlitmarket.deposit.DepositConfig.load();
                    SunlitMarketConfig.invalidate();
                    FactionRelations.reload(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal("SunlitMarket config reloaded"), false);
                    return 1;
                }))
                .then(Commands.literal("unlock")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String id = StringArgumentType.getString(ctx, "id");
                                    UnlockData.get(player.server).grant(player, id);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Unlocked: " + id), false);
                                    return 1;
                                })
                                .then(Commands.literal("off").executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String id = StringArgumentType.getString(ctx, "id");
                                    UnlockData.get(player.server).revoke(player, id);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Locked: " + id), false);
                                    return 1;
                                }))))
                .then(Commands.literal("locks").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    String list = String.join(", ", UnlockData.get(player.server).list(player));
                    ctx.getSource().sendSuccess(() -> Component.literal("Unlocks: [" + list + "]"), false);
                    return 1;
                }))
                .then(Commands.literal("debug")
                        .then(Commands.argument("merchant", StringArgumentType.string())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    Merchant merchant = MarketRegistry.get(StringArgumentType.getString(ctx, "merchant"));
                                    if (merchant == null) {
                                        ctx.getSource().sendFailure(Component.literal("Unknown merchant"));
                                        return 0;
                                    }
                                    String json = MarketService.view(player, merchant).toJson();
                                    SunlitMarketMod.LOG.info("[SunlitMarket] view {}", json);
                                    ctx.getSource().sendSuccess(() -> Component.literal(json), false);
                                    return 1;
                                }))));
    }
}
