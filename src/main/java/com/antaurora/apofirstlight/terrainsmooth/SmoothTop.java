package com.antaurora.apofirstlight.terrainsmooth;

import com.antaurora.apofirstlight.ground.SmoothField;

/**
 * Terrain rework R2b (docs/worldgen/terrain_rework_v1_design.md 13.9): the drawn top of a sloped natural block, pure.
 * SmoothRules' geometry key gives the block's four corners (shared with every block at a corner point); SmoothField
 * joins them by a Catmull-Rom patch over the 4 x 4 corner points round the block, read here at 0.5 m: nine points, the
 * corners, the four edge midpoints and the centre, in SmoothRules#corners' fan layout (nw, ne, sw, se, centre, N, S, W,
 * E), so SmoothRules#surface, #topQuads, #boxes and #slope draw, collide and stand on it as on a dug hole's fan.
 * <ul>
 *   <li><b>Corner points</b> other than the block's own come from the four columns' natural surfaces only
 *   ({@link #point}: the same for every block whatever its level), so the two blocks sharing an edge read the same line
 *   of points along it.</li>
 *   <li><b>Edges</b> curve only where the neighbour across is a smooth top too, with the same two corners (its geometry
 *   key, {@link Keys}): both blocks then compute the same midpoint. Toward anything else (a step with its side band, a
 *   flat block, a fan, an edited column, a road's turf, rock) the edge stays straight, the midpoint on the chord, as
 *   every other model and the side bands expect.</li>
 *   <li>A fan (dig R1) stays a fan.</li>
 *   <li>The midpoints and the centre are rounded to 1/64 block: every consumer reads the same numbers (and the shapes
 *   cache by them).</li>
 * </ul>
 * Heights are blocks above the block's floor, between the block's lowest and highest corner (SmoothField keeps them
 * there): the side bands, the collision's reach and the neighbours' sides are as before.
 */
public final class SmoothTop {
    /** The midpoints' and the centre's step (blocks). */
    public static final int Q = 64;
    /** per side N, S, W, E: the neighbour's offset, this block's corners at the edge's ends, the neighbour's same corners */
    static final int[][] SIDE_OFF = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
    static final int[][] MINE = {{0, 1}, {2, 3}, {0, 2}, {1, 3}}, THEIRS = {{2, 3}, {0, 1}, {1, 3}, {0, 2}};
    /** the midpoint's index in the nine points, per side */
    static final int[] MID = {5, 6, 7, 8};

    private SmoothTop() {}

    /** A neighbour column's top: its geometry key at its natural surface (-1 none or flat) and that surface's y. */
    public interface Keys {
        long key(int x, int z);

        int floor(int x, int z);
    }

