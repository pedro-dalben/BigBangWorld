package com.pedrodalben.bigbangworld.fabric;

import com.pedrodalben.bigbangworld.BigBangWorld;
import com.pedrodalben.bigbangworld.fabric.impl.FabricPlatformProvider;
import com.pedrodalben.bigbangworld.util.Platform;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class FabricModEntrypoint implements ModInitializer {

    @Override
    public void onInitialize() {
        // Initialize Platform bridge
        Platform.init(new FabricPlatformProvider());

        // Initialize common systems
        BigBangWorld.init();

        // Register Server Lifecycle events
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            FabricPlatformProvider.setServer(server);
            BigBangWorld.GameEvents.onServerStarting(server);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            BigBangWorld.GameEvents.onServerStopping(server);
            FabricPlatformProvider.setServer(null);
        });

        // Register Command Registration event
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            BigBangWorld.GameEvents.onRegisterCommands(dispatcher);
        });
    }
}
