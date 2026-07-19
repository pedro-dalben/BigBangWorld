package com.pedrodalben.bigbangworld;

import com.pedrodalben.bigbangworld.config.ConfigManager;
import com.pedrodalben.bigbangworld.world.WorldManager;
import com.pedrodalben.bigbangworld.world.WorldgenGateway;
import com.pedrodalben.bigbangworld.command.BigBangWorldCommand;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BigBangWorld {
    private static final Logger LOGGER = LoggerFactory.getLogger(BigBangWorld.class);
    private static BigBangWorld instance;

    public static void init() {
        instance = new BigBangWorld();
    }

    public BigBangWorld() {
        LOGGER.info("╔════════════════════════════════════════════════════════════════╗");
        LOGGER.info("║               BigBangWorld v1.0.0.0 Mod Initializing           ║");
        LOGGER.info("╚════════════════════════════════════════════════════════════════╝");
    }

    public static class GameEvents {
        public static void onServerStarting(MinecraftServer server) {
            LOGGER.info("⚙ Initializing BigBangWorld configuration...");
            ConfigManager.load();
            ConfigManager.save();

            LOGGER.info("⚙ Initializing WorldManager...");
            WorldManager.getInstance().init(server);
            WorldgenGateway.prepare(ConfigManager.getConfig().getWorlds().stream()
                    .filter(def -> def.isActive())
                    .toList());
        }

        public static void onServerStopping(MinecraftServer server) {
            LOGGER.info("⚙ Shutting down BigBangWorld...");
            ConfigManager.save();
            WorldManager.getInstance().shutdown();
            LOGGER.info("⚙ BigBangWorld shutdown complete.");
        }

        public static void onRegisterCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
            LOGGER.info("⚙ Registering BigBangWorld commands...");
            BigBangWorldCommand.register(dispatcher);
        }
    }
}
