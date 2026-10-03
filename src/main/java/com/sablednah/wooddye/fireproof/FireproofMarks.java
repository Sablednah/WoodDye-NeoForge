package com.sablednah.wooddye.fireproof;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.LongStream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The positions in one dimension whose wood has been fireproofed, saved with the world.
 *
 * <p>This is the whole of "fireproof as data": no block of its own, just a set of positions that
 * fire and lava are told to leave alone (see the mixins) and that follows the block when something
 * moves it. It is plain vanilla {@link SavedData}, so it is the same on every loader.
 *
 * <p>A mark is only meaningful while the block at the position is wood; {@link Fireproofing} drops
 * any mark it finds on something else, so a mark that was missed when a block went away cannot
 * fireproof whatever is put there later.
 */
public final class FireproofMarks extends SavedData {

    private static final String NAME = "wooddye_fireproof";
    private static final Codec<FireproofMarks> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.listOf().fieldOf("positions")
                    .forGetter(marks -> LongStream.of(marks.positions.toLongArray()).boxed().toList()))
            .apply(instance, FireproofMarks::new));
    private static final SavedDataType<FireproofMarks> TYPE = new SavedDataType<>(NAME, FireproofMarks::new, CODEC);

    /** How many ticks a just-removed mark is still answered for, so a broken block's drops see it. */
    private static final long GRACE_TICKS = 5;

    private final LongOpenHashSet positions = new LongOpenHashSet();

    /**
     * Positions unmarked recently, with the game time of the removal. A block's drops are worked
     * out after it has been taken out of the world, by which time its mark is gone; this is how
     * the drop still learns it was fireproof.
     */
    private final Long2LongOpenHashMap removed = new Long2LongOpenHashMap();

    private FireproofMarks() {}

    private FireproofMarks(List<Long> saved) {
        positions.addAll(saved);
    }

    static FireproofMarks of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    boolean contains(BlockPos pos) {
        return positions.contains(pos.asLong());
    }

    boolean add(BlockPos pos) {
        if (positions.add(pos.asLong())) {
            removed.remove(pos.asLong());
            setDirty();
            return true;
        }
        return false;
    }

    boolean remove(BlockPos pos, long gameTime) {
        if (positions.remove(pos.asLong())) {
            removed.put(pos.asLong(), gameTime);
            setDirty();
            return true;
        }
        return false;
    }

    /** Whether the position was unmarked within the last few ticks. */
    boolean removedRecently(BlockPos pos, long gameTime) {
        long when = removed.getOrDefault(pos.asLong(), Long.MIN_VALUE);
        return when != Long.MIN_VALUE && gameTime - when <= GRACE_TICKS;
    }

    /** Forget removals older than the grace period. Called once a tick; the map stays tiny. */
    void expire(long gameTime) {
        if (!removed.isEmpty()) {
            removed.long2LongEntrySet().removeIf(entry -> gameTime - entry.getLongValue() > GRACE_TICKS);
        }
    }

    int size() {
        return positions.size();
    }

    /** Every marked position within {@code radius} blocks of {@code centre}, on all three axes. */
    void forEachNear(BlockPos centre, int radius, Consumer<BlockPos> action) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (LongIterator it = positions.iterator(); it.hasNext();) {
            pos.set(it.nextLong());
            if (Math.abs(pos.getX() - centre.getX()) <= radius
                    && Math.abs(pos.getY() - centre.getY()) <= radius
                    && Math.abs(pos.getZ() - centre.getZ()) <= radius) {
                action.accept(pos.immutable());
            }
        }
    }
}
