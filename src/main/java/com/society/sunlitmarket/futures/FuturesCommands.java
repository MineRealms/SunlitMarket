package com.society.sunlitmarket.futures;

import com.google.gson.JsonElement;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * {@code /sunlitfutures}: the player interface until a futures screen exists. Every subcommand is
 * a thin wrapper over {@link FuturesService}; no permission level is required to trade.
 */
public final class FuturesCommands {
    private FuturesCommands() {
    }

    public static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("sunlitfutures")
                .then(Commands.literal("list")
                        .executes(c -> book(c, ""))
                        .then(Commands.argument("index", StringArgumentType.string()).executes(c -> book(c, StringArgumentType.getString(c, "index")))))
                .then(Commands.literal("offer").then(Commands.argument("index", StringArgumentType.string())
                        .then(side("long", true)).then(side("short", false))))
                .then(Commands.literal("take").then(Commands.argument("id", IntegerArgumentType.integer(1))
                        .then(Commands.argument("lots", IntegerArgumentType.integer(1)).executes(c -> reply(c,
                                FuturesService.take(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "id"),
                                        IntegerArgumentType.getInteger(c, "lots")))))))
                .then(Commands.literal("cancel").then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(c -> reply(c,
                        FuturesService.cancel(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "id"))))))
                .then(Commands.literal("mine").executes(c -> json(c, FuturesService.mine(c.getSource().getPlayerOrException()))))
                .then(Commands.literal("claim").executes(c -> {
                    String line = FuturesService.deliver(c.getSource().getPlayerOrException());
                    return reply(c, line.isEmpty() ? "没有待领的期货款" : line);
                })));
    }

    /** {@code /sunlitmarket futures ...} admin views, attached by CoreCommands. */
    public static LiteralArgumentBuilder<CommandSourceStack> admin() {
        return Commands.literal("futures")
                .then(Commands.literal("status").executes(c -> json(c, FuturesService.status(c.getSource().getServer()))))
                .then(Commands.literal("history").executes(c -> json(c, FuturesService.history(c.getSource().getServer()))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> side(String name, boolean isLong) {
        return Commands.literal(name).then(Commands.argument("lots", IntegerArgumentType.integer(1))
                .then(Commands.argument("price", IntegerArgumentType.integer(1)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    return reply(c, FuturesService.offer(p, StringArgumentType.getString(c, "index"), isLong,
                            IntegerArgumentType.getInteger(c, "lots"), IntegerArgumentType.getInteger(c, "price")));
                })));
    }

    private static int book(CommandContext<CommandSourceStack> c, String index) {
        return json(c, FuturesService.book(c.getSource().getServer(), index));
    }

    private static int json(CommandContext<CommandSourceStack> c, JsonElement json) {
        c.getSource().sendSuccess(() -> Component.literal(json.toString()), false);
        return 1;
    }

    private static int reply(CommandContext<CommandSourceStack> c, String line) {
        c.getSource().sendSuccess(() -> Component.literal(line), false);
        return 1;
    }
}
