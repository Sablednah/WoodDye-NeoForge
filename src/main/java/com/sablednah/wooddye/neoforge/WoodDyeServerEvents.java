package com.sablednah.wooddye.neoforge;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;

/**
 * Server-side registrations on the NeoForge game event bus: permission nodes, the {@code /wooddye}
 * command, the right-click dyeing handler, and rebuilding the dye chains. Registered from
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

    /** The dye chains are built from block tags, so a datapack (re)load makes them stale. */
    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        WoodTransforms.invalidate();
    }

    /**
     * Build the chains as the server comes up rather than on the first click, so the order is in
     * the log from the start and any texture measuring is not charged to a player's right-click.
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        WoodTransforms.invalidate();
        WoodTransforms.woodOrder();
    }
}
