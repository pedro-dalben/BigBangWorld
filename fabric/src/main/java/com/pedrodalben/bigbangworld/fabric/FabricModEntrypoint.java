package com.pedrodalben.bigbangworld.fabric;

import com.pedrodalben.bigbangworld.BigBangWorld;
import com.pedrodalben.bigbangworld.fabric.impl.FabricPlatformProvider;
import com.pedrodalben.bigbangworld.util.Platform;
import com.pedrodalben.bigbangworld.world.VoidPlatformChunkGenerator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

public class FabricModEntrypoint implements ModInitializer {

    @Override
    public void onInitialize() {
        Platform.init(new FabricPlatformProvider());
        BigBangWorld.init();

        Registry.register(
            BuiltInRegistries.CHUNK_GENERATOR,
            ResourceLocation.fromNamespaceAndPath("bigbangworld", "void_platform"),
            VoidPlatformChunkGenerator.CODEC
        );

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            FabricPlatformProvider.setServer(server);
            BigBangWorld.GameEvents.onServerStarting(server);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            BigBangWorld.GameEvents.onServerStopping(server);
            FabricPlatformProvider.setServer(null);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            BigBangWorld.GameEvents.onRegisterCommands(dispatcher);
        });
    }
}
