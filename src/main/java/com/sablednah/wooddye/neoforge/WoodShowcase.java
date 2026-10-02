package com.sablednah.wooddye.neoforge;

import java.util.List;
import java.util.Locale;

import com.sablednah.wooddye.core.WoodType.Form;
import com.sablednah.wooddye.neoforge.WoodFamilies.Family;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Builds every dyeable wood side by side, in dye order: the picture on the mod page, generated from
 * whatever woods this server actually has. One column per wood running east, and a flight of steps
 * rising to the south, each step a different form.
 *
 * <p>It reads the same lists dyeing does, so it is also the quickest way to <em>see</em> an order —
 * where a modded wood landed, or what RAINBOW looks like — rather than read it off
 * {@code /wooddye woods}.
 */
final class WoodShowcase {

    /**
     * The steps, front to back, each one block higher than the last. The log is in front because
     * it is the only step that shows bark, and so the only one laid out in bark order.
     */
    private static final Form[] STEPS = {
            Form.LOG, Form.STRIPPED_LOG, Form.PLANKS, Form.STAIRS, Form.SLAB, Form.FENCE_GATE, Form.FENCE,
    };

    /** What a build produced: how many woods, and where to stand to see them all. */
    record Result(int woods, String viewpoint) {}

    private WoodShowcase() {}

    /** Build the showcase with its front-left corner at {@code origin}, replacing what is there. */
    static Result build(ServerLevel level, BlockPos origin) {
        List<Family> wood = WoodTransforms.woodOrder();
        List<Family> bark = WoodTransforms.barkOrder();

        for (int column = 0; column < wood.size(); column++) {
            for (int step = 0; step < STEPS.length; step++) {
                Form form = STEPS[step];
                Family family = (form == Form.LOG ? bark : wood).get(column);
                // Each step stands on a pillar of its own wood's planks, so the flight is solid.
                BlockState pillar = family.block(Form.PLANKS).defaultBlockState();
                for (int y = 0; y < STEPS.length + 1; y++) {
                    BlockState state = y < step ? pillar : Blocks.AIR.defaultBlockState();
                    if (y == step && family.block(form) != null) {
                        state = facingViewer(family.block(form).defaultBlockState());
                    }
                    level.setBlockAndUpdate(origin.offset(column, y, step), state);
                }
            }
        }

        // A block is placed in its default shape and only its neighbours are told, so the fences
        // (and stairs) need a second look once the whole row exists to join up with each other.
        for (int column = 0; column < wood.size(); column++) {
            for (int step = 0; step < STEPS.length; step++) {
                BlockPos pos = origin.offset(column, step, step);
                BlockState state = level.getBlockState(pos);
                BlockState joined = Block.updateFromNeighbourShapes(state, level, pos);
                if (joined != state) {
                    level.setBlockAndUpdate(pos, joined);
                }
            }
        }
        return new Result(wood.size(), viewpoint(origin, wood.size()));
    }

    /** Stairs climb away from the viewer and gates stand across their view. */
    private static BlockState facingViewer(BlockState state) {
        return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
                : state;
    }

    /**
     * A {@code /tp} that frames the whole build: centred, far enough back for the widest row to fit
     * a normal field of view, and high enough to look down the steps.
     */
    private static String viewpoint(BlockPos origin, int woods) {
        double back = woods * 0.55 + 6;
        double x = origin.getX() + woods / 2.0;
        double y = origin.getY() + back * 0.35 + 4;
        double z = origin.getZ() - back;
        double pitch = Math.toDegrees(Math.atan2(y - (origin.getY() + 3), back + 3));
        return String.format(Locale.ROOT, "/tp @s %.1f %.1f %.1f 0 %.0f", x, y, z, pitch);
    }
}
