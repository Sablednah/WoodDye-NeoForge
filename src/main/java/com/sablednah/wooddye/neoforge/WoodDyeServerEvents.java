package com.sablednah.wooddye.neoforge;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;

/**
 * Server-side registrations on the NeoForge game event bus: permission nodes, the {@code /wooddye}
 * command, and the right-click dyeing handler. Registered from
 * {@link com.sablednah.wooddye.WoodDye}.
 *
 * <p>Axe stripping of the fireproof logs needs no handler here — it is data-driven, via the
 * {@code neoforge:strippables} data map that {@code tools/gen_resources.py} generates.
 */
public final class WoodDyeServerEvents {

    private WoodDyeServerEvents() {}

    @SubscribeEvent
    public static void onGatherPermissionNodes(PermissionGatherEvent.Nodes event) {
        WoodDyePermissions.onGatherNodes(event);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        WoodDyeCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        WoodDyeInteractions.handle(event);
    }
}
