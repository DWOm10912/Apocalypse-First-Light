package com.antaurora.apofirstlight.dev;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

public final class TerrainSuitabilitySampler {
    private TerrainSuitabilitySampler() {
    }

    public static Result sample(ServerLevel level, BlockPos center, int size) {
        int half = size / 2;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        long sumY = 0L;
        int samples = 0;
        int waterSamples = 0;
        int maxLocalSlope = 0;

        for (int x = center.getX() - half; x < center.getX() - half + size; x++) {
            for (int z = center.getZ() - half; z < center.getZ() - half + size; z++) {
                BlockPos column = new BlockPos(x, level.getMinBuildHeight(), z);
                if (!level.hasChunkAt(column)) {
                    continue;
                }
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                minY = Math.min(minY, surfaceY);
                maxY = Math.max(maxY, surfaceY);
                sumY += surfaceY;
                samples++;

                BlockPos surfaceBlock = new BlockPos(x, surfaceY - 1, z);
                if (!level.getFluidState(surfaceBlock).isEmpty()) {
                    waterSamples++;
                }

                if (x > center.getX() - half) {
                    int previous = level.getHeight(Heightmap.Types.WORLD_SURFACE, x - 1, z);
                    maxLocalSlope = Math.max(maxLocalSlope, Math.abs(surfaceY - previous));
                }
                if (z > center.getZ() - half) {
                    int previous = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z - 1);
                    maxLocalSlope = Math.max(maxLocalSlope, Math.abs(surfaceY - previous));
                }
            }
        }

        if (samples == 0) {
            return new Result(size, 0, 0, 0, 0.0, 0, 0.0, false);
        }
        int deltaY = maxY - minY;
        double averageY = (double) sumY / samples;
        double waterRatio = (double) waterSamples / samples;
        boolean cityFriendly = (size <= 64 && deltaY <= 5)
                || (size <= 128 && deltaY <= 8)
                || (size <= 256 && deltaY <= 15);
        return new Result(size, minY, maxY, deltaY, averageY, maxLocalSlope, waterRatio, cityFriendly);
    }

    public record Result(int size, int minY, int maxY, int deltaY, double averageY,
                         int maxLocalSlope, double waterRatio, boolean cityFriendly) {
    }
}
