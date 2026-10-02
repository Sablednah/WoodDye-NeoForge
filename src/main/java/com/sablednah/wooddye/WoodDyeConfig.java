package com.sablednah.wooddye;

import java.util.List;

import com.sablednah.wooddye.core.DyeOrder;
import com.sablednah.wooddye.core.LogOrder;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Common configuration ({@code config/wooddye-common.toml}). Values are read live via {@code .get()},
 * so edits to the TOML (or via {@code /wooddye reload}) apply on save.
 */
public final class WoodDyeConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    /** Consume the dye / magma cream when used in world (never consumed in creative). */
    public static final ForgeConfigSpec.BooleanValue USE_ITEMS = BUILDER
            .comment("Consume the dye / magma cream when treating a block in world. A Wet Sponge is",
                    "not used up — it only dries out, and can be re-soaked. Never consumed in",
                    "creative mode. Crafting recipes always use their ingredients regardless.")
            .define("useItems", false);

    /** Enable Magma Cream fireproofing, in world and on the crafting bench. */
    public static final ForgeConfigSpec.BooleanValue FIREPROOF = BUILDER
            .comment("Enable Magma Cream fireproofing, both in world and on the crafting bench.",
                    "Restoring wood with a Wet Sponge always stays possible, so wood that is already",
                    "fireproof is never stuck that way.")
            .define("fireProof", true);

    /** Whether the Wet Sponge dries out when used to restore fireproof wood in a crafting grid. */
    public static final ForgeConfigSpec.BooleanValue SPONGE_DRIES = BUILDER
            .comment("When a Wet Sponge restores fireproof wood in a crafting grid, the sponge is",
                    "handed back rather than consumed. Set true to hand back a dry Sponge instead,",
                    "so it must be re-soaked between batches.")
            .define("spongeDries", false);

    /** How dyeing steps through logs, whose bark and end-grain colours run in different orders. */
    public static final ForgeConfigSpec.EnumValue<LogOrder> LOG_ORDER = BUILDER
            .comment("Dye order for logs (bark vs end-grain colours differ):",
                    "  SAME_AS_PLANKS - by inner-wood colour (looks right on the ends)",
                    "  BARK           - by bark colour (looks right on the sides)",
                    "  INTELLIGENT    - bark order on side clicks, wood order on end clicks")
            .defineEnum("logOrder", LogOrder.INTELLIGENT);

    /** How the chain of woods a dye steps along is sorted. */
    public static final ForgeConfigSpec.EnumValue<DyeOrder> DYE_ORDER = BUILDER
            .comment("How woods are ordered for dyeing. The order is measured from each wood's texture.",
                    "  SHADE   - lightest to darkest",
                    "  RAINBOW - around the colour wheel; greyish woods come first, lightest to darkest",
                    "Run /wooddye woods to see the resulting order.")
            .defineEnum("dyeOrder", DyeOrder.SHADE);

    /** Let dyeing reach woods added by other mods. */
    public static final ForgeConfigSpec.BooleanValue MODDED_WOODS = BUILDER
            .comment("Include woods added by other mods in the dye order. Any mod that puts its blocks",
                    "in the standard tags (#minecraft:planks, #minecraft:wooden_slabs, ...) is picked up",
                    "automatically. Modded woods can be dyed but not yet fireproofed.")
            .define("moddedWoods", false);

    /** Let dyeing reach crimson and warped. */
    public static final ForgeConfigSpec.BooleanValue NETHER_WOODS = BUILDER
            .comment("Include crimson and warped (any wood tagged #minecraft:non_flammable_wood) in the",
                    "dye order. Off by default: in SHADE order teal warped planks land between acacia",
                    "and spruce, which surprises people. They suit RAINBOW order well.")
            .define("netherWoods", false);

    /** Woods, or whole mods, left out of dyeing. */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> EXCLUDED_WOODS = BUILDER
            .comment("Woods left out of the dye order. Each entry is a mod id, to leave out every wood",
                    "from that mod (\"biomesoplenty\"), or a single wood (\"biomesoplenty:fir\",",
                    "\"minecraft:bamboo\").")
            .defineListAllowEmpty("excludedWoods", List.of(), WoodDyeConfig::isString);

    /** Hand-set colours that replace a measured tone. */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> TONE_OVERRIDES = BUILDER
            .comment("Replace a measured colour, to move a wood within the dye order. Each entry is",
                    "\"<block id>=#rrggbb\". A planks block sets the wood's place in the plank order and",
                    "a log block its place in the bark order, e.g. \"minecraft:oak_log=#715834\".")
            .defineListAllowEmpty("toneOverrides", List.of(), WoodDyeConfig::isString);

    /** Play a particle + sound effect at the block when a treatment succeeds. */
    public static final ForgeConfigSpec.BooleanValue SHOW_EFFECTS = BUILDER
            .comment("Play a particle + sound effect when wood is dyed, fireproofed, or restored.")
            .define("showEffects", true);

    /** Show an action-bar message to the player when a treatment succeeds. */
    public static final ForgeConfigSpec.BooleanValue SHOW_MESSAGE = BUILDER
            .comment("Show an action-bar message to the player when a treatment succeeds.")
            .define("showMessage", true);

    /** The action-bar message. Supports '&' colour codes and %P (player name). */
    public static final ForgeConfigSpec.ConfigValue<String> MESSAGE = BUILDER
            .comment("Action-bar message shown on success. Supports '&' colour codes and %P (player name).")
            .define("message", "&aWood treated!");

    /** Extra logging. */
    public static final ForgeConfigSpec.BooleanValue DEBUG = BUILDER
            .comment("Enable extra debug logging.")
            .define("debugMode", false);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private WoodDyeConfig() {}

    private static boolean isString(Object element) {
        return element instanceof String;
    }
}
