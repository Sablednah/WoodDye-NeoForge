package com.sablednah.wooddye.client;

import com.sablednah.wooddye.WoodDye;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only entrypoint. Its sole job is to register the in-game configuration screen so the
 * "Config" button in the Mods menu opens {@code wooddye-common.toml} for editing (single-player /
 * LAN host). The mod itself is fully server-side; this class never loads on a dedicated server.
 */
@Mod(value = WoodDye.MODID, dist = Dist.CLIENT)
public final class WoodDyeClient {

    public WoodDyeClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
