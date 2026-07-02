package com.pedrodalben.bigbangworld.world;

import com.pedrodalben.bigbangworld.config.ConfigManager;
import com.pedrodalben.bigbangworld.config.OperationsManager;
import com.pedrodalben.bigbangworld.config.OperationsManager.WorldOperation;
import com.pedrodalben.bigbangworld.domain.WorldDefinition;
import com.pedrodalben.bigbangworld.domain.WorldLifecycleState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WorldBootstrap {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorldBootstrap.class);

    public static void bootstrap(LevelStorageSource.LevelStorageAccess storageSource) {
        LOGGER.info("[BigBangWorld] Bootstrap started");

        ConfigManager.load();
        OperationsManager.load();

        Path worldDir = storageSource.getLevelDirectory().path();

        migrateOldStates();

        List<WorldOperation> pendingOps = new ArrayList<>(OperationsManager.getOperations());

        for (WorldOperation op : pendingOps) {
            try {
                processOperation(op, worldDir, storageSource);
            } catch (Exception e) {
                LOGGER.error("[BigBangWorld] Failed to process operation for world '{}'", op.worldId(), e);
            }
        }

        recoverOrphanedStates();

        Collection<WorldDefinition> activeWorlds = ConfigManager.getConfig().getWorlds().stream()
                .filter(def -> def.getState() == WorldLifecycleState.ACTIVE)
                .collect(Collectors.toList());

        Path dataPackDir = worldDir.resolve("datapacks/bigbangworld-dimensions");
        DimensionDataPackGenerator.generate(dataPackDir, activeWorlds);

        LOGGER.info("[BigBangWorld] Bootstrap complete. {} worlds configured.", activeWorlds.size());
    }

    private static void migrateOldStates() {
        List<WorldDefinition> worlds = ConfigManager.getConfig().getWorlds();
        boolean changed = false;
        for (WorldDefinition def : worlds) {
            if (def.getState() == null) {
                def.setState(WorldLifecycleState.FAILED);
                changed = true;
                LOGGER.warn("[BigBangWorld] World '{}' had null state, marked FAILED", def.getId());
            }
        }
        if (changed) {
            ConfigManager.save();
        }
    }

    private static void recoverOrphanedStates() {
        List<WorldDefinition> worlds = ConfigManager.getConfig().getWorlds();
        boolean changed = false;
        for (WorldDefinition def : worlds) {
            if (def.getState() == WorldLifecycleState.PENDING_CREATE
                || def.getState() == WorldLifecycleState.PENDING_RESET
                || def.getState() == WorldLifecycleState.PENDING_DELETE) {

                boolean hasOperation = OperationsManager.findOperation(def.getId()).isPresent();
                if (!hasOperation) {
                    LOGGER.warn("[BigBangWorld] World '{}' in state {} but no operation found. Marking FAILED for manual recovery.",
                        def.getId(), def.getState());
                    def.setState(WorldLifecycleState.FAILED);
                    changed = true;
                }
            }
        }
        if (changed) {
            ConfigManager.save();
        }
    }

    private static void processOperation(WorldOperation op, Path worldDir, LevelStorageSource.LevelStorageAccess storageSource) {
        WorldDefinition def = findWorldDef(op.worldId());
        if (def == null) {
            LOGGER.error("[BigBangWorld] Operation references unknown world '{}', removing", op.worldId());
            OperationsManager.removeOperation(op.worldId());
            return;
        }

        switch (op.targetState()) {
            case PENDING_CREATE -> processCreate(def, worldDir, storageSource);
            case PENDING_RESET -> processReset(def, op, worldDir, storageSource);
            case PENDING_DELETE -> processDelete(def, worldDir, storageSource);
            default -> LOGGER.warn("[BigBangWorld] Unknown operation target state: {}", op.targetState());
        }

        OperationsManager.removeOperation(op.worldId());
    }

    private static void processCreate(WorldDefinition def, Path worldDir, LevelStorageSource.LevelStorageAccess storageSource) {
        LOGGER.info("[BigBangWorld] Processing PENDING_CREATE for world '{}'", def.getId());
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath("bigbangworld", def.getId()));
        Path dimPath = storageSource.getDimensionPath(dimKey);
        try {
            Files.createDirectories(dimPath);
        } catch (IOException e) {
            LOGGER.error("[BigBangWorld] Failed to create dimension directory for '{}'", def.getId(), e);
            def.setState(WorldLifecycleState.FAILED);
            ConfigManager.save();
            return;
        }
        def.setState(WorldLifecycleState.ACTIVE);
        def.setCreatedAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        def.setLastResetAt(null);
        def.setResetCount(0);
        ConfigManager.save();
        LOGGER.info("[BigBangWorld] World '{}' created and set to ACTIVE", def.getId());
    }

    private static void processReset(WorldDefinition def, WorldOperation op, Path worldDir, LevelStorageSource.LevelStorageAccess storageSource) {
        LOGGER.info("[BigBangWorld] Processing PENDING_RESET for world '{}'", def.getId());
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath("bigbangworld", def.getId()));
        Path dimPath = storageSource.getDimensionPath(dimKey);

        if (Files.exists(dimPath)) {
            try {
                boolean backup = ConfigManager.getConfig().isBackupBeforeReset();
                if (backup) {
                    String timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
                            .withZone(java.time.ZoneId.systemDefault()).format(Instant.now());
                    Path backupRoot = worldDir.resolve("bigbangworld-backups").resolve(def.getId());
                    Path backupDir = backupRoot.resolve(timestamp);
                    Files.createDirectories(backupRoot);
                    moveDirectory(dimPath, backupDir);
                    LOGGER.info("[BigBangWorld] Backed up '{}' to '{}'", def.getId(), backupDir);
                    cleanupBackups(backupRoot);
                } else {
                    deleteDirectory(dimPath);
                }
            } catch (IOException e) {
                LOGGER.error("[BigBangWorld] Failed to backup/delete world files for reset '{}'", def.getId(), e);
                def.setState(WorldLifecycleState.FAILED);
                ConfigManager.save();
                return;
            }
        }

        long newSeed = def.getSeed();
        if ("random-seed".equalsIgnoreCase(op.seedOption())) {
            newSeed = new Random().nextLong();
        } else if ("seed".equalsIgnoreCase(op.seedOption()) && op.seedValue() != null) {
            try {
                newSeed = Long.parseLong(op.seedValue());
            } catch (NumberFormatException e) {
                newSeed = op.seedValue().hashCode();
            }
        }
        def.setSeed(newSeed);
        def.setState(WorldLifecycleState.ACTIVE);
        def.setLastResetAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        def.setResetCount(def.getResetCount() + 1);

        try {
            Files.createDirectories(dimPath);
        } catch (IOException e) {
            LOGGER.error("[BigBangWorld] Failed to recreate dimension directory after reset '{}'", def.getId(), e);
            def.setState(WorldLifecycleState.FAILED);
        }
        ConfigManager.save();
        LOGGER.info("[BigBangWorld] World '{}' reset complete. New seed: {}", def.getId(), newSeed);
    }

    private static void processDelete(WorldDefinition def, Path worldDir, LevelStorageSource.LevelStorageAccess storageSource) {
        LOGGER.info("[BigBangWorld] Processing PENDING_DELETE for world '{}'", def.getId());
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath("bigbangworld", def.getId()));
        Path dimPath = storageSource.getDimensionPath(dimKey);

        if (Files.exists(dimPath)) {
            try {
                String timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
                        .withZone(java.time.ZoneId.systemDefault()).format(Instant.now());
                Path quarantineDir = worldDir.resolve("bigbangworld-quarantine").resolve(def.getId() + "_" + timestamp);
                Files.createDirectories(quarantineDir.getParent());
                moveDirectory(dimPath, quarantineDir);
                LOGGER.info("[BigBangWorld] Moved '{}' to quarantine at '{}'", def.getId(), quarantineDir);
            } catch (IOException e) {
                LOGGER.error("[BigBangWorld] Failed to quarantine world files for delete '{}'", def.getId(), e);
                def.setState(WorldLifecycleState.FAILED);
                ConfigManager.save();
                return;
            }
        }

        ConfigManager.getConfig().getWorlds().remove(def);
        ConfigManager.save();
        LOGGER.info("[BigBangWorld] World '{}' deleted from config", def.getId());
    }

    private static WorldDefinition findWorldDef(String id) {
        return ConfigManager.getConfig().getWorlds().stream()
                .filter(w -> w.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    private static void moveDirectory(Path source, Path target) throws IOException {
        if (!Files.exists(source)) return;
        Files.createDirectories(target.getParent());
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try {
                copyDirectory(source, target);
                deleteDirectory(source);
            } catch (IOException e2) {
                LOGGER.error("[BigBangWorld] Failed to move directory, copy+delete also failed", e2);
                throw e;
            }
        }
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> walk = Files.walk(source)) {
            walk.forEach(src -> {
                try {
                    Path dest = target.resolve(source.relativize(src));
                    if (Files.isDirectory(src)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private static void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    private static void cleanupBackups(Path backupsRoot) {
        try {
            if (!Files.exists(backupsRoot)) return;
            java.io.File[] files = backupsRoot.toFile().listFiles(java.io.File::isDirectory);
            if (files == null) return;
            List<java.io.File> dirs = new ArrayList<>(java.util.Arrays.asList(files));
            dirs.sort(java.util.Comparator.comparingLong(java.io.File::lastModified));
            int max = ConfigManager.getConfig().getMaxBackupsPerWorld();
            while (dirs.size() > max) {
                java.io.File toDelete = dirs.remove(0);
                deleteDirectory(toDelete.toPath());
            }
        } catch (Exception e) {
            LOGGER.error("[BigBangWorld] Failed to cleanup old backups", e);
        }
    }
}
