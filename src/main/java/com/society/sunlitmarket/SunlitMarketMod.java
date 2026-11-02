package com.society.sunlitmarket;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.society.sunlitmarket.alignment.AlignmentData;
import com.society.sunlitmarket.alignment.FactionRelations;
import com.society.sunlitmarket.config.SunlitMarketConfig;
import com.society.sunlitmarket.currency.Currency;
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
    private static final String PROTOCOL = "2";
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
        com.society.sunlitmarket.core.CoreEvents.register(bus);
        bus.addListener(this::onCommonSetup);
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(this::onCommands);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, this::onEntityInteract);
        MinecraftForge.EVENT_BUS.addListener(this::onEntityJoin);
        MinecraftForge.EVENT_BUS.addListener(this::onChangeTarget);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> { com.society.sunlitmarket.client.MarketClient.init(); com.society.sunlitmarket.client.CounterClient.init(); });
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
        LOG.info("[SunlitMarket] ready (numismatics={})", ModList.get().isLoaded("numismatics"));
    }

    private void onServerStarted(ServerStartedEvent event) {
        MarketRegistry.loadJson();
        com.society.sunlitmarket.core.MarketRules.loadJson();
        FactionRelations.reload(event.getServer());
        lastAlignmentDay = Long.MIN_VALUE;
        com.society.sunlitmarket.market.TradeSessions.clear();
    }

    /** Once per in-game day: drift alignment toward zero and cool down wanted level. */
    private void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;
        long day = server.overworld().getDayTime() / 24000L;
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

    private void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sunlitmarket")
                .requires(source -> source.hasPermission(2))
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
                .then(Commands.literal("reload").executes(ctx -> {
                    MarketRegistry.loadJson();
                    com.society.sunlitmarket.core.MarketRules.loadJson();
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
