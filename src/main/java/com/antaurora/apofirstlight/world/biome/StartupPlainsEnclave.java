package com.antaurora.apofirstlight.world.biome;

import com.antaurora.apofirstlight.registry.AflBiomes;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
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
    private static final double WOODLAND_LOBE_START_RADIUS = WOODLAND_BASE_OUTER_RADIUS - 128.0D;
    private static final double WOODLAND_LOBE_NOISE_AMPLITUDE = 24.0D;
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

    /**
     * Diagnostic source for the Woodland portion of the startup ecology shape.
     * BASE is the guaranteed radial Woodland ring; the lobe values are only
     * returned outside that ring.
     */
    public enum ShapeSource {
        BASE,
        PRIMARY_LOBE,
        SECONDARY_LOBE_0,
        SECONDARY_LOBE_1,
        NONE
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
        return woodlandShapeSource(x, z, seed) == ShapeSource.NONE
                ? Zone.OUTSIDE : Zone.WOODLAND_BUFFER;
    }

    public static ShapeSource woodlandShapeSource(int x, int z, long seed) {
        double distance = distance(x, z);
        if (distance <= plainsBoundary(x, z, seed)) {
            return ShapeSource.NONE;
        }
        if (distance <= woodlandOuterBoundary(x, z, seed)) {
            return ShapeSource.BASE;
        }
        if (matchesLobe(x, z, seed, -1)) {
            return ShapeSource.PRIMARY_LOBE;
        }
        int secondaryCount = secondaryLobeCount(seed);
        if (secondaryCount >= 1 && matchesLobe(x, z, seed, 0)) {
            return ShapeSource.SECONDARY_LOBE_0;
        }
        if (secondaryCount >= 2 && matchesLobe(x, z, seed, 1)) {
            return ShapeSource.SECONDARY_LOBE_1;
        }
        return ShapeSource.NONE;
    }

    public static double primaryLobeAngleDegrees(long seed) {
        return Math.toDegrees(lobeAngle(seed, -1));
    }

    public static int primaryLobeExtraLength(long seed) {
        return lobeExtraLength(seed, -1);
    }

    public static int primaryLobeHalfWidth(long seed) {
        return lobeHalfWidth(seed, -1);
    }

    public static int secondaryLobeCount(long seed) {
        return (int) (unit(mixSeed(seed, 0x1F123BB5A7C3D9E1L)) * 3.0D);
    }

    public static double secondaryLobeAngleDegrees(long seed, int index) {
        return Math.toDegrees(lobeAngle(seed, index));
    }

    public static int secondaryLobeExtraLength(long seed, int index) {
        return lobeExtraLength(seed, index);
    }

    public static int secondaryLobeHalfWidth(long seed, int index) {
        return lobeHalfWidth(seed, index);
    }

    /** Returns the forward projection for the primary or secondary lobe. */
    public static double lobeForward(int x, int z, long seed, int index) {
        return forwardProjection(x, z, lobeAngle(seed, index));
    }

    /** Returns the signed side projection for the primary or secondary lobe. */
    public static double lobeSide(int x, int z, long seed, int index) {
        return sideProjection(x, z, lobeAngle(seed, index));
    }

    /** Positive means inside the selected lobe's noisy side boundary. */
    public static double lobeBoundaryMargin(int x, int z, long seed, int index) {
        return lobeMargin(x, z, seed, index);
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
        if (isAquaticSurfaceBiome(original)) {
            return original.unwrapKey().orElse(null);
        }
        return switch (zoneAt(x, z, seed)) {
            case CORE_PLAINS, FRINGE_PLAINS -> Biomes.PLAINS;
            case WOODLAND_BUFFER -> AflBiomes.IRRADIATED_WOODLAND;
            case OUTSIDE -> original.unwrapKey().orElse(null);
        };
    }

    /**
     * Aquatic surface biomes remain authoritative even inside the startup
     * geometry.  The holder tags are the 1.20.1 source of truth and cover the
     * ocean/deep-ocean family plus river/frozen-river.
     */
    public static boolean isAquaticSurfaceBiome(Holder<Biome> biome) {
        return biome != null && (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER));
    }

    private static double distance(int x, int z) {
        return Math.sqrt((double) (x - CENTER_X) * (x - CENTER_X)
                + (double) (z - CENTER_Z) * (z - CENTER_Z));
    }

    private static boolean matchesLobe(int x, int z, long seed, int index) {
        return lobeMargin(x, z, seed, index) >= 0.0D;
    }

    private static double lobeMargin(int x, int z, long seed, int index) {
        double angle = lobeAngle(seed, index);
        double forward = forwardProjection(x, z, angle);
        double side = sideProjection(x, z, angle);
        double end = WOODLAND_BASE_OUTER_RADIUS + lobeExtraLength(seed, index);
        if (forward < WOODLAND_LOBE_START_RADIUS) {
            return forward - WOODLAND_LOBE_START_RADIUS;
        }
        if (forward > end) {
            return end - forward;
        }
        double progress = (forward - WOODLAND_LOBE_START_RADIUS)
                / (end - WOODLAND_LOBE_START_RADIUS);
        double widthProfile = 0.78D + 0.18D * Math.sin(progress * Math.PI) + 0.04D * progress;
        double edgeNoise = smoothNoise(x, z, seed ^ lobeNoiseSalt(index), 384.0D)
                * WOODLAND_LOBE_NOISE_AMPLITUDE;
        double allowedHalfWidth = lobeHalfWidth(seed, index) * widthProfile + edgeNoise;
        return allowedHalfWidth - Math.abs(side);
    }

    private static double lobeAngle(long seed, int index) {
        double primary = unit(mixSeed(seed, 0x6A09E667F3BCC909L)) * Math.PI * 2.0D;
        if (index < 0) {
            return primary;
        }
        double offset;
        if (index == 0) {
            offset = (0.60D + unit(mixSeed(seed, 0xBB67AE8584CAA73BL)) * 0.25D) * Math.PI;
        } else {
            offset = (1.15D + unit(mixSeed(seed, 0x3C6EF372FE94F82BL)) * 0.20D) * Math.PI;
        }
        return normalizeAngle(primary + offset);
    }

    private static int lobeExtraLength(long seed, int index) {
        long salt = index < 0 ? 0xA54FF53A5F1D36F1L : 0x510E527FADE682D1L + index * 0x100000001B3L;
        int minimum = index < 0 ? 700 : 350;
        int span = index < 0 ? 301 : 301;
        return minimum + (int) (unit(mixSeed(seed, salt)) * span);
    }

    private static int lobeHalfWidth(long seed, int index) {
        long salt = index < 0 ? 0x9B05688C2B3E6C1FL : 0x1F83D9ABFB41BD6BL + index * 0x100000001B3L;
        int minimum = index < 0 ? 220 : 150;
        int span = index < 0 ? 101 : 101;
        return minimum + (int) (unit(mixSeed(seed, salt)) * span);
    }

    private static long lobeNoiseSalt(int index) {
        return index < 0 ? 0xCBBB9D5DC1059ED8L : 0x629A292A367CD507L + index * 0x100000001B3L;
    }

    private static double forwardProjection(int x, int z, double angle) {
        double dx = x - CENTER_X;
        double dz = z - CENTER_Z;
        return dx * Math.cos(angle) + dz * Math.sin(angle);
    }

    private static double sideProjection(int x, int z, double angle) {
        double dx = x - CENTER_X;
        double dz = z - CENTER_Z;
        return -dx * Math.sin(angle) + dz * Math.cos(angle);
    }

    private static double normalizeAngle(double angle) {
        double normalized = angle % (Math.PI * 2.0D);
        return normalized < 0.0D ? normalized + Math.PI * 2.0D : normalized;
    }

    private static long mixSeed(long seed, long salt) {
        long value = seed + salt;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static double unit(long value) {
        return (value >>> 11) * 0x1.0p-53;
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
