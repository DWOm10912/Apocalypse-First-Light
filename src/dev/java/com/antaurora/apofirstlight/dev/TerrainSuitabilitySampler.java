package com.antaurora.apofirstlight.dev;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Read-only sampler for already loaded, buildable terrain columns. */
public final class TerrainSuitabilitySampler {
    private static final int MAX_GROUND_SCAN = 32;
    private static final double MIN_COVERAGE = 0.90D;
    private static final double MAX_CITY_WATER_RATIO = 0.10D;
    private static final int MAX_P99_LOCAL_SLOPE = 3;

    private TerrainSuitabilitySampler() {
    }

    public static Result sample(ServerLevel level, BlockPos center, int size) {
        int half = size / 2;
        int startX = center.getX() - half;
        int startZ = center.getZ() - half;
        int expectedColumns = size * size;
        List<Integer> groundHeights = new ArrayList<>();
        List<Integer> localSlopes = new ArrayList<>();
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        long sumY = 0L;
        int skippedColumns = 0;
        int waterColumns = 0;
        int rawMaxLocalSlope = 0;
        int skippedSlopeEdges = 0;

        for (int x = startX; x < startX + size; x++) {
            for (int z = startZ; z < startZ + size; z++) {
                Column column = readColumn(level, x, z);
                if (column == null) {
                    skippedColumns++;
                    continue;
                }
                int groundY = column.groundY();
                minY = Math.min(minY, groundY);
                maxY = Math.max(maxY, groundY);
                sumY += groundY;
                groundHeights.add(groundY);
                if (column.water()) waterColumns++;

                if (x + 1 < startX + size) {
                    int edge = compareEdge(level, column, x + 1, z);
                    if (edge >= 0) {
                        localSlopes.add(edge);
                        rawMaxLocalSlope = Math.max(rawMaxLocalSlope, edge);
                    } else {
                        skippedSlopeEdges++;
                    }
                }
                if (z + 1 < startZ + size) {
                    int edge = compareEdge(level, column, x, z + 1);
                    if (edge >= 0) {
                        localSlopes.add(edge);
                        rawMaxLocalSlope = Math.max(rawMaxLocalSlope, edge);
                    } else {
                        skippedSlopeEdges++;
                    }
                }
            }
        }

        int sampledColumns = groundHeights.size();
        int sampledSlopeEdges = localSlopes.size();
        double coverage = (double) sampledColumns / expectedColumns;
        if (sampledColumns == 0) {
            return new Result(size, 0, 0, 0, 0, 0, 0, 0, 0.0,
                    0, 0, 0.0, 0, skippedColumns, coverage, sampledSlopeEdges,
                    skippedSlopeEdges, false, "UNKNOWN", "INSUFFICIENT_COVERAGE");
        }

        int rawDeltaY = maxY - minY;
        double averageY = (double) sumY / sampledColumns;
        double waterRatio = (double) waterColumns / sampledColumns;
        int p05Y = nearestRankPercentile(groundHeights, 0.05D);
        int medianY = nearestRankPercentile(groundHeights, 0.50D);
        int p95Y = nearestRankPercentile(groundHeights, 0.95D);
        int robustDeltaY = p95Y - p05Y;
        int p99LocalSlope = localSlopes.isEmpty()
                ? 0
                : nearestRankPercentile(localSlopes, 0.99D);
        boolean reliable = coverage >= MIN_COVERAGE;

        List<String> failureReasons = new ArrayList<>();
        if (!reliable) failureReasons.add("INSUFFICIENT_COVERAGE");
        if (robustDeltaY > cityDeltaThreshold(size)) failureReasons.add("ROBUST_DELTA");
        if (p99LocalSlope > MAX_P99_LOCAL_SLOPE) failureReasons.add("ROBUST_SLOPE");
        if (waterRatio > MAX_CITY_WATER_RATIO) failureReasons.add("WATER_RATIO");

        String reason = failureReasons.isEmpty() ? "OK" : String.join(",", failureReasons);
        String cityFriendly = !reliable ? "UNKNOWN" : (failureReasons.isEmpty() ? "TRUE" : "FALSE");
        return new Result(size, minY, maxY, rawDeltaY, p05Y, medianY, p95Y, robustDeltaY,
                averageY, rawMaxLocalSlope, p99LocalSlope, waterRatio, sampledColumns,
                skippedColumns, coverage, sampledSlopeEdges, skippedSlopeEdges, reliable,
                cityFriendly, reason);
    }

    private static int cityDeltaThreshold(int size) {
        if (size <= 64) return 6;
        if (size <= 128) return 10;
        return 15;
    }

    /** Nearest-rank percentile: ceil(p * n) is one-based, converted to a zero-based index. */
    private static int nearestRankPercentile(List<Integer> values, double percentile) {
        List<Integer> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int rank = (int) Math.ceil(percentile * sorted.size());
        int index = Math.max(0, Math.min(sorted.size() - 1, rank - 1));
        return sorted.get(index);
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

    public record Result(int size, int minY, int maxY, int deltaY,
                         int p05Y, int medianY, int p95Y, int robustDeltaY,
                         double averageY, int maxLocalSlope, int p99LocalSlope,
                         double waterRatio, int sampledColumns, int skippedColumns,
                         double coverage, int sampledSlopeEdges, int skippedSlopeEdges,
                         boolean reliable, String cityFriendly, String reason) {
    }
}
