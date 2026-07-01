package com.pedrodalben.bigbangworld.api;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

public interface WorldPolicyApi {
    boolean isManagedWorld(ServerLevel level);
    boolean isTemporaryWorld(ServerLevel level);
    boolean isHomeCreationAllowed(ServerPlayer player);
    boolean isWaystonePlacementAllowed(ServerPlayer player, BlockState state);
    boolean isClaimCreationAllowed(ServerPlayer player);
    boolean isChunkLoadingAllowed(ServerPlayer player);
}
