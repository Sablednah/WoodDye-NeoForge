package com.sablednah.wooddye.neoforge;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;

/**
 * Server-side registrations on the Forge game event bus: permission nodes, the {@code /wooddye}
 * command, the right-click dyeing handler, axe stripping, and rebuilding the dye chains. Registered
 * from {@link com.sablednah.wooddye.WoodDye}.
 *
 * <p>Axe stripping of the fireproof logs is a handler on this version, where the NeoForge lines
 * use a data map: Forge 1.20.1 has no data maps. See {@link WoodDyeStripping}.
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

    /** Not server-only: an axe is used on both sides, and the client predicts the result. */
    @SubscribeEvent
    public static void onToolModification(BlockEvent.BlockToolModificationEvent event) {
        WoodDyeStripping.handle(event);
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
        WoodTransforms.woodOrder(); // tags were bound before the level loaded; a build now is current
    }
}
