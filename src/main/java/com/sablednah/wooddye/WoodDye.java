package com.sablednah.wooddye;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.sablednah.wooddye.compat.CreateFireproof;
import com.sablednah.wooddye.fireproof.FireproofComponents;
import com.sablednah.wooddye.fireproof.FireproofEvents;
import com.sablednah.wooddye.neoforge.WoodDyeServerEvents;
import com.sablednah.wooddye.neoforge.WoodTransforms;
import com.sablednah.wooddye.registry.WoodDyeBlocks;
import com.sablednah.wooddye.registry.WoodDyeCreativeTab;
import com.sablednah.wooddye.registry.WoodDyeItems;
import com.sablednah.wooddye.registry.WoodDyeRecipes;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * WoodDye — main mod entrypoint (common: loaded on both client and dedicated server).
 *
 * <p>A modern NeoForge rewrite of the classic WoodDye Bukkit plugin. Right-click wooden
 * planks/slabs/stairs with a dye to shift them one step along a light&rarr;dark shade chain, or with
 * Magma Cream to convert them into a matching, non-flammable {@code fireproof_*} block.
 *
 * <p>Design note: reusable, loader-agnostic data (the ordered wood shade chain) lives under
 * {@code com.sablednah.wooddye.core}; loader-specific glue lives under {@code registry} and
 * {@code neoforge}. That package keeps its name on this MinecraftForge line, so that a change made
 * on the NeoForge lines still applies here file for file.
 */
@Mod(WoodDye.MODID)
public class WoodDye {

    /** The mod id — must match {@code mod_id} in gradle.properties and the modId in mods.toml. */
    public static final String MODID = "wooddye";

    /** Shared logger. */
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * No-argument, because FML on 1.20.1 constructs a mod that way: it does not hand the bus and
     * the container to the constructor as NeoForge does. The same two are fetched here instead.
     */
    public WoodDye() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModContainer modContainer = ModLoadingContext.get().getActiveContainer();

        // Server-side (common) configuration: useItems, fireProof, debugMode.
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, WoodDyeConfig.SPEC);

        // Content registration on the mod event bus (blocks before items before the creative tab).
        WoodDyeBlocks.register(modEventBus);
        WoodDyeItems.register(modEventBus);
        WoodDyeCreativeTab.register(modEventBus);
        WoodDyeRecipes.register(modEventBus);
        FireproofComponents.register(modEventBus);

        // Game-bus glue: permission nodes, the /wooddye command, and the right-click handler.
        MinecraftForge.EVENT_BUS.register(WoodDyeServerEvents.class);
        MinecraftForge.EVENT_BUS.register(FireproofEvents.class);

        // Create moves blocks about in contraptions; fireproofing has to go with them.
        if (ModList.get().isLoaded("create")) {
            CreateFireproof.register();
        }

        // Which woods dye, and in what order, is config; a change there makes the chains stale.
        modEventBus.addListener((ModConfigEvent event) -> WoodTransforms.invalidate());

        LOGGER.info("WoodDye {} initialising", modContainer.getModInfo().getVersion());
    }
}
