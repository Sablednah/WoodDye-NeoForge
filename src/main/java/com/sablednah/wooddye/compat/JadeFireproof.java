package com.sablednah.wooddye.compat;

import com.sablednah.wooddye.WoodDye;
import com.sablednah.wooddye.fireproof.Fireproofing;
import com.sablednah.wooddye.neoforge.WoodTransforms;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade shows "Fireproof" under a fireproofed block. The mark lives on the server, so the server
 * half writes it into Jade's data for the block being looked at and the client half reads it back.
 *
 * <p>Only loaded by Jade, through the annotation; nothing else refers to this class.
 */
@WailaPlugin
public final class JadeFireproof implements IWailaPlugin {

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(WoodDye.MODID, "fireproof");
    private static final String KEY = "wooddye_fireproof";

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(ServerHalf.INSTANCE, Block.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(ClientHalf.INSTANCE, Block.class);
    }

    /**
     * Writes the mark into Jade's data for the block being looked at. A separate object from the
     * tooltip half: Jade rejects a data provider that is also a component provider (since 1.21.6).
     */
    private enum ServerHalf implements IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (WoodTransforms.isWood(accessor.getBlock()) && Fireproofing.isFireproof(accessor.getLevel(), accessor.getPosition())) {
                data.putBoolean(KEY, true);
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    /** Reads it back on the client and adds the line. */
    private enum ClientHalf implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (accessor.getServerData().getBoolean(KEY)) {
                tooltip.add(Component.translatable("wooddye.jade.fireproof").withStyle(ChatFormatting.GOLD));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }
}
