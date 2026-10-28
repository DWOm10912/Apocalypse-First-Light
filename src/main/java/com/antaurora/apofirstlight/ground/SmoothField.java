package com.antaurora.apofirstlight.ground;

/**
 * Terrain rework R2 (docs/worldgen/terrain_rework_v1_design.md 13): the smooth top of an untouched loose-ground block on
 * the 0.25 m vertices, pure. The block's own four corners (SmoothRules: the heights every block at a corner point agrees
 * on) are joined by a Catmull-Rom patch over the 4 x 4 corner points round it, so a slope is one curved surface instead
 * of a fold of flat triangles per block:
 * <ul>
 *   <li><b>Missing corners</b> (pinned, not loose ground, not loaded: NaN) are extrapolated straight on along their row
 *   or column, so a surface meets a step, a rock or a road with the slope it has, never bending toward a value it
 *   does not share.</li>
 *   <li><b>Edges</b> depend only on the corner points on their own line and each edge vertex lies between the edge's two
 *   end corners: the two blocks sharing an edge compute it alike, with no crack and no bulge past either corner.</li>
 *   <li><b>Inside</b> the vertices lie between the block's lowest and highest corner (no crest rising above the
 *   corners that the collision and the neighbours' sides would not know of).</li>
 *   <li><b>Fixed neighbours</b> (an edited column, a road's drawn top: their own 0.25 m vertices) win the edge they
 *   share, and every corner point they touch, diagonal ones too; the block bends to meet them by a Coons patch of the
 *   differences, the free edges between moved corners taking a straight share, so free neighbours stay joined.</li>
 * </ul>
 * Heights are blocks above the block's floor.
 */
public final class SmoothField {
    /** Vertices along a side (0.25 m apart). */
    public static final int SIDE = 5, PER_BLOCK = SIDE - 1;
    /** Corner points along a side of the input grid: x - 1 .. x + 2 (and z). */
    public static final int RING = 4;
    public static final int NORTH = 0, SOUTH = 1, WEST = 2, EAST = 3;

    private SmoothField() {}

    /**
     * The block's 25 vertex heights, out[j * SIDE + i] (i along x from the west edge, j along z from the north).
     *
     * @param ring    the 16 corner points from (x - 1, z - 1) to (x + 2, z + 2), ring[j * RING + i], above this block's
     *                floor; the middle four are the block's own corners and must be given; others NaN when missing
     * @param edges   per edge (NORTH, SOUTH, WEST, EAST) the fixed neighbour's 5 vertex heights along it, west to east /
     *                north to south, above this block's floor; null (or the edge's array null) when the neighbour is free
     * @param corners per own corner (nw, ne, sw, se) the height a fixed block touching that point (an adjacent or a
     *                diagonal one) has there, NaN when none does; null when none
     */
    public static void vertices(float[] ring, double[][] edges, double[] corners, double[] out) {
        double[] g = new double[RING * RING];
        for (int k = 0; k < g.length; k++) g[k] = ring[k];
        fill(g);
        double lo = Math.min(Math.min(g[5], g[6]), Math.min(g[9], g[10])), hi = Math.max(Math.max(g[5], g[6]), Math.max(g[9], g[10]));
        for (int j = 0; j < SIDE; j++) {
            double v = j / (double) PER_BLOCK;
            for (int i = 0; i < SIDE; i++) {
                double u = i / (double) PER_BLOCK;
                double[] col = new double[RING];
                for (int r = 0; r < RING; r++) col[r] = cr(g[r * RING], g[r * RING + 1], g[r * RING + 2], g[r * RING + 3], u);
                double h = cr(col[0], col[1], col[2], col[3], v);
                // edges: between the edge's own end corners (and exactly them at the corners); inside: the block's range
                if (j == 0) h = between(h, g[5], g[6]);
                else if (j == PER_BLOCK) h = between(h, g[9], g[10]);
                else if (i == 0) h = between(h, g[5], g[9]);
                else if (i == PER_BLOCK) h = between(h, g[6], g[10]);
                else h = Math.max(lo, Math.min(hi, h));
                out[j * SIDE + i] = h;
            }
        }
        out[0] = g[5];
        out[PER_BLOCK] = g[6];
        out[PER_BLOCK * SIDE] = g[9];
        out[PER_BLOCK * SIDE + PER_BLOCK] = g[10];
        fix(edges, corners, out);
    }

