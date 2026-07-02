package com.pedrodalben.bigbangworld.neoforge.mixin;

import com.pedrodalben.bigbangworld.accessor.MinecraftServerAccessor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin implements MinecraftServerAccessor {
    @Override
    @Accessor(value = "levels", remap = false)
    public abstract Map<ResourceKey<Level>, ServerLevel> getLevels();

    @Override
    @Accessor(value = "storageSource", remap = false)
    public abstract LevelStorageSource.LevelStorageAccess getStorageSource();
}
