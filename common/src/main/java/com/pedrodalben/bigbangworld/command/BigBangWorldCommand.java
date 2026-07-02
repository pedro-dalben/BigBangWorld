package com.pedrodalben.bigbangworld.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.pedrodalben.bigbangworld.config.ConfigManager;
import com.pedrodalben.bigbangworld.domain.*;
import com.pedrodalben.bigbangworld.util.PermissionService;
import com.pedrodalben.bigbangworld.util.TranslationUtil;
import com.pedrodalben.bigbangworld.world.WorldManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;

import java.util.Collection;

public class BigBangWorldCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Register /bbworld
        dispatcher.register(Commands.literal("bbworld")
            .requires(src -> src.hasPermission(4) || src.isPlayer())
            .then(Commands.literal("create")
                .then(Commands.argument("id", StringArgumentType.word())
                    .then(Commands.argument("type", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("normal");
                            builder.suggest("superflat");
                            builder.suggest("void");
                            return builder.buildFuture();
                        })
                        .executes(ctx -> executeCreate(ctx, StringArgumentType.getString(ctx, "id"), StringArgumentType.getString(ctx, "type"), null))
                        .then(Commands.argument("seed", StringArgumentType.string())
                            .executes(ctx -> executeCreate(ctx, StringArgumentType.getString(ctx, "id"), StringArgumentType.getString(ctx, "type"), StringArgumentType.getString(ctx, "seed")))
                        )
                    )
                )
            )
            .then(Commands.literal("list")
                .executes(BigBangWorldCommand::executeList)
            )
            .then(Commands.literal("info")
                .then(Commands.argument("id", StringArgumentType.word())
                    .executes(ctx -> executeInfo(ctx, StringArgumentType.getString(ctx, "id")))
                )
            )
            .then(Commands.literal("tp")
                .then(Commands.argument("id", StringArgumentType.word())
                    .executes(ctx -> executeTp(ctx, StringArgumentType.getString(ctx, "id"), null))
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> executeTp(ctx, StringArgumentType.getString(ctx, "id"), EntityArgument.getPlayer(ctx, "player")))
                    )
                )
            )
            .then(Commands.literal("setspawn")
                .then(Commands.argument("id", StringArgumentType.word())
                    .executes(ctx -> executeSetSpawn(ctx, StringArgumentType.getString(ctx, "id")))
                )
            )
            .then(Commands.literal("access")
                .then(Commands.argument("id", StringArgumentType.word())
                    .then(Commands.literal("public")
                        .executes(ctx -> executeAccess(ctx, StringArgumentType.getString(ctx, "id"), true))
                    )
                    .then(Commands.literal("private")
                        .executes(ctx -> executeAccess(ctx, StringArgumentType.getString(ctx, "id"), false))
                    )
                )
            )
            .then(Commands.literal("border")
                .then(Commands.argument("id", StringArgumentType.word())
                    .then(Commands.literal("off")
                        .executes(ctx -> executeBorder(ctx, StringArgumentType.getString(ctx, "id"), "off"))
                    )
                    .then(Commands.argument("diameter", DoubleArgumentType.doubleArg(1.0))
                        .executes(ctx -> executeBorder(ctx, StringArgumentType.getString(ctx, "id"), String.valueOf(DoubleArgumentType.getDouble(ctx, "diameter"))))
                    )
                )
            )
            .then(Commands.literal("enable")
                .then(Commands.argument("id", StringArgumentType.word())
                    .executes(ctx -> executeEnable(ctx, StringArgumentType.getString(ctx, "id")))
                )
            )
            .then(Commands.literal("disable")
                .then(Commands.argument("id", StringArgumentType.word())
                    .executes(ctx -> executeDisable(ctx, StringArgumentType.getString(ctx, "id")))
                )
            )
            .then(Commands.literal("reset")
                .then(Commands.argument("id", StringArgumentType.word())
                    .executes(ctx -> executeReset(ctx, StringArgumentType.getString(ctx, "id"), null))
                    .then(Commands.argument("arguments", StringArgumentType.greedyString())
                        .executes(ctx -> executeReset(ctx, StringArgumentType.getString(ctx, "id"), StringArgumentType.getString(ctx, "arguments")))
                    )
                )
            )
            .then(Commands.literal("delete")
                .then(Commands.argument("id", StringArgumentType.word())
                    .executes(ctx -> executeDelete(ctx, StringArgumentType.getString(ctx, "id"), null))
                    .then(Commands.argument("confirm", StringArgumentType.word())
                        .executes(ctx -> executeDelete(ctx, StringArgumentType.getString(ctx, "id"), StringArgumentType.getString(ctx, "confirm")))
                    )
                )
            )
            .then(Commands.literal("diagnose")
                .then(Commands.argument("id", StringArgumentType.word())
                    .executes(ctx -> executeDiagnose(ctx, StringArgumentType.getString(ctx, "id")))
                )
            )
        );

        // Register /explorar
        dispatcher.register(Commands.literal("explorar")
            .requires(src -> src.isPlayer() && PermissionService.hasPermission(src.getPlayer(), "bigbangworld.explore"))
            .executes(BigBangWorldCommand::executeExplore)
        );
    }

    private static int executeCreate(CommandContext<CommandSourceStack> ctx, String id, String typeStr, String seed) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Apenas jogadores podem criar mundos."));
            return 0;
        }

        if (!PermissionService.hasPermission(player, "bigbangworld.create") && !PermissionService.hasPermission(player, "bigbangworld.admin")) {
            player.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.no_permission"));
            return 0;
        }

        WorldType type;
        try {
            type = WorldType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendSystemMessage(Component.literal("§cTipo de mundo inválido: " + typeStr + ". Escolha entre normal, superflat, void."));
            return 0;
        }

        WorldManager.getInstance().createWorld(id, type, seed, player);
        return 1;
    }

    private static int executeList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayer();
        if (player != null && !PermissionService.hasPermission(player, "bigbangworld.teleport") && !PermissionService.hasPermission(player, "bigbangworld.admin")) {
            player.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.no_permission"));
            return 0;
        }

        Collection<WorldDefinition> list = WorldManager.getInstance().getAllWorlds();

        src.sendSuccess(() -> Component.literal("§6=== Mundos BigBangWorld (" + list.size() + ") ==="), false);
        for (WorldDefinition def : list) {
            int count = 0;
            MinecraftServer server = src.getServer();
            ResourceLocation resLoc = ResourceLocation.fromNamespaceAndPath("bigbangworld", def.getId());
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, resLoc);
            ServerLevel level = server.getLevel(key);
            if (level != null) {
                count = level.players().size();
            }

            final int playerCount = count;
            src.sendSuccess(() -> Component.literal(
                String.format("§eID: §f%s §7| §eNome: §f%s §7| §eTipo: §a%s §7| §eEstado: §b%s §7| §eJogadores: §d%d §7| §eAcesso: §f%s",
                    def.getId(), def.getDisplayName(), def.getType(), def.getState(), playerCount, def.isPublicAccess() ? "Público" : "Privado")
            ), false);
        }
        return 1;
    }

    private static int executeInfo(CommandContext<CommandSourceStack> ctx, String id) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayer();
        if (player != null && !PermissionService.hasPermission(player, "bigbangworld.teleport") && !PermissionService.hasPermission(player, "bigbangworld.admin")) {
            player.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.no_permission"));
            return 0;
        }

        WorldDefinition def = WorldManager.getInstance().getWorld(id);
        if (def == null) {
            src.sendFailure(TranslationUtil.getComponent("bigbangworld.message.world_not_found", id));
            return 0;
        }

        src.sendSuccess(() -> Component.literal("§6=== Detalhes do Mundo: " + id + " ==="), false);
        src.sendSuccess(() -> Component.literal("§eID: §f" + def.getId()), false);
        src.sendSuccess(() -> Component.literal("§eNome: §f" + def.getDisplayName()), false);
        src.sendSuccess(() -> Component.literal("§eTipo: §f" + def.getType()), false);
        src.sendSuccess(() -> Component.literal("§eEstado: §f" + def.getState()), false);
        src.sendSuccess(() -> Component.literal("§eSeed: §f" + def.getSeed()), false);
        src.sendSuccess(() -> Component.literal("§eChave de Dimensão: §f" + def.getDimensionKey()), false);
        src.sendSuccess(() -> Component.literal("§eAcesso: §f" + (def.isPublicAccess() ? "Público" : "Privado")), false);
        src.sendSuccess(() -> Component.literal("§eCriado em: §f" + def.getCreatedAt()), false);
        src.sendSuccess(() -> Component.literal("§eÚltimo Reset: §f" + (def.getLastResetAt() == null ? "Nunca" : def.getLastResetAt())), false);
        src.sendSuccess(() -> Component.literal("§eContador de Resets: §f" + def.getResetCount()), false);
        src.sendSuccess(() -> Component.literal(String.format("§eSpawn: §fX=%.1f, Y=%.1f, Z=%.1f", def.getSpawn().getX(), def.getSpawn().getY(), def.getSpawn().getZ())), false);
        src.sendSuccess(() -> Component.literal("§eBorda Ativa: §f" + (def.getBorder().isEnabled() ? def.getBorder().getDiameter() + " blocos" : "Desativada")), false);
        
        WorldPolicies pol = def.getPolicies();
        src.sendSuccess(() -> Component.literal(String.format("§ePolíticas: §7[Home=%b, Waystone=%b, Claim=%b, ChunkLoad=%b]", 
            pol.isAllowHomeCreation(), pol.isAllowWaystones(), pol.isAllowClaims(), pol.isAllowChunkLoading())), false);

        return 1;
    }

    private static int executeTp(CommandContext<CommandSourceStack> ctx, String id, ServerPlayer target) {
        CommandSourceStack src = ctx.getSource();
        WorldDefinition def = WorldManager.getInstance().getWorld(id);
        if (def == null) {
            src.sendFailure(TranslationUtil.getComponent("bigbangworld.message.world_not_found", id));
            return 0;
        }

        if (def.getState() != WorldLifecycleState.ACTIVE) {
            src.sendFailure(TranslationUtil.getComponent("bigbangworld.message.cannot_teleport_unavailable", id));
            return 0;
        }

        ServerPlayer sender = src.getPlayer();
        if (sender != null) {
            boolean isSelf = (target == null || target == sender);
            String perm = isSelf ? "bigbangworld.teleport" : "bigbangworld.teleport.other";
            if (!PermissionService.hasPermission(sender, perm) && !PermissionService.hasPermission(sender, "bigbangworld.admin")) {
                sender.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.no_permission"));
                return 0;
            }
        }

        ServerPlayer playerToTeleport = target != null ? target : sender;
        if (playerToTeleport == null) {
            src.sendFailure(Component.literal("Apenas jogadores podem ser teleportados."));
            return 0;
        }

        WorldManager.getInstance().teleportPlayerToWorld(playerToTeleport, def);
        return 1;
    }

    private static int executeSetSpawn(CommandContext<CommandSourceStack> ctx, String id) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Apenas jogadores podem definir o spawn."));
            return 0;
        }

        if (!PermissionService.hasPermission(player, "bigbangworld.configure") && !PermissionService.hasPermission(player, "bigbangworld.admin")) {
            player.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.no_permission"));
            return 0;
        }

        WorldManager.getInstance().setSpawn(id, player);
        return 1;
    }

    private static ServerPlayer adminOrNull(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getPlayer();
    }

    private static int executeAccess(CommandContext<CommandSourceStack> ctx, String id, boolean publicAccess) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = adminOrNull(ctx);
        if (player != null && !PermissionService.hasPermission(player, "bigbangworld.configure") && !PermissionService.hasPermission(player, "bigbangworld.admin")) {
            player.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.no_permission"));
            return 0;
        }

        WorldManager.getInstance().setAccess(id, publicAccess, player);
        return 1;
    }

    private static int executeBorder(CommandContext<CommandSourceStack> ctx, String id, String diameter) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = adminOrNull(ctx);
        if (player != null && !PermissionService.hasPermission(player, "bigbangworld.configure") && !PermissionService.hasPermission(player, "bigbangworld.admin")) {
            player.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.no_permission"));
            return 0;
        }

        WorldManager.getInstance().setBorder(id, diameter, player);
        return 1;
    }

    private static int executeEnable(CommandContext<CommandSourceStack> ctx, String id) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = adminOrNull(ctx);
        if (player != null && !PermissionService.hasPermission(player, "bigbangworld.configure") && !PermissionService.hasPermission(player, "bigbangworld.admin")) {
            player.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.no_permission"));
            return 0;
        }

        WorldManager.getInstance().enableWorld(id, player);
        return 1;
    }

    private static int executeDisable(CommandContext<CommandSourceStack> ctx, String id) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = adminOrNull(ctx);
        if (player != null && !PermissionService.hasPermission(player, "bigbangworld.configure") && !PermissionService.hasPermission(player, "bigbangworld.admin")) {
            player.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.no_permission"));
            return 0;
        }

        WorldManager.getInstance().disableWorld(id, player);
        return 1;
    }

    private static int executeReset(CommandContext<CommandSourceStack> ctx, String id, String argsStr) {
        ServerPlayer player = adminOrNull(ctx);
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Apenas jogadores podem executar o reset."));
            return 0;
        }

        if (!PermissionService.hasPermission(player, "bigbangworld.reset") && !PermissionService.hasPermission(player, "bigbangworld.admin")) {
            player.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.no_permission"));
            return 0;
        }

        if (argsStr == null || argsStr.isBlank()) {
            WorldManager.getInstance().startResetFlow(id, "same-seed", null, player);
            return 1;
        }

        String[] parts = argsStr.split("\\s+");
        boolean confirm = false;
        String seedOption = "same-seed";
        String seedValue = null;

        for (int i = 0; i < parts.length; i++) {
            if ("--confirm".equals(parts[i])) {
                confirm = true;
            } else if ("random-seed".equals(parts[i])) {
                seedOption = "random-seed";
            } else if ("same-seed".equals(parts[i])) {
                seedOption = "same-seed";
            } else if ("seed".equals(parts[i]) && i + 1 < parts.length) {
                seedOption = "seed";
                seedValue = parts[i + 1];
                i++;
            }
        }

        if (confirm) {
            WorldManager.getInstance().confirmAction(player);
        } else {
            WorldManager.getInstance().startResetFlow(id, seedOption, seedValue, player);
        }
        return 1;
    }

    private static int executeDelete(CommandContext<CommandSourceStack> ctx, String id, String confirmStr) {
        ServerPlayer player = adminOrNull(ctx);
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Apenas jogadores podem excluir mundos."));
            return 0;
        }

        if (!PermissionService.hasPermission(player, "bigbangworld.delete") && !PermissionService.hasPermission(player, "bigbangworld.admin")) {
            player.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.no_permission"));
            return 0;
        }

        if ("--confirm".equals(confirmStr)) {
            WorldManager.getInstance().confirmAction(player);
        } else {
            WorldManager.getInstance().startDeleteFlow(id, player);
        }
        return 1;
    }

    private static int executeDiagnose(CommandContext<CommandSourceStack> ctx, String id) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayer();
        if (player != null && !PermissionService.hasPermission(player, "bigbangworld.admin")) {
            player.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.no_permission"));
            return 0;
        }

        WorldDefinition def = WorldManager.getInstance().getWorld(id);
        if (def == null) {
            src.sendFailure(TranslationUtil.getComponent("bigbangworld.message.world_not_found", id));
            return 0;
        }

        ResourceLocation resLoc = ResourceLocation.fromNamespaceAndPath("bigbangworld", id);
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, resLoc);
        ServerLevel level = src.getServer().getLevel(key);

        src.sendSuccess(() -> Component.literal("§6=== Diagnóstico do Mundo: " + id + " ==="), false);
        src.sendSuccess(() -> Component.literal("§eTipo de Mundo: §f" + def.getType()), false);
        src.sendSuccess(() -> Component.literal("§eChave de Dimensão: §f" + def.getDimensionKey()), false);
        src.sendSuccess(() -> Component.literal("§eEstado: §f" + def.getState()), false);
        src.sendSuccess(() -> Component.literal("§eSeed: §f" + def.getSeed()), false);
        src.sendSuccess(() -> Component.literal("§eAcesso: §f" + (def.isPublicAccess() ? "Público" : "Privado")), false);
        src.sendSuccess(() -> Component.literal(String.format("§eSpawn: §fX=%.1f, Y=%.1f, Z=%.1f", def.getSpawn().getX(), def.getSpawn().getY(), def.getSpawn().getZ())), false);
        src.sendSuccess(() -> Component.literal("§eÚltimo Reset: §f" + (def.getLastResetAt() == null ? "Nunca" : def.getLastResetAt())), false);
        src.sendSuccess(() -> Component.literal("§eContador de Resets: §f" + def.getResetCount()), false);

        if (level != null) {
            ChunkGenerator generator = level.getChunkSource().getGenerator();
            src.sendSuccess(() -> Component.literal("§eGerador Utilizado: §a" + generator.getClass().getSimpleName()), false);
            src.sendSuccess(() -> Component.literal("§eBiome Source: §a" + generator.getBiomeSource().getClass().getSimpleName()), false);
            
            int structuresCount = level.registryAccess().registryOrThrow(Registries.STRUCTURE).size();
            int structureSetsCount = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).size();
            src.sendSuccess(() -> Component.literal("§eEstruturas no Registry: §a" + structuresCount), false);
            src.sendSuccess(() -> Component.literal("§eStructure Sets no Registry: §a" + structureSetsCount), false);
        } else {
            src.sendSuccess(() -> Component.literal("§cGerador: N/A (Mundo descarregado)"), false);
        }

        boolean hasCobblemon = BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.fromNamespaceAndPath("cobblemon", "pokemon"));
        boolean hasCobblemonExtra = false;
        try {
            hasCobblemonExtra = com.pedrodalben.bigbangworld.util.Platform.isModLoaded("cobblemon_extra_structures") ||
                com.pedrodalben.bigbangworld.util.Platform.isModLoaded("cobblemon-extra-structures");
        } catch (Exception ignored) {}
        boolean hasWaystones = BuiltInRegistries.BLOCK.containsKey(ResourceLocation.fromNamespaceAndPath("waystones", "waystone"));

        src.sendSuccess(() -> Component.literal("§ePresença de Cobblemon: " + (hasCobblemon ? "§aSim" : "§cNão")), false);
        src.sendSuccess(() -> Component.literal("§ePresença de Waystones: " + (hasWaystones ? "§aSim" : "§cNão")), false);

        if (hasCobblemon) {
            src.sendSuccess(() -> Component.literal("§aCobblemon detectado. Estruturas serão geradas conforme as regras do mod."), false);
        }
        
        src.sendSuccess(() -> Component.literal(String.format("§ePolíticas Ativas: §7[Home=%b, Waystone=%b, Claim=%b, ChunkLoad=%b]", 
            def.getPolicies().isAllowHomeCreation(), def.getPolicies().isAllowWaystones(), 
            def.getPolicies().isAllowClaims(), def.getPolicies().isAllowChunkLoading())), false);

        src.sendSuccess(() -> Component.literal("§eBorda Ativa: §f" + (def.getBorder().isEnabled() ? def.getBorder().getDiameter() + " blocos" : "Desativada")), false);

        return 1;
    }

    private static int executeExplore(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Apenas jogadores podem usar o comando /explorar."));
            return 0;
        }

        String explorationId = ConfigManager.getConfig().getDefaultExplorationWorld();
        WorldDefinition def = WorldManager.getInstance().getWorld(explorationId);

        if (def == null) {
            player.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.no_exploration_world"));
            return 0;
        }

        if (def.getState() != WorldLifecycleState.ACTIVE) {
            player.sendSystemMessage(TranslationUtil.getComponent("bigbangworld.message.world_unavailable"));
            return 0;
        }

        WorldManager.getInstance().teleportPlayerToWorld(player, def);
        return 1;
    }
}