    /**
     * NaN corner points extrapolated straight on. Each edge's line (rows 1, 2 and columns 1, 2) is filled from its own two
     * middle points only, so the two blocks sharing that line fill it alike whatever else their windows hold; the four
     * outer corners (inside only) then from their row.
     */
    static void fill(double[] g) {
        end(g, 4, 5, 6);    // row 1 (the north edge's line): its west end
        end(g, 7, 6, 5);    //   and its east end
        end(g, 8, 9, 10);   // row 2 (the south edge's line)
        end(g, 11, 10, 9);
        end(g, 1, 5, 9);    // column 1 (the west edge's line): its north end
        end(g, 13, 9, 5);   //   and its south end
        end(g, 2, 6, 10);   // column 2 (the east edge's line)
        end(g, 14, 10, 6);
        end(g, 0, 1, 2);    // the outer corners, along rows 0 and 3
        end(g, 3, 2, 1);
        end(g, 12, 13, 14);
        end(g, 15, 14, 13);
    }

    /**
     * The slope {d height / dx, d height / dz} of the Catmull-Rom patch over the ring at (u, v) in 0..1 of the block, the
     * missing points filled as #vertices does: the smooth tops' vertex normals (shading). Catmull-Rom is C1 at the corners,
     * so two blocks sharing an edge (and its line of corner points) give the same slope there.
     */
    public static void gradient(float[] ring, double u, double v, double[] out) {
        double[] g = new double[RING * RING];
        for (int k = 0; k < g.length; k++) g[k] = ring[k];
        fill(g);
        double[] col = new double[RING], dcol = new double[RING];
        for (int r = 0; r < RING; r++) {
            col[r] = cr(g[r * RING], g[r * RING + 1], g[r * RING + 2], g[r * RING + 3], u);
            dcol[r] = dcr(g[r * RING], g[r * RING + 1], g[r * RING + 2], g[r * RING + 3], u);
        }
        out[0] = cr(dcol[0], dcol[1], dcol[2], dcol[3], v);
        out[1] = dcr(col[0], col[1], col[2], col[3], v);
    }

    /** d/dt of #cr. */
    static double dcr(double p0, double p1, double p2, double p3, double t) {
        return 0.5 * ((-p0 + p2) + 2 * (2 * p0 - 5 * p1 + 4 * p2 - p3) * t + 3 * (-p0 + 3 * p1 - 3 * p2 + p3) * t * t);
    }

    /** Point k, when NaN, from the next point b and the one after it c on its line: 2b - c. */
    private static void end(double[] g, int k, int b, int c) {
        if (Double.isNaN(g[k])) g[k] = 2 * g[b] - g[c];
    }

