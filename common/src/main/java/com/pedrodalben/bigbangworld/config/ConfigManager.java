package com.pedrodalben.bigbangworld.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pedrodalben.bigbangworld.domain.*;
import com.pedrodalben.bigbangworld.util.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config config = new Config();

    public static Config getConfig() {
        return config;
    }

    public static void load() {
        try {
            Path configPath = getConfigPath();
            if (!Files.exists(configPath)) {
                LOGGER.info("Config file not found, creating default config...");
                createDefaultConfig();
                save();
                return;
            }

            try (FileReader reader = new FileReader(configPath.toFile())) {
                config = GSON.fromJson(reader, Config.class);
                if (config == null) {
                    config = new Config();
                }
                LOGGER.info("Config loaded successfully with {} worlds defined.", config.getWorlds().size());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load config, using defaults.", e);
            config = new Config();
        }
    }

    public static void save() {
        try {
            Path configPath = getConfigPath();
            Path parent = configPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            try (FileWriter writer = new FileWriter(configPath.toFile())) {
                GSON.toJson(config, writer);
                LOGGER.debug("Config saved successfully.");
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save config.", e);
        }
    }

    private static Path getConfigPath() {
        return Platform.getConfigDir().resolve("bigbangworld/config.json");
    }

    private static void createDefaultConfig() {
        config = new Config();
        config.setDefaultExplorationWorld("exploracao");
        config.setFallbackDimension("minecraft:overworld");
        config.setBackupBeforeReset(true);
        config.setMaxBackupsPerWorld(3);
        config.setDefaultWorldBorderDiameter(20000.0);
        config.setVoidPlatformMaterial("minecraft:stone");
        config.setSuperflatBiome("minecraft:plains");
        config.setSuperflatLayers(new ArrayList<>(List.of(
            "minecraft:bedrock,1",
            "minecraft:dirt,3",
            "minecraft:grass_block,1"
        )));

        WorldDefinition def = new WorldDefinition();
        def.setId("exploracao");
        def.setDisplayName("Mundo de Exploracao");
        def.setType(WorldType.NORMAL);
        def.setSeed(0L);
        def.setDimensionKey("bigbangworld:exploracao");
        def.setState(WorldLifecycleState.ACTIVE);
        def.setPublicAccess(true);
        def.setCreatedAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        def.setLastResetAt(null);
        def.setResetCount(0);

        SpawnPosition spawn = new SpawnPosition(0.5, 96.0, 0.5, 0.0f, 0.0f);
        def.setSpawn(spawn);

        BorderConfig border = new BorderConfig(true, 20000);
        def.setBorder(border);

        WorldPolicies policies = new WorldPolicies(false, false, false, false);
        def.setPolicies(policies);

        config.getWorlds().add(def);
    }
}
