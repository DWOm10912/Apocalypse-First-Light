package com.antaurora.apofirstlight.radiation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class RadiationSafeAreaFinder {
    public static final int DEFAULT_MAX_RADIUS = 20_000;
    public static final int SEARCH_STEP = 256;
    public static final int MAX_CANDIDATE_SAMPLES = 25_000;
    public static final int VALIDATION_RADIUS = 64;
    public static final int GRID_STEP = 32;
    public static final int GRID_SIZE = 5;
    public static final int REQUIRED_SAFE_SAMPLES = 21;
    private static volatile SearchStats lastSearchStats = new SearchStats(0, 0, DEFAULT_MAX_RADIUS, SEARCH_STEP);

    private RadiationSafeAreaFinder() {
    }

    public static NaturalSafeAreaResult findNearestNaturalSafeArea(ServerLevel level, BlockPos origin, int maxRadius) {
        RadiationZoneAreaResult result = findNearestZoneArea(level, origin, RadiationZone.SAFE, maxRadius);
        return result == null ? null : new NaturalSafeAreaResult(result.center(), result.distance(),
                result.baseField(), result.baseZone(), result.matchingSamples(), result.totalSamples(), result.validationRadius());
    }

    public static RadiationZoneAreaResult findNearestZoneArea(ServerLevel level, BlockPos origin,
                                                               RadiationZone target, int maxRadius) {
        long start = System.nanoTime();
        int radius = Math.min(DEFAULT_MAX_RADIUS, Math.max(SEARCH_STEP, maxRadius));
        int samples = 0;
        for (int ring = 0; ring * SEARCH_STEP <= radius; ring++) {
            int offset = ring * SEARCH_STEP;
            if (ring == 0) {
                if (++samples > MAX_CANDIDATE_SAMPLES) break;
                RadiationZoneAreaResult result = testCandidate(level, origin.getX(), origin.getZ(), origin, target);
                if (result != null) return finish(result, samples, start, radius);
                continue;
            }
            for (int i = -ring; i <= ring; i++) {
                if (samples >= MAX_CANDIDATE_SAMPLES) return finish(null, samples, start, radius);
                samples++;
                RadiationZoneAreaResult result = testCandidate(level, origin.getX() + offset, origin.getZ() + i * SEARCH_STEP, origin, target);
                if (result != null) return finish(result, samples, start, radius);
                if (samples >= MAX_CANDIDATE_SAMPLES) return finish(null, samples, start, radius);
                samples++;
                result = testCandidate(level, origin.getX() - offset, origin.getZ() + i * SEARCH_STEP, origin, target);
                if (result != null) return finish(result, samples, start, radius);
                if (samples >= MAX_CANDIDATE_SAMPLES) return finish(null, samples, start, radius);
                samples++;
                result = testCandidate(level, origin.getX() + i * SEARCH_STEP, origin.getZ() + offset, origin, target);
                if (result != null) return finish(result, samples, start, radius);
                if (samples >= MAX_CANDIDATE_SAMPLES) return finish(null, samples, start, radius);
                samples++;
                result = testCandidate(level, origin.getX() + i * SEARCH_STEP, origin.getZ() - offset, origin, target);
                if (result != null) return finish(result, samples, start, radius);
            }
        }
        return finish(null, samples, start, radius);
    }

    public static boolean isNaturalSafeArea(ServerLevel level, BlockPos center) {
        return isZoneArea(level, center, RadiationZone.SAFE);
    }

    public static boolean isZoneArea(ServerLevel level, BlockPos center, RadiationZone target) {
        int safe = 0;
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (RadiationManager.isNaturalZone(level,
                        new BlockPos(center.getX() + x * GRID_STEP, 64, center.getZ() + z * GRID_STEP), target)) {
                    safe++;
                }
            }
        }
        return RadiationManager.isNaturalZone(level, center, target) && safe >= REQUIRED_SAFE_SAMPLES;
    }

    private static RadiationZoneAreaResult testCandidate(ServerLevel level, int x, int z, BlockPos origin,
                                                         RadiationZone target) {
        BlockPos candidate = new BlockPos(x, 64, z);
        if (!RadiationManager.isNaturalZone(level, candidate, target)) return null;

        int safe = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos point = new BlockPos(x + dx * GRID_STEP, 64, z + dz * GRID_STEP);
                if (RadiationManager.isNaturalZone(level, point, target)) safe++;
            }
        }
        if (safe < REQUIRED_SAFE_SAMPLES) return null;
        double distance = Math.sqrt(origin.distSqr(candidate));
        return new RadiationZoneAreaResult(candidate, distance, RadiationManager.getNaturalBaseField(level, x, z),
                target, safe, GRID_SIZE * GRID_SIZE, VALIDATION_RADIUS);
    }

    public static SearchStats lastSearchStats() { return lastSearchStats; }

    private static RadiationZoneAreaResult finish(RadiationZoneAreaResult result, int samples, long start, int radius) {
        lastSearchStats = new SearchStats(samples, (System.nanoTime() - start) / 1_000_000L, radius, SEARCH_STEP);
        return result;
    }

    public record SearchStats(int samples, long elapsedMs, int maxRadius, int searchStep) {}
}
