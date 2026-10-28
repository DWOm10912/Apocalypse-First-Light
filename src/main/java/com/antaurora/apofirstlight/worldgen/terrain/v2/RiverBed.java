package com.antaurora.apofirstlight.worldgen.terrain.v2;

/**
 * River / lake V1 R1-R2 (docs/worldgen/river_lake_v1_design.md 4.2-4.5, 13): the channel of a river AFL's water runs in
 * (worlds with the {@code river_flow} gate), as US streams are measured. A pure function of the column (the river query
 * and the column's generated top), so two chunks agree on their border.
 * <ul>
 *   <li>Size: the channel's width is HydroQuery's (R2: 1.6 times the plan's, each bank wandering); the bankfull mean
 *   depth D = 0.40 A^0.22 (A in km2, 0.35-1.4 m) sets the depths below.</li>
 *   <li>Section (R2), by the query's pool share: a riffle {@link #riffleDepth} (0.3-0.5 m) deep, a pool
 *   {@link #poolDepth} (1.2-2 m), a run between them in between (0.6-1 m), a deep hole (HydroQuery: about one pool in
 *   three) 2.5-3.5 m. A flat bottom across the middle, its sides rising to {@link #EDGE} over the water at the edge, never
 *   steeper than 1 in 1 (a narrow channel's pool is only as deep as its sides allow). In a bend the deepest line moves
 *   toward the outer bank and the inner side runs out long and flat (the point bar).</li>
 *   <li>Banks past the edge, by the bank's character along the line (HydroQuery#Point.bank): a beach (1 in 4), an
 *   ordinary bank (35 % for 2 m, then 80 %), a low steep bank (up to 1 m at 1 in 1); a bend's outer bank a cut bank
 *   (1-1.5 m nearly straight up), its inner bank a beach. Up to the ground the noise made (never higher); never under the
 *   water surface + 0.1 m beside the water, so no water stands against air.</li>
 *   <li>Whole column tops (no dither, R2): under the water the nearest block, but never less than {@link #MIN_WATER}
 *   under it; the shallower margin and the dry ground rounded up, at least 0.1 m over the water; Terrain Smooth's corners
 *   draw the slope between them.</li>
 *   <li>Top material (4.5): mud in a pool's deep, slow water, sand on a point bar and in patches on the runs, round gravel
 *   on the rest of the bed; at the water's edge sand or gravel patches on a beach and on a bar, the land's own soil
 *   elsewhere (no bare strip). The patches are clustered ({@link #patch}, terrain rework 13.12): sand lies in drifts a
 *   few metres across, not as single columns (user 2026-10-28: the sand read as stuck-on squares).</li>
 * </ul>
 */
public final class RiverBed {
    /** How far past the water's edge the banks are graded (m). */
    public static final double REACH = RiverNetwork.BANK_REACH;
    /** BARE: no longer chosen (R2), kept for the block table. */
    public static final byte KEEP = 0, GRAVEL = 1, SAND = 2, MUD = 3, BARE = 4;
    /** The bed at the water's edge stands this far over the water (m). */
    public static final double EDGE = 0.2;
    /** Steepest a channel side may be (m a metre, before the ease). */
    public static final double MAX_SIDE = 1.0;
    /** A column holds at least this much water (m), or it is dry ground (at least 0.1 m over the water). */
    public static final double MIN_WATER = 0.15;

    private RiverBed() {
    }

    /** One column's channel. */
    public static final class Out {
        /** The ground's surface (m, continuous) and its top block. */
        public double surface;
        public int top;
        /** Depth of the bed under the water surface (m, negative above it). */
        public double depth;
        public byte material;
    }

    /** Bankfull mean depth (m) for a drainage area (m2). */
    public static double bankfullDepth(double area) {
        return Math.max(0.35, Math.min(1.4, 0.40 * Math.pow(Math.max(area, 1) / 1e6, 0.22)));
    }

