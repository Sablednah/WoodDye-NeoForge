package com.sablednah.wooddye.fireproof;

import java.util.ArrayList;
import java.util.List;

import com.sablednah.wooddye.WoodDye;
import com.sablednah.wooddye.neoforge.WoodTransforms;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Fireproofing as a property of a <em>position</em> rather than a block of its own, which is what
 * lets any wood be fireproofed &mdash; a mod's as much as vanilla's &mdash; without a cloned block
 * set per wood.
 *
 * <p>The marks live in {@link FireproofMarks}. Fire and lava consult {@link #isFireproof} (from the
 * mixins); placing, breaking, pistons and Create's contraptions keep the marks in step with the
 * blocks ({@link FireproofEvents}, {@code compat/CreateFireproof}); and an item's fireproofing
 * travels as a component ({@link FireproofComponents}).
 */
public final class Fireproofing {

    /** Marks waiting for their block to be put down, applied at the end of the tick. */
    private static final List<Pending> PENDING = new ArrayList<>();

    private record Pending(ServerLevel level, BlockPos pos, int triesLeft) {}

    private Fireproofing() {}

    /**
     * Whether the wood at {@code pos} is fireproof. Only a server level knows; anywhere else the
     * answer is no. A mark found on something that is not wood is stale and is dropped.
     */
    public static boolean isFireproof(BlockGetter level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        FireproofMarks marks = FireproofMarks.of(server);
        if (!marks.contains(pos)) {
            return false;
        }
        if (!WoodTransforms.isWood(server.getBlockState(pos).getBlock())) {
            marks.remove(pos, server.getGameTime());
            return false;
        }
        return true;
    }

    /** Whether the position is fireproof now, or was until a moment ago (for a block's drops). */
    public static boolean wasFireproof(ServerLevel level, BlockPos pos) {
        FireproofMarks marks = FireproofMarks.of(level);
        return marks.contains(pos) || marks.removedRecently(pos, level.getGameTime());
    }

    /** Whether this block can be fireproofed by marking: any wood but the legacy fireproof blocks. */
    public static boolean canMark(Block block) {
        return WoodTransforms.isWood(block) && !WoodTransforms.isLegacyFireproof(block);
    }

    /** Fireproof the wood at {@code pos} (both halves of a door). Returns false if it already was. */
    public static boolean mark(ServerLevel level, BlockPos pos) {
        boolean changed = false;
        for (BlockPos part : parts(level, pos)) {
            changed |= FireproofMarks.of(level).add(part);
        }
        return changed;
    }

    /** Undo {@link #mark}. Returns false if the wood was not fireproof. */
    public static boolean unmark(ServerLevel level, BlockPos pos) {
        boolean changed = false;
        for (BlockPos part : parts(level, pos)) {
            changed |= FireproofMarks.of(level).remove(part, level.getGameTime());
        }
        return changed;
    }

    /** A door is two blocks that must agree; anything else is just itself. */
    private static List<BlockPos> parts(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(DoorBlock.HALF)) {
            BlockPos lower = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
            return List.of(lower, lower.above());
        }
        return List.of(pos);
    }

    /**
     * Mark {@code pos} once the block that is on its way there has arrived. Create puts a
     * contraption's blocks back after it has told each actor it stopped, so a mark set immediately
     * would land on air and be dropped as stale.
     */
    public static void markWhenPlaced(ServerLevel level, BlockPos pos) {
        PENDING.add(new Pending(level, pos, 3));
    }

    /** End-of-tick housekeeping: apply pending marks, forget old removals. */
    public static void tick(ServerLevel level) {
        FireproofMarks.of(level).expire(level.getGameTime());
        if (PENDING.isEmpty()) {
            return;
        }
        List<Pending> retry = new ArrayList<>();
        for (Pending pending : PENDING) {
            if (pending.level() != level) {
                retry.add(pending);
            } else if (canMark(level.getBlockState(pending.pos()).getBlock())) {
                mark(level, pending.pos());
            } else if (pending.triesLeft() > 1) {
                retry.add(new Pending(level, pending.pos(), pending.triesLeft() - 1));
            } else {
                WoodDye.LOGGER.warn("WoodDye: a fireproof block was expected at {} but never arrived", pending.pos());
            }
        }
        PENDING.clear();
        PENDING.addAll(retry);
    }

    /** How many positions are fireproof in this dimension, for {@code /wooddye fireproof}. */
    public static int count(ServerLevel level) {
        return FireproofMarks.of(level).size();
    }
}
