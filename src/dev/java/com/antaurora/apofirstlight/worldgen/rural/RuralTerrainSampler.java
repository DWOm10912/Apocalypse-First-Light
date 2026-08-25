package com.antaurora.apofirstlight.worldgen.rural;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Shared natural-ground and surface-water sampling for Rural site and lot validation. */
public final class RuralTerrainSampler {
    public static final int MAX_GROUND_SCAN_DEPTH = 32;

    private RuralTerrainSampler() {
    }

    public static Sample sample(ServerLevel level, int x, int z) {
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

    private static boolean isDecorative(BlockState state, ServerLevel level, BlockPos pos) {
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

    public static boolean isWaterLike(BlockState state) {
        return state.is(Blocks.WATER) || state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE)
                || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE);
    }

    public record Sample(int surfaceY, boolean water, int vegetationBlocksSkipped, boolean valid) {
    }
}
