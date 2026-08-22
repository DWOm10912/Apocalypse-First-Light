package com.antaurora.apofirstlight.dev;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** DEV-only calibration and read-only profile helpers for Terrain V2. */
public final class TerrainV2Diagnostics {
    public static final double BASE_DENSITY_PER_BLOCK = 2.0D / 268.0D;
    public static final int TARGET_MACRO_HEIGHT = 8;
    public static final double SELECTED_MACRO_AMPLITUDE = 0.06D;
    public static final double SELECTED_MACRO_CLAMP = 0.075D;
    public static final int PROFILE_RADIUS = 512;
    public static final int PROFILE_STEP = 64;
    public static final int CORE_RADIUS = 256;
    public static final int OUTER_MIN_RADIUS = 320;

    private static final ResourceLocation MACRO_ID = new ResourceLocation(
            "apocalypse_firstlight", "terrain/macro_relief");
    private static final int MAX_TERRAIN_SCAN = 32;

    private TerrainV2Diagnostics() {
    }

    public static Calibration calibration(ServerLevel level, BlockPos center) {
        Registry<DensityFunction> registry = level.registryAccess().registryOrThrow(Registries.DENSITY_FUNCTION);
        DensityFunction macro = registry.get(MACRO_ID);
        if (macro == null) {
            return new Calibration(BASE_DENSITY_PER_BLOCK, TARGET_MACRO_HEIGHT, SELECTED_MACRO_AMPLITUDE,
                    Double.NaN, Double.NaN, -SELECTED_MACRO_CLAMP / BASE_DENSITY_PER_BLOCK,
                    SELECTED_MACRO_CLAMP / BASE_DENSITY_PER_BLOCK, false);
        }

        List<Double> noiseValues = new ArrayList<>();
        for (int x = center.getX() - PROFILE_RADIUS; x <= center.getX() + PROFILE_RADIUS; x += PROFILE_STEP) {
            for (int z = center.getZ() - PROFILE_RADIUS; z <= center.getZ() + PROFILE_RADIUS; z += PROFILE_STEP) {
                double contribution = macro.compute(new DensityFunction.SinglePointContext(x, 70, z));
                noiseValues.add(contribution / SELECTED_MACRO_AMPLITUDE);
            }
        }
        double min = Collections.min(noiseValues);
        double max = Collections.max(noiseValues);
        return new Calibration(BASE_DENSITY_PER_BLOCK, TARGET_MACRO_HEIGHT, SELECTED_MACRO_AMPLITUDE,
                min, max, -SELECTED_MACRO_CLAMP / BASE_DENSITY_PER_BLOCK,
                SELECTED_MACRO_CLAMP / BASE_DENSITY_PER_BLOCK, true);
    }

    public static Profile globalProfile(ServerLevel level, BlockPos center) {
        return sample(level, center, 0, PROFILE_RADIUS, PROFILE_STEP);
    }

    public static Profile coreProfile(ServerLevel level, BlockPos center) {
        return sample(level, center, 0, CORE_RADIUS, PROFILE_STEP);
    }

    public static Profile outerProfile(ServerLevel level, BlockPos center) {
        return sample(level, center, OUTER_MIN_RADIUS, PROFILE_RADIUS, PROFILE_STEP);
    }

    private static Profile sample(ServerLevel level, BlockPos center, int innerRadius, int outerRadius, int step) {
        List<Integer> heights = new ArrayList<>();
        int skipped = 0;
        for (int dx = -outerRadius; dx <= outerRadius; dx += step) {
            for (int dz = -outerRadius; dz <= outerRadius; dz += step) {
                int distanceSquared = dx * dx + dz * dz;
                if (distanceSquared < innerRadius * innerRadius || distanceSquared > outerRadius * outerRadius) continue;
                Integer height = terrainSurface(level, center.getX() + dx, center.getZ() + dz);
                if (height == null) skipped++;
                else heights.add(height);
            }
        }
        if (heights.isEmpty()) return Profile.empty(skipped);
        Collections.sort(heights);
        int p10 = percentile(heights, 0.10D);
        int p25 = percentile(heights, 0.25D);
        int median = percentile(heights, 0.50D);
        int p75 = percentile(heights, 0.75D);
        int p90 = percentile(heights, 0.90D);
        int min = heights.get(0);
        int max = heights.get(heights.size() - 1);
        return new Profile(heights.size(), skipped, min, p10, p25, median, p75, p90, max,
                p90 - p10, max - min);
    }

    private static Integer terrainSurface(ServerLevel level, int x, int z) {
        int height = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        for (int y = height - 1; y >= Math.max(level.getMinBuildHeight(), height - MAX_TERRAIN_SCAN); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (!level.getFluidState(pos).isEmpty()) {
                continue;
            }
            if (isTerrainVegetation(state) || state.isAir()) continue;
            if (!state.getCollisionShape(level, pos).isEmpty()) return y + 1;
        }
        return null;
    }

    private static boolean isTerrainVegetation(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) return false;
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS) || state.is(Blocks.GRASS) || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN) || state.is(Blocks.VINE)
                || state.is(Blocks.SNOW) || state.canBeReplaced();
    }

    private static int percentile(List<Integer> values, double percentile) {
        int index = (int) Math.round((values.size() - 1) * percentile);
        return values.get(Math.max(0, Math.min(values.size() - 1, index)));
    }

    public record Calibration(double baseDensityPerBlock, int targetMacroHeight, double macroDensityAmplitude,
                              double macroNoiseMin, double macroNoiseMax, double expectedHeightMin,
                              double expectedHeightMax, boolean macroFunctionResolved) {
    }

    public record Profile(int sampleCount, int skippedSamples, int minSurfaceY, int p10, int p25, int median,
                          int p75, int p90, int maxSurfaceY, int p90MinusP10, int totalRelief) {
        static Profile empty(int skippedSamples) {
            return new Profile(0, skippedSamples, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }
}
