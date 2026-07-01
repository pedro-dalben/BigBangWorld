package com.pedrodalben.bigbangworld.util;

import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

public class PermissionService {

    public static boolean hasPermission(ServerPlayer player, String permission) {
        if (player == null) {
            return false;
        }

        // Minecraft OP bypass: level 4 players should have all permissions
        if (player.getServer() != null && player.getServer().getPlayerList().isOp(player.getGameProfile())) {
            return true;
        }

        try {
            // Check BigBangEssentials PermissionAPI via reflection
            Class<?> apiClass = Class.forName("com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI");
            java.lang.reflect.Method hasPermMethod = apiClass.getMethod("hasPermission", UUID.class, String.class);
            return (boolean) hasPermMethod.invoke(null, player.getUUID(), permission);
        } catch (Throwable t) {
            // Fallback: only operators are allowed
            return player.getServer() != null && player.getServer().getPlayerList().isOp(player.getGameProfile());
        }
    }
}
