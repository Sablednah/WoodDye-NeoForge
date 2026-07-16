package com.sablednah.wooddye;

import com.sablednah.wooddye.core.LogOrder;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common configuration ({@code config/wooddye-common.toml}). Values are read live via {@code .get()},
 * so edits to the TOML (or via {@code /wooddye reload}) apply on save.
 */
public final class WoodDyeConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** Consume the dye / magma cream when used (never consumed in creative). */
    public static final ModConfigSpec.BooleanValue USE_ITEMS = BUILDER
            .comment("Consume the dye / magma cream when used. Never consumed in creative mode.")
            .define("useItems", false);

    /** Enable Magma Cream fireproofing (right-click wood with magma cream to make it fireproof). */
    public static final ModConfigSpec.BooleanValue FIREPROOF = BUILDER
            .comment("Enable Magma Cream fireproofing.")
            .define("fireProof", true);

    /** How dyeing steps through logs, whose bark and end-grain colours run in different orders. */
    public static final ModConfigSpec.EnumValue<LogOrder> LOG_ORDER = BUILDER
            .comment("Dye order for logs (bark vs end-grain colours differ):",
                    "  SAME_AS_PLANKS - by inner-wood colour (looks right on the ends)",
                    "  BARK           - by bark colour (looks right on the sides)",
                    "  INTELLIGENT    - bark order on side clicks, wood order on end clicks")
            .defineEnum("logOrder", LogOrder.INTELLIGENT);

    /** Play a particle + sound effect at the block when a treatment succeeds. */
    public static final ModConfigSpec.BooleanValue SHOW_EFFECTS = BUILDER
            .comment("Play a particle + sound effect when wood is dyed, fireproofed, or restored.")
            .define("showEffects", true);

    /** Show an action-bar message to the player when a treatment succeeds. */
    public static final ModConfigSpec.BooleanValue SHOW_MESSAGE = BUILDER
            .comment("Show an action-bar message to the player when a treatment succeeds.")
            .define("showMessage", true);

    /** The action-bar message. Supports '&' colour codes and %P (player name). */
    public static final ModConfigSpec.ConfigValue<String> MESSAGE = BUILDER
            .comment("Action-bar message shown on success. Supports '&' colour codes and %P (player name).")
            .define("message", "&aWood treated!");

    /** Extra logging. */
    public static final ModConfigSpec.BooleanValue DEBUG = BUILDER
            .comment("Enable extra debug logging.")
            .define("debugMode", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private WoodDyeConfig() {}
}