    /** A riffle's normal water depth (m): 0.3-0.5. */
    public static double riffleDepth(double d) {
        return Math.max(0.3, Math.min(0.5, 0.25 + 0.2 * d));
    }

    /** A pool's normal water depth (m): 1.2-2. */
    public static double poolDepth(double d) {
        return Math.max(1.2, Math.min(2.0, 0.9 + 0.8 * d));
    }

    private static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /** The bank's rise (m over the edge's EDGE) e metres past the water's edge, by its character and the bend. */
    static double bankRise(HydroQuery.Point p, double e, boolean outerSide) {
        double beach = 0.25 * e;
        double ordinary = 0.35 * Math.min(e, 2) + 0.8 * Math.max(0, e - 2);
        double steep = Math.min(1.0, e) + 0.35 * Math.max(0, e - 1);
        double b = p.bank, wb = 1 - smooth((b - 0.2) / 0.2), ws = smooth((b - 0.65) / 0.2);
        double rise = beach * wb + steep * ws + ordinary * (1 - wb - ws);
        double bend = p.pool * p.bend;
        if (outerSide) {
            double cut = 1.0 + 0.5 * p.bank;
            double cutRise = Math.min(cut, cut * e / 0.6) + 0.35 * Math.max(0, e - 0.6);
            rise += (cutRise - rise) * smooth(bend / 0.6);
        } else rise += (beach - rise) * smooth(bend / 0.6);
        return rise;
    }

    /** Depth under the water surface (m, negative above) at the point (the river query's), bankfull depth d. */
    public static double depth(HydroQuery.Point p, double d) {
        // the outer and inner banks' half widths (the point's own side where the query gave only that)
        double hwO = Math.max(1, p.outer > 0 ? p.right : p.left), hwI = Math.max(1, p.outer > 0 ? p.left : p.right);
        if (p.left <= 0 && p.right <= 0) hwO = hwI = Math.max(1, p.halfWidth);
        double q = p.offset * p.outer;
        double riffle = riffleDepth(d), pool = Math.max(poolDepth(d), p.hole);
        double dc = riffle + (pool - riffle) * p.pool;
        double bend = p.pool * p.bend;
        // the deepest line toward the outer bank in a bend; the side's run from it to the water's edge
        double c = 0.35 * Math.min(hwO, hwI) * bend, x = q - c;
        boolean outerSide = x >= 0;
        double ls = outerSide ? hwO - c : hwI + c, ax = Math.abs(x);
        if (ax >= ls) return -EDGE - bankRise(p, ax - ls, outerSide);
        // eased sides: steepest 1.5 x rise / run, never over MAX_SIDE; the shorter side's whole run sets how deep the
        // channel may be, for both sides
        dc = Math.min(dc, MAX_SIDE * Math.min(hwO - c, hwI + c) / 1.5 - EDGE);
        // the flat bottom's share of the side: wide on a riffle, narrow in a pool, the point bar's side long and flat
        double f = 0.6 + ((outerSide ? 0.45 : 0.45 + (0.1 - 0.45) * smooth(bend / 0.6)) - 0.6) * p.pool;
        double rise = dc + EDGE, run = ls * (1 - f);
        if (1.5 * rise > MAX_SIDE * run) f = Math.max(0, 1 - 1.5 * rise / MAX_SIDE / ls);
        double t = (ax / ls - f) / Math.max(1e-9, 1 - f);
        return dc - rise * smooth(t);
    }

    /** The column (x, z) at the point p, its generated top block top, into out. */
    public static void plan(HydroQuery.Point p, int x, int z, int top, Out out) {
        double d = bankfullDepth(p.area), dep = depth(p, d), h = p.eta - dep;
        // dry ground follows the bank down to the noise's ground, never above it, never under the water beside it
        if (dep <= 0) h = Math.max(p.eta + 0.1, Math.min(h, top + 1));
        out.surface = h;
        out.depth = p.eta - h;
        int s;
        if (dep >= MIN_WATER) {
            s = (int) Math.round(h);
            if (p.eta - s < MIN_WATER) s--;
        } else s = (int) Math.ceil(Math.max(h, p.eta + 0.1) - 1e-6);
        out.top = s - 1;
        out.material = material(out.depth, p, d, x, z);
    }

