package com.sablednah.wooddye.fireproof;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Keeps the fireproof marks in step with the blocks they belong to.
 *
 * <ul>
 *     <li><b>Placing</b> a stamped item marks the position; placing a plain item onto a marked
 *         position (a slab completing a double slab) unmarks it, so fireproofing is never gained
 *         for free. (A dispenser cannot place wood, so only players place.)</li>
 *     <li><b>Any change</b> that leaves something other than wood at a marked position unmarks
 *         it, remembering the removal for a few ticks.</li>
 *     <li><b>Drops</b> from a position that was fireproof are stamped (by
 *         {@link FireproofLootModifier}; Forge 1.20.1 has no block-drops event).</li>
 *     <li><b>Pistons</b> carry marks with the blocks they push or pull, sticky chains included.</li>
 * </ul>
 */
public final class FireproofEvents {

    /** Marked wood near a piston that is about to move, with what stood there, from Pre to Post. */
    private static final Map<BlockPos, Map<BlockPos, Block>> PISTON_NEARBY = new HashMap<>();

    /** As far as a piston's influence reaches: the push limit, plus the head, plus a little. */
    private static final int PISTON_REACH = 16;

    /** What each player is about to place, noted before the stack in hand is used up by placing it. */
    private static final Map<UUID, Placement> PLACING = new HashMap<>();

    private record Placement(Block block, boolean stamped) {}

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        if (!event.getLevel().isClientSide() && held.getItem() instanceof BlockItem item && Fireproofing.canMark(item.getBlock())) {
            PLACING.put(event.getEntity().getUUID(), new Placement(item.getBlock(), FireproofComponents.isStamped(held)));
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Player player)) {
            return;
        }
        Block placed = event.getPlacedBlock().getBlock();
        if (!Fireproofing.canMark(placed)) {
            return;
        }
        // By now the stack has been shrunk, and an emptied stack reads as air, so the right-click
        // handler above noted what was in hand a moment earlier.
        Placement placing = PLACING.remove(player.getUUID());
        if (placing != null && placing.block() == placed && placing.stamped()) {
            Fireproofing.mark(level, event.getPos());
        } else {
            Fireproofing.unmark(level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onBlockChanged(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockState now = event.getState();
        // A moving piston is a block in transit, not a replacement; the mark is handled by onPiston.
        if (now.is(Blocks.MOVING_PISTON) || Fireproofing.canMark(now.getBlock())) {
            return;
        }
        Fireproofing.unmark(level, event.getPos());
    }

    /**
     * Note the marked wood a piston could move. The resolver the event offers cannot be trusted
     * here: on a retraction it runs while the piston head is still in the way and reports nothing,
     * so instead the move is read off the world afterwards, in {@link #onPistonPost}.
     */
    @SubscribeEvent
    public static void onPistonPre(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Map<BlockPos, Block> nearby = new HashMap<>();
        Fireproofing.forEachNear(level, event.getPos(), PISTON_REACH,
                pos -> nearby.put(pos, level.getBlockState(pos).getBlock()));
        if (!nearby.isEmpty()) {
            PISTON_NEARBY.put(event.getPos().immutable(), nearby);
        }
    }

    /**
     * The blocks are in transit now: each moved block is a moving-piston block one step along the
     * push direction from where it stood, remembering what it was. A marked block that has gone
     * from its place and reappears that way one step on takes its mark with it.
     */
    @SubscribeEvent
    public static void onPistonPost(PistonEvent.Post event) {
        Map<BlockPos, Block> nearby = PISTON_NEARBY.remove(event.getPos());
        if (nearby == null || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Direction push = event.getPistonMoveType().isExtend ? event.getDirection() : event.getDirection().getOpposite();
        FireproofMarks marks = FireproofMarks.of(level);
        List<BlockPos> arrived = new ArrayList<>();
        for (Map.Entry<BlockPos, Block> was : nearby.entrySet()) {
            BlockPos from = was.getKey();
            BlockPos to = from.relative(push);
            if (!level.getBlockState(from).is(was.getValue())
                    && level.getBlockEntity(to) instanceof PistonMovingBlockEntity moving
                    && !moving.isSourcePiston()
                    && moving.getMovedState().is(was.getValue())) {
                marks.remove(from, level.getGameTime());
                arrived.add(to);
            }
        }
        for (BlockPos to : arrived) {
            marks.add(to);
        }
    }

    /**
     * Holding Magma Cream or a Wet Sponge shows which blocks nearby are fireproof: a small flame
     * on each, sent to that player alone. Fireproof wood looks like any other, and this is the
     * moment a player wants to know which is which.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % 10 != 0) {
            return;
        }
        if (!player.getMainHandItem().is(Items.MAGMA_CREAM) && !player.getMainHandItem().is(Items.WET_SPONGE)
                && !player.getOffhandItem().is(Items.MAGMA_CREAM) && !player.getOffhandItem().is(Items.WET_SPONGE)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Fireproofing.forEachNear(level, player.blockPosition(), 12, pos -> level.sendParticles(player,
                ParticleTypes.SMALL_FLAME, true, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                1, 0.2, 0.0, 0.2, 0.0));
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
            Fireproofing.tick(level);
            PISTON_NEARBY.clear(); // a Pre without a Post means the move was cancelled
            PLACING.clear();      // a right-click that placed nothing
        }
    }
}
