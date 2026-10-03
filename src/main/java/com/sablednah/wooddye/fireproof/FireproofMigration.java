package com.sablednah.wooddye.fireproof;

import java.util.concurrent.atomic.AtomicLong;

import com.sablednah.wooddye.WoodDye;
import com.sablednah.wooddye.WoodDyeConfig;
import com.sablednah.wooddye.neoforge.WoodDyeInteractions;
import com.sablednah.wooddye.neoforge.WoodTransforms;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

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
            if (WoodDyeConfig.DEBUG.get()) {
                WoodDye.LOGGER.info("WoodDye: converted {} legacy fireproof blocks in chunk {} to marked wood", converted, chunk.getPos());
            } else if (CONVERTED.get() == 0 && CHUNKS.get() == 0) {
                WoodDye.LOGGER.info("WoodDye: converting 2.0 fireproof blocks to marked wood as their chunks load; "
                        + "a total is logged when the server stops (debugMode logs each chunk)");
            }
            CONVERTED.addAndGet(converted);
            CHUNKS.incrementAndGet();
        }
    }

    /** Conversions this session, for one summary line instead of one per chunk. */
    private static final AtomicLong CONVERTED = new AtomicLong();
    private static final AtomicLong CHUNKS = new AtomicLong();

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        long blocks = CONVERTED.getAndSet(0);
        long chunks = CHUNKS.getAndSet(0);
        if (blocks > 0) {
            WoodDye.LOGGER.info("WoodDye: converted {} legacy fireproof blocks in {} chunks to marked wood this session", blocks, chunks);
        }
    }

    /** Convert legacy items in a player's inventory, once a second. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof BlockItem item) {
                Block vanilla = WoodTransforms.fromFireproof(item.getBlock());
                if (vanilla != null) {
                    inventory.setItem(slot, FireproofComponents.stamp(new ItemStack(vanilla, stack.getCount())));
                }
            }
        }
    }
}