    /** Catmull-Rom between p1 (t 0) and p2 (t 1). */
    static double cr(double p0, double p1, double p2, double p3, double t) {
        return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (-p0 + 3 * p1 - 3 * p2 + p3) * t * t * t);
    }

    private static double between(double h, double a, double b) {
        return Math.max(Math.min(a, b), Math.min(Math.max(a, b), h));
    }

    /**
     * The fixed neighbours' edges and corner points won, the rest bent to meet them: a Coons patch of the differences.
     * A corner's difference comes from its fixed value (#vertices' corners, or a fixed edge's end); a free edge's
     * difference runs straight between its corners' (the free neighbour across it computes the same line).
     */
    static void fix(double[][] edges, double[] corners, double[] out) {
        double[] dc = new double[4];   // nw, ne, sw, se
        boolean any = false;
        int[] at = {0, PER_BLOCK, PER_BLOCK * SIDE, PER_BLOCK * SIDE + PER_BLOCK};
        for (int k = 0; k < 4; k++) {
            double f = corners == null ? Double.NaN : corners[k];
            if (Double.isNaN(f) && edges != null) f = edgeEnd(edges, k);
            if (!Double.isNaN(f)) {
                dc[k] = f - out[at[k]];
                any |= Math.abs(dc[k]) > 1e-9;
            }
        }
        double[][] d = new double[4][SIDE];   // N, S (along x), W, E (along z)
        int[][] ends = {{0, 1}, {2, 3}, {0, 2}, {1, 3}};
        for (int e = 0; e < 4; e++) {
            double[] fixed = edges == null ? null : edges[e];
            for (int t = 0; t < SIDE; t++) {
                if (fixed != null) {
                    d[e][t] = fixed[t] - out[edgeIndex(e, t)];
                    any |= Math.abs(d[e][t]) > 1e-9;
                } else {
                    double s = t / (double) PER_BLOCK;
                    d[e][t] = dc[ends[e][0]] * (1 - s) + dc[ends[e][1]] * s;
                }
            }
        }
        if (!any) return;
        // the corners as the edges have them (a fixed edge's end is the corner's value)
        double cnw = d[NORTH][0], cne = d[NORTH][PER_BLOCK], csw = d[SOUTH][0], cse = d[SOUTH][PER_BLOCK];
        double[] res = new double[SIDE * SIDE];
        for (int j = 0; j < SIDE; j++) {
            double v = j / (double) PER_BLOCK;
            for (int i = 0; i < SIDE; i++) {
                double u = i / (double) PER_BLOCK;
                double c = (1 - v) * d[NORTH][i] + v * d[SOUTH][i] + (1 - u) * d[WEST][j] + u * d[EAST][j]
                        - ((1 - u) * (1 - v) * cnw + u * (1 - v) * cne + (1 - u) * v * csw + u * v * cse);
                res[j * SIDE + i] = out[j * SIDE + i] + c;
            }
        }
        // the edges exactly as given (the patch reproduces them up to rounding)
        for (int e = 0; e < 4; e++)
            for (int t = 0; t < SIDE; t++) res[edgeIndex(e, t)] = out[edgeIndex(e, t)] + d[e][t];
        System.arraycopy(res, 0, out, 0, res.length);
    }

    /** The corner's height off a fixed edge ending there (nw, ne, sw, se), NaN when none. */
    private static double edgeEnd(double[][] edges, int k) {
        double[] n = edges[NORTH], s = edges[SOUTH], w = edges[WEST], e = edges[EAST];
        return switch (k) {
            case 0 -> n != null ? n[0] : w != null ? w[0] : Double.NaN;
            case 1 -> n != null ? n[PER_BLOCK] : e != null ? e[0] : Double.NaN;
            case 2 -> s != null ? s[0] : w != null ? w[PER_BLOCK] : Double.NaN;
            default -> s != null ? s[PER_BLOCK] : e != null ? e[PER_BLOCK] : Double.NaN;
        };
    }

    /** The vertex index of position t (0..4) along edge e (N, S west to east; W, E north to south). */
    public static int edgeIndex(int e, int t) {
        return switch (e) {
            case NORTH -> t;
            case SOUTH -> PER_BLOCK * SIDE + t;
            case WEST -> t * SIDE;
            default -> t * SIDE + PER_BLOCK;
        };
    }

    /** Whether the 25 vertices lie on one plane (within eps blocks): such a top needs two triangles only. */
    public static boolean planar(double[] v, double eps) {
        double a = v[0], gx = (v[PER_BLOCK] - a) / PER_BLOCK, gz = (v[PER_BLOCK * SIDE] - a) / PER_BLOCK;
        for (int j = 0; j < SIDE; j++)
            for (int i = 0; i < SIDE; i++)
                if (Math.abs(v[j * SIDE + i] - (a + gx * i + gz * j)) > eps) return false;
        return true;
    }

    /** The height at (fx, fz) in 0..1 on the vertices' triangles (each 0.25 m cell split nw-se, as EarthGrid's skin). */
    public static double at(double[] v, double fx, double fz) {
        double px = Math.max(0, Math.min(PER_BLOCK - 1e-9, fx * PER_BLOCK)), pz = Math.max(0, Math.min(PER_BLOCK - 1e-9, fz * PER_BLOCK));
        int i = (int) px, j = (int) pz;
        double u = px - i, w = pz - j;
        double nw = v[j * SIDE + i], ne = v[j * SIDE + i + 1], sw = v[(j + 1) * SIDE + i], se = v[(j + 1) * SIDE + i + 1];
        return w >= u ? nw + (se - sw) * u + (sw - nw) * w : nw + (ne - nw) * u + (se - ne) * w;
    }
}
