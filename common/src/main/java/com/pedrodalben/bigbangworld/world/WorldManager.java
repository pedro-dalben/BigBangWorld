package com.pedrodalben.bigbangworld.world;

import com.pedrodalben.bigbangworld.api.BigBangWorldApi;
import com.pedrodalben.bigbangworld.api.WorldPolicyApi;
import com.pedrodalben.bigbangworld.config.Config;
import com.pedrodalben.bigbangworld.config.ConfigManager;
import com.pedrodalben.bigbangworld.domain.*;
import com.pedrodalben.bigbangworld.mixin.MinecraftServerAccessor;
import com.pedrodalben.bigbangworld.util.TranslationUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

public class WorldManager implements WorldPolicyApi {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorldManager.class);
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9_-]+$");
    private static final WorldManager INSTANCE = new WorldManager();
    private static final long CONFIRMATION_TIMEOUT_MS = 60000;

    private final Map<String, WorldDefinition> worlds = new ConcurrentHashMap<>();
    private final Map<UUID, PendingConfirmation> pendingConfirmations = new ConcurrentHashMap<>();
    private final ExecutorService asyncExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BigBangWorld-Async-Worker");
        t.setDaemon(true);
        return t;
    });
    private final Set<String> lockedWorlds = ConcurrentHashMap.newKeySet();
    private MinecraftServer server;
    private ScheduledExecutorService confirmationCleanup;

    private WorldManager() {
        BigBangWorldApi.register(this);
    }

    public static WorldManager getInstance() {
        return INSTANCE;
    }

    public void init(MinecraftServer server) {
        this.server = server;
        loadWorldsFromConfig();
        startConfirmationCleanup();
    }

    public void shutdown() {
        if (confirmationCleanup != null) {
            confirmationCleanup.shutdown();
        }
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void startConfirmationCleanup() {
        confirmationCleanup = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BigBangWorld-Confirmation-Cleanup");
            t.setDaemon(true);
            return t;
        });
        confirmationCleanup.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            pendingConfirmations.values().removeIf(c -> (now - c.timestamp) > CONFIRMATION_TIMEOUT_MS);
        }, 30, 30, TimeUnit.SECONDS);
    }

    public void loadWorldsFromConfig() {
        worlds.clear();
        Config config = ConfigManager.getConfig();
        for (WorldDefinition def : config.getWorlds()) {
            worlds.put(def.getId(), def);
            if (def.getState() == WorldLifecycleState.ACTIVE) {
                try {
                    loadOrCreateWorld(def);
                    LOGGER.info("Successfully restored active world: {}", def.getId());
                } catch (Exception e) {
                    LOGGER.error("Failed to restore active world: {}", def.getId(), e);
                    def.setState(WorldLifecycleState.FAILED);
                    ConfigManager.save();
                }
            }
        }
    }

    public ServerLevel loadOrCreateWorld(WorldDefinition def) {
        String id = def.getId();
        ResourceLocation resLoc = ResourceLocation.fromNamespaceAndPath("bigbangworld", id);
        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, resLoc);

        if (server.getLevel(dimensionKey) != null) {
            return server.getLevel(dimensionKey);
        }

        WorldData worldData = server.getWorldData();
        ServerLevelData overworldData = worldData.overworldData();
        DerivedLevelData derivedData = new DerivedLevelData(worldData, overworldData);

        Holder<DimensionType> dimensionTypeHolder = server.registryAccess()
                .registryOrThrow(Registries.DIMENSION_TYPE)
                .getHolderOrThrow(BuiltinDimensionTypes.OVERWORLD);

        ChunkGenerator generator;
        if (def.getType() == WorldType.NORMAL) {
            ServerLevel overworld = server.getLevel(Level.OVERWORLD);
            if (overworld == null) {
                throw new IllegalStateException("Overworld is not loaded!");
            }
            generator = overworld.getChunkSource().getGenerator();
        } else if (def.getType() == WorldType.SUPERFLAT) {
            HolderLookup.Provider registryAccess = server.registryAccess();
            Config config = ConfigManager.getConfig();

            Holder<Biome> flatBiome;
            ResourceLocation biomeId = ResourceLocation.tryParse(config.getSuperflatBiome());
            if (biomeId != null) {
                flatBiome = registryAccess.lookupOrThrow(Registries.BIOME).getOrThrow(
                    ResourceKey.create(Registries.BIOME, biomeId)
                );
            } else {
                flatBiome = registryAccess.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
            }

            FlatLevelGeneratorSettings settings = FlatLevelGeneratorSettings.getDefault(
                registryAccess.lookupOrThrow(Registries.BIOME),
                registryAccess.lookupOrThrow(Registries.STRUCTURE_SET),
                registryAccess.lookupOrThrow(Registries.PLACED_FEATURE)
            );

            settings.getLayersInfo().clear();
            List<String> layerDefs = config.getSuperflatLayers();
            if (layerDefs.isEmpty()) {
                settings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.GRASS_BLOCK));
                settings.getLayersInfo().add(new FlatLayerInfo(2, Blocks.DIRT));
                settings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.BEDROCK));
            } else {
                for (String layerDef : layerDefs) {
                    String[] parts = layerDef.split(",");
                    if (parts.length >= 2) {
                        try {
                            String blockId = parts[0];
                            int count = Integer.parseInt(parts[1].trim());
                            ResourceLocation blockLoc = ResourceLocation.tryParse(blockId);
                            if (blockLoc != null && BuiltInRegistries.BLOCK.containsKey(blockLoc)) {
                                settings.getLayersInfo().add(new FlatLayerInfo(count, BuiltInRegistries.BLOCK.get(blockLoc)));
                            } else {
                                settings.getLayersInfo().add(new FlatLayerInfo(count, Blocks.GRASS_BLOCK));
                            }
                        } catch (NumberFormatException ignored) {
                            settings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.GRASS_BLOCK));
                        }
                    }
                }
            }

            generator = new FlatLevelSource(settings);
        } else if (def.getType() == WorldType.VOID) {
            HolderLookup.Provider registryAccess = server.registryAccess();
            HolderGetter<Biome> biomeGetter = registryAccess.lookupOrThrow(Registries.BIOME);

            Holder<Biome> voidBiome = biomeGetter.getOrThrow(Biomes.THE_VOID);
            FlatLevelGeneratorSettings settings = new FlatLevelGeneratorSettings(
                    Optional.empty(),
                    voidBiome,
                    new ArrayList<>()
            );
            settings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.AIR));

            generator = new FlatLevelSource(settings);
        } else {
            throw new IllegalArgumentException("Unknown world type: " + def.getType());
        }

        LevelStem levelStem = new LevelStem(dimensionTypeHolder, generator);

        ChunkProgressListener progressListener = new ChunkProgressListener() {
            @Override public void updateSpawnPos(ChunkPos center) {}
            @Override public void onStatusChange(ChunkPos pos, ChunkStatus status) {}
            @Override public void start() {}
            @Override public void stop() {}
        };

        ServerLevel level = new ServerLevel(
                server,
                server,
                ((MinecraftServerAccessor) server).getStorageSource(),
                derivedData,
                dimensionKey,
                levelStem,
                progressListener,
                false,
                def.getSeed(),
                new ArrayList<>(),
                true,
                null
        );

        if (def.getBorder() != null && def.getBorder().isEnabled()) {
            level.getWorldBorder().setSize(def.getBorder().getDiameter());
        }

        Map<ResourceKey<Level>, ServerLevel> levelsMap = ((MinecraftServerAccessor) server).getLevels();
        levelsMap.put(dimensionKey, level);

        // Post NeoForge event if available
        postNeoForgeLoadEvent(level);

        return level;
    }

    public void generateVoidPlatform(ServerLevel level, WorldDefinition def) {
        int spawnX = (int) def.getSpawn().getX();
        int spawnY = (int) def.getSpawn().getY() - 1;
        int spawnZ = (int) def.getSpawn().getZ();

        Block baseBlock = Blocks.STONE;
        String materialStr = ConfigManager.getConfig().getVoidPlatformMaterial();
        if (materialStr != null && !materialStr.isEmpty()) {
            ResourceLocation matLoc = ResourceLocation.tryParse(materialStr);
            if (matLoc != null && BuiltInRegistries.BLOCK.containsKey(matLoc)) {
                baseBlock = BuiltInRegistries.BLOCK.get(matLoc);
            }
        }

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos floorPos = new BlockPos(spawnX + x, spawnY, spawnZ + z);
                level.setBlockAndUpdate(floorPos, baseBlock.defaultBlockState());

                if (x == -2 || x == 2 || z == -2 || z == 2) {
                    BlockPos wallPos = floorPos.above();
                    level.setBlockAndUpdate(wallPos, Blocks.GLASS.defaultBlockState());
                } else {
                    level.setBlockAndUpdate(floorPos.above(), Blocks.AIR.defaultBlockState());
                    level.setBlockAndUpdate(floorPos.above(2), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private void postNeoForgeLoadEvent(ServerLevel level) {
        try {
            Class<?> eventClass = Class.forName("net.neoforged.neoforge.event.level.LevelEvent$Load");
            Object event = eventClass.getConstructor(net.minecraft.world.level.Level.class).newInstance(level);
            Class<?> neoforgeClass = Class.forName("net.neoforged.neoforge.common.NeoForge");
            Object eventBus = neoforgeClass.getField("EVENT_BUS").get(null);
            eventBus.getClass().getMethod("post", Object.class).invoke(eventBus, event);
        } catch (Throwable ignored) {
            // Not NeoForge or event post failed
        }
    }

    public boolean createWorld(String id, WorldType type, String seedInput, ServerPlayer creator) {
        if (!validateId(id)) {
            creator.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.invalid_world_id"));
            return false;
        }

        if (worlds.containsKey(id)) {
            creator.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.world_already_exists", id));
            return false;
        }

        creator.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.creating_world", id));

        long seed;
        if ("random".equalsIgnoreCase(seedInput) || seedInput == null || seedInput.isBlank()) {
            seed = new Random().nextLong();
        } else {
            try {
                seed = Long.parseLong(seedInput);
            } catch (NumberFormatException e) {
                seed = seedInput.hashCode();
            }
        }

        WorldDefinition def = new WorldDefinition();
        def.setId(id);
        def.setDisplayName(id.substring(0, 1).toUpperCase() + id.substring(1));
        def.setType(type);
        def.setSeed(seed);
        def.setDimensionKey("bigbangworld:" + id);
        def.setState(WorldLifecycleState.CREATING);
        def.setPublicAccess(true);
        def.setCreatedAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        def.setLastResetAt(null);
        def.setResetCount(0);

        SpawnPosition spawn = new SpawnPosition(0.5, 96.0, 0.5, 0.0f, 0.0f);
        def.setSpawn(spawn);

        BorderConfig border = new BorderConfig(true, ConfigManager.getConfig().getDefaultWorldBorderDiameter());
        def.setBorder(border);

        WorldPolicies policies = new WorldPolicies(false, false, false, false);
        def.setPolicies(policies);

        worlds.put(id, def);
        ConfigManager.getConfig().getWorlds().add(def);

        long startTime = System.currentTimeMillis();
        try {
            ServerLevel level = loadOrCreateWorld(def);

            if (type == WorldType.VOID) {
                generateVoidPlatform(level, def);
            }

            // Safe spawn search for NORMAL/SUPERFLAT
            if (type != WorldType.VOID) {
                BlockPos startPos = new BlockPos(0, 96, 0);
                BlockPos safeSpawn = findSafeSpawnPosition(level, startPos);
                if (safeSpawn != null) {
                    def.getSpawn().setX(safeSpawn.getX() + 0.5);
                    def.getSpawn().setY(safeSpawn.getY());
                    def.getSpawn().setZ(safeSpawn.getZ() + 0.5);
                    level.setDefaultSpawnPos(safeSpawn, 0.0f);
                } else {
                    // Create fallback platform
                    BlockPos fallbackSpawn = createSafetyPlatform(level, startPos);
                    def.getSpawn().setX(fallbackSpawn.getX() + 0.5);
                    def.getSpawn().setY(fallbackSpawn.getY());
                    def.getSpawn().setZ(fallbackSpawn.getZ() + 0.5);
                    level.setDefaultSpawnPos(fallbackSpawn, 0.0f);
                }
            }

            def.setState(WorldLifecycleState.ACTIVE);
            ConfigManager.save();

            long duration = System.currentTimeMillis() - startTime;
            creator.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.world_created", id));
            LOGGER.info("[BigBangWorld] World '{}' created successfully in {}ms by {}. Seed: {}", id, duration, creator.getName().getString(), seed);

            // Teleport creator
            teleportPlayerToWorld(creator, def);
            return true;

        } catch (Exception e) {
            LOGGER.error("Failed to create dynamic world '{}'", id, e);
            def.setState(WorldLifecycleState.FAILED);
            ConfigManager.save();
            creator.sendSystemMessage(Component.literal("§cErro crítico ao criar o mundo. Detalhes no console."));
            return false;
        }
    }

    public boolean validateId(String id) {
        return id != null && ID_PATTERN.matcher(id).matches() && !id.contains("..") && !id.contains("/") && !id.contains("\\");
    }

    public WorldDefinition getWorld(String id) {
        return worlds.get(id);
    }

    public Collection<WorldDefinition> getAllWorlds() {
        return worlds.values();
    }

    public boolean teleportPlayerToWorld(ServerPlayer player, WorldDefinition def) {
        // State check must be the first guard
        if (def.getState() != WorldLifecycleState.ACTIVE) {
            player.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.cannot_teleport_unavailable", def.getId()));
            return false;
        }

        // Check private access
        if (!def.isPublicAccess()) {
            String perm = "bigbangworld.access." + def.getId();
            boolean allowed = false;
            try {
                Class<?> essentialsPerm = Class.forName("com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI");
                java.lang.reflect.Method hasPerm = essentialsPerm.getMethod("hasPermission", UUID.class, String.class);
                allowed = (boolean) hasPerm.invoke(null, player.getUUID(), perm);
            } catch (Throwable ignored) {}
            if (!allowed) {
                player.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.private_world_deny"));
                return false;
            }
        }

        ResourceLocation resLoc = ResourceLocation.fromNamespaceAndPath("bigbangworld", def.getId());
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, resLoc);
        ServerLevel level = server.getLevel(key);
        if (level == null) {
            player.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.cannot_teleport_unavailable", def.getId()));
            return false;
        }

        player.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.teleporting", def.getId()));
        SpawnPosition spawn = def.getSpawn();
        player.teleportTo(level, spawn.getX(), spawn.getY(), spawn.getZ(), spawn.getYaw(), spawn.getPitch());
        return true;
    }

    public boolean setSpawn(String id, ServerPlayer player) {
        WorldDefinition def = worlds.get(id);
        if (def == null) {
            player.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.world_not_found", id));
            return false;
        }

        ResourceLocation resLoc = ResourceLocation.fromNamespaceAndPath("bigbangworld", def.getId());
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, resLoc);
        if (player.level().dimension() != key) {
            player.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.spawn_set_invalid_world", id));
            return false;
        }

        // Validate safe position
        BlockPos pos = player.blockPosition();
        if (pos.getY() < player.level().getMinBuildHeight() || pos.getY() > player.level().getMaxBuildHeight() - 2) {
            player.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.spawn_not_safe"));
            return false;
        }

        BlockState floor = player.level().getBlockState(pos.below());
        BlockState body = player.level().getBlockState(pos);
        BlockState head = player.level().getBlockState(pos.above());

        if (floor.isAir() || !floor.getFluidState().isEmpty() || floor.getBlock() == Blocks.LAVA || floor.getBlock() == Blocks.CACTUS) {
            player.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.spawn_not_safe"));
            return false;
        }

        def.getSpawn().setX(player.getX());
        def.getSpawn().setY(player.getY());
        def.getSpawn().setZ(player.getZ());
        def.getSpawn().setYaw(player.getYRot());
        def.getSpawn().setPitch(player.getXRot());
        ConfigManager.save();

        ServerLevel level = (ServerLevel) player.level();
        level.setDefaultSpawnPos(pos, player.getYRot());

        player.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.spawn_set", id));
        return true;
    }

    private void sendMessage(ServerPlayer player, String key, Object... args) {
        if (player != null) {
            player.sendSystemMessage(TranslationUtil.getComponent(key, args));
        } else {
            LOGGER.info("[BigBangWorld] {}", TranslationUtil.get(key, args));
        }
    }

    private void sendLiteral(ServerPlayer player, String message) {
        if (player != null) {
            player.sendSystemMessage(Component.literal(message));
        } else {
            LOGGER.info("[BigBangWorld] {}", message);
        }
    }

    public void setAccess(String id, boolean publicAccess, ServerPlayer admin) {
        WorldDefinition def = worlds.get(id);
        if (def == null) {
            sendMessage(admin, "bigbangworld.message.world_not_found", id);
            return;
        }
        def.setPublicAccess(publicAccess);
        ConfigManager.save();
        sendMessage(admin, "bigbangworld.message.access_set", id, publicAccess ? "public" : "private");
    }

    public void setBorder(String id, String diameterInput, ServerPlayer admin) {
        WorldDefinition def = worlds.get(id);
        if (def == null) {
            sendMessage(admin, "bigbangworld.message.world_not_found", id);
            return;
        }

        ResourceLocation resLoc = ResourceLocation.fromNamespaceAndPath("bigbangworld", def.getId());
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, resLoc);
        ServerLevel level = server.getLevel(key);

        if ("off".equalsIgnoreCase(diameterInput)) {
            def.getBorder().setEnabled(false);
            if (level != null) {
                level.getWorldBorder().setSize(3.0E7);
            }
            ConfigManager.save();
            sendMessage(admin, "bigbangworld.message.border_off", id);
        } else {
            try {
                double diameter = Double.parseDouble(diameterInput);
                if (diameter <= 0) {
                    sendLiteral(admin, "§cDiâmetro deve ser maior que zero.");
                    return;
                }
                def.getBorder().setEnabled(true);
                def.getBorder().setDiameter(diameter);
                if (level != null) {
                    level.getWorldBorder().setSize(diameter);
                }
                ConfigManager.save();
                sendMessage(admin, "bigbangworld.message.border_set", id, String.valueOf((int) diameter));
            } catch (NumberFormatException e) {
                sendLiteral(admin, "§cValor de borda inválido.");
            }
        }
    }

    public void enableWorld(String id, ServerPlayer admin) {
        WorldDefinition def = worlds.get(id);
        if (def == null) {
            sendMessage(admin, "bigbangworld.message.world_not_found", id);
            return;
        }

        if (def.getState() == WorldLifecycleState.ACTIVE) {
            sendLiteral(admin, "§eEste mundo já está ativo.");
            return;
        }

        try {
            loadOrCreateWorld(def);
            def.setState(WorldLifecycleState.ACTIVE);
            ConfigManager.save();
            sendMessage(admin, "bigbangworld.message.world_enabled", id);
        } catch (Exception e) {
            LOGGER.error("Failed to enable world '{}'", id, e);
            sendLiteral(admin, "§cErro ao carregar o mundo. Detalhes no console.");
        }
    }

    public void disableWorld(String id, ServerPlayer admin) {
        WorldDefinition def = worlds.get(id);
        if (def == null) {
            sendMessage(admin, "bigbangworld.message.world_not_found", id);
            return;
        }

        if (def.getState() == WorldLifecycleState.DISABLED) {
            sendLiteral(admin, "§eEste mundo já está desabilitado.");
            return;
        }

        long startTime = System.currentTimeMillis();
        boolean success = unloadWorld(def);
        if (success) {
            def.setState(WorldLifecycleState.DISABLED);
            ConfigManager.save();
            sendMessage(admin, "bigbangworld.message.world_disabled", id);
            LOGGER.info("[BigBangWorld] World '{}' disabled successfully in {}ms.", id, (System.currentTimeMillis() - startTime));
        } else {
            sendLiteral(admin, "§cErro ao descarregar o mundo. Certifique-se de que não há jogadores e tente novamente.");
        }
    }

    private boolean unloadWorld(WorldDefinition def) {
        ResourceLocation resLoc = ResourceLocation.fromNamespaceAndPath("bigbangworld", def.getId());
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, resLoc);
        ServerLevel targetLevel = server.getLevel(key);
        if (targetLevel == null) {
            return true; // already unloaded
        }

        // Teleport players to fallback
        ResourceLocation fallbackLoc = ResourceLocation.tryParse(ConfigManager.getConfig().getFallbackDimension());
        ResourceKey<Level> fallbackKey = fallbackLoc != null ? ResourceKey.create(Registries.DIMENSION, fallbackLoc) : Level.OVERWORLD;
        ServerLevel fallbackLevel = server.getLevel(fallbackKey);
        if (fallbackLevel == null) {
            fallbackLevel = server.getLevel(Level.OVERWORLD);
        }

        BlockPos fallbackSpawn = fallbackLevel.getSharedSpawnPos();
        List<ServerPlayer> players = new ArrayList<>(targetLevel.players());
        for (ServerPlayer player : players) {
            player.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.world_disabled", def.getId()));
            player.teleportTo(fallbackLevel, fallbackSpawn.getX() + 0.5, fallbackSpawn.getY(), fallbackSpawn.getZ() + 0.5, 0.0f, 0.0f);
        }

        try {
            // Save and close target level
            targetLevel.save(null, true, false);
            targetLevel.close();

            // Remove from levels map
            Map<ResourceKey<Level>, ServerLevel> levelsMap = ((MinecraftServerAccessor) server).getLevels();
            levelsMap.remove(key);
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to unload dynamic world '{}'", def.getId(), e);
            return false;
        }
    }

    public void startResetFlow(String id, String seedOption, String seedValue, ServerPlayer admin) {
        WorldDefinition def = worlds.get(id);
        if (def == null) {
            sendMessage(admin, "bigbangworld.message.world_not_found", id);
            return;
        }

        if (lockedWorlds.contains(id)) {
            sendLiteral(admin, "§cJá existe uma operação em andamento para este mundo.");
            return;
        }

        PendingConfirmation conf = new PendingConfirmation(PendingAction.RESET, id, seedOption, seedValue, System.currentTimeMillis());
        pendingConfirmations.put(admin.getUUID(), conf);
        sendMessage(admin, "bigbangworld.message.confirm_reset", id);
    }

    public void startDeleteFlow(String id, ServerPlayer admin) {
        if (!worlds.containsKey(id)) {
            sendMessage(admin, "bigbangworld.message.world_not_found", id);
            return;
        }

        if (lockedWorlds.contains(id)) {
            sendLiteral(admin, "§cJá existe uma operação em andamento para este mundo.");
            return;
        }

        PendingConfirmation conf = new PendingConfirmation(PendingAction.DELETE, id, null, null, System.currentTimeMillis());
        pendingConfirmations.put(admin.getUUID(), conf);
        sendMessage(admin, "bigbangworld.message.confirm_delete", id);
    }

    public boolean confirmAction(ServerPlayer admin) {
        PendingConfirmation conf = pendingConfirmations.remove(admin.getUUID());
        if (conf == null || (System.currentTimeMillis() - conf.timestamp) > CONFIRMATION_TIMEOUT_MS) {
            sendLiteral(admin, "§cConfirmação expirada ou inexistente.");
            return false;
        }

        if (!lockedWorlds.add(conf.worldId)) {
            sendLiteral(admin, "§cJá existe uma operação em andamento para o mundo " + conf.worldId + ".");
            return false;
        }

        try {
            if (conf.action == PendingAction.RESET) {
                executeReset(conf.worldId, conf.seedOption, conf.seedValue, admin);
                return true;
            } else if (conf.action == PendingAction.DELETE) {
                executeDelete(conf.worldId, admin);
                return true;
            }
        } finally {
            lockedWorlds.remove(conf.worldId);
        }
        return false;
    }

    private void executeReset(String id, String seedOption, String seedValue, ServerPlayer admin) {
        WorldDefinition def = worlds.get(id);
        if (def == null) return;

        sendMessage(admin, "bigbangworld.message.resetting_world", id);
        def.setState(WorldLifecycleState.RESETTING);
        ConfigManager.save();

        long startTime = System.currentTimeMillis();
        Path backupDir = null;
        try {
            boolean unloaded = unloadWorld(def);
            if (!unloaded) {
                throw new IOException("Failed to unload level files.");
            }

            ResourceLocation resLoc = ResourceLocation.fromNamespaceAndPath("bigbangworld", id);
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, resLoc);
            Path dimensionPath = ((MinecraftServerAccessor) server).getStorageSource().getDimensionPath(key);

            if (Files.exists(dimensionPath)) {
                if (ConfigManager.getConfig().isBackupBeforeReset()) {
                    String timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(java.time.ZoneId.systemDefault()).format(Instant.now());
                    backupDir = Paths.get(((MinecraftServerAccessor) server).getStorageSource().getWorldDir().toString(), "bigbangworld-backups", id, timestamp);
                    Files.createDirectories(backupDir);

                    try {
                        Files.move(dimensionPath, backupDir.resolve("region"), StandardCopyOption.REPLACE_EXISTING);
                        LOGGER.info("[BigBangWorld] Backed up '{}' to '{}'", id, backupDir);
                    } catch (IOException e) {
                        LOGGER.warn("Failed to backup by move, attempting copy & delete.", e);
                        if (!backupDir.resolve("region").toFile().exists()) {
                            Files.createDirectories(backupDir.resolve("region"));
                        }
                        copyDirectory(dimensionPath, backupDir);
                        deleteDirectorySync(dimensionPath);
                    }

                    asyncExecutor.execute(() -> cleanupBackups(id));
                } else {
                    deleteDirectorySync(dimensionPath);
                }
            }

            long seed = def.getSeed();
            if ("random-seed".equalsIgnoreCase(seedOption)) {
                seed = new Random().nextLong();
            } else if ("seed".equalsIgnoreCase(seedOption) && seedValue != null) {
                try {
                    seed = Long.parseLong(seedValue);
                } catch (NumberFormatException e) {
                    seed = seedValue.hashCode();
                }
            }
            def.setSeed(seed);

            ServerLevel level = loadOrCreateWorld(def);

            if (def.getType() == WorldType.VOID) {
                generateVoidPlatform(level, def);
            }

            if (def.getType() != WorldType.VOID) {
                BlockPos startPos = new BlockPos(0, 96, 0);
                BlockPos safeSpawn = findSafeSpawnPosition(level, startPos);
                if (safeSpawn != null) {
                    def.getSpawn().setX(safeSpawn.getX() + 0.5);
                    def.getSpawn().setY(safeSpawn.getY());
                    def.getSpawn().setZ(safeSpawn.getZ() + 0.5);
                    level.setDefaultSpawnPos(safeSpawn, 0.0f);
                } else {
                    BlockPos fallbackSpawn = createSafetyPlatform(level, startPos);
                    def.getSpawn().setX(fallbackSpawn.getX() + 0.5);
                    def.getSpawn().setY(fallbackSpawn.getY());
                    def.getSpawn().setZ(fallbackSpawn.getZ() + 0.5);
                    level.setDefaultSpawnPos(fallbackSpawn, 0.0f);
                }
            }

            def.setState(WorldLifecycleState.ACTIVE);
            def.setLastResetAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
            def.setResetCount(def.getResetCount() + 1);
            ConfigManager.save();

            long duration = System.currentTimeMillis() - startTime;
            sendMessage(admin, "bigbangworld.message.world_reset_success", id);
            LOGGER.info("[BigBangWorld] World '{}' reset successfully in {}ms by {}. New seed: {}", id, duration, admin.getName().getString(), seed);

        } catch (Exception e) {
            LOGGER.error("Failed to reset dynamic world '{}'", id, e);
            def.setState(WorldLifecycleState.FAILED);
            ConfigManager.save();
            sendMessage(admin, "bigbangworld.message.world_reset_failed", id);
        }
    }

    private void executeDelete(String id, ServerPlayer admin) {
        WorldDefinition def = worlds.get(id);
        if (def == null) return;

        def.setState(WorldLifecycleState.DELETING);
        ConfigManager.save();

        try {
            boolean unloaded = unloadWorld(def);
            if (!unloaded) {
                throw new IOException("Failed to unload level before deletion.");
            }

            ResourceLocation resLoc = ResourceLocation.fromNamespaceAndPath("bigbangworld", id);
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, resLoc);
            Path dimensionPath = ((MinecraftServerAccessor) server).getStorageSource().getDimensionPath(key);

            if (Files.exists(dimensionPath)) {
                Path tempDeleteDir = Paths.get(dimensionPath.getParent().toString(), id + "_deleting_" + System.currentTimeMillis());
                Files.move(dimensionPath, tempDeleteDir);
                Path finalTemp = tempDeleteDir;
                asyncExecutor.execute(() -> {
                    try {
                        deleteDirectorySync(finalTemp);
                        LOGGER.info("[BigBangWorld] Deleted files for world '{}'", id);
                    } catch (IOException e) {
                        LOGGER.error("Failed to delete directory '{}'", finalTemp, e);
                    }
                });
            }

            worlds.remove(id);
            ConfigManager.getConfig().getWorlds().remove(def);
            ConfigManager.save();

            sendMessage(admin, "bigbangworld.message.world_deleted", id);
            LOGGER.info("[BigBangWorld] World '{}' deleted successfully by {}.", id, admin.getName().getString());

        } catch (Exception e) {
            LOGGER.error("Failed to delete dynamic world '{}'", id, e);
            def.setState(WorldLifecycleState.FAILED);
            ConfigManager.save();
            sendLiteral(admin, "§cFalha ao excluir o mundo. Detalhes no console.");
        }
    }

    private void cleanupBackups(String id) {
        try {
            Path backupsRoot = Paths.get(((MinecraftServerAccessor) server).getStorageSource().getWorldDir().toString(), "bigbangworld-backups", id);
            if (!Files.exists(backupsRoot)) return;

            File[] files = backupsRoot.toFile().listFiles(File::isDirectory);
            if (files == null) return;

            List<File> directories = new ArrayList<>(Arrays.asList(files));
            directories.sort(Comparator.comparingLong(File::lastModified));

            int maxBackups = ConfigManager.getConfig().getMaxBackupsPerWorld();
            while (directories.size() > maxBackups) {
                File toDelete = directories.remove(0);
                LOGGER.info("[BigBangWorld] Cleaning up old backup: {}", toDelete.getAbsolutePath());
                try {
                    deleteDirectorySync(toDelete.toPath());
                } catch (Exception ex) {
                    LOGGER.error("Failed to delete old backup '{}'", toDelete.getAbsolutePath(), ex);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to cleanup old backups for world '{}'", id, e);
        }
    }

    private void deleteDirectorySync(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(src -> {
            try {
                Path dest = target.resolve(source.relativize(src));
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dest);
                } else {
                    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to copy directory: " + src, e);
            }
        });
    }

    // Spawn searching helper logic
    private boolean isSafeBlock(ServerLevel level, BlockPos pos) {
        BlockState floor = level.getBlockState(pos.below());
        BlockState body = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());

        if (floor.isAir() || !floor.getFluidState().isEmpty()) return false;

        net.minecraft.world.level.block.Block block = floor.getBlock();
        if (block == Blocks.LAVA || block == Blocks.WATER || block == Blocks.FIRE ||
            block == Blocks.CACTUS || block == Blocks.MAGMA_BLOCK) return false;

        return body.getCollisionShape(level, pos).isEmpty() &&
               head.getCollisionShape(level, pos.above()).isEmpty();
    }

    public BlockPos findSafeSpawnPosition(ServerLevel level, BlockPos startPos) {
        int searchRadius = 50;
        for (int r = 0; r < searchRadius; r++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (Math.abs(x) == r || Math.abs(z) == r) {
                        int targetX = startPos.getX() + x;
                        int targetZ = startPos.getZ() + z;

                        int highestY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, targetX, targetZ);
                        for (int y = highestY + 5; y >= highestY - 20; y--) {
                            BlockPos checkPos = new BlockPos(targetX, y, targetZ);
                            if (isSafeBlock(level, checkPos)) {
                                return checkPos;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    public BlockPos createSafetyPlatform(ServerLevel level, BlockPos pos) {
        int startX = pos.getX();
        int startY = pos.getY() - 1; // underneath feet
        int startZ = pos.getZ();

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos p = new BlockPos(startX + x, startY, startZ + z);
                level.setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(p.above(), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(p.above(2), Blocks.AIR.defaultBlockState());
            }
        }
        return pos;
    }

    // WorldPolicyApi Implementation
    @Override
    public boolean isManagedWorld(ServerLevel level) {
        if (level == null) return false;
        String namespace = level.dimension().location().getNamespace();
        return "bigbangworld".equals(namespace);
    }

    @Override
    public boolean isTemporaryWorld(ServerLevel level) {
        if (level == null) return false;
        String namespace = level.dimension().location().getNamespace();
        if (!"bigbangworld".equals(namespace)) return false;
        String id = level.dimension().location().getPath();
        WorldDefinition def = worlds.get(id);
        return def != null; // Currently all managed worlds under this mod are temporary/resettable
    }

    @Override
    public boolean isHomeCreationAllowed(ServerPlayer player) {
        if (player == null) return true;
        ServerLevel level = player.serverLevel();
        if (isTemporaryWorld(level)) {
            String id = level.dimension().location().getPath();
            WorldDefinition def = worlds.get(id);
            if (def != null) {
                return def.getPolicies().isAllowHomeCreation();
            }
            return false;
        }
        return true;
    }

    @Override
    public boolean isWaystonePlacementAllowed(ServerPlayer player, BlockState state) {
        if (player == null) return true;
        ServerLevel level = player.serverLevel();
        if (isTemporaryWorld(level)) {
            String id = level.dimension().location().getPath();
            WorldDefinition def = worlds.get(id);
            if (def != null) {
                return def.getPolicies().isAllowWaystones();
            }
            return false;
        }
        return true;
    }

    @Override
    public boolean isClaimCreationAllowed(ServerPlayer player) {
        if (player == null) return true;
        ServerLevel level = player.serverLevel();
        if (isTemporaryWorld(level)) {
            String id = level.dimension().location().getPath();
            WorldDefinition def = worlds.get(id);
            if (def != null) {
                return def.getPolicies().isAllowClaims();
            }
            return false;
        }
        return true;
    }

    @Override
    public boolean isChunkLoadingAllowed(ServerPlayer player) {
        if (player == null) return true;
        ServerLevel level = player.serverLevel();
        if (isTemporaryWorld(level)) {
            String id = level.dimension().location().getPath();
            WorldDefinition def = worlds.get(id);
            if (def != null) {
                return def.getPolicies().isAllowChunkLoading();
            }
            return false;
        }
        return true;
    }

    // Helper classes for reset/delete confirmations
    private enum PendingAction {
        RESET, DELETE
    }

    private static class PendingConfirmation {
        final PendingAction action;
        final String worldId;
        final String seedOption;
        final String seedValue;
        final long timestamp;

        PendingConfirmation(PendingAction action, String worldId, String seedOption, String seedValue, long timestamp) {
            this.action = action;
            this.worldId = worldId;
            this.seedOption = seedOption;
            this.seedValue = seedValue;
            this.timestamp = timestamp;
        }
    }
}