    /** 0..1 from a column. */
    private static double hash(int x, int z, int salt) {
        long k = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
        k = (k ^ (k >>> 30)) * 0xBF58476D1CE4E5B9L;
        k = (k ^ (k >>> 27)) * 0x94D049BB133111EBL;
        return ((k ^ (k >>> 31)) >>> 11) * 0x1.0p-53;
    }

    static byte material(double dep, HydroQuery.Point p, double d, int x, int z) {
        boolean inner = p.offset * p.outer < 0;
        double bar = p.pool * p.bend;
        if (dep > 0) {
            if (p.pool > 0.6 && dep > 1.0) return MUD;                                  // a pool's deep, slow water
            if (bar > 0.3 && inner && dep < 0.9) return SAND;                           // the point bar's foot
            if (dep < 0.7 && p.pool > 0.3 && patch(x, z, 1) < 0.2) return SAND;         // sand patches on the runs
            return GRAVEL;
        }
        if (dep > -0.5 && bar > 0.3 && inner) return SAND;                              // the bar over the water
        if (dep > -0.4 && p.bank < 0.3) return patch(x, z, 2) < 0.5 ? SAND : GRAVEL;    // a beach
        if (dep > -0.3 && patch(x, z, 3) < 0.35) return GRAVEL;                         // gravel at the water's edge, in patches
        return KEEP;
    }

    /**
     * Terrain rework 13.12: 0..1, spread evenly (u < s holds on a share s of the columns, as a column hash would), but
     * clustered: value noise of wavelengths about 5 and 2 m in world coordinates, put through its own distribution
     * ({@link #CDF}), so a patch is a few metres of the same material with a ragged edge.
     */
    public static double patch(int x, int z, int salt) {
        double v = 0.65 * noise(x / 5.0, z / 5.0, salt * 2) + 0.35 * noise(x / 2.0, z / 2.0, salt * 2 + 1);
        int lo = 0, hi = CDF.length - 1;
        if (v <= CDF[lo]) return 0;
        if (v >= CDF[hi]) return 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (CDF[mid] <= v) lo = mid;
            else hi = mid;
        }
        return (lo + (v - CDF[lo]) / Math.max(1e-12, CDF[hi] - CDF[lo])) / (CDF.length - 1);
    }

    /** Smooth value noise 0..1 at (x, z) (cells a unit wide, corner values from #hash). */
    private static double noise(double x, double z, int salt) {
        int i = (int) Math.floor(x), j = (int) Math.floor(z);
        double fx = x - i, fz = z - j;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = hash(i, j, 100 + salt), b = hash(i + 1, j, 100 + salt), c = hash(i, j + 1, 100 + salt), e = hash(i + 1, j + 1, 100 + salt);
        return (a + (b - a) * fx) * (1 - fz) + (c + (e - c) * fx) * fz;
    }

    /** #patch's noise sorted over a fixed spread of 65536 columns (the same on every machine): its distribution. */
    private static final double[] CDF;

    static {
        int n = 1 << 16, k = 0;
        double[] v = new double[n];
        for (int z = 0; z < 256; z++)
            for (int x = 0; x < 256; x++) {
                int wx = x * 7 + z * 3, wz = z * 7 - x * 2;
                v[k++] = 0.65 * noise(wx / 5.0, wz / 5.0, 0) + 0.35 * noise(wx / 2.0, wz / 2.0, 1);
            }
        java.util.Arrays.sort(v);
        CDF = new double[257];
        for (int q = 0; q <= 256; q++) CDF[q] = v[Math.min(n - 1, q * (n / 256))];
    }
}
