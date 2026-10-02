package com.sablednah.wooddye.neoforge;

import java.util.HashMap;
import java.util.Map;

import com.sablednah.wooddye.core.WoodType;
import com.sablednah.wooddye.core.WoodType.Form;
import com.sablednah.wooddye.registry.WoodDyeBlocks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * Axe stripping of the fireproof logs: a fireproof log strips to the fireproof stripped log, and
 * fireproof all-bark wood to fireproof stripped wood, keeping the axis. Stripping keeps the
 * fireproofing.
 *
 * <p>This class exists on the 1.20.1 Forge line only. On the NeoForge lines the same pairs are a
 * data map (or, from 26.3, block transformers) written by {@code tools/gen_resources.py}, and
 * vanilla's {@code AxeItem} reads it; Forge 1.20.1 has no data maps, and {@code AxeItem}'s own table
 * is private. What it does have is this event, which {@code AxeItem} raises before consulting that
 * table and which still leaves the sound, the tool damage and the advancement trigger to vanilla.
 */
final class WoodDyeStripping {

    /** Fireproof log or wood &rarr; its stripped counterpart. Built on first use, after registration. */
    private static volatile Map<Block, Block> stripped;

    private WoodDyeStripping() {}

    static void handle(BlockEvent.BlockToolModificationEvent event) {
        // The event is raised for every tool action, and for whatever item is in hand; vanilla's own
        // stripping makes the same check (IForgeBlock#getToolModifiedState).
        if (!ToolActions.AXE_STRIP.equals(event.getToolAction())
                || !event.getHeldItemStack().canPerformAction(ToolActions.AXE_STRIP)) {
            return;
        }
        BlockState state = event.getFinalState();
        Block target = state == null ? null : stripped(state.getBlock());
        if (target != null) {
            event.setFinalState(WoodDyeInteractions.copyMatchingProperties(state, target));
        }
    }

    /** The stripped counterpart of a fireproof log or wood block, or {@code null} for anything else. */
    static Block stripped(Block block) {
        Map<Block, Block> pairs = stripped;
        if (pairs == null) {
            pairs = new HashMap<>();
            for (WoodType wood : WoodType.values()) {
                pair(pairs, wood, Form.LOG, Form.STRIPPED_LOG);
                pair(pairs, wood, Form.WOOD, Form.STRIPPED_WOOD);
            }
            stripped = pairs;
        }
        return pairs.get(block);
    }

    /** Record one pair, where this wood has both halves of it (bamboo has no all-bark wood). */
    private static void pair(Map<Block, Block> pairs, WoodType wood, Form from, Form to) {
        RegistryObject<? extends Block> plain = WoodDyeBlocks.get(from, wood);
        RegistryObject<? extends Block> bare = WoodDyeBlocks.get(to, wood);
        if (plain != null && bare != null) {
            pairs.put(plain.get(), bare.get());
        }
    }
}
