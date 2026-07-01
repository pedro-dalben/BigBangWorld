package com.pedrodalben.bigbangworld.util;

import net.minecraft.server.MinecraftServer;
import java.nio.file.Path;

public interface PlatformProvider {
    MinecraftServer getCurrentServer();
    Path getConfigDir();
    Path getGameDir();
    boolean isModLoaded(String modId);
}
