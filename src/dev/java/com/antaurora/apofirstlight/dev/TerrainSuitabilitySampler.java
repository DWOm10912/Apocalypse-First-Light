package com.antaurora.apofirstlight.dev;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Read-only sampler for already loaded, buildable terrain columns. */
public final class TerrainSuitabilitySampler {
    private static final int MAX_GROUND_SCAN = 32;

    private TerrainSuitabilitySampler() {
    }

    public static Result sample(ServerLevel level, BlockPos center, int size) {
        int half = size / 2;
        int startX = center.getX() - half;
        int startZ = center.getZ() - half;
        int expectedColumns = size * size;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        long sumY = 0L;
        int sampledColumns = 0;
        int skippedColumns = 0;
        int waterColumns = 0;
        int maxLocalSlope = 0;
        int sampledSlopeEdges = 0;
        int skippedSlopeEdges = 0;

        for (int x = startX; x < startX + size; x++) {
            for (int z = startZ; z < startZ + size; z++) {
                Column column = readColumn(level, x, z);
                if (column == null) {
                    skippedColumns++;
                    continue;
                }
                minY = Math.min(minY, column.groundY());
                maxY = Math.max(maxY, column.groundY());
                sumY += column.groundY();
                sampledColumns++;
                if (column.water()) waterColumns++;

                if (x + 1 < startX + size) {
                    int edge = compareEdge(level, column, x + 1, z);
                    if (edge >= 0) {
                        sampledSlopeEdges++;
                        maxLocalSlope = Math.max(maxLocalSlope, edge);
                    } else skippedSlopeEdges++;
                }
                if (z + 1 < startZ + size) {
                    int edge = compareEdge(level, column, x, z + 1);
                    if (edge >= 0) {
                        sampledSlopeEdges++;
                        maxLocalSlope = Math.max(maxLocalSlope, edge);
                    } else skippedSlopeEdges++;
                }
            }
        }

        if (sampledColumns == 0) {
            return new Result(size, 0, 0, 0, 0.0, 0, 0.0,
                    sampledColumns, skippedColumns, 0.0, sampledSlopeEdges, skippedSlopeEdges, false, false);
        }
        int deltaY = maxY - minY;
        double averageY = (double) sumY / sampledColumns;
        double waterRatio = (double) waterColumns / sampledColumns;
        double coverage = (double) sampledColumns / expectedColumns;
        boolean reliable = coverage >= 0.90D;
        boolean cityFriendly = reliable && ((size <= 64 && deltaY <= 5)
                || (size <= 128 && deltaY <= 8)
                || (size <= 256 && deltaY <= 15));
        return new Result(size, minY, maxY, deltaY, averageY, maxLocalSlope, waterRatio,
                sampledColumns, skippedColumns, coverage, sampledSlopeEdges, skippedSlopeEdges,
                reliable, cityFriendly);
    }

    private static int compareEdge(ServerLevel level, Column first, int x, int z) {
        Column second = readColumn(level, x, z);
        if (second == null || first.water() || second.water()) return -1;
        return Math.abs(first.groundY() - second.groundY());
    }

    private static Column readColumn(ServerLevel level, int x, int z) {
        if (!isLoaded(level, x, z)) return null;
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos surface = new BlockPos(x, surfaceY - 1, z);
        boolean water = !level.getFluidState(surface).isEmpty();
        int groundY = water ? findGround(level, x, surfaceY - 1, z) : surfaceY;
        return groundY == Integer.MIN_VALUE ? null : new Column(groundY, water);
    }

    private static int findGround(ServerLevel level, int x, int startY, int z) {
        for (int y = startY; y >= Math.max(level.getMinBuildHeight(), startY - MAX_GROUND_SCAN); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !level.getFluidState(pos).isEmpty()) continue;
            return y + 1;
        }
        return Integer.MIN_VALUE;
    }

    private static boolean isLoaded(ServerLevel level, int x, int z) {
        return level.getChunkSource().hasChunk(x >> 4, z >> 4);
    }

    private record Column(int groundY, boolean water) {}

    public record Result(int size, int minY, int maxY, int deltaY, double averageY,
                         int maxLocalSlope, double waterRatio, int sampledColumns,
                         int skippedColumns, double coverage, int sampledSlopeEdges,
                         int skippedSlopeEdges, boolean reliable, boolean cityFriendly) {}
}