    /**
     * Absolute height of corner point (px, pz) from its four columns' natural surfaces (mode B as SmoothRules#corner for
     * natural columns: the mean, the middle when two apart, NaN when further apart or any column is not natural ground).
     */
    public static float point(SmoothRules.Cells c, int px, int pz) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, sum = 0;
        for (int dz = -1; dz <= 0; dz++)
            for (int dx = -1; dx <= 0; dx++) {
                int x = px + dx, z = pz + dz, s = c.surface(x, z);
                if (s == SmoothRules.NO_SURFACE || !SmoothRules.natural(c.cell(x, s, z))) return Float.NaN;
                lo = Math.min(lo, s + 1);
                hi = Math.max(hi, s + 1);
                sum += s + 1;
            }
        if (hi - lo > 2) return Float.NaN;
        return hi - lo == 2 ? lo + 1 : sum * 0.25F;
    }

    /** The 16 corner points from (x - 1, z - 1) to (x + 2, z + 2), absolute (#point), [j * 4 + i]. */
    public static float[] points(SmoothRules.Cells c, int x, int z) {
        float[] out = new float[16];
        for (int j = 0; j < 4; j++)
            for (int i = 0; i < 4; i++) out[j * 4 + i] = point(c, x + i - 1, z + j - 1);
        return out;
    }

    /** The four neighbours' keys and floors (N, S, W, E) as {@link Keys} answers them, into keys / floors. */
    public static void neighbours(Keys k, int x, int z, long[] keys, int[] floors) {
        for (int s = 0; s < 4; s++) {
            int nx = x + SIDE_OFF[s][0], nz = z + SIDE_OFF[s][1];
            keys[s] = k.key(nx, nz);
            floors[s] = keys[s] < 0 ? Integer.MIN_VALUE : k.floor(nx, nz);
        }
    }

    /**
     * The drawn top of block (x, y, z) with geometry key g (>= 0): a fan's nine points as SmoothRules#corners, else the
     * smooth top's nine.
     *
     * @param points #points (absolute)
     * @param keys   the N, S, W, E neighbours' keys (#neighbours), floors their floors
     */
    public static float[] top(long g, int y, float[] points, long[] keys, int[] floors) {
        float[] c = SmoothRules.corners(g);
        if (c.length == 9) return c;
        float[] ring = new float[16];
        for (int k = 0; k < 16; k++) ring[k] = points[k] - y;
        ring[5] = c[0];
        ring[6] = c[1];
        ring[9] = c[2];
        ring[10] = c[3];
        double[][] edges = new double[4][];
        for (int s = 0; s < 4; s++) {
            if (shared(c, y, s, keys[s], floors[s])) continue;
            float a = c[MINE[s][0]], b = c[MINE[s][1]];
            edges[s] = new double[SmoothField.SIDE];
            for (int t = 0; t < SmoothField.SIDE; t++) edges[s][t] = a + (b - a) * t / (double) SmoothField.PER_BLOCK;
        }
        double[] v = new double[SmoothField.SIDE * SmoothField.SIDE];
        SmoothField.vertices(ring, edges, null, v);
        float[] out = new float[9];
        System.arraycopy(c, 0, out, 0, 4);
        // the fixed edges' Coons patch can push the inside past the corners: the centre back between the block's lowest and
        // highest corner, each midpoint between its edge's (a curved one already is; the centre is this block's alone)
        double lo = Math.min(Math.min(c[0], c[1]), Math.min(c[2], c[3])), hi = Math.max(Math.max(c[0], c[1]), Math.max(c[2], c[3]));
        out[4] = q(Math.max(lo, Math.min(hi, v[12])));
        int[] at = {2, 22, 10, 14};
        for (int s = 0; s < 4; s++) {
            float a = c[MINE[s][0]], b = c[MINE[s][1]];
            out[MID[s]] = q(Math.max(Math.min(a, b), Math.min(Math.max(a, b), v[at[s]])));
        }
        return out;
    }

    /** Whether side s's edge curves: the neighbour is a smooth top (a key, no fan) with this block's two corners there. */
    static boolean shared(float[] c, int y, int s, long key, int floor) {
        if (key < 0 || (key & SmoothRules.FAN) != 0) return false;
        float[] n = SmoothRules.corners(key);
        for (int e = 0; e < 2; e++)
            if (Math.abs(y + c[MINE[s][e]] - (floor + n[THEIRS[s][e]])) > 1e-4F) return false;
        return true;
    }

    private static float q(double v) { return Math.round(v * Q) / (float) Q; }

    /**
     * Shading normals (unit {x, y, z}) at the 25 points 0.25 m apart of a top (out[(j * 5 + i) * 3 ..]): the Catmull-Rom
     * patch's slope over the ring (#top's), so neighbouring smooth tops shade alike along their edges; a fan takes its
     * drawn faces' slope.
     */
    public static void normals(long g, int y, float[] points, float[] top, float[] out) {
        boolean fan = (g & SmoothRules.CENTRE) != 0;
        float[] ring = new float[16];
        if (!fan) {
            float[] c = SmoothRules.corners(g);
            for (int k = 0; k < 16; k++) ring[k] = points[k] - y;
            ring[5] = c[0];
            ring[6] = c[1];
            ring[9] = c[2];
            ring[10] = c[3];
        }
        double[] d = new double[2];
        for (int j = 0; j < 5; j++)
            for (int i = 0; i < 5; i++) {
                double u = i / 4.0, v = j / 4.0;
                if (fan) {
                    double[] s = SmoothRules.slope(top, Math.min(u, 1 - 1e-3), Math.min(v, 1 - 1e-3));
                    d[0] = s[0];
                    d[1] = s[1];
                } else SmoothField.gradient(ring, u, v, d);
                double l = Math.sqrt(d[0] * d[0] + 1 + d[1] * d[1]);
                int o = (j * 5 + i) * 3;
                out[o] = (float) (-d[0] / l);
                out[o + 1] = (float) (1 / l);
                out[o + 2] = (float) (-d[1] / l);
            }
    }

    /** The shape caches' key of a nine-point top: corners in quarters (4 bits each), the other five in 1/64 (8 bits each). */
    public static long pack(float[] top) {
        long k = 0;
        for (int i = 0; i < 4; i++) k |= (long) Math.max(0, Math.min(15, Math.round(top[i] * 4))) << (4 * i);
        for (int i = 4; i < 9; i++) k |= (long) Math.max(0, Math.min(255, Math.round(top[i] * Q))) << (16 + 8 * (i - 4));
        return k;
    }
}
