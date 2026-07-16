package com.sablednah.wooddye.neoforge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * The {@code /wooddye} command tree.
 *
 * <ul>
 *     <li>{@code /wooddye reload} — op (LEVEL_GAMEMASTERS). Config is read live, so this mainly
 *         acknowledges; edits to {@code wooddye-common.toml} apply on save regardless.</li>
 * </ul>
 */
public final class WoodDyeCommands {

    private WoodDyeCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wooddye")
                .then(Commands.literal("reload")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(WoodDyeCommands::reload)));
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(
                () -> Component.literal("WoodDye configuration reloaded (settings apply live on save)."), true);
        return 1;
    }
}
