package com.antaurora.apofirstlight.world.biome;

import com.antaurora.apofirstlight.registry.AflBiomes;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * Deterministic, compact surface-biome pocket for the startup bunker.
 * Coordinates are evaluated in block space; MultiNoiseBiomeSource receives
 * quart coordinates and converts before calling this policy.
 */
public final class StartupPlainsEnclave {
    public static final int CENTER_X = 0;
    public static final int CENTER_Z = 0;
    public static final int CORE_RADIUS_BLOCKS = 160;
    public static final int PLAINS_BASE_RADIUS = 208;
    public static final int PLAINS_NOISE_AMPLITUDE = 32;
    public static final int WOODLAND_BASE_OUTER_RADIUS = 480;
    public static final int WOODLAND_NOISE_AMPLITUDE = 56;
    public static final int WOODLAND_NOISE_SCALE = 256;
    public static final int MIN_WOODLAND_BUFFER = 208;
    public static final int MAX_WOODLAND_BUFFER = 336;
    public static final int SURFACE_BAND_MIN_BLOCK_Y = 48;
    public static final int SURFACE_BAND_MAX_BLOCK_Y = 112;
    public static final int SURFACE_BAND_MIN_QUART_Y = 12;
    public static final int SURFACE_BAND_MAX_QUART_Y = 28;

    public enum Zone {
        CORE_PLAINS,
        FRINGE_PLAINS,
        WOODLAND_BUFFER,
        OUTSIDE
    }

    private StartupPlainsEnclave() {
    }

    public static BlockPos referenceCenter(int y) {
        return new BlockPos(CENTER_X, y, CENTER_Z);
    }

    public static boolean containsBlock(int x, int z) {
        return zoneAt(x, z, 0L) != Zone.OUTSIDE;
    }

    public static boolean containsQuart(int quartX, int quartZ) {
        return containsBlock(quartX << 2, quartZ << 2);
    }

    public static boolean isSurfaceQuartY(int quartY) {
        return quartY >= SURFACE_BAND_MIN_QUART_Y && quartY <= SURFACE_BAND_MAX_QUART_Y;
    }

    public static Zone zoneAt(int x, int z, long seed) {
        double distance = distance(x, z);
        if (distance <= CORE_RADIUS_BLOCKS) {
            return Zone.CORE_PLAINS;
        }
        if (distance <= plainsBoundary(x, z, seed)) {
            return Zone.FRINGE_PLAINS;
        }
        return distance <= woodlandOuterBoundary(x, z, seed)
                ? Zone.WOODLAND_BUFFER : Zone.OUTSIDE;
    }

    public static int plainsBoundary(int x, int z, long seed) {
        return (int) Math.round(Math.max(CORE_RADIUS_BLOCKS,
                PLAINS_BASE_RADIUS + smoothNoise(x, z, seed ^ 0x4F1BBCDCBFA54001L, 128.0D)
                        * PLAINS_NOISE_AMPLITUDE));
    }

    public static int woodlandOuterBoundary(int x, int z, long seed) {
        int plains = plainsBoundary(x, z, seed);
        double noisy = WOODLAND_BASE_OUTER_RADIUS
                + smoothNoise(x, z, seed ^ 0x9E3779B97F4A7C15L, WOODLAND_NOISE_SCALE) * WOODLAND_NOISE_AMPLITUDE;
        return (int) Math.round(Math.min(plains + MAX_WOODLAND_BUFFER,
                Math.max(plains + MIN_WOODLAND_BUFFER, noisy)));
    }

    public static ResourceKey<Biome> resolveBiome(int x, int z, long seed, Holder<Biome> original) {
        return switch (zoneAt(x, z, seed)) {
            case CORE_PLAINS, FRINGE_PLAINS -> Biomes.PLAINS;
            case WOODLAND_BUFFER -> AflBiomes.IRRADIATED_WOODLAND;
            case OUTSIDE -> original.unwrapKey().orElse(null);
        };
    }

    private static double distance(int x, int z) {
        return Math.sqrt((double) (x - CENTER_X) * (x - CENTER_X)
                + (double) (z - CENTER_Z) * (z - CENTER_Z));
    }

    private static double smoothNoise(int x, int z, long seed, double scale) {
        double fx = x / scale;
        double fz = z / scale;
        int x0 = (int) Math.floor(fx);
        int z0 = (int) Math.floor(fz);
        double tx = fade(fx - x0);
        double tz = fade(fz - z0);
        double a = lattice(x0, z0, seed);
        double b = lattice(x0 + 1, z0, seed);
        double c = lattice(x0, z0 + 1, seed);
        double d = lattice(x0 + 1, z0 + 1, seed);
        return lerp(lerp(a, b, tx), lerp(c, d, tx), tz);
    }

    private static double lattice(long x, long z, long seed) {
        long h = seed + x * 0x9E3779B97F4A7C15L + z * 0xC2B2AE3D27D4EB4FL;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return h / (double) Long.MAX_VALUE;
    }

    private static double fade(double t) {
        return t * t * (3.0D - 2.0D * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
