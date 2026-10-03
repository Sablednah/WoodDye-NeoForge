package com.sablednah.wooddye.compat;

import com.sablednah.wooddye.WoodDye;
import com.sablednah.wooddye.WoodDyeConfig;
import com.sablednah.wooddye.fireproof.Fireproofing;
import com.sablednah.wooddye.neoforge.WoodTransforms;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.piston.PistonContraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;

import java.lang.reflect.Field;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Fireproofing rides Create's contraptions. Every wood block gets this behaviour, so when a
 * contraption picks one up the mark is lifted off the world into the block's travelling data, and
 * when the contraption sets it down the mark is put back where the block lands.
 *
 * <p>Only loaded when Create is present: this class names Create's types, so it must not be
 * touched otherwise. {@code WoodDye} checks the mod list before calling {@link #register()}.
 */
public final class CreateFireproof implements MovementBehaviour {

    private static final String KEY = "wooddye_fireproof";
    private static final CreateFireproof INSTANCE = new CreateFireproof();

    private CreateFireproof() {}

    public static void register() {
        // A provider rather than a registration per block: which blocks are wood is only known once
        // the tags have loaded, and may include any mod's.
        MovementBehaviour.REGISTRY.registerProvider(block -> WoodTransforms.isWood(block) ? INSTANCE : null);
        WoodDye.LOGGER.info("WoodDye: Create found; fireproofing will travel with contraptions");
    }

    @Override
    public void startMoving(MovementContext context) {
        if (!(context.world instanceof ServerLevel level)) {
            return;
        }
        BlockPos from = liftedFrom(context);
        boolean fireproof = Fireproofing.wasFireproof(level, from);
        if (WoodDyeConfig.DEBUG.get()) {
            WoodDye.LOGGER.info("WoodDye/Create: {} lifted from {} (anchor {} local {} entity {}): {}",
                    context.state.getBlock(), from, context.contraption.anchor, context.localPos,
                    context.contraption.entity == null ? "none" : context.contraption.entity.position(),
                    fireproof ? "fireproof" : "plain");
        }
        if (fireproof) {
            context.data.putBoolean(KEY, true);
            Fireproofing.unmark(level, from);
        }
    }

    @Override
    public void stopMoving(MovementContext context) {
        if (!(context.world instanceof ServerLevel level)) {
            return;
        }
        boolean fireproof = context.data.getBooleanOr(KEY, false);
        if (WoodDyeConfig.DEBUG.get()) {
            WoodDye.LOGGER.info("WoodDye/Create: {} landing at {} (anchor {} local {} entity {}): {}",
                    context.state.getBlock(), landing(context), context.contraption.anchor, context.localPos,
                    context.contraption.entity == null ? "none" : context.contraption.entity.position(),
                    fireproof ? "fireproof" : "plain");
        }
        if (fireproof) {
            // Create puts the blocks back after telling the actors they stopped, so mark at end of tick.
            Fireproofing.markWhenPlaced(level, landing(context));
        }
    }

    /**
     * Where the block was lifted from. The actors are told they are moving before the contraption
     * entity exists, so this is anchor + localPos — except for a mechanical piston that is
     * retracting, which assembles at the extended end: its contraption remembers how far out that
     * is, in two protected fields, read here by reflection with anchor + localPos as the fallback.
     */
    private static BlockPos liftedFrom(MovementContext context) {
        BlockPos from = context.contraption.anchor.offset(context.localPos);
        if (context.contraption instanceof PistonContraption piston && PISTON_ORIENTATION != null && PISTON_PROGRESS != null) {
            try {
                Direction orientation = (Direction) PISTON_ORIENTATION.get(piston);
                int progress = PISTON_PROGRESS.getInt(piston);
                return from.relative(orientation, progress);
            } catch (ReflectiveOperationException | RuntimeException e) {
                WoodDye.LOGGER.warn("WoodDye: could not read Create's piston extension; a retracting piston may lose fireproofing", e);
            }
        }
        return from;
    }

    private static final Field PISTON_ORIENTATION = field("orientation");
    private static final Field PISTON_PROGRESS = field("initialExtensionProgress");

    private static Field field(String name) {
        try {
            Field field = PistonContraption.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            WoodDye.LOGGER.warn("WoodDye: Create's PistonContraption has no {} field; a retracting piston may lose fireproofing", name);
            return null;
        }
    }

    /**
     * Where the block is about to be put down. Create only tracks {@code context.position} for
     * actors that do work while moving, so it is worked out from the contraption entity's own
     * transform, which at this point has settled on the resting angle and offset.
     */
    private static BlockPos landing(MovementContext context) {
        AbstractContraptionEntity entity = context.contraption.entity;
        if (entity == null) {
            return context.contraption.anchor.offset(context.localPos);
        }
        return BlockPos.containing(entity.toGlobalVector(Vec3.atCenterOf(context.localPos), 1.0f));
    }
}
