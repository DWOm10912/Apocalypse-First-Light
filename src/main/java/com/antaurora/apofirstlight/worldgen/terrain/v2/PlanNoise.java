package com.antaurora.apofirstlight.worldgen.terrain.v2;

/**
 * The Terrain V2 planner's deterministic noise (2026-10-10, docs/worldgen/terrain_v2_real_terrain_research_v1.md):
 * splitmix hashing, 2D gradient noise with a quintic fade, and normalised fBm. Shared by the planner (TerrainPlanV2)
 * and the runtime surface (TerrainPlanSurface) so the in-game height is the planned height, bit for bit.
 */
public final class PlanNoise {
    private PlanNoise() {
    }

    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    public static double hash01(long noiseSeed, long k) {
        return (mix(noiseSeed ^ (k * 0x9E3779B97F4A7C15L)) >>> 11) * 0x1.0p-53;
    }

    /** Gradient noise, about -0.7..0.7, unit wavelength. */
    public static double noise(long noiseSeed, double x, double z, int salt) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        double fx = x - x0, fz = z - z0;
        double u = fx * fx * fx * (fx * (fx * 6 - 15) + 10), w = fz * fz * fz * (fz * (fz * 6 - 15) + 10);
        double a = grad(noiseSeed, x0, z0, fx, fz, salt), b = grad(noiseSeed, x0 + 1, z0, fx - 1, fz, salt);
        double c = grad(noiseSeed, x0, z0 + 1, fx, fz - 1, salt), d = grad(noiseSeed, x0 + 1, z0 + 1, fx - 1, fz - 1, salt);
        return (a + (b - a) * u) + ((c + (d - c) * u) - (a + (b - a) * u)) * w;
    }

    private static double grad(long noiseSeed, int xi, int zi, double dx, double dz, int salt) {
        long hsh = mix(noiseSeed ^ (xi * 0x632BE59BD9B4E019L) ^ (zi * 0x9E3779B97F4A7C15L) ^ ((long) salt << 40));
        double ang = (hsh >>> 11) * 0x1.0p-53 * Math.PI * 2;
        return dx * Math.cos(ang) + dz * Math.sin(ang);
    }

    /** fBm normalised to about unit standard deviation; wavelength of the first octave in blocks. */
    public static double fbm(long noiseSeed, double x, double z, double wavelength, int octaves, double gain, int salt) {
        double s = 0, amp = 1, norm = 0, f = 1 / wavelength;
        for (int o = 0; o < octaves; o++) {
            s += amp * noise(noiseSeed, x * f + o * 17.3, z * f - o * 9.1, salt * 7 + o);
            norm += amp * amp;
            amp *= gain;
            f *= 2.03;
        }
        return s / Math.sqrt(norm) / 0.27;
    }
}
