package com.sablednah.wooddye.core;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/**
 * The overworld wood types WoodDye operates on, in <b>light &rarr; dark</b> shade order (the enum
 * order is the dye chain). Each wood is paired with its vanilla {@code WoodType} (for sounds and its
 * {@link BlockSetType}); the concrete vanilla blocks for each {@link Form} are resolved by name.
 */
public enum WoodType {
    PALE_OAK("pale_oak", net.minecraft.world.level.block.state.properties.WoodType.PALE_OAK),
    CHERRY("cherry", net.minecraft.world.level.block.state.properties.WoodType.CHERRY),
    BIRCH("birch", net.minecraft.world.level.block.state.properties.WoodType.BIRCH),
    BAMBOO("bamboo", net.minecraft.world.level.block.state.properties.WoodType.BAMBOO),
    OAK("oak", net.minecraft.world.level.block.state.properties.WoodType.OAK),
    JUNGLE("jungle", net.minecraft.world.level.block.state.properties.WoodType.JUNGLE),
    ACACIA("acacia", net.minecraft.world.level.block.state.properties.WoodType.ACACIA),
    SPRUCE("spruce", net.minecraft.world.level.block.state.properties.WoodType.SPRUCE),
    MANGROVE("mangrove", net.minecraft.world.level.block.state.properties.WoodType.MANGROVE),
    DARK_OAK("dark_oak", net.minecraft.world.level.block.state.properties.WoodType.DARK_OAK);

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
     */
    public enum Form {
        PLANKS("%s_planks", Kind.SIMPLE, true, false, Order.WOOD, Special.NONE),
        SLAB("%s_slab", Kind.SLAB, true, false, Order.WOOD, Special.NONE),
        STAIRS("%s_stairs", Kind.STAIRS, true, false, Order.WOOD, Special.NONE),
        LOG("%s_log", Kind.PILLAR, true, false, Order.LOG, Special.NONE),
        STRIPPED_LOG("stripped_%s_log", Kind.PILLAR, true, false, Order.WOOD, Special.NONE),
        WOOD("%s_wood", Kind.PILLAR, true, false, Order.BARK, Special.NONE),
        STRIPPED_WOOD("stripped_%s_wood", Kind.PILLAR, true, false, Order.WOOD, Special.NONE),
        FENCE("%s_fence", Kind.FENCE, true, false, Order.WOOD, Special.NONE),
        FENCE_GATE("%s_fence_gate", Kind.FENCE_GATE, true, true, Order.WOOD, Special.NONE),
        DOOR("%s_door", Kind.DOOR, true, true, Order.WOOD, Special.DOOR),
        TRAPDOOR("%s_trapdoor", Kind.TRAPDOOR, true, true, Order.WOOD, Special.NONE),
        PRESSURE_PLATE("%s_pressure_plate", Kind.PRESSURE_PLATE, true, false, Order.WOOD, Special.NONE),
        BUTTON("%s_button", Kind.BUTTON, true, true, Order.WOOD, Special.NONE),
        SIGN("%s_sign", Kind.NONE, false, true, Order.WOOD, Special.SIGN),
        WALL_SIGN("%s_wall_sign", Kind.NONE, false, true, Order.WOOD, Special.SIGN),
        HANGING_SIGN("%s_hanging_sign", Kind.NONE, false, true, Order.WOOD, Special.SIGN),
        WALL_HANGING_SIGN("%s_wall_hanging_sign", Kind.NONE, false, true, Order.WOOD, Special.SIGN),
        SHELF("%s_shelf", Kind.NONE, false, true, Order.WOOD, Special.SHELF);

        private final String template;
        private final Kind kind;
        private final boolean fireproof;
        private final boolean interactive;
        private final Order order;
        private final Special special;

        Form(String template, Kind kind, boolean fireproof, boolean interactive, Order order, Special special) {
            this.template = template;
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
    private final net.minecraft.world.level.block.state.properties.WoodType mojang;

    WoodType(String key, net.minecraft.world.level.block.state.properties.WoodType mojang) {
        this.key = key;
        this.mojang = mojang;
    }

    /** Registry-path key, e.g. {@code "dark_oak"}. */
    public String key() {
        return key;
    }

    /** The vanilla {@code WoodType} (sounds), used for fence gates. */
    public net.minecraft.world.level.block.state.properties.WoodType mojangWoodType() {
        return mojang;
    }

    /** The vanilla {@link BlockSetType} (sounds/activation), used for doors, trapdoors, buttons, plates. */
    public BlockSetType blockSetType() {
        return mojang.setType();
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
