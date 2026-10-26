package com.antaurora.apofirstlight.worldgen.river;

import com.antaurora.apofirstlight.terrainsmooth.SmoothRules;

/**
 * River / lake V1 R0 (docs/worldgen/river_lake_v1_design.md 7, 4.2-4.3): the technical sample, pure, so the dev command
 * (RiverDevCommand) and the offline check (WetGroundCheck) build the same thing. A field of column tops relative to the
 * water level L (column top T: ground up to L + T - 1, water from L + T to L where T <= 0) with each column's top
 * material, on a plain 2 blocks above L (its top 1.11 m above the water surface L + 8/9):
 * <ul>
 *   <li>a river reach, W 8 m (about 10 km2 of drainage), one meander wavelength 11 W along z, apex radius about 2.5 W:
 *   riffles at the crossings (symmetric trapezoid, 0.3 m deep, banks 35 %), pools at the apexes (thalweg 1.6 m deep near
 *   the outer bank, a vertical cut bank, the point bar rising across the channel at 25 % and on at 10 % to the plain),
 *   blended by |sin| of the meander phase;</li>
 *   <li>a pond east of it, R 10 m, 1.4 m deep, its west shore a beach (10 %), its east shore steep (40 %).</li>
 * </ul>
 * The continuous target height is dithered (2 x 2 Bayer) into integer tops, so Terrain Smooth's corner means (mode B,
 * four columns) follow it to a quarter of a block.
 * <p>
 * Wet ground (4.9): every bed under water is wet, and a column at the water level (T = 1) is wet where its sloped top
 * dips below the water surface ({@link #below}); nothing else. Such a column only ever has ground or water beside it at
 * its level (every column with T <= 0 is filled to L), so no wet cell spills water onto dry land.
 */
public final class RiverSample {
    public static final int SIZE_X = 64, SIZE_Z = 96;
    /** the plain's column top above L */
    public static final int PLAIN = 2;
    /** the water surface above L: a vanilla source's height */
    public static final double SURFACE = 8.0 / 9.0;
    public static final byte GRASS = 0, BARE = 1, GRAVEL = 2, SAND = 3, MUD = 4;
    public static final String[] MATERIAL = {"topsoil grass", "topsoil bare", "round gravel", "sand", "mud"};

    static final double W = 8, LAMBDA = 11 * W, AMPLITUDE = 9, CENTRE_X = 18;
    static final double POND_X = 50, POND_Z = 48, POND_R = 10, POND_DEPTH = 1.4;
    private static final double[] BAYER = {0.125, 0.625, 0.875, 0.375};

    /** column tops above L and top materials, [z * SIZE_X + x] */
    public final int[] top = new int[SIZE_X * SIZE_Z];
    public final byte[] material = new byte[SIZE_X * SIZE_Z];
    /** the continuous target: depth below the water surface (m, negative above it) */
    public final double[] depth = new double[SIZE_X * SIZE_Z];
    /** 0 riffle .. 1 pool, for the report; NaN off the river */
    public final double[] pool = new double[SIZE_X * SIZE_Z];
    /** the column's top cell holds water (NaturalGroundBlock#WATERLOGGED) */
    public final boolean[] wet = new boolean[SIZE_X * SIZE_Z];

    private RiverSample() {}

