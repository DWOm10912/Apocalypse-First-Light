package com.antaurora.apofirstlight.radiation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class RadiationSafeAreaFinder {
    public static final int DEFAULT_MAX_RADIUS = 20_000;
    public static final int SEARCH_STEP = 128;
    public static final int VALIDATION_RADIUS = 64;
    public static final int GRID_STEP = 32;
    public static final int GRID_SIZE = 5;
    public static final int REQUIRED_SAFE_SAMPLES = 21;

    private RadiationSafeAreaFinder() {
    }

    public static NaturalSafeAreaResult findNearestNaturalSafeArea(ServerLevel level, BlockPos origin, int maxRadius) {
        int radius = Math.max(SEARCH_STEP, maxRadius);
        for (int ring = 0; ring * SEARCH_STEP <= radius; ring++) {
            int offset = ring * SEARCH_STEP;
            if (ring == 0) {
                NaturalSafeAreaResult result = testCandidate(level, origin.getX(), origin.getZ(), origin);
                if (result != null) return result;
                continue;
            }
            for (int i = -ring; i <= ring; i++) {
                NaturalSafeAreaResult result = testCandidate(level, origin.getX() + offset, origin.getZ() + i * SEARCH_STEP, origin);
                if (result != null) return result;
                result = testCandidate(level, origin.getX() - offset, origin.getZ() + i * SEARCH_STEP, origin);
                if (result != null) return result;
                result = testCandidate(level, origin.getX() + i * SEARCH_STEP, origin.getZ() + offset, origin);
                if (result != null) return result;
                result = testCandidate(level, origin.getX() + i * SEARCH_STEP, origin.getZ() - offset, origin);
                if (result != null) return result;
            }
        }
        return null;
    }

    public static boolean isNaturalSafeArea(ServerLevel level, BlockPos center) {
        int safe = 0;
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (RadiationManager.isNaturalSafe(level,
                        new BlockPos(center.getX() + x * GRID_STEP, 64, center.getZ() + z * GRID_STEP))) {
                    safe++;
                }
            }
        }
        return RadiationManager.isNaturalSafe(level, center) && safe >= REQUIRED_SAFE_SAMPLES;
    }

    private static NaturalSafeAreaResult testCandidate(ServerLevel level, int x, int z, BlockPos origin) {
        BlockPos candidate = new BlockPos(x, 64, z);
        RadiationSample sample = RadiationManager.getRadiationSample(level, candidate);
        if (!RadiationManager.isNaturalSafe(level, candidate)) return null;

        int safe = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos point = new BlockPos(x + dx * GRID_STEP, 64, z + dz * GRID_STEP);
                if (RadiationManager.isNaturalSafe(level, point)) safe++;
            }
        }
        if (safe < REQUIRED_SAFE_SAMPLES) return null;
        double distance = Math.sqrt(origin.distSqr(candidate));
        return new NaturalSafeAreaResult(candidate, distance, sample.baseField(),
                RadiationZone.SAFE, safe, GRID_SIZE * GRID_SIZE, VALIDATION_RADIUS);
    }
}
