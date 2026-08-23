package com.antaurora.apofirstlight.worldgen.highway;

import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.Arrays;

/** Random-access pre-decoration sampling backed by the per-world bounded cache. */
public final class HighwayTerrainSampler {
    public static final int PROFILE_ANCHOR_SPACING = 512;
    private static final int[] ANCHOR_SAMPLE_OFFSETS = {-256, -128, 0, 128, 256};
    private static final int[] ANCHOR_SAMPLE_WEIGHTS = {1, 2, 3, 2, 1};

    private final WorldGenLevel level;
    private final ChunkGenerator generator;
    private final RandomState randomState;
    private final NaturalHighwayCacheManager.WorldCache cache;

    public HighwayTerrainSampler(WorldGenLevel level, ChunkGenerator generator, RandomState randomState) {
        this(level, generator, randomState, new NaturalHighwayCacheManager.WorldCache());
    }

    public HighwayTerrainSampler(WorldGenLevel level, ChunkGenerator generator, RandomState randomState,
                                 NaturalHighwayCacheManager.WorldCache cache) {
        this.level = level;
        this.generator = generator;
        this.randomState = randomState;
        this.cache = cache;
    }

    public int globalRoadY(PrimaryHighwayNetwork.Corridor corridor, double globalStation) {
        long lowerStation = Math.floorDiv((long) Math.floor(globalStation), PROFILE_ANCHOR_SPACING)
                * (long) PROFILE_ANCHOR_SPACING;
        long upperStation = lowerStation + PROFILE_ANCHOR_SPACING;
        int lower = roadAnchor(corridor, lowerStation);
        int upper = roadAnchor(corridor, upperStation);
        double fraction = (globalStation - lowerStation) / PROFILE_ANCHOR_SPACING;
        return (int) Math.round(lower + (upper - lower) * fraction);
    }

    /**
     * Tier 1 uses cached surface/ocean-floor heights. A vertical column is read
     * only where those heightmaps indicate that the surface may actually be fluid.
     */
    public CrossSection crossSection(PrimaryHighwayNetwork.Corridor corridor, double globalStation) {
        long started = System.nanoTime();
        NaturalHighwayRuntimeStats.terrainSampleCall();
        HeightCrossSection cheap = heightCrossSection(corridor, globalStation, true);
        int water = 0;
        int solid = HighwayPlan.MAIN_WIDTH;
        if (cheap.possibleWaterColumns > 0) {
            int station = (int) Math.round(globalStation);
            for (int lateral = -HighwayProfile.ROAD_HALF_WIDTH;
                 lateral <= HighwayProfile.ROAD_HALF_WIDTH; lateral++) {
                if (!cheap.possibleWater[lateral + HighwayProfile.ROAD_HALF_WIDTH]) continue;
                int x = worldX(corridor, station, lateral);
                int z = worldZ(corridor, station, lateral);
                int flags = surfaceFlags(x, z, cheap.heights[lateral + HighwayProfile.ROAD_HALF_WIDTH]);
                if ((flags & 1) != 0) {
                    water++;
                    solid--;
                }
            }
        }
        NaturalHighwayRuntimeStats.terrainSampling(System.nanoTime() - started);
        return new CrossSection(cheap.medianY, cheap.minY, cheap.maxY, cheap.centerY, water, solid);
    }

    private int roadAnchor(PrimaryHighwayNetwork.Corridor corridor, long station) {
        NaturalHighwayCacheManager.AnchorKey key = new NaturalHighwayCacheManager.AnchorKey(
                corridor.orientation(), corridor.index(), station);
        return cache.anchor(key, () -> {
            long started = System.nanoTime();
            long weighted = 0;
            int totalWeight = 0;
            for (int i = 0; i < ANCHOR_SAMPLE_OFFSETS.length; i++) {
                NaturalHighwayRuntimeStats.terrainSampleCall();
                int terrain = heightCrossSection(corridor,
                        station + ANCHOR_SAMPLE_OFFSETS[i], false).medianY;
                weighted += (long) terrain * ANCHOR_SAMPLE_WEIGHTS[i];
                totalWeight += ANCHOR_SAMPLE_WEIGHTS[i];
            }
            NaturalHighwayRuntimeStats.terrainSampling(System.nanoTime() - started);
            int value = (int) Math.round(weighted / (double) totalWeight);
            return Math.max(level.getMinBuildHeight() + 8,
                    Math.min(level.getMaxBuildHeight() - 32, value));
        });
    }

