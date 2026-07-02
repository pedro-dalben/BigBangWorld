package com.pedrodalben.bigbangworld.world;

import com.pedrodalben.bigbangworld.api.BigBangWorldApi;
import com.pedrodalben.bigbangworld.api.WorldPolicyApi;
import com.pedrodalben.bigbangworld.config.Config;
import com.pedrodalben.bigbangworld.config.ConfigManager;
import com.pedrodalben.bigbangworld.config.OperationsManager;
import com.pedrodalben.bigbangworld.config.OperationsManager.WorldOperation;
import com.pedrodalben.bigbangworld.domain.*;
import com.pedrodalben.bigbangworld.util.PermissionService;
import com.pedrodalben.bigbangworld.util.ThreadCheck;
import com.pedrodalben.bigbangworld.util.TranslationUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class WorldManager implements WorldPolicyApi {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorldManager.class);
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9_-]+$");
    private static final WorldManager INSTANCE = new WorldManager();

    private final Map<String, WorldDefinition> worlds = new ConcurrentHashMap<>();
    private MinecraftServer server;

    private WorldManager() {
        BigBangWorldApi.register(this);
    }

    public static WorldManager getInstance() {
        return INSTANCE;
    }

    public void init(MinecraftServer server) {
        this.server = server;
        loadWorldsFromConfig();
    }

    public void shutdown() {
    }

    private void requireServerThread(String operation) {
        ThreadCheck.requireServerThread(server, operation);
    }

    public void loadWorldsFromConfig() {
        worlds.clear();
        Config config = ConfigManager.getConfig();
        for (WorldDefinition def : config.getWorlds()) {
            worlds.put(def.getId(), def);
        }
    }

    private ResourceKey<Level> getDimensionKey(WorldDefinition def) {
        ResourceLocation resLoc = ResourceLocation.fromNamespaceAndPath("bigbangworld", def.getId());
        return ResourceKey.create(Registries.DIMENSION, resLoc);
    }

    public boolean createWorld(ServerPlayer creator, String id, WorldType type, String seedInput) {
        requireServerThread("WorldManager.createWorld");
        if (!validateId(id)) {
            creator.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.invalid_world_id"));
            return false;
        }
        if (worlds.containsKey(id)) {
            creator.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.world_already_exists", id));
            return false;
        }
        if (OperationsManager.hasPendingOperation(id)) {
            creator.sendSystemMessage(Component.literal("§cJ existe uma operacao pendente para este mundo."));
            return false;
        }

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
        def.setState(WorldLifecycleState.PENDING_CREATE);
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
        ConfigManager.save();

        OperationsManager.addOperation(new WorldOperation(id, WorldLifecycleState.PENDING_CREATE, null, null, System.currentTimeMillis()));

        creator.sendSystemMessage(Component.literal("§eMundo §6" + id + " §eagendado com sucesso."));
        creator.sendSystemMessage(Component.literal("§eReinicie o servidor para criar e carregar esta dimensão."));
        LOGGER.info("[BigBangWorld] World '{}' scheduled for creation. Seed: {}. Type: {}", id, seed, type);
        return true;
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
        requireServerThread("WorldManager.teleportPlayerToWorld");
        if (def == null) return false;

        if (def.isPendingOperation()) {
            player.sendSystemMessage(Component.literal("§cWorld §6" + def.getId() + " §cest pending operation. Restart required."));
            return false;
        }

        if (def.getState() != WorldLifecycleState.ACTIVE) {
            player.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.cannot_teleport_unavailable", def.getId()));
            return false;
        }

        if (!def.isPublicAccess()) {
            String perm = "bigbangworld.access." + def.getId();
            boolean allowed = PermissionService.hasPermission(player, perm);
            if (!allowed) {
                player.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.private_world_deny"));
                return false;
            }
        }

        ResourceKey<Level> key = getDimensionKey(def);
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
        requireServerThread("WorldManager.setSpawn");
        WorldDefinition def = worlds.get(id);
        if (def == null) {
            player.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.world_not_found", id));
            return false;
        }

        ResourceKey<Level> key = getDimensionKey(def);
        if (player.level().dimension() != key) {
            player.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.spawn_set_invalid_world", id));
            return false;
        }

        BlockPos pos = player.blockPosition();
        if (pos.getY() < player.level().getMinBuildHeight() || pos.getY() > player.level().getMaxBuildHeight() - 2) {
            player.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.spawn_not_safe"));
            return false;
        }

        BlockState floor = player.level().getBlockState(pos.below());
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

    public void setAccess(String id, boolean publicAccess, ServerPlayer admin) {
        requireServerThread("WorldManager.setAccess");
        WorldDefinition def = worlds.get(id);
        if (def == null) {
            admin.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.world_not_found", id));
            return;
        }
        def.setPublicAccess(publicAccess);
        ConfigManager.save();
        admin.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.access_set", id, publicAccess ? "public" : "private"));
    }

    public void setBorder(String id, String diameterInput, ServerPlayer admin) {
        requireServerThread("WorldManager.setBorder");
        WorldDefinition def = worlds.get(id);
        if (def == null) {
            admin.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.world_not_found", id));
            return;
        }

        ResourceKey<Level> key = getDimensionKey(def);
        ServerLevel level = server.getLevel(key);

        if ("off".equalsIgnoreCase(diameterInput)) {
            def.getBorder().setEnabled(false);
            if (level != null) {
                level.getWorldBorder().setSize(3.0E7);
            }
            ConfigManager.save();
            admin.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.border_off", id));
        } else {
            try {
                double diameter = Double.parseDouble(diameterInput);
                if (diameter <= 0) {
                    admin.sendSystemMessage(Component.literal("§cDi metro deve ser maior que zero."));
                    return;
                }
                def.getBorder().setEnabled(true);
                def.getBorder().setDiameter(diameter);
                if (level != null) {
                    level.getWorldBorder().setSize(diameter);
                }
                ConfigManager.save();
                admin.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.border_set", id, String.valueOf((int) diameter)));
            } catch (NumberFormatException e) {
                admin.sendSystemMessage(Component.literal("§cValor de borda inv lido."));
            }
        }
    }

    public void enableWorld(String id, ServerPlayer admin) {
        requireServerThread("WorldManager.enableWorld");
        WorldDefinition def = worlds.get(id);
        if (def == null) {
            admin.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.world_not_found", id));
            return;
        }

        if (def.getState() == WorldLifecycleState.ACTIVE) {
            admin.sendSystemMessage(Component.literal("§eEste mundo j  est  ativo."));
            return;
        }

        if (def.isPendingOperation()) {
            admin.sendSystemMessage(Component.literal("§cMundo com opera o pendente. Reinicie o servidor primeiro."));
            return;
        }

        if (def.getState() == WorldLifecycleState.DISABLED || def.getState() == WorldLifecycleState.FAILED) {
            def.setState(WorldLifecycleState.ACTIVE);
            ConfigManager.save();
            admin.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.world_enabled", id));
            admin.sendSystemMessage(Component.literal("§eReinicie o servidor para carregar a dimens o."));
        } else {
            admin.sendSystemMessage(Component.literal("§cEstado inv lido para esta opera o."));
        }
    }

    public void disableWorld(String id, ServerPlayer admin) {
        requireServerThread("WorldManager.disableWorld");
        WorldDefinition def = worlds.get(id);
        if (def == null) {
            admin.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.world_not_found", id));
            return;
        }

        if (def.getState() == WorldLifecycleState.DISABLED) {
            admin.sendSystemMessage(Component.literal("§eEste mundo j  est  desabilitado."));
            return;
        }

        def.setState(WorldLifecycleState.DISABLED);
        ConfigManager.save();
        admin.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.world_disabled", id));
        admin.sendSystemMessage(Component.literal("§eReinicie o servidor para aplicar as altera es."));
    }

    public void startCreateFlow(String id, WorldType type, String seed, ServerPlayer admin) {
        createWorld(admin, id, type, seed);
    }

    public void startResetFlow(String id, String seedOption, String seedValue, ServerPlayer admin) {
        requireServerThread("WorldManager.startResetFlow");
        WorldDefinition def = worlds.get(id);
        if (def == null) {
            admin.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.world_not_found", id));
            return;
        }

        if (def.isPendingOperation()) {
            admin.sendSystemMessage(Component.literal("§cJ  existe uma opera o pendente para este mundo."));
            return;
        }

        if (def.getState() != WorldLifecycleState.ACTIVE) {
            admin.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.cannot_teleport_unavailable", id));
            return;
        }

        OperationsManager.addOperation(new WorldOperation(id, WorldLifecycleState.PENDING_RESET, seedOption, seedValue, System.currentTimeMillis()));
        def.setState(WorldLifecycleState.PENDING_RESET);
        ConfigManager.save();

        admin.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.world_reset_scheduled", id));
        admin.sendSystemMessage(Component.literal("§eReinicie o servidor para concluir o reset."));
        LOGGER.info("[BigBangWorld] World '{}' scheduled for reset by {}.", id, admin.getName().getString());
    }

    public void startDeleteFlow(String id, ServerPlayer admin) {
        requireServerThread("WorldManager.startDeleteFlow");
        WorldDefinition def = worlds.get(id);
        if (def == null) {
            admin.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.world_not_found", id));
            return;
        }

        if (def.isPendingOperation()) {
            admin.sendSystemMessage(Component.literal("§cJ  existe uma opera o pendente para este mundo."));
            return;
        }

        OperationsManager.addOperation(new WorldOperation(id, WorldLifecycleState.PENDING_DELETE, null, null, System.currentTimeMillis()));
        def.setState(WorldLifecycleState.PENDING_DELETE);
        ConfigManager.save();

        admin.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.world_delete_scheduled", id));
        admin.sendSystemMessage(Component.literal("§eReinicie o servidor para concluir a exclus o."));
        LOGGER.info("[BigBangWorld] World '{}' scheduled for deletion by {}.", id, admin.getName().getString());
    }

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
        return def != null;
    }

    @Override
    public boolean isHomeCreationAllowed(ServerPlayer player) {
        if (player == null) return false;
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
        if (player == null) return false;
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
        if (player == null) return false;
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
        if (player == null) return false;
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
}
