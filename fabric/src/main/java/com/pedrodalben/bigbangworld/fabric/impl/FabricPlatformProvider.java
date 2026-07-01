package com.pedrodalben.bigbangworld.fabric.impl;

import com.pedrodalben.bigbangworld.util.PlatformProvider;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import java.nio.file.Path;

public class FabricPlatformProvider implements PlatformProvider {
    private static MinecraftServer activeServer;

    public static void setServer(MinecraftServer server) {
        activeServer = server;
    }

    @Override
    public MinecraftServer getCurrentServer() {
        return activeServer;
    }

    @Override
    public Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public Path getGameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
}
