package com.sablednah.wooddye.neoforge;

import com.sablednah.wooddye.WoodDye;
import com.sablednah.wooddye.WoodDyeConfig;
import com.sablednah.wooddye.core.LogOrder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The in-world dyeing/fireproofing behaviour: right-click a wooden block with a dye to shade it, with
 * Magma Cream to fireproof it, or with a Wet Sponge to restore it. Server side only.
 *
 * <p>Forms with a vanilla right-click action of their own (gates, doors, trapdoors, buttons, signs,
 * shelves) require the player to <b>sneak</b>, so an ordinary click still just opens/presses them.
 */
public final class WoodDyeInteractions {

    private WoodDyeInteractions() {}

    /** The three kinds of successful treatment, each with its own feedback particle + sound. */
    private enum Kind {
        DYE(ParticleTypes.HAPPY_VILLAGER, SoundEvents.DYE_USE),
        FIREPROOF(ParticleTypes.FLAME, SoundEvents.FIRECHARGE_USE),
        RESTORE(ParticleTypes.SPLASH, SoundEvents.SPONGE_ABSORB);

        final ParticleOptions particle;
        final SoundEvent sound;

        Kind(ParticleOptions particle, SoundEvent sound) {
            this.particle = particle;
            this.sound = sound;
        }
    }

    public static void handle(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }
        ItemStack held = event.getItemStack();
        if (held.isEmpty()) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        Player player = event.getEntity();
        // Forms that do something on a plain right-click only dye when the player sneaks, so that
        // normal use (opening a gate, pressing a button, editing a sign) still works with a dye held.
        if (WoodTransforms.requiresSneak(block) && !player.isShiftKeyDown()) {
            return;
        }

        Block target = null;
        Kind kind = null;
        WoodTransforms.Shift shift = dyeShift(held.getItem());
        if (shift != null) {
            // Only unstripped logs and all-bark wood have a bark colour to follow; everything else
            // (planks, stripped wood, fences, doors, ...) always dyes along the inner-wood order.
            boolean bark = WoodTransforms.isBarkCapable(block) && useBarkOrder(event, state, block);
            target = WoodTransforms.shade(block, shift, bark);
            kind = Kind.DYE;
        } else if (held.is(Items.MAGMA_CREAM) && WoodDyeConfig.FIREPROOF.get()) {
            target = WoodTransforms.toFireproof(block);
            kind = Kind.FIREPROOF;
        } else if (held.is(Items.WET_SPONGE)) {
            // A wet sponge soaks the magma cream back out — revert fireproof wood to plain wood.
            // Always allowed (even if fireProof creation is disabled) so fireproofing is reversible.
            target = WoodTransforms.fromFireproof(block);
            kind = Kind.RESTORE;
        }
        if (target == null) {
            return; // not a relevant item/block combination
        }

        // Original plugin's permission check was inverted (did nothing when allowed); corrected here.
        if (player instanceof ServerPlayer serverPlayer && !WoodDyePermissions.canDye(serverPlayer)) {
            return;
        }

        if (!replace(level, pos, state, target)) {
            return; // the block was not in a state we can safely convert
        }

        if (WoodDyeConfig.USE_ITEMS.get() && !player.getAbilities().instabuild) {
            spend(player, event.getHand(), held, kind);
        }
        player.swing(event.getHand());
        event.setCanceled(true);

        feedback(level, player, pos, kind);

