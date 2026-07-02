package com.pedrodalben.bigbangworld.restriction;

import com.pedrodalben.bigbangworld.api.BigBangWorldApi;
import com.pedrodalben.bigbangworld.api.WorldPolicyApi;
import com.pedrodalben.bigbangworld.config.ConfigManager;
import com.pedrodalben.bigbangworld.util.TranslationUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

public class WorldRestrictionService {

    private static boolean isWaystoneBlock(String blockIdStr, String namespace) {
        return "waystones".equals(namespace) ||
               "fwaystones".equals(namespace) ||
               blockIdStr.contains("waystone") ||
               blockIdStr.contains("sharestone") ||
               blockIdStr.contains("portstone") ||
               blockIdStr.contains("warp_plate");
    }

    public static boolean isPlacementBlocked(ServerPlayer player, BlockState state, ServerLevel level) {
        if (state == null || level == null) {
            return false;
        }

        WorldPolicyApi api = BigBangWorldApi.get();
        if (api == null) {
            return false;
        }

        if (!api.isTemporaryWorld(level)) {
            return false;
        }

        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String blockIdStr = blockId.toString();
        String namespace = blockId.getNamespace();

        if (player != null && isWaystoneBlock(blockIdStr, namespace) && api.isWaystonePlacementAllowed(player, state)) {
            return false;
        }

        var config = ConfigManager.getConfig();
        boolean blockRestricted = config.getRestrictedPlacementBlocks().contains(blockIdStr);
        boolean namespaceRestricted = config.getRestrictedPlacementNamespaces().contains(namespace);

        if (blockRestricted || namespaceRestricted || isWaystoneBlock(blockIdStr, namespace)) {
            if (player != null) {
                player.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.waystone_placement_blocked"));
            }
            return true;
        }

        return false;
    }
}
