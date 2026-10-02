package com.sablednah.wooddye.neoforge;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.sablednah.wooddye.WoodDye;
import com.sablednah.wooddye.WoodDyeConfig;
import com.sablednah.wooddye.core.DyeOrder;
import com.sablednah.wooddye.core.WoodType;
import com.sablednah.wooddye.core.WoodType.Form;
import com.sablednah.wooddye.neoforge.WoodFamilies.Family;
import com.sablednah.wooddye.registry.WoodDyeBlocks;

import net.minecraft.world.level.block.Block;

/**
 * Lookup tables that drive every WoodDye transform.
 *
 * <p>Each wooden {@link Form} dyes along a chain of woods. The chain is the {@linkplain WoodFamilies
 * wood families found in the block tags}, sorted by measured tone in the configured {@link DyeOrder}.
 * Most forms follow the inner-wood order; unstripped logs and all-bark "wood" blocks additionally
 * get a chain in bark order (see {@link com.sablednah.wooddye.core.LogOrder}). Each chain exists
 * twice, once for plain blocks and once for their fireproof counterparts, so dyeing never changes
 * whether a block is fireproof. Separate maps convert plain&harr;fireproof, and block sets record
 * which blocks need sneak-to-dye or special in-world handling (doors, signs, shelves).
 *
 * <p>The tables depend on tags and config, both of which can change while the server runs, so they
 * are built on first use and thrown away by {@link #invalidate()} whenever either reloads.
 */
public final class WoodTransforms {

    /** Which way along a chain a dye moves a block. */
    public enum Shift { LIGHTEN, DARKEN }

    /** One immutable build of every table; swapped whole so a reader never sees a half-built set. */
    private static final class Tables {
        final Map<Block, Block> woodLighter = new HashMap<>();
        final Map<Block, Block> woodDarker = new HashMap<>();
        final Map<Block, Block> barkLighter = new HashMap<>();
        final Map<Block, Block> barkDarker = new HashMap<>();

        final Set<Block> barkCapable = new HashSet<>(); // unstripped logs + all-bark wood
        final Set<Block> allBark = new HashSet<>();      // all-bark wood only
        final Set<Block> sneakRequired = new HashSet<>();
        final Set<Block> doors = new HashSet<>();
        final Set<Block> signs = new HashSet<>();
        final Set<Block> shelves = new HashSet<>();

        final Map<Block, Block> toFireproof = new HashMap<>();
        final Map<Block, Block> fromFireproof = new HashMap<>();

        /** Every block WoodDye knows to be wood: all forms of all tagged families, plus the legacy fireproof blocks. */
        final Set<Block> wood = new HashSet<>();

        List<Family> woodOrder = List.of();
        List<Family> barkOrder = List.of();
    }

    private static volatile Tables tables;

    private WoodTransforms() {}

    /** Drop the tables; the next query rebuilds them. Call when tags or config change. */
    public static void invalidate() {
        tables = null;
    }

    private static Tables tables() {
        Tables current = tables;
        if (current == null) {
            synchronized (WoodTransforms.class) {
                current = tables;
                if (current == null) {
                    current = build();
                    tables = current;
                }
            }
        }
        return current;
    }

    private static Tables build() {
        Tables t = new Tables();

        // Plain <-> fireproof pairs come from our own registry, not from tags: they exist for
        // exactly the vanilla blocks we registered a counterpart for.
        for (WoodDyeBlocks.Entry entry : WoodDyeBlocks.ALL) {
            Block vanilla = entry.wood().vanilla(entry.form());
            Block fireproof = entry.block().get();
            t.toFireproof.put(vanilla, fireproof);
            t.fromFireproof.put(fireproof, vanilla);
            classify(t, entry.form(), vanilla);
            classify(t, entry.form(), fireproof);
        }

        // What each tagged block is matters even for a wood that may not be dyed, so that (say) a
        // sneak is still needed to fireproof its door. Classify everything before filtering.
        Map<String, Map<Form, Block>> discovered = WoodFamilies.discover();
        for (Map<Form, Block> blocks : discovered.values()) {
            blocks.forEach((form, block) -> classify(t, form, block));
        }

        List<Family> families = WoodFamilies.dyeable(discovered);
        DyeOrder order = WoodDyeConfig.DYE_ORDER.get();
        t.woodOrder = order.sort(families, family -> family.wood().tone(), Family::id);
        t.barkOrder = order.sort(families, family -> family.bark().tone(), Family::id);

        for (Form form : Form.values()) {
            link(t, chain(t.woodOrder, form), t.woodLighter, t.woodDarker);
            if (isBarkForm(form)) {
                link(t, chain(t.barkOrder, form), t.barkLighter, t.barkDarker);
            }
        }

        WoodDye.LOGGER.info("WoodDye: {} wood families, {} order. Planks: {}. Bark: {}.",
                families.size(), order, describe(t.woodOrder), describe(t.barkOrder));
        return t;
    }

