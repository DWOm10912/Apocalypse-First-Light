package com.antaurora.apofirstlight.ground;

import java.util.Arrays;

/**
 * Terrain rework R2 (docs/worldgen/terrain_rework_v1_design.md 13.3): where one loose ground meets another, a smooth,
 * irregular boundary instead of the block's edge. Pure.
 * <ul>
 *   <li>Each 0.25 m vertex gives every material its share of the four block centres round it (bilinear; columns that
 *   are not loose ground take no part), plus world noise of its own ({@link #NOISE}: three octaves, about 0.2-1.2 m);
 *   at a block's centre its own material gets {@link #CENTRE} more, so every block shows its own there. A vertex's
 *   weights depend on its world position and those four columns only: the blocks sharing it agree.</li>
 *   <li>Each 0.25 m cell's two triangles (nw-sw-se, nw-se-ne, as SmoothField#at) are cut where the leading material
 *   changes: the part where material m leads is the triangle clipped by every w_m >= w_k, a convex piece, and the pieces
 *   tile the triangle. A triangle led by one material at all three corners is that material whole.</li>
 * </ul>
 * The block stays what it is (drops, physics): a drawn boundary lies within about half a block of the block's edge.
 */
public final class GroundBlend {
    /** The noise's reach in weight: the boundary moves at most about NOISE / 2 x 2 = 0.45 m off the block's edge. */
    public static final double NOISE = 0.45;
    /** A block's own material's extra weight at its centre vertex. */
    public static final double CENTRE = 1;
    /** Not loose ground (rock, a road, a building, air): no share. */
    public static final int NONE = -1;
    /** Materials a block can see (its 3 x 3 columns). */
    public static final int MAX = 9;
    /** The weight of a material none of a vertex's four centres has. */
    static final double ABSENT = -10;
    private static final int SIDE = SmoothField.SIDE, PER = SmoothField.PER_BLOCK;

    private GroundBlend() {}

    /** A piece of a top: its material (an index into #materials' ids) and its points {fx, height, fz}, counter-clockwise from above. */
    public interface Pieces {
        void piece(int material, double[][] points, int count);
    }

    /** The distinct materials (>= 0) of the 3 x 3 columns mats[j * 3 + i] into ids; their count. The block's own first. */
    public static int materials(int[] mats, int[] ids) {
        int n = 0;
        ids[n++] = mats[4];
        for (int k = 0; k < 9; k++) {
            int m = mats[k];
            if (m < 0) continue;
            boolean seen = false;
            for (int i = 0; i < n; i++) if (ids[i] == m) seen = true;
            if (!seen) ids[n++] = m;
        }
        return n;
    }

    /**
     * A column whose surface lies this far or more above or below the block's own takes no part in its top (R2b): there the
     * corners are pinned and the tops part (SmoothRules#corner), so do the materials. Where tops join at a vertex its four
     * columns are at most two apart, so every block there weighs it alike.
     */
    public static final int STEP = 3;

    /**
     * The n materials' weights at block (x, z)'s 25 vertices: w[k * 25 + j * 5 + i].
     *
     * @param mats the 3 x 3 columns' materials round the block (mats[4] its own, >= 0), NONE where not loose ground
     * @param surf the 3 x 3 columns' natural surface heights (surf[4] the block's own): a column {@link #STEP} or more off
     *             the block's own is left out
     */
    public static void weights(int x, int z, int[] mats, int[] surf, int[] ids, int n, double[] w) {
        double[] acc = new double[n];
        for (int j = 0; j < SIDE; j++)
            for (int i = 0; i < SIDE; i++) {
                double px = x + i / (double) PER, pz = z + j / (double) PER;
                double ax = px - 0.5, az = pz - 0.5;
                int cx = (int) Math.floor(ax), cz = (int) Math.floor(az);
                double fx = ax - cx, fz = az - cz, total = 0;
                Arrays.fill(acc, 0);
                for (int dz = 0; dz < 2; dz++)
                    for (int dx = 0; dx < 2; dx++) {
                        int ci = cx + dx - x + 1, cj = cz + dz - z + 1;
                        if (ci < 0 || ci > 2 || cj < 0 || cj > 2) continue;
                        int m = mats[cj * 3 + ci];
                        if (m < 0 || Math.abs(surf[cj * 3 + ci] - surf[4]) >= STEP) continue;
                        double k = (dx == 0 ? 1 - fx : fx) * (dz == 0 ? 1 - fz : fz);
                        if (k <= 0) continue;
                        total += k;
                        for (int t = 0; t < n; t++) if (ids[t] == m) acc[t] += k;
                    }
                for (int t = 0; t < n; t++) {
                    // a material none of the four centres has is out at this vertex: the neighbour sharing it cannot see
                    // that material at all, and both must pick the same leader
                    double v = acc[t] <= 0 ? ABSENT : acc[t] / total + NOISE * fbm(px, pz, ids[t]);
                    if (i == PER / 2 && j == PER / 2 && t == 0) v += CENTRE;
                    w[t * SIDE * SIDE + j * SIDE + i] = v;
                }
            }
    }

    /** The leading material at each vertex (index into ids): lead[j * 5 + i]. */
    public static void leaders(double[] w, int n, int[] lead) {
        for (int v = 0; v < SIDE * SIDE; v++) {
            int best = 0;
            for (int t = 1; t < n; t++) if (w[t * SIDE * SIDE + v] > w[best * SIDE * SIDE + v]) best = t;
            lead[v] = best;
        }
    }

