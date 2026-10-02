package com.sablednah.wooddye.core;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/**
 * The vanilla woods WoodDye registers {@code fireproof_*} blocks for. This is <b>only</b> the
 * fireproofing list: which woods can be <em>dyed</em>, and in what order, is discovered at runtime
 * from block tags and measured tones (see {@code neoforge/WoodFamilies}), so declaration order here
 * means nothing.
 *
 * <p>The list names every wood in any supported Minecraft version. A wood this version lacks is
 * simply not {@linkplain #exists() present} and is skipped, and its vanilla {@code WoodType} is found
 * by name rather than by field, so this file compiles unchanged on every version branch.
 *
 * <p>Crimson and warped are absent on purpose: vanilla already makes them non-flammable.
 */
public enum WoodType {
    PALE_OAK("pale_oak"),
    CHERRY("cherry"),
    BIRCH("birch"),
    BAMBOO("bamboo"),
    POPLAR("poplar"),
    OAK("oak"),
    JUNGLE("jungle"),
    ACACIA("acacia"),
    SPRUCE("spruce"),
    MANGROVE("mangrove"),
    DARK_OAK("dark_oak");

    /** Registration factory family for a {@link Form}. */
    public enum Kind { SIMPLE, PILLAR, SLAB, STAIRS, FENCE, FENCE_GATE, DOOR, TRAPDOOR, PRESSURE_PLATE, BUTTON, NONE }

    /** Which shade chain a form dyes along. */
    public enum Order { WOOD, BARK, LOG }

    /** Special in-world handling a form needs when dyed. */
    public enum Special { NONE, DOOR, SIGN, SHELF }

    /**
     * A wooden object family. {@code fireproof} forms get a registered {@code fireproof_*} block;
     * {@code interactive} forms (that do something on a plain right-click) require sneak+right-click
     * to dye so normal use still works.
     *
     * <p>Each form is found in the world through a {@code wooddye:dyeable/*} block tag. The four
     * pillar forms share one tag, because vanilla's {@code #minecraft:logs} does not tell a log from
     * its stripped or all-bark variants; {@link #ofLog} sorts those out by name.
     */
    public enum Form {
        PLANKS("%s_planks", "planks", Kind.SIMPLE, true, false, Order.WOOD, Special.NONE),
        SLAB("%s_slab", "slabs", Kind.SLAB, true, false, Order.WOOD, Special.NONE),
        STAIRS("%s_stairs", "stairs", Kind.STAIRS, true, false, Order.WOOD, Special.NONE),
        LOG("%s_log", "logs", Kind.PILLAR, true, false, Order.LOG, Special.NONE),
        STRIPPED_LOG("stripped_%s_log", "logs", Kind.PILLAR, true, false, Order.WOOD, Special.NONE),
        WOOD("%s_wood", "logs", Kind.PILLAR, true, false, Order.BARK, Special.NONE),
        STRIPPED_WOOD("stripped_%s_wood", "logs", Kind.PILLAR, true, false, Order.WOOD, Special.NONE),
        FENCE("%s_fence", "fences", Kind.FENCE, true, false, Order.WOOD, Special.NONE),
        FENCE_GATE("%s_fence_gate", "fence_gates", Kind.FENCE_GATE, true, true, Order.WOOD, Special.NONE),
        DOOR("%s_door", "doors", Kind.DOOR, true, true, Order.WOOD, Special.DOOR),
        TRAPDOOR("%s_trapdoor", "trapdoors", Kind.TRAPDOOR, true, true, Order.WOOD, Special.NONE),
        PRESSURE_PLATE("%s_pressure_plate", "pressure_plates", Kind.PRESSURE_PLATE, true, false, Order.WOOD, Special.NONE),
        BUTTON("%s_button", "buttons", Kind.BUTTON, true, true, Order.WOOD, Special.NONE),
        SIGN("%s_sign", "signs", Kind.NONE, false, true, Order.WOOD, Special.SIGN),
        WALL_SIGN("%s_wall_sign", "wall_signs", Kind.NONE, false, true, Order.WOOD, Special.SIGN),
        HANGING_SIGN("%s_hanging_sign", "hanging_signs", Kind.NONE, false, true, Order.WOOD, Special.SIGN),
        WALL_HANGING_SIGN("%s_wall_hanging_sign", "wall_hanging_signs", Kind.NONE, false, true, Order.WOOD, Special.SIGN),
        SHELF("%s_shelf", "shelves", Kind.NONE, false, true, Order.WOOD, Special.SHELF);

        /** The tag path shared by the four pillar forms. */
        public static final String LOGS_TAG = "logs";

        /** Pillar name endings: a log by any of its vanilla names, then the all-bark variants. */
        private static final String[] LOG_SUFFIXES = { "_log", "_stem", "_block" };
        private static final String[] BARK_SUFFIXES = { "_wood", "_hyphae" };

