package com.pedrodalben.bigbangworld.neoforge;

import com.pedrodalben.bigbangworld.BigBangWorld;
import com.pedrodalben.bigbangworld.neoforge.impl.NeoForgePlatformProvider;
import com.pedrodalben.bigbangworld.neoforge.listener.NeoForgeEvents;
import com.pedrodalben.bigbangworld.util.Platform;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod("bigbangworld")
public class NeoForgeModEntrypoint {

    public NeoForgeModEntrypoint(IEventBus modEventBus) {
        // Initialize Platform bridge
        Platform.init(new NeoForgePlatformProvider());

        // Initialize common systems
        BigBangWorld.init();

        // Register NeoForge event listeners
        NeoForge.EVENT_BUS.register(NeoForgeEvents.class);
    }
}
