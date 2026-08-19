package com.antaurora.apofirstlight.radiation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class RadiationManager {
    private static final double SAFE_THRESHOLD = 0.08;
    private static final double HEAVY_THRESHOLD = 0.62;
    private static final double SCORCHED_THRESHOLD = 0.84;
    private static final double FALLOFF_RADIUS = 64.0;
    private static final java.util.Map<ServerLevel, RadiationField> FIELDS =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private RadiationManager() {}

    public static RadiationSample getRadiationSample(ServerLevel level, BlockPos pos) {
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
            return RadiationSample.safe(0, 0);
        }
        RadiationWorldData data = RadiationWorldData.get(level);
        long chunkX = pos.getX() >> 4;
        long chunkZ = pos.getZ() >> 4;
        boolean core = chunkX == data.safeChunkX() && chunkZ == data.safeChunkZ();
        double base = field(level).sample(pos.getX(), pos.getZ());
        double suppression = core ? 0.0 : smoothstep(Math.min(1.0, distanceFromCore(pos, data) / FALLOFF_RADIUS));
        double effectiveField = base * suppression;
        double ambient = rateFor(effectiveField);
        RadiationZone zone = zoneFor(effectiveField);
        if (core) {
            ambient = 0.0;
            zone = RadiationZone.SAFE;
        }
        return new RadiationSample(base, zone, ambient, 0.0, ambient, core, suppression,
                data.safeChunkX(), data.safeChunkZ());
    }

    public static double getAmbientRadiation(ServerLevel level, BlockPos pos) {
        return getRadiationSample(level, pos).worldAmbientRadiation();
    }

    public static double getLocalRadiation(ServerLevel level, BlockPos pos) { return 0.0; }
    public static double getFinalRadiation(ServerLevel level, BlockPos pos) { return getRadiationSample(level, pos).finalRadiation(); }
    public static RadiationZone getRadiationZone(ServerLevel level, BlockPos pos) { return getRadiationSample(level, pos).zone(); }

    public static boolean isNaturalSafe(ServerLevel level, BlockPos pos) {
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
            return false;
        }
        RadiationSample sample = getRadiationSample(level, pos);
        return zoneFor(sample.baseField()) == RadiationZone.SAFE
                && !sample.spawnSafeCore()
                && sample.spawnSuppression() >= 1.0;
    }

    public static void setSpawnSafeChunk(ServerLevel level, long chunkX, long chunkZ) {
        RadiationWorldData.get(level).setSpawnSafeChunk(chunkX, chunkZ);
    }

    private static RadiationField field(ServerLevel level) {
        return FIELDS.computeIfAbsent(level, ignored -> new RadiationField(level.getSeed()));
    }

    private static double distanceFromCore(BlockPos pos, RadiationWorldData data) {
        double minX = data.safeChunkX() * 16.0;
        double minZ = data.safeChunkZ() * 16.0;
        double dx = Math.max(minX - pos.getX(), Math.max(0.0, pos.getX() - (minX + 15.999)));
        double dz = Math.max(minZ - pos.getZ(), Math.max(0.0, pos.getZ() - (minZ + 15.999)));
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static RadiationZone zoneFor(double field) {
        if (field < SAFE_THRESHOLD) return RadiationZone.SAFE;
        if (field < HEAVY_THRESHOLD) return RadiationZone.IRRADIATED;
        if (field < SCORCHED_THRESHOLD) return RadiationZone.HEAVY_FALLOUT;
        return RadiationZone.SCORCHED;
    }

    private static double rateFor(double field) {
        if (field < SAFE_THRESHOLD) return 0.0;
        if (field < HEAVY_THRESHOLD) return lerp(0.10, 1.50, (field - SAFE_THRESHOLD) / (HEAVY_THRESHOLD - SAFE_THRESHOLD));
        if (field < SCORCHED_THRESHOLD) return lerp(1.50, 6.00, (field - HEAVY_THRESHOLD) / (SCORCHED_THRESHOLD - HEAVY_THRESHOLD));
        return lerp(6.00, 20.00, (field - SCORCHED_THRESHOLD) / (1.00 - SCORCHED_THRESHOLD));
    }

    private static double smoothstep(double t) { return t * t * (3.0 - 2.0 * t); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
}
