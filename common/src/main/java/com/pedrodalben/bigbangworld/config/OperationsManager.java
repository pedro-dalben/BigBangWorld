package com.pedrodalben.bigbangworld.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.pedrodalben.bigbangworld.domain.WorldLifecycleState;
import com.pedrodalben.bigbangworld.util.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class OperationsManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(OperationsManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static List<WorldOperation> operations = new ArrayList<>();

    public static List<WorldOperation> getOperations() {
        return operations;
    }

    public static void load() {
        try {
            Path path = getOperationsPath();
            if (!Files.exists(path)) {
                operations = new ArrayList<>();
                return;
            }
            try (FileReader reader = new FileReader(path.toFile())) {
                operations = GSON.fromJson(reader, new TypeToken<List<WorldOperation>>() {}.getType());
                if (operations == null) operations = new ArrayList<>();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load operations", e);
            operations = new ArrayList<>();
        }
    }

    public static void save() {
        try {
            Path path = getOperationsPath();
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            try (FileWriter writer = new FileWriter(path.toFile())) {
                GSON.toJson(operations, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save operations", e);
        }
    }

    public static void addOperation(WorldOperation op) {
        operations.add(op);
        save();
    }

    public static boolean removeOperation(String worldId) {
        boolean removed = operations.removeIf(op -> op.worldId().equals(worldId));
        if (removed) save();
        return removed;
    }

    public static Optional<WorldOperation> findOperation(String worldId) {
        return operations.stream().filter(op -> op.worldId().equals(worldId)).findFirst();
    }

    public static boolean hasPendingOperation(String worldId) {
        return operations.stream().anyMatch(op -> op.worldId().equals(worldId));
    }

    private static Path getOperationsPath() {
        return Platform.getConfigDir().resolve("bigbangworld/operations.json");
    }

    public record WorldOperation(String worldId, WorldLifecycleState targetState,
                                 String seedOption, String seedValue, long timestamp) {
        public boolean isCreate() {
            return targetState == WorldLifecycleState.PENDING_CREATE;
        }

        public boolean isReset() {
            return targetState == WorldLifecycleState.PENDING_RESET;
        }

        public boolean isDelete() {
            return targetState == WorldLifecycleState.PENDING_DELETE;
        }
    }
}