    public static RiverSample make() {
        RiverSample s = new RiverSample();
        for (int z = 0; z < SIZE_Z; z++)
            for (int x = 0; x < SIZE_X; x++) {
                int i = z * SIZE_X + x;
                double[] r = river(x + 0.5, z + 0.5), p = pond(x + 0.5, z + 0.5);
                double plain = PLAIN - SURFACE;
                double d = Math.max(-plain, Math.max(r[0], p[0]));
                // the outermost two columns stay plain: the sample is a closed basin
                if (x < 2 || z < 2 || x >= SIZE_X - 2 || z >= SIZE_Z - 2) d = -plain;
                s.depth[i] = d;
                s.pool[i] = r[0] >= p[0] ? r[1] : Double.NaN;
                double h = SURFACE - d;
                int t = (int) Math.floor(h);
                if (h - t > BAYER[(z & 1) * 2 + (x & 1)]) t++;
                s.top[i] = Math.max(-2, Math.min(PLAIN, t));
                s.material[i] = r[0] >= p[0] ? riverMaterial(d, r[1], r[2]) : pondMaterial(d, p[1]);
            }
        // the dither can put two levels between neighbours on a slope (a pinned corner, a bared side): where their targets
        // lie less than 1.5 m apart (anything but the cut bank), the higher one comes down a level
        for (int pass = 0; pass < 3; pass++)
            for (int z = 2; z < SIZE_Z - 2; z++)
                for (int x = 2; x < SIZE_X - 2; x++) {
                    int i = z * SIZE_X + x;
                    for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int j = (z + d[1]) * SIZE_X + x + d[0];
                        if (s.top[i] - s.top[j] >= 2 && s.depth[j] - s.depth[i] < 1.5) { s.top[i]--; break; }
                    }
                }
        for (int i = 0; i < s.top.length; i++) s.wet[i] = s.top[i] <= 0;
        Cells c = s.cells();
        boolean[] shore = new boolean[s.wet.length];
        for (int z = 0; z < SIZE_Z; z++)
            for (int x = 0; x < SIZE_X; x++)
                if (s.top[z * SIZE_X + x] == 1) shore[z * SIZE_X + x] = below(c, x, 0, z, SURFACE);
        for (int i = 0; i < shore.length; i++) s.wet[i] |= shore[i];
        return s;
    }

    /**
     * Whether natural surface cell (x, y, z) needs water in it: its drawn top (Terrain Smooth, mode B) dips below the
     * absolute water surface waterTop somewhere (the top is two flat triangles, so at a corner).
     */
    public static boolean below(SmoothRules.Cells c, int x, int y, int z, double waterTop) {
        long g = SmoothRules.geometry(c, SmoothRules.MODE_B, x, y, z);
        if (g < 0) return y + 1 < waterTop;
        for (float k : SmoothRules.corners(g)) if (y + k < waterTop - 1e-6) return true;
        return false;
    }

    /** The sample as Terrain Smooth's cells, L = 0 (outside it: the plain). */
    public Cells cells() { return new Cells(this); }

    public static final class Cells implements SmoothRules.Cells {
        final RiverSample s;

        Cells(RiverSample s) { this.s = s; }

        public int top(int x, int z) {
            return x < 0 || z < 0 || x >= SIZE_X || z >= SIZE_Z ? PLAIN : s.top[z * SIZE_X + x];
        }

        boolean wet(int x, int z) {
            return x >= 0 && z >= 0 && x < SIZE_X && z < SIZE_Z && s.wet[z * SIZE_X + x];
        }

        @Override
        public int cell(int x, int y, int z) {
            int t = top(x, z);
            if (y < t - 1) return SmoothRules.NATURAL;
            if (y == t - 1) return wet(x, z) ? SmoothRules.WET : SmoothRules.NATURAL;
            return y <= 0 ? SmoothRules.FLUID : SmoothRules.AIR;
        }

        @Override
        public int surface(int x, int z) {
            return SmoothRules.surface(this, x, z, PLAIN + 2, -8);
        }
    }

    /** {depth below the surface, pool 0..1, side: + outer bank, - inner} of the river reach at (x, z). */
    static double[] river(double x, double z) {
        double phi = 2 * Math.PI * z / LAMBDA, sin = Math.sin(phi);
        double xc = CENTRE_X + AMPLITUDE * sin, slope = AMPLITUDE * 2 * Math.PI / LAMBDA * Math.cos(phi);
        double n = (x - xc) / Math.sqrt(1 + slope * slope);     // signed distance across the channel, m
        double p = Math.pow(Math.abs(sin), 1.5), q = n * Math.signum(sin);   // q > 0 toward the outer bank
        double riffle = riffle(Math.abs(n)), poolDepth = poolSection(q);
        return new double[]{riffle + (poolDepth - riffle) * p, p, q};
    }

    /** Riffle: 0.3 m deep to 2.4 m out, banks up to 0.25 m above the water at the channel edge (4 m), then 35 % to the plain. */
    static double riffle(double r) {
        if (r <= 2.4) return 0.3;
        if (r <= 4) return 0.3 - 0.55 * (r - 2.4) / 1.6;
        return -0.25 - 0.35 * (r - 4);
    }

    /** Pool: thalweg 1.6 m at q 2.4..3.6, a vertical cut bank beyond; the point bar 25 % up to 0.4 m above the water, then 10 %. */
    static double poolSection(double q) {
        if (q >= 3.6) return -9;
        if (q >= 2.4) return 1.6;
        double d = 1.6 - 0.25 * (2.4 - q);
        return d >= -0.4 ? d : -0.4 - 0.10 * (-5.6 - q);
    }

    /** {depth below the surface, shore steepness 0 beach .. 1 steep} of the pond at (x, z). */
    static double[] pond(double x, double z) {
        double dx = x - POND_X, dz = z - POND_Z, r = Math.hypot(dx, dz);
        double steep = r < 1e-9 ? 0.5 : (1 + dx / r) / 2;        // east steep, west a beach
        if (r <= POND_R) return new double[]{POND_DEPTH * (1 - (r / POND_R) * (r / POND_R)), steep};
        return new double[]{-(0.10 + 0.30 * steep) * (r - POND_R), steep};
    }

    static byte riverMaterial(double d, double pool, double side) {
        if (d > 1.0) return MUD;                                  // the pool's deep water
        if (d > 0) return pool > 0.4 && side < 0 ? SAND : GRAVEL; // the point bar's foot, else gravel
        if (d > -0.5 && pool > 0.3 && side < 0) return SAND;      // the bar above the water
        if (d > -0.35) return BARE;                               // the wet margin
        if (pool > 0.5 && side > 3.0) return BARE;                // the cut bank's face
        return GRASS;
    }

    static byte pondMaterial(double d, double steep) {
        if (d > 0.8) return MUD;
        if (d > 0) return steep < 0.5 ? SAND : GRAVEL;
        if (d > -0.5 && steep < 0.4) return SAND;                 // the beach
        if (d > -0.3) return BARE;
        return GRASS;
    }
}
