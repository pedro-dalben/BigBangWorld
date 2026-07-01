package com.pedrodalben.bigbangworld.util;

import net.minecraft.server.MinecraftServer;
import java.nio.file.Path;

public class Platform {
    private static PlatformProvider provider;

    public static void init(PlatformProvider provider) {
        if (Platform.provider != null) {
            throw new IllegalStateException("Platform already initialized!");
        }
        Platform.provider = provider;
    }

    public static MinecraftServer getCurrentServer() {
        return provider.getCurrentServer();
    }

    public static Path getConfigDir() {
        return provider.getConfigDir();
    }

    public static Path getGameDir() {
        return provider.getGameDir();
    }

    public static boolean isModLoaded(String modId) {
        return provider.isModLoaded(modId);
    }
}
