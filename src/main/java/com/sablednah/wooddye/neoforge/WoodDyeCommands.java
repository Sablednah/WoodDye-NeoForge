package com.sablednah.wooddye.neoforge;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sablednah.wooddye.WoodDyeConfig;
import com.sablednah.wooddye.fireproof.Fireproofing;
import com.sablednah.wooddye.neoforge.WoodFamilies.Family;
import com.sablednah.wooddye.neoforge.WoodFamilies.Measured;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * The {@code /wooddye} command tree.
 *
 * <ul>
 *     <li>{@code /wooddye reload} — op (LEVEL_GAMEMASTERS). Config is read live, so this mainly
 *         acknowledges; edits to {@code wooddye-common.toml} apply on save regardless.</li>
 *     <li>{@code /wooddye woods} — op. Lists the woods in dye order, with how each one's place was
 *         worked out, so an owner can see why a modded wood landed where it did.</li>
 *     <li>{@code /wooddye showcase} — op. Builds every dyeable wood side by side in dye order at
 *         the caller's position (see {@link WoodShowcase}), and says where to stand to see it.</li>
 * </ul>
 */
public final class WoodDyeCommands {

    private WoodDyeCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wooddye")
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(WoodDyeCommands::reload))
                .then(Commands.literal("woods")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(WoodDyeCommands::woods))
                .then(Commands.literal("showcase")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(WoodDyeCommands::showcase))
                .then(Commands.literal("fireproof")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(WoodDyeCommands::fireproofCount)
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> fireproof(ctx, null))
                                .then(Commands.literal("set").executes(ctx -> fireproof(ctx, true)))
                                .then(Commands.literal("clear").executes(ctx -> fireproof(ctx, false))))));
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        WoodTransforms.invalidate();
        ctx.getSource().sendSuccess(
                () -> Component.literal("WoodDye configuration reloaded (settings apply live on save)."), true);
        return 1;
    }

    private static int woods(CommandContext<CommandSourceStack> ctx) {
        List<Family> wood = WoodTransforms.woodOrder();
        String order = WoodDyeConfig.DYE_ORDER.get().name();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "WoodDye: " + wood.size() + " woods, " + order + " order.\n"
                        + "Planks: " + describe(wood, Family::wood) + "\n"
                        + "Bark: " + describe(WoodTransforms.barkOrder(), Family::bark)), false);
        return wood.size();
    }

    private static int showcase(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        WoodShowcase.Result built = WoodShowcase.build(source.getLevel(), BlockPos.containing(source.getPosition()));
        source.sendSuccess(() -> Component.literal(
                "WoodDye: built a showcase of " + built.woods() + " woods. See it all from: " + built.viewpoint()), true);
        return built.woods();
    }

    private static int fireproofCount(CommandContext<CommandSourceStack> ctx) {
        int count = Fireproofing.count(ctx.getSource().getLevel());
        ctx.getSource().sendSuccess(() -> Component.literal("WoodDye: " + count + " fireproof positions in this dimension."), false);
        return count;
    }

    /** Report, set or clear the fireproofing of one position: an admin tool, and how tests ask. */
    private static int fireproof(CommandContext<CommandSourceStack> ctx, Boolean set) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        if (set != null) {
            if (set && !Fireproofing.canMark(level.getBlockState(pos).getBlock())) {
                ctx.getSource().sendFailure(Component.literal("WoodDye: " + pos.toShortString() + " is not wood that can be fireproofed."));
                return 0;
            }
            if (set) {
                Fireproofing.mark(level, pos);
            } else {
                Fireproofing.unmark(level, pos);
            }
        }
        boolean fireproof = Fireproofing.isFireproof(level, pos);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "WoodDye: " + pos.toShortString() + " is " + (fireproof ? "fireproof" : "not fireproof") + "."), set != null);
        return fireproof ? 1 : 0;
    }

    /** One line per chain: each wood with its lightness, and a marker when it was not measured. */
    private static String describe(List<Family> order, Function<Family, Measured> face) {
        return order.stream().map(family -> {
            Measured measured = face.apply(family);
            String note = switch (measured.source()) {
                case OVERRIDE -> " (set in config)";
                case MAP_COLOUR -> " (from map colour)";
                default -> "";
            };
            return String.format(Locale.ROOT, "%s %.2f%s", family.label(), measured.tone().lightness(), note);
        }).collect(Collectors.joining(" > "));
    }
}
