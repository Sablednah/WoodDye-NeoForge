package com.sablednah.wooddye.neoforge;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.sablednah.wooddye.WoodDyeConfig;
import com.sablednah.wooddye.neoforge.WoodFamilies.Family;
import com.sablednah.wooddye.neoforge.WoodFamilies.Measured;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

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
                        .executes(WoodDyeCommands::showcase)));
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
