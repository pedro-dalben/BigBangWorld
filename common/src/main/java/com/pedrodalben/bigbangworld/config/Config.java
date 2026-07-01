package com.pedrodalben.bigbangworld.config;

import com.pedrodalben.bigbangworld.domain.WorldDefinition;
import java.util.ArrayList;
import java.util.List;

public class Config {
    private String defaultExplorationWorld = "exploracao";
    private String fallbackDimension = "minecraft:overworld";
    private boolean backupBeforeReset = true;
    private int maxBackupsPerWorld = 3;
    private double defaultWorldBorderDiameter = 20000.0;
    private String voidPlatformMaterial = "minecraft:stone";
    private List<String> restrictedPlacementBlocks = new ArrayList<>(List.of(
        "waystones:waystone",
        "waystones:sharestone",
        "waystones:portstone",
        "waystones:warp_plate"
    ));
    private List<String> restrictedPlacementNamespaces = new ArrayList<>();
    private String superflatBiome = "minecraft:plains";
    private List<String> superflatLayers = new ArrayList<>(List.of(
        "minecraft:bedrock,1",
        "minecraft:dirt,3",
        "minecraft:grass_block,1"
    ));
    private List<WorldDefinition> worlds = new ArrayList<>();

    public Config() {}

    public String getDefaultExplorationWorld() { return defaultExplorationWorld; }
    public void setDefaultExplorationWorld(String defaultExplorationWorld) { this.defaultExplorationWorld = defaultExplorationWorld; }

    public String getFallbackDimension() { return fallbackDimension; }
    public void setFallbackDimension(String fallbackDimension) { this.fallbackDimension = fallbackDimension; }

    public boolean isBackupBeforeReset() { return backupBeforeReset; }
    public void setBackupBeforeReset(boolean backupBeforeReset) { this.backupBeforeReset = backupBeforeReset; }

    public int getMaxBackupsPerWorld() { return maxBackupsPerWorld; }
    public void setMaxBackupsPerWorld(int maxBackupsPerWorld) { this.maxBackupsPerWorld = maxBackupsPerWorld; }

    public double getDefaultWorldBorderDiameter() { return defaultWorldBorderDiameter; }
    public void setDefaultWorldBorderDiameter(double defaultWorldBorderDiameter) { this.defaultWorldBorderDiameter = defaultWorldBorderDiameter; }

    public String getVoidPlatformMaterial() { return voidPlatformMaterial; }
    public void setVoidPlatformMaterial(String voidPlatformMaterial) { this.voidPlatformMaterial = voidPlatformMaterial; }

    public List<String> getRestrictedPlacementBlocks() { return restrictedPlacementBlocks; }
    public void setRestrictedPlacementBlocks(List<String> restrictedPlacementBlocks) { this.restrictedPlacementBlocks = restrictedPlacementBlocks; }

    public List<String> getRestrictedPlacementNamespaces() { return restrictedPlacementNamespaces; }
    public void setRestrictedPlacementNamespaces(List<String> restrictedPlacementNamespaces) { this.restrictedPlacementNamespaces = restrictedPlacementNamespaces; }

    public String getSuperflatBiome() { return superflatBiome; }
    public void setSuperflatBiome(String superflatBiome) { this.superflatBiome = superflatBiome; }

    public List<String> getSuperflatLayers() { return superflatLayers; }
    public void setSuperflatLayers(List<String> superflatLayers) { this.superflatLayers = superflatLayers; }

    public List<WorldDefinition> getWorlds() { return worlds; }
    public void setWorlds(List<WorldDefinition> worlds) { this.worlds = worlds; }
}
