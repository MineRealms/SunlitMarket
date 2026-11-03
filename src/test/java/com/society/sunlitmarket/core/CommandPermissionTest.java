package com.society.sunlitmarket.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The admin subtree shares the {@code sunlitmarket} literal with player commands, and Brigadier
 * keeps only the first registered root's requirement when it merges the two. These tests pin the
 * consequence: every admin node gates itself, whichever tree registers first.
 */
class CommandPermissionTest {
    private static final List<String> ADMIN_CHILDREN = List.of("team", "wantedscope", "bankunlock", "identity",
            "owner", "recovercounter", "audit", "market", "futures", "treasury", "selftest");

    private static CommandSourceStack source(int permission) {
        return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, permission, "test",
                Component.literal("test"), null, null);
    }

    /** Stand-in for SunlitMarketMod's open root with one player subcommand. */
    private static LiteralArgumentBuilder<CommandSourceStack> playerTree() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("sunlitmarket")
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("consign").executes(c -> 1));
    }

    private static void assertGated(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("sunlitmarket");
        assertTrue(root.canUse(source(0)), "players must reach the root");
        assertTrue(root.getChild("consign").canUse(source(0)), "player subcommands stay open");
        for (String name : ADMIN_CHILDREN) {
            CommandNode<CommandSourceStack> child = root.getChild(name);
            assertNotNull(child, name);
            assertFalse(child.canUse(source(0)), name + " must not be usable at permission 0");
            assertTrue(child.canUse(source(2)), name + " must stay usable for operators");
        }
    }

    @Test void adminNodesGateThemselvesWhenTheyRegisterFirst() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        CoreCommands.build(dispatcher);
        dispatcher.register(playerTree());
        assertGated(dispatcher);
    }

    @Test void adminNodesGateThemselvesWhenTheyRegisterSecond() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(playerTree());
        CoreCommands.build(dispatcher);
        assertGated(dispatcher);
    }

    @Test void playerRootsStayOpen() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        CoreCommands.build(dispatcher);
        assertTrue(dispatcher.getRoot().getChild("sunlitbribe").canUse(source(0)));
        assertTrue(dispatcher.getRoot().getChild("sunlitalignment").canUse(source(0)));
    }
}
