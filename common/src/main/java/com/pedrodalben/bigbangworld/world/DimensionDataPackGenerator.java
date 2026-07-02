package com.pedrodalben.bigbangworld.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pedrodalben.bigbangworld.config.ConfigManager;
import com.pedrodalben.bigbangworld.domain.WorldDefinition;
import com.pedrodalben.bigbangworld.domain.WorldType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

public class DimensionDataPackGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DimensionDataPackGenerator.class);

    public static void generate(Path outputDir, Collection<WorldDefinition> activeWorlds) {
        try {
            Files.createDirectories(outputDir.resolve("data/bigbangworld/dimension"));

            for (WorldDefinition def : activeWorlds) {
                generateDimensionJson(outputDir, def);
            }

            generatePackMeta(outputDir);

            LOGGER.info("[BigBangWorld] Generated dimension data pack at {} with {} dimensions", outputDir, activeWorlds.size());
        } catch (Exception e) {
            LOGGER.error("[BigBangWorld] Failed to generate dimension data pack", e);
        }
    }

    private static void generatePackMeta(Path outputDir) throws Exception {
        JsonObject meta = new JsonObject();
        JsonObject pack = new JsonObject();
        pack.addProperty("description", "BigBangWorld dimension definitions");
        pack.addProperty("pack_format", 48);
        meta.add("pack", pack);

        Path metaFile = outputDir.resolve("pack.mcmeta");
        Files.writeString(metaFile, meta.toString());
    }

    private static void generateDimensionJson(Path outputDir, WorldDefinition def) throws Exception {
        JsonObject root = new JsonObject();

        // Dimension type reference
        if (def.getType() == WorldType.SUPERFLAT || def.getType() == WorldType.VOID) {
            root.addProperty("type", "minecraft:overworld");
        } else {
            root.addProperty("type", "minecraft:overworld");
        }

        // Generator
        JsonObject generator = new JsonObject();
        generator.addProperty("seed", def.getSeed());

        switch (def.getType()) {
            case NORMAL -> {
                generator.addProperty("type", "minecraft:noise");
                generator.addProperty("settings", "minecraft:overworld");
                generator.add("biome_source", createNoiseBiomeSource());
            }
            case SUPERFLAT -> {
                generator.addProperty("type", "minecraft:flat");
                generator.add("settings", createSuperflatSettings(def));
            }
            case VOID -> {
                generator.addProperty("type", "bigbangworld:void_platform");
                generator.add("biome_source", createFixedBiomeSource("minecraft:the_void"));
            }
        }

        root.add("generator", generator);

        Path dimFile = outputDir.resolve("data/bigbangworld/dimension/" + def.getId() + ".json");
        Files.createDirectories(dimFile.getParent());
        Files.writeString(dimFile, root.toString());
    }

    private static JsonObject createNoiseBiomeSource() {
        JsonObject bs = new JsonObject();
        bs.addProperty("type", "minecraft:multi_noise");
        bs.addProperty("preset", "minecraft:overworld");
        return bs;
    }

    private static JsonObject createSuperflatSettings(WorldDefinition def) {
        JsonObject settings = new JsonObject();

        String biomeStr = ConfigManager.getConfig().getSuperflatBiome();
        if (biomeStr == null || biomeStr.isEmpty()) {
            biomeStr = "minecraft:plains";
        }
        settings.addProperty("biome", biomeStr);

        JsonArray layers = new JsonArray();
        java.util.List<String> layerDefs = ConfigManager.getConfig().getSuperflatLayers();
        if (layerDefs == null || layerDefs.isEmpty()) {
            layerDefs = java.util.List.of("minecraft:bedrock,1", "minecraft:dirt,3", "minecraft:grass_block,1");
        }
        for (String layerStr : layerDefs) {
            String[] parts = layerStr.split(",");
            JsonObject layer = new JsonObject();
            layer.addProperty("block", parts[0]);
            try {
                layer.addProperty("height", Integer.parseInt(parts[1].trim()));
            } catch (Exception e) {
                layer.addProperty("height", 1);
            }
            layers.add(layer);
        }
        settings.add("layers", layers);

        JsonObject structures = new JsonObject();
        structures.addProperty("structures", "minecraft:stronghold");
        settings.add("structures", structures);

        return settings;
    }

    private static JsonObject createFixedBiomeSource(String biomeId) {
        JsonObject bs = new JsonObject();
        bs.addProperty("type", "minecraft:fixed");
        bs.addProperty("biome", biomeId);
        return bs;
    }
}