        if (WoodDyeConfig.DEBUG.get()) {
            WoodDye.LOGGER.info("WoodDye: {} -> {} at {}", block, target, pos);
        }
    }

    /**
     * Charge the player for a treatment, under the {@code useItems} config.
     *
     * <p>A dye or magma cream is used up. A wet sponge is not: it only gives up its water, becoming a
     * plain sponge the player can re-soak and use again — destroying a whole sponge to wring out one
     * block would be a strange price.
     */
    private static void spend(Player player, InteractionHand hand, ItemStack held, Kind kind) {
        held.shrink(1);
        if (kind != Kind.RESTORE) {
            return;
        }
        ItemStack dried = new ItemStack(Items.SPONGE);
        if (held.isEmpty()) {
            player.setItemInHand(hand, dried); // the hand is free now — put it straight back
        } else if (!player.getInventory().add(dried)) {
            player.drop(dried, false); // holding more sponges and no room: hand it to the world
        }
    }

    /**
     * Swap the block at {@code pos} for {@code target}, routing the forms that need more than a plain
     * block swap. Returns {@code false} if the block was left untouched.
     */
    private static boolean replace(Level level, BlockPos pos, BlockState state, Block target) {
        Block block = state.getBlock();
        if (WoodTransforms.isDoor(block)) {
            return replaceDoor(level, pos, state, target);
        }
        if (WoodTransforms.isShelf(block) && !isEmpty(level, pos)) {
            return false; // a stocked shelf would drop its contents; leave it alone
        }
        BlockState newState = copyMatchingProperties(state, target);
        if (WoodTransforms.isSign(block)) {
            return replaceSign(level, pos, newState);
        }
        level.setBlockAndUpdate(pos, newState);
        return true;
    }

    /**
     * Doors occupy two blocks. Each half's {@code updateShape} breaks it when the other half is not
     * the same block, so both are swapped with shape updates suppressed — replacing one at a time
     * would pop the door as an item the moment the halves disagreed.
     */
    private static boolean replaceDoor(Level level, BlockPos pos, BlockState state, Block target) {
        if (!state.hasProperty(DoorBlock.HALF)) {
            return false;
        }
        BlockPos lower = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        BlockPos upper = lower.above();
        BlockState lowerState = level.getBlockState(lower);
        BlockState upperState = level.getBlockState(upper);
        if (!lowerState.is(state.getBlock()) || !upperState.is(state.getBlock())) {
            return false; // half-broken door; not ours to fix
        }
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        level.setBlock(lower, copyMatchingProperties(lowerState, target), flags);
        level.setBlock(upper, copyMatchingProperties(upperState, target), flags);
        return true;
    }

    /**
     * Signs keep their text in a block entity, which the swap destroys — so the old entity's data is
     * saved first and loaded back into the new one. Sign forms only ever map sign&rarr;sign of the
     * same shape, so the block-entity types always match.
     */
    private static boolean replaceSign(Level level, BlockPos pos, BlockState newState) {
        BlockEntity old = level.getBlockEntity(pos);
        if (!(old instanceof SignBlockEntity)) {
            return false;
        }
        HolderLookup.Provider registries = level.registryAccess();
        CompoundTag data = old.saveCustomOnly(registries);
        level.setBlockAndUpdate(pos, newState);
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            sign.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registries, data));
            sign.setChanged();
        }
        return true;
    }

    /** Whether the container block entity at {@code pos} holds nothing. */
    private static boolean isEmpty(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof Container container && container.isEmpty();
    }

    /** Particle + sound + optional action-bar message so the player sees the treatment took effect. */
    private static void feedback(Level level, Player player, BlockPos pos, Kind kind) {
        if (WoodDyeConfig.SHOW_EFFECTS.get()) {
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(kind.particle,
                        pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5,
                        12, 0.3, 0.3, 0.3, 0.02);
            }
            level.playSound(null, pos, kind.sound, SoundSource.BLOCKS, 0.8f, 1.0f);
        }
        if (WoodDyeConfig.SHOW_MESSAGE.get()) {
            String message = WoodDyeConfig.MESSAGE.get();
            if (message != null && !message.isEmpty()) {
                String text = message.replace('&', '§').replace("%P", player.getName().getString());
                player.displayClientMessage(Component.literal(text), true);
            }
        }
    }

    /**
     * Decide whether a dye step should follow the <b>bark</b> order (vs. the plank/wood order), per
     * the {@code logOrder} config. In INTELLIGENT mode this depends on the clicked face: an end face
     * (whose direction lies along the log's axis) shows end-grain rings and uses wood order; any side
     * face shows bark and uses bark order. All-bark "wood" blocks have no end grain on any face, so
     * they always read as bark.
     */
    private static boolean useBarkOrder(PlayerInteractEvent.RightClickBlock event, BlockState state, Block block) {
        LogOrder mode = WoodDyeConfig.LOG_ORDER.get();
        return switch (mode) {
            case SAME_AS_PLANKS -> false;
            case BARK -> true;
            case INTELLIGENT -> {
                if (WoodTransforms.isAllBarkWood(block)) {
                    yield true; // every face is bark
                }
                Direction face = event.getFace();
                if (face == null || !state.hasProperty(RotatedPillarBlock.AXIS)) {
                    yield true; // no face / not a pillar: default to bark
                }
                yield face.getAxis() != state.getValue(RotatedPillarBlock.AXIS);
            }
        };
    }

    /** Maps a held item to a shade direction, or {@code null} if it isn't a WoodDye dye. */
    private static WoodTransforms.Shift dyeShift(Item item) {
        if (item == Items.BLACK_DYE || item == Items.BROWN_DYE) {
            return WoodTransforms.Shift.DARKEN;
        }
        if (item == Items.WHITE_DYE || item == Items.LIGHT_GRAY_DYE || item == Items.BONE_MEAL) {
            return WoodTransforms.Shift.LIGHTEN;
        }
        return null;
    }

    /**
     * Build {@code to}'s default state carrying over every block-state property shared with
     * {@code from}. Since we only ever map like-to-like (planks&rarr;planks, door&rarr;door, and so
     * on), the property sets match exactly — preserving slab type (incl. double), stair
     * facing/half/shape, log axis, door hinge/open state, and waterlogging.
     */
    public static BlockState copyMatchingProperties(BlockState from, Block to) {
        BlockState result = to.defaultBlockState();
        for (Property<?> property : from.getProperties()) {
            if (result.hasProperty(property)) {
                result = copyValue(result, from, property);
            }
        }
        return result;
    }

    private static <T extends Comparable<T>> BlockState copyValue(BlockState to, BlockState from, Property<T> property) {
        return to.setValue(property, from.getValue(property));
    }
}
