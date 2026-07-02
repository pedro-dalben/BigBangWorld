package com.pedrodalben.bigbangworld.accessor;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.util.Map;

public interface MinecraftServerAccessor {
    Map<ResourceKey<Level>, ServerLevel> getLevels();

    LevelStorageSource.LevelStorageAccess getStorageSource();
}
