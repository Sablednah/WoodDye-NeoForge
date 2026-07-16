package com.sablednah.wooddye.neoforge;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.sablednah.wooddye.core.WoodType;
import com.sablednah.wooddye.registry.WoodDyeBlocks;

import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * Lookup tables that drive every WoodDye transform, built once from the {@link WoodType} order.
 *
 * <p>Each wooden {@link WoodType.Form} dyes along a light&rarr;dark chain. Most forms use the plank
 * (inner-wood) order; unstripped logs and all-bark "wood" blocks additionally get a chain in bark
 * colour order (see {@link com.sablednah.wooddye.core.LogOrder}). Chains are built for both vanilla
 * and fireproof blocks. Separate maps convert vanilla&harr;fireproof, and block sets record which
 * blocks need sneak-to-dye or special in-world handling (doors, signs, shelves).
 *
 * <p>Initialised lazily on first use, by when all fireproof blocks are registered.
 */
public final class WoodTransforms {

    /** Which way along a light&rarr;dark chain a dye moves a block. */
    public enum Shift { LIGHTEN, DARKEN }

    /** Plank/inner-wood colour order (lightest first) — the enum declaration order. */
    private static final WoodType[] WOOD_ORDER = WoodType.values();

    /** Bark colour order (lightest first). */
    private static final WoodType[] BARK_ORDER = {
            WoodType.BIRCH, WoodType.ACACIA, WoodType.PALE_OAK, WoodType.BAMBOO, WoodType.OAK,
            WoodType.JUNGLE, WoodType.MANGROVE, WoodType.DARK_OAK, WoodType.SPRUCE, WoodType.CHERRY,
    };

    private static final Map<Block, Block> WOOD_LIGHTER = new HashMap<>();
    private static final Map<Block, Block> WOOD_DARKER = new HashMap<>();
    private static final Map<Block, Block> BARK_LIGHTER = new HashMap<>();
    private static final Map<Block, Block> BARK_DARKER = new HashMap<>();

    private static final Set<Block> BARK_CAPABLE = new HashSet<>(); // unstripped logs + all-bark wood
    private static final Set<Block> ALL_BARK = new HashSet<>();      // all-bark wood only
    private static final Set<Block> SNEAK_REQUIRED = new HashSet<>();
    private static final Set<Block> DOORS = new HashSet<>();
    private static final Set<Block> SIGNS = new HashSet<>();
    private static final Set<Block> SHELVES = new HashSet<>();

    private static final Map<Block, Block> TO_FIREPROOF = new HashMap<>();
    private static final Map<Block, Block> FROM_FIREPROOF = new HashMap<>();

    private static boolean initialised = false;

    private WoodTransforms() {}

    private static synchronized void init() {
        if (initialised) {
            return;
        }
        for (WoodType.Form form : WoodType.Form.values()) {
            // Wood-order chain for every form (vanilla + fireproof).
            buildChain(chain(WOOD_ORDER, form, false), WOOD_LIGHTER, WOOD_DARKER);
            if (form.fireproof()) {
                buildChain(chain(WOOD_ORDER, form, true), WOOD_LIGHTER, WOOD_DARKER);
            }
            // Bark-order chain only for the bark-capable forms (unstripped logs + all-bark wood).
            if (isBarkForm(form)) {
                buildChain(chain(BARK_ORDER, form, false), BARK_LIGHTER, BARK_DARKER);
                if (form.fireproof()) {
                    buildChain(chain(BARK_ORDER, form, true), BARK_LIGHTER, BARK_DARKER);
                }
            }
            for (WoodType wood : WOOD_ORDER) {
                Block vanilla = wood.vanilla(form);
                Block fireproof = form.fireproof() ? fireproof(wood, form) : null;
                classify(form, vanilla);
                classify(form, fireproof);
                if (vanilla != null && fireproof != null) {
                    TO_FIREPROOF.put(vanilla, fireproof);
                    FROM_FIREPROOF.put(fireproof, vanilla);
                }
            }
        }
        initialised = true;
    }

    private static boolean isBarkForm(WoodType.Form form) {
        return form.order() == WoodType.Order.LOG || form.order() == WoodType.Order.BARK;
    }

    private static void classify(WoodType.Form form, Block block) {
        if (block == null) {
            return;
        }
        if (isBarkForm(form)) {
            BARK_CAPABLE.add(block);
        }
        if (form.order() == WoodType.Order.BARK) {
            ALL_BARK.add(block);
        }
        if (form.interactive()) {
            SNEAK_REQUIRED.add(block);
        }
        switch (form.special()) {
            case DOOR -> DOORS.add(block);
            case SIGN -> SIGNS.add(block);
            case SHELF -> SHELVES.add(block);
            default -> { }
        }
    }

    private static void buildChain(Block[] ordered, Map<Block, Block> lighter, Map<Block, Block> darker) {
        for (int i = 0; i < ordered.length - 1; i++) {
            darker.put(ordered[i], ordered[i + 1]);
            lighter.put(ordered[i + 1], ordered[i]);
        }
    }

    private static Block[] chain(WoodType[] order, WoodType.Form form, boolean fireproof) {
        return Arrays.stream(order)
                .map(wood -> fireproof ? fireproof(wood, form) : wood.vanilla(form))
                .filter(Objects::nonNull)
                .toArray(Block[]::new);
    }

    private static Block fireproof(WoodType wood, WoodType.Form form) {
        DeferredBlock<? extends Block> holder = WoodDyeBlocks.get(form, wood);
        return holder == null ? null : holder.get();
    }

    // --- queries used by the interaction handler ---

    /** Whether the block can dye along the bark order (unstripped log or all-bark wood). */
    public static boolean isBarkCapable(Block block) {
        init();
        return BARK_CAPABLE.contains(block);
    }

    /** Whether the block is an all-bark "wood" block (every face bark, no end grain). */
    public static boolean isAllBarkWood(Block block) {
        init();
        return ALL_BARK.contains(block);
    }

    /** Whether dyeing this block requires sneaking (it has a vanilla right-click action). */
    public static boolean requiresSneak(Block block) {
        init();
        return SNEAK_REQUIRED.contains(block);
    }

    public static boolean isDoor(Block block) {
        init();
        return DOORS.contains(block);
    }

    public static boolean isSign(Block block) {
        init();
        return SIGNS.contains(block);
    }

    public static boolean isShelf(Block block) {
        init();
        return SHELVES.contains(block);
    }

    /** The next block one shade lighter/darker, in the wood order (or bark order when {@code bark}). */
    public static Block shade(Block block, Shift shift, boolean bark) {
        init();
        Map<Block, Block> map = bark
                ? (shift == Shift.LIGHTEN ? BARK_LIGHTER : BARK_DARKER)
                : (shift == Shift.LIGHTEN ? WOOD_LIGHTER : WOOD_DARKER);
        return map.get(block);
    }

    /** The fireproof counterpart of a vanilla wood block, or {@code null} if not applicable. */
    public static Block toFireproof(Block block) {
        init();
        return TO_FIREPROOF.get(block);
    }

    /** The plain vanilla counterpart of a fireproof block, or {@code null} if not applicable. */
    public static Block fromFireproof(Block block) {
        init();
        return FROM_FIREPROOF.get(block);
    }

}
