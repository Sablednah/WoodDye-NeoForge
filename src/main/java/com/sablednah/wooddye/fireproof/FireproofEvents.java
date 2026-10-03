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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Keeps the fireproof marks in step with the blocks they belong to.
 *
 * <ul>
 *     <li><b>Placing</b> a stamped item marks the position; placing a plain item onto a marked
 *         position (a slab completing a double slab) unmarks it, so fireproofing is never gained
 *         for free. (A dispenser cannot place wood, so only players place.)</li>
 *     <li><b>Any change</b> that leaves something other than wood at a marked position unmarks
 *         it, remembering the removal for a few ticks.</li>
 *     <li><b>Drops</b> from a position that was fireproof are stamped.</li>
 *     <li><b>Pistons</b> carry marks with the blocks they push or pull.</li>
 * </ul>
 */
public final class FireproofEvents {

    /** Marks that a piston about to move will carry, from the Pre event to the Post. */
    private static final Map<BlockPos, List<Move>> PISTON_MOVES = new HashMap<>();

    private record Move(BlockPos from, BlockPos to) {}

    private FireproofEvents() {}

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

    @SubscribeEvent
    public static void onDrops(BlockDropsEvent event) {
        ServerLevel level = event.getLevel();
        if (!Fireproofing.wasFireproof(level, event.getPos())) {
            return;
        }
        for (ItemEntity drop : event.getDrops()) {
            if (FireproofComponents.isWood(drop.getItem())) {
                drop.setItem(FireproofComponents.stamp(drop.getItem().copy()));
            }
        }
        Fireproofing.unmark(level, event.getPos()); // the block is gone, whatever flags removed it with
    }

    /** Work out, before anything moves, which marked blocks this piston will carry and to where. */
    @SubscribeEvent
    public static void onPistonPre(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        PistonStructureResolver structure = event.getStructureHelper();
        if (structure == null || !structure.resolve()) {
            return;
        }
        Direction push = structure.getPushDirection();
        List<Move> moves = new ArrayList<>();
        for (BlockPos moved : structure.getToPush()) {
            if (Fireproofing.isFireproof(level, moved)) {
                moves.add(new Move(moved.immutable(), moved.relative(push)));
            }
        }
        if (!moves.isEmpty()) {
            PISTON_MOVES.put(event.getPos().immutable(), moves);
        }
    }

    /**
     * The blocks are in transit now, as moving-piston blocks that will become the wood again in a
     * couple of ticks. The marks move with them: off the sources, onto the destinations.
     */
    @SubscribeEvent
    public static void onPistonPost(PistonEvent.Post event) {
        List<Move> moves = PISTON_MOVES.remove(event.getPos());
        if (moves == null || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        FireproofMarks marks = FireproofMarks.of(level);
        for (Move move : moves) {
            marks.remove(move.from(), level.getGameTime());
        }
        for (Move move : moves) {
            marks.add(move.to());
        }
    }

    /**
     * Holding Magma Cream or a Wet Sponge shows which blocks nearby are fireproof: a small flame
     * on each, sent to that player alone. Fireproof wood looks like any other, and this is the
     * moment a player wants to know which is which.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 0) {
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
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Fireproofing.tick(level);
            PISTON_MOVES.clear(); // a Pre without a Post means the move was cancelled
            PLACING.clear();      // a right-click that placed nothing
        }
    }
}
