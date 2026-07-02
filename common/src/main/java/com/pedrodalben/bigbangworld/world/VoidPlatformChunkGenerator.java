package com.pedrodalben.bigbangworld.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class VoidPlatformChunkGenerator extends ChunkGenerator {

    public static final MapCodec<VoidPlatformChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
        instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(VoidPlatformChunkGenerator::getBiomeSource)
        ).apply(instance, VoidPlatformChunkGenerator::new)
    );

    private static final int PLATFORM_Y = 96;
    private static final int PLATFORM_RADIUS = 2;
    private static final int WALL_HEIGHT = 2;

    public VoidPlatformChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState random, ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        if (chunkPos.x != 0 || chunkPos.z != 0) return;

        int chunkMinX = chunkPos.getMinBlockX();
        int chunkMinZ = chunkPos.getMinBlockZ();

        for (int x = -PLATFORM_RADIUS; x <= PLATFORM_RADIUS; x++) {
            for (int z = -PLATFORM_RADIUS; z <= PLATFORM_RADIUS; z++) {
                int wx = x;
                int wz = z;

                if (wx < chunkMinX || wx >= chunkMinX + 16) continue;
                if (wz < chunkMinZ || wz >= chunkMinZ + 16) continue;

                BlockPos floorPos = new BlockPos(wx, PLATFORM_Y - 1, wz);
                chunk.setBlockState(floorPos, Blocks.STONE.defaultBlockState(), false);

                if (Math.abs(x) == PLATFORM_RADIUS || Math.abs(z) == PLATFORM_RADIUS) {
                    for (int wy = 0; wy < WALL_HEIGHT; wy++) {
                        chunk.setBlockState(new BlockPos(wx, PLATFORM_Y + wy, wz), Blocks.GLASS.defaultBlockState(), false);
                    }
                } else {
                    chunk.setBlockState(new BlockPos(wx, PLATFORM_Y, wz), Blocks.AIR.defaultBlockState(), false);
                    chunk.setBlockState(new BlockPos(wx, PLATFORM_Y + 1, wz), Blocks.AIR.defaultBlockState(), false);
                }
            }
        }
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomeManager, StructureManager structures, ChunkAccess chunk, GenerationStep.Carving carver) {
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk) {
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public int getSeaLevel() {
        return -64;
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return PLATFORM_Y;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        int minY = height.getMinBuildHeight();
        int heightRange = height.getHeight();
        BlockState[] blocks = new BlockState[heightRange];
        Arrays.fill(blocks, Blocks.AIR.defaultBlockState());
        return new NoiseColumn(minY, blocks);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
    }
}
