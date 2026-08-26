package com.antaurora.apofirstlight.worldgen.rural;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/** Shared natural-ground and surface-water sampling for Rural site and lot validation. */
public final class RuralTerrainSampler {
    public static final int MAX_GROUND_SCAN_DEPTH = 32;

    private RuralTerrainSampler() {
    }

    public static RuralTerrainSource source(LevelReader level) {
        return (x, z) -> level.hasChunk(x >> 4, z >> 4)
                ? sample(level, x, z)
                : new Sample(0, false, 0, false);
    }

    /**
     * Generator-time sampler. It reads the already available noise column and never asks a
     * Level/WorldGenRegion to load a chunk, which makes it safe for findGenerationPoint.
     */
    public static RuralTerrainSource source(ChunkGenerator generator, LevelHeightAccessor heightAccessor,
                                            RandomState randomState) {
        return (x, z) -> sample(generator, heightAccessor, randomState, x, z);
    }

    public static Sample sample(LevelReader level, int x, int z) {
        int heightmapY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int startY = Math.min(level.getMaxBuildHeight() - 1, heightmapY - 1);
        int minY = Math.max(level.getMinBuildHeight(), startY - MAX_GROUND_SCAN_DEPTH);
        boolean water = false;
        int vegetationSkipped = 0;

        for (int y = startY; y >= minY; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (!level.getFluidState(pos).isEmpty() || isWaterLike(state)) {
                water = true;
                continue;
            }
            if (isDecorative(state, level, pos)) {
                vegetationSkipped++;
                continue;
            }
            if (!state.isAir() && !state.canBeReplaced()
                    && !state.getCollisionShape(level, pos).isEmpty()) {
                return new Sample(y + 1, water, vegetationSkipped, true);
            }
        }
        return new Sample(0, water, vegetationSkipped, false);
    }

    public static Sample sample(ChunkGenerator generator, LevelHeightAccessor heightAccessor,
                                RandomState randomState, int x, int z) {
        int heightmapY = generator.getBaseHeight(x, z, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                heightAccessor, randomState);
        int startY = Math.min(heightAccessor.getMaxBuildHeight() - 1, heightmapY - 1);
        int minY = Math.max(heightAccessor.getMinBuildHeight(), startY - MAX_GROUND_SCAN_DEPTH);
        NoiseColumn column = generator.getBaseColumn(x, z, heightAccessor, randomState);
        boolean water = false;
        int vegetationSkipped = 0;

        for (int y = startY; y >= minY; y--) {
            BlockState state = column.getBlock(y);
            if (isWaterLike(state)) {
                water = true;
                continue;
            }
            if (isGeneratorDecorative(state)) {
                vegetationSkipped++;
                continue;
            }
            if (!state.isAir() && !state.canBeReplaced()) {
                return new Sample(y + 1, water, vegetationSkipped, true);
            }
        }
        return new Sample(0, water, vegetationSkipped, false);
    }

    private static boolean isDecorative(BlockState state, LevelReader level, BlockPos pos) {
        return state.isAir()
                || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.LOGS)
                || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.FLOWERS)
                || state.getBlock() instanceof BushBlock
                || state.getBlock() instanceof VineBlock
                || state.is(Blocks.SNOW)
                || state.canBeReplaced() && state.getCollisionShape(level, pos).isEmpty();
    }

    private static boolean isGeneratorDecorative(BlockState state) {
        return state.isAir()
                || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.LOGS)
                || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.FLOWERS)
                || state.getBlock() instanceof BushBlock
                || state.getBlock() instanceof VineBlock
                || state.is(Blocks.SNOW)
                || state.canBeReplaced();
    }

    public static boolean isWaterLike(BlockState state) {
        return state.is(Blocks.WATER) || state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE)
                || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE);
    }

    public record Sample(int surfaceY, boolean water, int vegetationBlocksSkipped, boolean valid) {
    }
}
