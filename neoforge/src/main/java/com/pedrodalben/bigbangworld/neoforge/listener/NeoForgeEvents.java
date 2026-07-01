package com.pedrodalben.bigbangworld.neoforge.listener;

import com.pedrodalben.bigbangworld.BigBangWorld;
import com.pedrodalben.bigbangworld.restriction.WorldRestrictionService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

public class NeoForgeEvents {

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        BigBangWorld.GameEvents.onServerStarting(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        BigBangWorld.GameEvents.onServerStopping(event.getServer());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BigBangWorld.GameEvents.onRegisterCommands(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (event.getLevel() instanceof ServerLevel level) {
                if (WorldRestrictionService.isPlacementBlocked(player, event.getPlacedBlock(), level)) {
                    event.setCanceled(true);
                }
            }
        }
    }
}
