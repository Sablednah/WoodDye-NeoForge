package com.sablednah.wooddye.registry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.sablednah.wooddye.WoodDye;
import com.sablednah.wooddye.core.WoodType;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The custom fireproof blocks — a {@code fireproof_*} counterpart for every {@code fireproof()}
 * {@link WoodType.Form} of every wood. Each copies its vanilla block's behaviour via
 * {@link BlockBehaviour.Properties#ofFullCopy}; because none are registered with {@code FireBlock}'s
 * flammability map they inherently will not burn — that is the whole "fireproof" mechanism.
 */
public final class WoodDyeBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(WoodDye.MODID);

    /** Fireproof holders, keyed by form then wood. */
    public static final Map<WoodType.Form, Map<WoodType, DeferredBlock<? extends Block>>> FIREPROOF =
            new EnumMap<>(WoodType.Form.class);

    /** A registered fireproof block together with the form and wood it was built from. */
    public record Entry(WoodType.Form form, WoodType wood, DeferredBlock<? extends Block> block) {}

    /** Every registered fireproof block, in registration order (for items / creative tab). */
    public static final List<Entry> ALL = new ArrayList<>();

    static {
        for (WoodType.Form form : WoodType.Form.values()) {
            if (!form.fireproof()) {
                continue;
            }
            Map<WoodType, DeferredBlock<? extends Block>> byWood = new EnumMap<>(WoodType.class);
            for (WoodType wood : WoodType.values()) {
                Block vanilla = wood.vanilla(form);
                if (vanilla == null) {
                    continue; // e.g. bamboo has no all-bark "wood" form
                }
                DeferredBlock<? extends Block> holder = BLOCKS.registerBlock(
                        "fireproof_" + vanillaPath(vanilla),
                        factory(wood, form),
                        () -> BlockBehaviour.Properties.ofFullCopy(vanilla));
                byWood.put(wood, holder);
                ALL.add(new Entry(form, wood, holder));
            }
            FIREPROOF.put(form, byWood);
        }
    }

    /** The registered fireproof holder for a form+wood, or {@code null} if none (non-fireproof / absent). */
    public static DeferredBlock<? extends Block> get(WoodType.Form form, WoodType wood) {
        Map<WoodType, DeferredBlock<? extends Block>> byWood = FIREPROOF.get(form);
        return byWood == null ? null : byWood.get(wood);
    }

    /** Picks the block constructor for a form, supplying the extra args (base state, set type) it needs. */
    private static Function<BlockBehaviour.Properties, ? extends Block> factory(WoodType wood, WoodType.Form form) {
        return switch (form.kind()) {
            case SIMPLE -> Block::new;
            case PILLAR -> RotatedPillarBlock::new;
            case SLAB -> SlabBlock::new;
            case STAIRS -> {
                BlockState base = wood.vanilla(WoodType.Form.PLANKS).defaultBlockState();
                yield props -> new StairBlock(base, props) {};
            }
            case FENCE -> FenceBlock::new;
            case FENCE_GATE -> props -> new FenceGateBlock(wood.mojangWoodType(), props);
            // These constructors are protected; an anonymous subclass reaches them.
            case DOOR -> props -> new DoorBlock(wood.blockSetType(), props) {};
            case TRAPDOOR -> props -> new TrapDoorBlock(wood.blockSetType(), props) {};
            case PRESSURE_PLATE -> props -> new PressurePlateBlock(wood.blockSetType(), props) {};
            case BUTTON -> props -> new ButtonBlock(wood.blockSetType(), 30, props) {};
            case NONE -> throw new IllegalStateException("Non-fireproof form has no block factory: " + form);
        };
    }

    /** The registry path of a vanilla block, e.g. {@code oak_log} or {@code bamboo_block}. */
    private static String vanillaPath(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).getPath();
    }

    private WoodDyeBlocks() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