    /**
     * The top's pieces: per 0.25 m cell its two triangles, whole when one material leads at their three corners, else cut
     * into the parts each material leads. h: the 25 vertex heights.
     */
    public static void pieces(double[] h, double[] w, int n, Pieces out) {
        int[] lead = new int[SIDE * SIDE];
        leaders(w, n, lead);
        for (int j = 0; j < PER; j++)
            for (int i = 0; i < PER; i++) cell(h, w, n, lead, i, j, out);
    }

    /**
     * Whether cell (i, j) splits on its nw-se diagonal (else sw-ne): along the diagonals from the block's centre to its
     * corners, as the fan quarters of a nine-point top (SmoothRules#topQuads) are split, so cells whose heights lie on such
     * a top (SmoothRules#surface at the vertices) are exactly on it (R2b).
     */
    public static boolean nwSe(int i, int j) {
        return (i >= PER / 2) == (j >= PER / 2);
    }

    /** Cell (i, j)'s pieces (#pieces): its two triangles. */
    public static void cell(double[] h, double[] w, int n, int[] lead, int i, int j, Pieces out) {
        int nw = j * SIDE + i, ne = nw + 1, sw = nw + SIDE, se = sw + 1;
        if (nwSe(i, j)) {
            triangle(h, w, n, lead, new int[]{nw, sw, se}, out);
            triangle(h, w, n, lead, new int[]{nw, se, ne}, out);
        } else {
            triangle(h, w, n, lead, new int[]{nw, sw, ne}, out);
            triangle(h, w, n, lead, new int[]{sw, se, ne}, out);
        }
    }

    private static void triangle(double[] h, double[] w, int n, int[] lead, int[] tri, Pieces out) {
        double[][] t = new double[3][];
        for (int k = 0; k < 3; k++) t[k] = point(h, w, n, tri[k]);
        if (lead[tri[0]] == lead[tri[1]] && lead[tri[1]] == lead[tri[2]]) {
            out.piece(lead[tri[0]], copy(t, 3), 3);
            return;
        }
        for (int m = 0; m < n; m++) {
            double[][] cur = t;
            int c = 3;
            // a triangle clipped by up to MAX - 1 half planes has at most MAX + 2 points
            for (int k = 0; k < n && c > 0; k++) {
                if (k == m) continue;
                double[][] next = new double[MAX + 3][];
                c = clip(cur, c, m, k, next);
                cur = next;
            }
            if (c >= 3 && area(cur, c) > 1e-12) out.piece(m, copy(cur, c), c);
        }
    }

    /** A vertex as {fx, height, fz, weights...}. */
    private static double[] point(double[] h, double[] w, int n, int v) {
        double[] p = new double[3 + n];
        p[0] = (v % SIDE) / (double) PER;
        p[1] = h[v];
        p[2] = (v / SIDE) / (double) PER;
        for (int t = 0; t < n; t++) p[3 + t] = w[t * SIDE * SIDE + v];
        return p;
    }

    /** The part of polygon p (count points) where weight m >= weight k, into out; its count. Attributes linear along edges. */
    private static int clip(double[][] p, int count, int m, int k, double[][] out) {
        int c = 0;
        for (int i = 0; i < count; i++) {
            double[] s = p[i], e = p[(i + 1) % count];
            double ds = s[3 + m] - s[3 + k], de = e[3 + m] - e[3 + k];
            boolean si = ds >= 0, ei = de >= 0;
            if (si) out[c++] = s;
            if (si != ei) {
                double t = ds / (ds - de);
                double[] q = new double[s.length];
                for (int a = 0; a < s.length; a++) q[a] = s[a] + (e[a] - s[a]) * t;
                out[c++] = q;
            }
        }
        return c;
    }

    /** Area (blocks^2) of polygon p in the x-z plane. */
    static double area(double[][] p, int count) {
        double s = 0;
        for (int i = 0; i < count; i++) {
            double[] a = p[i], b = p[(i + 1) % count];
            s += a[0] * b[2] - b[0] * a[2];
        }
        return Math.abs(s) / 2;
    }

    private static double[][] copy(double[][] p, int count) {
        double[][] out = new double[count][];
        for (int i = 0; i < count; i++) out[i] = new double[]{p[i][0], p[i][1], p[i][2]};
        return out;
    }

    // ---- noise: world coordinates only, integer hashes (the same on every machine, every chunk build)

    static int hash(int a, int b, int salt) {
        int h = a * 0x27d4eb2d ^ b * 0x165667b1 ^ salt * 0x5bd1e995;
        h ^= h >>> 15;
        h *= 0x2c1b3c6d;
        h ^= h >>> 12;
        return h;
    }

    private static double unit(int h) {
        return (h >>> 8) / (double) (1 << 24);
    }

    private static double noise(double x, double z, int salt) {
        int i = (int) Math.floor(x), j = (int) Math.floor(z);
        double fx = x - i, fz = z - j;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = unit(hash(i, j, salt)), b = unit(hash(i + 1, j, salt)), c = unit(hash(i, j + 1, salt)), d = unit(hash(i + 1, j + 1, salt));
        return (a + (b - a) * fx) * (1 - fz) + (c + (d - c) * fx) * fz;
    }

    /** -1..1: three octaves (wavelengths about 1.25, 0.5 and 0.2 m), the fine ones strong enough for a ragged edge. */
    public static double fbm(double x, double z, int material) {
        int salt = 100 + material * 10;
        return (noise(x * 0.8, z * 0.8, salt) * 0.45 + noise(x * 2.1, z * 2.1, salt + 1) * 0.33 + noise(x * 5.3, z * 5.3, salt + 2) * 0.22) * 2 - 1;
    }
}
