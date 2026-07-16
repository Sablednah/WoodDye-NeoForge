package com.sablednah.wooddye;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.sablednah.wooddye.neoforge.WoodDyeServerEvents;
import com.sablednah.wooddye.registry.WoodDyeBlocks;
import com.sablednah.wooddye.registry.WoodDyeCreativeTab;
import com.sablednah.wooddye.registry.WoodDyeItems;
import com.sablednah.wooddye.registry.WoodDyeRecipes;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

/**
 * WoodDye — main mod entrypoint (common: loaded on both client and dedicated server).
 *
 * <p>A modern NeoForge rewrite of the classic WoodDye Bukkit plugin. Right-click wooden
 * planks/slabs/stairs with a dye to shift them one step along a light&rarr;dark shade chain, or with
 * Magma Cream to convert them into a matching, non-flammable {@code fireproof_*} block.
 *
 * <p>Design note: reusable, loader-agnostic data (the ordered wood shade chain) lives under
 * {@code com.sablednah.wooddye.core}; NeoForge-specific glue lives under {@code registry} and
 * {@code neoforge}.
 */
@Mod(WoodDye.MODID)
public class WoodDye {

    /** The mod id — must match {@code mod_id} in gradle.properties and the modId in neoforge.mods.toml. */
    public static final String MODID = "wooddye";

    /** Shared logger. */
    public static final Logger LOGGER = LogUtils.getLogger();

    public WoodDye(IEventBus modEventBus, ModContainer modContainer) {
        // Server-side (common) configuration: useItems, fireProof, debugMode.
        modContainer.registerConfig(ModConfig.Type.COMMON, WoodDyeConfig.SPEC);

        // Content registration on the mod event bus (blocks before items before the creative tab).
        WoodDyeBlocks.register(modEventBus);
        WoodDyeItems.register(modEventBus);
        WoodDyeCreativeTab.register(modEventBus);
        WoodDyeRecipes.register(modEventBus);

        // Game-bus glue: permission nodes, the /wooddye command, and the right-click handler.
        NeoForge.EVENT_BUS.register(WoodDyeServerEvents.class);

        LOGGER.info("WoodDye {} initialising", modContainer.getModInfo().getVersion());
    }
}
