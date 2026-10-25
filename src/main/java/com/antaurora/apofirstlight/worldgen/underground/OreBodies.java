package com.antaurora.apofirstlight.worldgen.underground;

import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.OreBody;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan.OreKind;

/**
 * U3b batch 1 (docs/worldgen/underground_u3_rocks_ores_design.md 6.3, 14): where an ore body's rock is ore and of what
 * grade. Pure (no registries), so the offline check runs it: GeologyPass lays the blocks from it.
 * <ul>
 *   <li>Bodies are the plan's ellipsoids (rx along the strike, rz across it, ry up), their edge wobbling 15 % on a 4 m
 *   lattice so no body is a clean egg; a vein keeps at least 0.8 m either side of its plane (else it falls between
 *   block centres).</li>
 *   <li>Grade: rich inside 45 % of the way out (wobbling 10 %), lean beyond; a vein's rich ore in shoots along its strike
 *   (a third of its length, out to 80 %).</li>
 *   <li>A coal seam body is the district's workable seam: the geology's seam rock inside its outline (and a few metres of
 *   its level) is rich coal; seams outside every body are lean.</li>
 *   <li>A vein's metal is its district's ({@link #veinMetal}): gold 40 %, copper 35 %, lead 25 % by the district's hash.</li>
 * </ul>
 */
public final class OreBodies {
    private OreBodies() {}

    /** The kinds batch 1 places (the rest wait for batch 2). */
    public static boolean placed(OreKind k) {
        return k == OreKind.COAL_SEAM || k == OreKind.BANDED_IRON || k == OreKind.HYDROTHERMAL_VEIN || k == OreKind.MVT_LEAD_ZINC
                || k == OreKind.BRECCIA_PIPE;
    }

    /** half-width a vein keeps either side of its plane, m */
    static final double VEIN_MIN = 0.8;
    /** the coal seam body's reach above and below its level, m */
    static final double SEAM_REACH = 3;

    /** How far round a body reaches horizontally, m (its index). */
    public static double reach(OreBody o) { return Math.max(Math.max(o.rx(), o.rz()), VEIN_MIN) * 1.15 + 1; }

    /** The y range a body can touch, {lo, hi} (inclusive, block centres). */
    public static int[] yRange(OreBody o) {
        double r = o.kind() == OreKind.COAL_SEAM ? o.ry() + SEAM_REACH : o.ry() * 1.15 + 1;
        return new int[]{(int) Math.floor(o.y() - r), (int) Math.ceil(o.y() + r)};
    }

    /** The body's local frame at a block centre: {along the strike, up, across}, each over its semi-axis. */
    private static void local(OreBody o, double x, double y, double z, double[] out) {
        double dx = x - o.x(), dz = z - o.z(), c = Math.cos(o.yaw()), s = Math.sin(o.yaw());
        double u = dx * c + dz * s, v = -dx * s + dz * c;
        double rz = o.kind() == OreKind.HYDROTHERMAL_VEIN ? Math.max(o.rz(), VEIN_MIN) : o.rz();
        out[0] = u / o.rx();
        out[1] = (y - o.y()) / o.ry();
        out[2] = v / rz;
    }

    /**
     * The grade at block (x, y, z) of a body other than a coal seam: -1 outside, 0 lean, 1 rich. Callers check the rock is
     * rock (not air, water or soil).
     */
    public static int grade(OreBody o, long seed, int x, int y, int z) {
        double[] l = new double[3];
        local(o, x + 0.5, y + 0.5, z + 0.5, l);
        double q = l[0] * l[0] + l[1] * l[1] + l[2] * l[2];
        if (q > 1.4) return -1;
        double d = Math.sqrt(q), n = noise(seed + o.id() * 31L, x / 4.0, y / 4.0, z / 4.0);
        if (d > 1 + 0.3 * (n - 0.5)) return -1;
        if (o.kind() == OreKind.HYDROTHERMAL_VEIN) {
            // ore shoots: a third of the strike, plunging a little
            double shoot = noise(seed + o.id() * 37L + 5, (l[0] * o.rx() + 0.4 * l[1] * o.ry()) / 18.0, 0, 0);
            return shoot > 0.62 && d < 0.8 ? 1 : 0;
        }
        return d < 0.45 + 0.2 * (noise(seed + o.id() * 41L + 9, x / 6.0, y / 6.0, z / 6.0) - 0.5) ? 1 : 0;
    }

    /** Whether column (x, z) is inside a coal seam body's outline (its seam there is rich). */
    public static boolean inSeam(OreBody o, long seed, int x, int z) {
        double[] l = new double[3];
        local(o, x + 0.5, o.y(), z + 0.5, l);
        double d = Math.sqrt(l[0] * l[0] + l[2] * l[2]);
        return d < 1 + 0.3 * (noise(seed + o.id() * 31L, x / 8.0, 0, z / 8.0) - 0.5);
    }

    /** A vein district's metal: 0 gold, 1 copper, 2 lead (QuartzVeinBlock.Metal's order). */
    public static int veinMetal(long seed, int district) {
        double r = hashD(seed, district, 4421);
        return r < 0.4 ? 0 : r < 0.75 ? 1 : 2;
    }

    // ------------------------------------------------------------------ noise

    private static double hashD(long seed, long a, long b) {
        long h = seed * 0x9E3779B97F4A7C15L + a * 0xC2B2AE3D27D4EB4FL + b * 0x165667B19E3779F9L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    private static double lat(long seed, int i, int j, int k) { return hashD(seed, i * 73856093L ^ j * 19349663L, k); }

    /** Value noise on the unit lattice, smoothstepped, 0..1. */
    static double noise(long seed, double x, double y, double z) {
        int i = (int) Math.floor(x), j = (int) Math.floor(y), k = (int) Math.floor(z);
        double fx = x - i, fy = y - j, fz = z - k;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy); fz = fz * fz * (3 - 2 * fz);
        double v = 0;
        for (int c = 0; c < 8; c++) {
            int a = c & 1, b = c >> 1 & 1, d = c >> 2 & 1;
            v += lat(seed, i + a, j + b, k + d) * (a == 1 ? fx : 1 - fx) * (b == 1 ? fy : 1 - fy) * (d == 1 ? fz : 1 - fz);
        }
        return v;
    }
}
