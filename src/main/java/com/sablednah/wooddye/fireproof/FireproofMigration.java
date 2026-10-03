package com.sablednah.wooddye.fireproof;

import com.sablednah.wooddye.WoodDye;
import com.sablednah.wooddye.neoforge.WoodDyeInteractions;
import com.sablednah.wooddye.neoforge.WoodTransforms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Brings a 2.x world forward. The old {@code wooddye:fireproof_*} blocks are still registered, so
 * worlds load; this turns each one into its vanilla block plus a mark as its chunk loads, and turns
 * the old items into stamped vanilla items as they pass through a player's inventory. After one
 * visit to an area, and one handling of a stack, nothing legacy is left.
 */
public final class FireproofMigration {

    private FireproofMigration() {}

    /** Convert every legacy block in a chunk the moment it is loaded, before anyone sees it. */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        int converted = 0;
        LevelChunkSection[] sections = chunk.getSections();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            // The palette says whether a section can hold one at all; nearly every section cannot.
            if (section.hasOnlyAir() || !section.maybeHas(state -> WoodTransforms.isLegacyFireproof(state.getBlock()))) {
                continue;
            }
            int baseY = chunk.getSectionYFromSectionIndex(index) << 4;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        Block vanilla = WoodTransforms.fromFireproof(state.getBlock());
                        if (vanilla == null) {
                            continue;
                        }
                        BlockPos pos = chunk.getPos().getBlockAt(x, baseY + y, z);
                        chunk.setBlockState(pos, WoodDyeInteractions.copyMatchingProperties(state, vanilla), false);
                        Fireproofing.markRaw(level, pos);
                        converted++;
                    }
                }
            }
        }
        if (converted > 0) {
            chunk.setUnsaved(true);
            WoodDye.LOGGER.info("WoodDye: converted {} legacy fireproof blocks in chunk {} to marked wood", converted, chunk.getPos());
        }
    }

    /** Convert legacy items in a player's inventory, once a second. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        convert(player.getInventory().items);
        convert(player.getInventory().offhand);
    }

    private static void convert(NonNullList<ItemStack> slots) {
        for (int slot = 0; slot < slots.size(); slot++) {
            ItemStack stack = slots.get(slot);
            if (stack.getItem() instanceof BlockItem item) {
                Block vanilla = WoodTransforms.fromFireproof(item.getBlock());
                if (vanilla != null) {
                    slots.set(slot, FireproofComponents.stamp(new ItemStack(vanilla, stack.getCount())));
                }
            }
        }
    }
}