        private final String template;
        private final String tag;
        private final Kind kind;
        private final boolean fireproof;
        private final boolean interactive;
        private final Order order;
        private final Special special;

        Form(String template, String tag, Kind kind, boolean fireproof, boolean interactive, Order order,
                Special special) {
            this.template = template;
            this.tag = tag;
            this.kind = kind;
            this.fireproof = fireproof;
            this.interactive = interactive;
            this.order = order;
            this.special = special;
        }

        public Kind kind() { return kind; }

        public boolean fireproof() { return fireproof; }

        public boolean interactive() { return interactive; }

        public Order order() { return order; }

        public Special special() { return special; }

        /** Path of the {@code wooddye:dyeable/*} block tag that lists this form's blocks. */
        public String tag() { return tag; }

        /** Whether this is one of the four pillar forms, which share {@link #LOGS_TAG}. */
        public boolean pillar() { return kind == Kind.PILLAR; }

        /**
         * The wood a block of this form belongs to, read off its registry path: {@code oak} for
         * {@code oak_fence_gate}. Returns {@code null} if the path does not follow the form's naming,
         * in which case there is no telling which wood it is and the block is left alone.
         *
         * <p>Not for pillar forms; use {@link #ofLog} and {@link #logStem}.
         */
        public String stem(String path) {
            String suffix = template.substring(2); // drop the leading "%s"
            return path.endsWith(suffix) && path.length() > suffix.length()
                    ? path.substring(0, path.length() - suffix.length())
                    : null;
        }

        /**
         * Which pillar form a member of the logs tag is, by name: stripped or not, and log
         * ({@code _log}, {@code _stem}, bamboo's {@code _block}) or all-bark ({@code _wood},
         * {@code _hyphae}). Returns {@code null} for a name that is neither.
         */
        public static Form ofLog(String path) {
            boolean stripped = path.startsWith("stripped_");
            if (endsWithAny(path, BARK_SUFFIXES) != null) {
                return stripped ? STRIPPED_WOOD : WOOD;
            }
            if (endsWithAny(path, LOG_SUFFIXES) != null) {
                return stripped ? STRIPPED_LOG : LOG;
            }
            return null;
        }

        /** The wood a pillar block belongs to: {@code warped} for {@code stripped_warped_hyphae}. */
        public static String logStem(String path) {
            String bare = path.startsWith("stripped_") ? path.substring("stripped_".length()) : path;
            String suffix = endsWithAny(bare, BARK_SUFFIXES);
            if (suffix == null) {
                suffix = endsWithAny(bare, LOG_SUFFIXES);
            }
            return suffix == null || bare.length() == suffix.length()
                    ? null
                    : bare.substring(0, bare.length() - suffix.length());
        }

        private static String endsWithAny(String path, String[] suffixes) {
            for (String suffix : suffixes) {
                if (path.endsWith(suffix)) {
                    return suffix;
                }
            }
            return null;
        }

        /** The vanilla registry path for this form of {@code wood}, or {@code null} if none exists. */
        public String vanillaName(WoodType wood) {
            if (wood == BAMBOO) {
                // Bamboo's pillar is bamboo_block; it has no "wood" (all-bark) variant.
                switch (this) {
                    case LOG -> { return "bamboo_block"; }
                    case STRIPPED_LOG -> { return "stripped_bamboo_block"; }
                    case WOOD, STRIPPED_WOOD -> { return null; }
                    default -> { }
                }
            }
            return template.formatted(wood.key());
        }
    }

    private final String key;

    WoodType(String key) {
        this.key = key;
    }

    /** Registry-path key, e.g. {@code "dark_oak"}. */
    public String key() {
        return key;
    }

    /** Whether this Minecraft version has the wood at all (pale oak and poplar arrived late). */
    public boolean exists() {
        return vanilla(Form.PLANKS) != null;
    }

    /** The vanilla {@code WoodType} (sounds), used for fence gates. */
    public net.minecraft.world.level.block.state.properties.WoodType mojangWoodType() {
        return net.minecraft.world.level.block.state.properties.WoodType.values()
                .filter(type -> type.name().equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No vanilla wood type named " + key));
    }

    /** The vanilla {@link BlockSetType} (sounds/activation), used for doors, trapdoors, buttons, plates. */
    public BlockSetType blockSetType() {
        return mojangWoodType().setType();
    }

    /** The vanilla block for the given form, or {@code null} if this wood lacks it (e.g. bamboo wood). */
    public Block vanilla(Form form) {
        String name = form.vanillaName(this);
        if (name == null) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(name));
        return block == Blocks.AIR ? null : block;
    }
}
