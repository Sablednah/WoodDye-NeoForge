package com.sablednah.wooddye.compat;

import com.sablednah.wooddye.WoodDye;
import com.sablednah.wooddye.fireproof.Fireproofing;
import com.sablednah.wooddye.neoforge.WoodTransforms;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
public final class JadeFireproof implements IWailaPlugin, IServerDataProvider<BlockAccessor>, IBlockComponentProvider {

    private static final Identifier UID = Identifier.fromNamespaceAndPath(WoodDye.MODID, "fireproof");
    private static final String KEY = "wooddye_fireproof";

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(this, Block.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(this, Block.class);
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (WoodTransforms.isWood(accessor.getBlock()) && Fireproofing.isFireproof(accessor.getLevel(), accessor.getPosition())) {
            data.putBoolean(KEY, true);
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (accessor.getServerData().getBooleanOr(KEY, false)) {
            tooltip.add(Component.translatable("wooddye.jade.fireproof").withStyle(ChatFormatting.GOLD));
        }
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
