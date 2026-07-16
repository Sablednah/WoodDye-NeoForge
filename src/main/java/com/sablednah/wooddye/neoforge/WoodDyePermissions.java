package com.sablednah.wooddye.neoforge;

import com.sablednah.wooddye.WoodDye;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/**
 * The {@code wooddye.candye} permission node.
 *
 * <p>Uses NeoForge's {@link PermissionAPI}. With no permissions manager installed, the node's
 * default resolver applies (here: everyone may dye). Install a manager such as LuckPerms (which has
 * a NeoForge build) to restrict {@code wooddye.candye} per group — no extra code required.
 */
public final class WoodDyePermissions {

    private WoodDyePermissions() {}

    /** Whether a player may dye/fireproof wood in the world. Default: everyone. */
    public static final PermissionNode<Boolean> CAN_DYE = new PermissionNode<>(
            WoodDye.MODID, "candye", PermissionTypes.BOOLEAN,
            (player, playerUUID, context) -> Boolean.TRUE);

    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(CAN_DYE);
    }

    public static boolean canDye(ServerPlayer player) {
        return PermissionAPI.getPermission(player, CAN_DYE);
    }
}
