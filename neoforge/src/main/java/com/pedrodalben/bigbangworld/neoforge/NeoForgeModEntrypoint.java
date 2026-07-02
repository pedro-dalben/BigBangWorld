package com.pedrodalben.bigbangworld.neoforge;

import com.pedrodalben.bigbangworld.BigBangWorld;
import com.pedrodalben.bigbangworld.neoforge.impl.NeoForgePlatformProvider;
import com.pedrodalben.bigbangworld.neoforge.listener.NeoForgeEvents;
import com.pedrodalben.bigbangworld.util.Platform;
import com.pedrodalben.bigbangworld.world.VoidPlatformChunkGenerator;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod("bigbangworld")
public class NeoForgeModEntrypoint {

    public NeoForgeModEntrypoint(IEventBus modEventBus) {
        Platform.init(new NeoForgePlatformProvider());
        BigBangWorld.init();

        modEventBus.addListener(this::onRegister);

        NeoForge.EVENT_BUS.register(NeoForgeEvents.class);
    }

    private void onRegister(RegisterEvent event) {
        event.register(BuiltInRegistries.CHUNK_GENERATOR.key(), helper -> {
            helper.register(
                ResourceLocation.fromNamespaceAndPath("bigbangworld", "void_platform"),
                VoidPlatformChunkGenerator.CODEC
            );
        });
    }
}
