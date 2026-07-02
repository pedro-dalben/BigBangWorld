package com.pedrodalben.bigbangworld.mixin;

import com.pedrodalben.bigbangworld.world.WorldBootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MinecraftServerBootstrapMixin {

    @Shadow
    private LevelStorageSource.LevelStorageAccess storageSource;

    @Inject(method = "loadLevel", at = @At("HEAD"))
    private void onLoadLevel(CallbackInfo ci) {
        WorldBootstrap.bootstrap(storageSource);
    }
}