    private HeightCrossSection heightCrossSection(PrimaryHighwayNetwork.Corridor corridor,
                                                  double globalStation, boolean includeWaterHint) {
        int[] heights = new int[HighwayPlan.MAIN_WIDTH];
        int station = (int) Math.round(globalStation);
        int centerHeight = 0;
        for (int lateral = -HighwayProfile.ROAD_HALF_WIDTH;
             lateral <= HighwayProfile.ROAD_HALF_WIDTH; lateral++) {
            int height = baseHeight(worldX(corridor, station, lateral), worldZ(corridor, station, lateral),
                    NaturalHighwayCacheManager.HeightKind.SURFACE);
            heights[lateral + HighwayProfile.ROAD_HALF_WIDTH] = height;
            if (lateral == 0) centerHeight = height;
        }
        int[] sorted = heights.clone();
        Arrays.sort(sorted);
        boolean[] possibleWater = new boolean[HighwayPlan.MAIN_WIDTH];
        int possibleWaterColumns = 0;
        if (includeWaterHint) {
            for (int lateral = -HighwayProfile.ROAD_HALF_WIDTH;
                 lateral <= HighwayProfile.ROAD_HALF_WIDTH; lateral++) {
                int x = worldX(corridor, station, lateral);
                int z = worldZ(corridor, station, lateral);
                int surface = heights[lateral + HighwayProfile.ROAD_HALF_WIDTH];
                if (surface > baseHeight(x, z, NaturalHighwayCacheManager.HeightKind.OCEAN_FLOOR)) {
                    possibleWater[lateral + HighwayProfile.ROAD_HALF_WIDTH] = true;
                    possibleWaterColumns++;
                }
            }
        }
        return new HeightCrossSection(heights, sorted[sorted.length / 2], sorted[0],
                sorted[sorted.length - 1], centerHeight, possibleWater, possibleWaterColumns);
    }

    private int baseHeight(int x, int z, NaturalHighwayCacheManager.HeightKind kind) {
        return cache.height(new NaturalHighwayCacheManager.HeightKey(x, z, kind), () -> {
            NaturalHighwayRuntimeStats.getBaseHeightCall();
            Heightmap.Types type = kind == NaturalHighwayCacheManager.HeightKind.SURFACE
                    ? Heightmap.Types.MOTION_BLOCKING_NO_LEAVES : Heightmap.Types.OCEAN_FLOOR_WG;
            return generator.getBaseHeight(x, z, type, level, randomState);
        });
    }

    private int surfaceFlags(int x, int z, int height) {
        return cache.surfaceFlags(new NaturalHighwayCacheManager.ColumnKey(x, z), () -> {
            NaturalHighwayRuntimeStats.getBaseColumnCall();
            NoiseColumn column = generator.getBaseColumn(x, z, level, randomState);
            BlockState top = column.getBlock(height - 1);
            int flags = top.getFluidState().isEmpty() ? 0 : 1;
            if (!top.isAir()) flags |= 2;
            return flags;
        });
    }

    private static int worldX(PrimaryHighwayNetwork.Corridor corridor, int station, int lateral) {
        return corridor.orientation() == PrimaryHighwayNetwork.Orientation.PRIMARY_NORTH_SOUTH
                ? corridor.fixedCoordinate() + lateral : station;
    }

    private static int worldZ(PrimaryHighwayNetwork.Corridor corridor, int station, int lateral) {
        return corridor.orientation() == PrimaryHighwayNetwork.Orientation.PRIMARY_NORTH_SOUTH
                ? station : corridor.fixedCoordinate() + lateral;
    }

    public record CrossSection(int medianY, int minY, int maxY, int centerY,
                               int waterColumns, int solidColumns) {}

    private record HeightCrossSection(int[] heights, int medianY, int minY, int maxY,
                                      int centerY, boolean[] possibleWater,
                                      int possibleWaterColumns) {}
}
