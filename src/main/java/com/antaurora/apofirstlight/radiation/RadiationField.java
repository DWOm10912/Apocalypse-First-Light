package com.antaurora.apofirstlight.radiation;

/** Stateless, seed-based 2D value-noise sampler. */
public final class RadiationField {
    private static final double SCALE = 768.0;
    private static final double DETAIL_SCALE = 192.0;
    private final long seed;

    public RadiationField(long seed) {
        this.seed = seed;
    }

    public double sample(int x, int z) {
        double broad = valueNoise(x / SCALE, z / SCALE, 0x6A09E667F3BCC909L);
        double detail = valueNoise(x / DETAIL_SCALE, z / DETAIL_SCALE, 0xBB67AE8584CAA73BL);
        return clamp01(broad * 0.82 + detail * 0.18);
    }

    private double valueNoise(double x, double z, long salt) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double tx = smooth(x - x0);
        double tz = smooth(z - z0);
        double a = lattice(x0, z0, salt);
        double b = lattice(x0 + 1, z0, salt);
        double c = lattice(x0, z0 + 1, salt);
        double d = lattice(x0 + 1, z0 + 1, salt);
        return lerp(lerp(a, b, tx), lerp(c, d, tx), tz);
    }

    private double lattice(long x, long z, long salt) {
        long h = seed ^ salt ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53;
    }

    private static double smooth(double t) { return t * t * (3.0 - 2.0 * t); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    private static double clamp01(double value) { return Math.max(0.0, Math.min(1.0, value)); }
}