    private static String describe(List<Family> order) {
        return order.stream().map(Family::label).collect(Collectors.joining(" > "));
    }

    private static boolean isBarkForm(Form form) {
        return form.order() == WoodType.Order.LOG || form.order() == WoodType.Order.BARK;
    }

    private static void classify(Tables t, Form form, Block block) {
        t.wood.add(block);
        if (isBarkForm(form)) {
            t.barkCapable.add(block);
        }
        if (form.order() == WoodType.Order.BARK) {
            t.allBark.add(block);
        }
        if (form.interactive()) {
            t.sneakRequired.add(block);
        }
        switch (form.special()) {
            case DOOR -> t.doors.add(block);
            case SIGN -> t.signs.add(block);
            case SHELF -> t.shelves.add(block);
            default -> { }
        }
    }

    /** The blocks of one form along an order, skipping woods that lack the form (bamboo has no wood). */
    private static List<Block> chain(List<Family> order, Form form) {
        return order.stream().map(family -> family.block(form)).filter(Objects::nonNull).toList();
    }

    /**
     * Record each block's lighter and darker neighbour, then do the same for the fireproof
     * counterparts of the same chain. A wood with no fireproof version (any modded wood, for now) is
     * skipped in the fireproof chain rather than breaking it, so fireproof oak still dyes to
     * fireproof spruce across a modded wood that sits between them.
     */
    private static void link(Tables t, List<Block> plain, Map<Block, Block> lighter, Map<Block, Block> darker) {
        linkChain(plain, lighter, darker);
        linkChain(plain.stream().map(t.toFireproof::get).filter(Objects::nonNull).toList(), lighter, darker);
    }

    private static void linkChain(List<Block> ordered, Map<Block, Block> lighter, Map<Block, Block> darker) {
        for (int i = 0; i < ordered.size() - 1; i++) {
            darker.put(ordered.get(i), ordered.get(i + 1));
            lighter.put(ordered.get(i + 1), ordered.get(i));
        }
    }

    // --- queries used by the interaction handler ---

    /** Whether the block can dye along the bark order (unstripped log or all-bark wood). */
    public static boolean isBarkCapable(Block block) {
        return tables().barkCapable.contains(block);
    }

    /** Whether the block is an all-bark "wood" block (every face bark, no end grain). */
    public static boolean isAllBarkWood(Block block) {
        return tables().allBark.contains(block);
    }

    /** Whether dyeing this block requires sneaking (it has a vanilla right-click action). */
    public static boolean requiresSneak(Block block) {
        return tables().sneakRequired.contains(block);
    }

    public static boolean isDoor(Block block) {
        return tables().doors.contains(block);
    }

    public static boolean isSign(Block block) {
        return tables().signs.contains(block);
    }

    public static boolean isShelf(Block block) {
        return tables().shelves.contains(block);
    }

    /** The next block one step lighter/darker, in the wood order (or bark order when {@code bark}). */
    public static Block shade(Block block, Shift shift, boolean bark) {
        Tables t = tables();
        Map<Block, Block> map = bark
                ? (shift == Shift.LIGHTEN ? t.barkLighter : t.barkDarker)
                : (shift == Shift.LIGHTEN ? t.woodLighter : t.woodDarker);
        return map.get(block);
    }

    /** Whether WoodDye knows this block as wood of any form, in any family, dyeable or not. */
    public static boolean isWood(Block block) {
        return tables().wood.contains(block);
    }

    /** Whether this is one of the registered legacy {@code fireproof_*} blocks. */
    public static boolean isLegacyFireproof(Block block) {
        return tables().fromFireproof.containsKey(block);
    }

    /** The fireproof counterpart of a vanilla wood block, or {@code null} if not applicable. */
    public static Block toFireproof(Block block) {
        return tables().toFireproof.get(block);
    }

    /** The plain vanilla counterpart of a fireproof block, or {@code null} if not applicable. */
    public static Block fromFireproof(Block block) {
        return tables().fromFireproof.get(block);
    }

    /** The dyeable woods in inner-wood order, for {@code /wooddye woods}. */
    public static List<Family> woodOrder() {
        return tables().woodOrder;
    }

    /** The dyeable woods in bark order, for {@code /wooddye woods}. */
    public static List<Family> barkOrder() {
        return tables().barkOrder;
    }
}
