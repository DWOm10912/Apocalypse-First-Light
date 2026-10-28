package com.antaurora.apofirstlight.earthwork;

import static com.antaurora.apofirstlight.earthwork.CarveChunk.N;

/**
 * The geometry of a carved or worn rock block's chunk mesh (U3a-3 round 2, docs/worldgen/underground_u3_rocks_ores_design.md
 * 13.3; U6 RockRelief: a worn face is "carved" here too), without textures or light (terrainsmooth.client.RockCarveMesh adds those; UndergroundCheck tests it for gaps): a
 * surface net over the 1/8 m sub-cells of the block and its 26 neighbours, the part the block owns.
 * <ul>
 *   <li>Field: {@link #L}^3 sub-cells, the block's 8 and 2 of each neighbour on either side ({@link #sample}); per
 *   neighbour (index dx + 1 + 3 (dy + 1) + 9 (dz + 1), 13 the block itself) whether it is carved, and whether it is drawn
 *   the vanilla way as a full cube (not carved, solid render).</li>
 *   <li>Vertices: one per dual cell the surface crosses, at the mean of its crossing edges' midpoints (a flat face stays
 *   exactly on its plane, a cut is rounded off); where a dual cell reaches into a vanilla cube neighbour, at its sub-cell
 *   corner, so the net meets that cube's faces with no gap.</li>
 *   <li>Ownership: a crossing edge is drawn by the block of its solid end if that one is carved, else by the block of
 *   its empty end if that one is carved (an uncarved neighbour's face toward a carving is culled by the carved block,
 *   still a full cube in the data), else by nobody (vanilla's own face).</li>
 *   <li>Flat faces (terrain rework R2e, {@link #buildMerged}): the squares of whole sub-cells in one plane with one look
 *   become a few rectangles: 14-35 % fewer quads on worn rock, about 25 % on a pick's cuts (most of a worn face is
 *   curved, not flat: doc 13.14).</li>
 * </ul>
 */
public final class CarveNet {
    private CarveNet() {}

    /** sample cells per axis and the block's first */
    public static final int L = N + 4, LO = 2;

    /**
     * A quad of the net: its four corners (block-local, wound to face from the solid end to the empty end), the crossing
     * edge's axis, its ends' neighbours, the empty end's sample (a carved or a worn sub-cell: which texture).
     */
    public interface Sink {
        void quad(float[][] pts, int axis, boolean lowSolid, int solidN, int emptyN, int emptySample);
    }

    public static int sample(int i, int j, int k) { return i + L * (j + L * k); }

    /** The neighbour block index (-1, 0, 1) of sample coordinate i along one axis. */
    public static int blockOf(int i) { return Math.floorDiv(i - LO, N); }

    public static int nb(int i, int j, int k) { return blockOf(i) + 1 + 3 * (blockOf(j) + 1) + 9 * (blockOf(k) + 1); }

    /** the length of build's vertex scratch: the dual cells' plain and relaxed vertices */
    public static final int VERTS = 2 * (L - 1) * (L - 1) * (L - 1);

    /** The net's quads the block owns. vert: a scratch array of {@link #VERTS} (cleared here). */
    public static void build(boolean[] solid, boolean[] carved, boolean[] cube, float[][] vert, Sink sink) {
        java.util.Arrays.fill(vert, null);
        for (int axis = 0; axis < 3; axis++) {
            int ex = axis == 0 ? 1 : 0, ey = axis == 1 ? 1 : 0, ez = axis == 2 ? 1 : 0;
            for (int k = 1; k + ez < L - 1; k++) for (int j = 1; j + ey < L - 1; j++) for (int i = 1; i + ex < L - 1; i++) {
                boolean a = solid[sample(i, j, k)], b = solid[sample(i + ex, j + ey, k + ez)];
                if (a == b) continue;
                int na = nb(i, j, k), nbb = nb(i + ex, j + ey, k + ez);
                if (na != 13 && nbb != 13) continue;
                int solidN = a ? na : nbb, emptyN = a ? nbb : na;
                int owner = carved[solidN] ? solidN : carved[emptyN] ? emptyN : -1;
                if (owner != 13) continue;
                // the four dual cells round the edge, counter-clockwise seen from the + end of the axis
                float[][] pts = new float[4][];
                for (int q = 0; q < 4; q++) {
                    int u = q == 1 || q == 2 ? 0 : -1, w = q >= 2 ? 0 : -1;
                    pts[q] = axis == 0 ? relaxed(solid, cube, vert, i, j + u, k + w)
                            : axis == 1 ? relaxed(solid, cube, vert, i + w, j, k + u) : relaxed(solid, cube, vert, i + u, j + w, k);
                }
                if (!a) {   // the solid end is the + one: facing the - end, the other way round
                    float[] t = pts[1];
                    pts[1] = pts[3];
                    pts[3] = t;
                }
                sink.quad(pts, axis, a, solidN, emptyN, a ? sample(i + ex, j + ey, k + ez) : sample(i, j, k));
            }
        }
    }

    /** The class of a quad's look for {@link #buildMerged}: quads of one class beside each other may be one quad (same texture). */
    public interface Look {
        int of(int emptyN, int emptySample);
    }

    /**
     * Terrain rework R2e (docs/worldgen/terrain_rework_v1_design.md 13.14): {@link #build}'s quads with the flat ones
     * merged. A quad whose four corners lie on the 1/8 m grid, a square of one sub-cell in its edge's plane (a flat face
     * of whole sub-cells; a relaxed or rounded vertex is off the grid and its quads go through as they are), is grouped
     * by axis, plane, facing, its ends' neighbours and look; each group's squares are covered by rectangles, greedily
     * (widest first, row by row), and each rectangle goes to the sink as one quad, wound as its squares, with its first
     * square's empty sample. Texture and light stay as they were: a face's uvs are affine in position on it (#Face in
     * RockCarveMesh), a quad's light is its empty end's. A rectangle's edges meet the quads round it at grid points
     * (T-junctions on exact 1/8 m coordinates: UndergroundCheck closes the net with its edges split on the grid).
     */
    public static void buildMerged(boolean[] solid, boolean[] carved, boolean[] cube, float[][] vert, Look look, Sink sink) {
        java.util.Map<Long, Group> groups = new java.util.LinkedHashMap<>();
        build(solid, carved, cube, vert, (pts, axis, lowSolid, solidN, emptyN, emptySample) -> {
            int cell = gridSquare(pts, axis);
            if (cell < 0) {
                sink.quad(pts, axis, lowSolid, solidN, emptyN, emptySample);
                return;
            }
            int plane = Math.round(pts[0][axis] * N);
            long key = ((((long) look.of(emptyN, emptySample) * 32 + emptyN) * 32 + solidN) * 2 + (lowSolid ? 1 : 0)) * 64 + plane * 3L + axis;
            Group g = groups.computeIfAbsent(key, k -> new Group(axis, plane, lowSolid, solidN, emptyN));
            g.mask |= 1L << cell;
            g.sample[cell] = emptySample;
        });
        for (Group g : groups.values()) {
            long m = g.mask;
            while (m != 0) {
                int c = Long.numberOfTrailingZeros(m), a = c % N, b = c / N, w = 1, h = 1;
                while (a + w < N && (m >>> (c + w) & 1) != 0) w++;
                grow:
                while (b + h < N) {
                    for (int t = 0; t < w; t++) if ((m >>> (c + t + N * h) & 1) == 0) break grow;
                    h++;
                }
                for (int r = 0; r < h; r++) m &= ~(((1L << w) - 1) << (c + N * r));
                sink.quad(rectangle(g.axis, g.plane, a, b, w, h, g.lowSolid), g.axis, g.lowSolid, g.solidN, g.emptyN, g.sample[c]);
            }
        }
    }

    /** a group of flat squares for buildMerged: one plane, facing and look; the squares as bits a + N b */
    private static final class Group {
        final int axis, plane, solidN, emptyN;
        final boolean lowSolid;
        final int[] sample = new int[N * N];
        long mask;

        Group(int axis, int plane, boolean lowSolid, int solidN, int emptyN) {
            this.axis = axis;
            this.plane = plane;
            this.lowSolid = lowSolid;
            this.solidN = solidN;
            this.emptyN = emptyN;
        }
    }

    /** the in-plane axes of a quad crossing an edge along axis, in build's corner order */
    private static int inA(int axis) { return axis == 0 ? 1 : 0; }

    private static int inB(int axis) { return axis == 2 ? 1 : 2; }

    /**
     * The square a + N b on the 1/8 m grid that quad pts covers in its edge's plane, or -1 when it is not one (a corner
     * off the grid or off the plane: a relaxed or rounded part).
     */
    public static int gridSquare(float[][] pts, int axis) {
        int ia = inA(axis), ib = inB(axis);
        float p = pts[0][axis] * N;
        if (p != Math.rint(p)) return -1;
        float a0 = Float.MAX_VALUE, b0 = Float.MAX_VALUE, a1 = -Float.MAX_VALUE, b1 = -Float.MAX_VALUE;
        for (float[] q : pts) {
            float x = q[ia] * N, y = q[ib] * N;
            if (q[axis] * N != p || x != Math.rint(x) || y != Math.rint(y)) return -1;
            a0 = Math.min(a0, x);
            a1 = Math.max(a1, x);
            b0 = Math.min(b0, y);
            b1 = Math.max(b1, y);
        }
        if (a1 - a0 != 1 || b1 - b0 != 1 || a0 < 0 || b0 < 0 || a1 > N || b1 > N) return -1;
        for (int q = 0; q < 4; q++)   // the four corners distinct (a square, not a folded quad)
            for (int r = q + 1; r < 4; r++)
                if (pts[q][ia] == pts[r][ia] && pts[q][ib] == pts[r][ib]) return -1;
        return (int) a0 + N * (int) b0;
    }

    /** A rectangle of w x h squares from square (a, b) on plane, its corners in build's order for the axis and facing. */
    static float[][] rectangle(int axis, int plane, int a, int b, int w, int h, boolean lowSolid) {
        int ia = inA(axis), ib = inB(axis);
        float pa0 = a / (float) N, pa1 = (a + w) / (float) N, pb0 = b / (float) N, pb1 = (b + h) / (float) N, pp = plane / (float) N;
        // build's order: axes 0 and 2 (a0 b0) (a1 b0) (a1 b1) (a0 b1); axis 1 (a0 b0) (a0 b1) (a1 b1) (a1 b0)
        float[][] ab = axis == 1 ? new float[][]{{pa0, pb0}, {pa0, pb1}, {pa1, pb1}, {pa1, pb0}}
                : new float[][]{{pa0, pb0}, {pa1, pb0}, {pa1, pb1}, {pa0, pb1}};
        float[][] pts = new float[4][3];
        for (int q = 0; q < 4; q++) {
            pts[q][axis] = pp;
            pts[q][ia] = ab[q][0];
            pts[q][ib] = ab[q][1];
        }
        if (!lowSolid) {   // as build: the solid end is the + one, facing the - end
            float[] t = pts[1];
            pts[1] = pts[3];
            pts[3] = t;
        }
        return pts;
    }

    private static boolean snapped(boolean[] cube, int i, int j, int k) {
        for (int d = 0; d < 8; d++) if (cube[nb(i + (d & 1), j + (d >> 1 & 1), k + (d >> 2 & 1))]) return true;
        return false;
    }

    /** Whether the surface crosses dual cell (i, j, k): its eight samples are not all alike. */
    private static boolean crossed(boolean[] solid, int i, int j, int k) {
        boolean s = solid[sample(i, j, k)];
        for (int d = 1; d < 8; d++) if (solid[sample(i + (d & 1), j + (d >> 1 & 1), k + (d >> 2 & 1))] != s) return true;
        return false;
    }

    /**
     * The dual cell's vertex relaxed once toward its crossed neighbours' (half way, kept inside the cell): a step of one
     * sub-cell becomes a slope over two or three instead of a 45-degree bevel, which took the other axis's shade and
     * texture and read as a drawn line on a wall (user 2026-10-24, sandstone cliffs). A vertex snapped to a vanilla cube
     * stays. Reads samples one dual cell further out than the plain vertex: the field's two-sample margin holds them, and
     * every block that shares the cell computes it from the same samples, so the nets still meet exactly.
     */
    static float[] relaxed(boolean[] solid, boolean[] cube, float[][] vert, int i, int j, int k) {
        int n = (L - 1) * (L - 1) * (L - 1), c = n + i + (L - 1) * (j + (L - 1) * k);
        if (vert[c] != null) return vert[c];
        float[] v = vertex(solid, cube, vert, i, j, k);
        if (snapped(cube, i, j, k)) return vert[c] = v;
        float sx = 0, sy = 0, sz = 0;
        int cnt = 0;
        for (int d = 0; d < 6; d++) {
            int a = i + (d == 0 ? -1 : d == 1 ? 1 : 0), b = j + (d == 2 ? -1 : d == 3 ? 1 : 0), e = k + (d == 4 ? -1 : d == 5 ? 1 : 0);
            if (a < 0 || b < 0 || e < 0 || a >= L - 1 || b >= L - 1 || e >= L - 1 || !crossed(solid, a, b, e)) continue;
            float[] o = vertex(solid, cube, vert, a, b, e);
            sx += o[0];
            sy += o[1];
            sz += o[2];
            cnt++;
        }
        if (cnt == 0) return vert[c] = v;
        float[] r = {0.5F * v[0] + 0.5F * sx / cnt, 0.5F * v[1] + 0.5F * sy / cnt, 0.5F * v[2] + 0.5F * sz / cnt};
        // the dual cell spans the sample centres i + 0.5 .. i + 1.5, block-local
        r[0] = Math.max((i + 0.5F - LO) / N, Math.min((i + 1.5F - LO) / N, r[0]));
        r[1] = Math.max((j + 0.5F - LO) / N, Math.min((j + 1.5F - LO) / N, r[1]));
        r[2] = Math.max((k + 0.5F - LO) / N, Math.min((k + 1.5F - LO) / N, r[2]));
        return vert[c] = r;
    }

    /** The dual cell (i, j, k)'s vertex (samples i..i+1 and so on), block-local coordinates. */
    static float[] vertex(boolean[] solid, boolean[] cube, float[][] vert, int i, int j, int k) {
        int c = i + (L - 1) * (j + (L - 1) * k);
        if (vert[c] != null) return vert[c];
        boolean snap = snapped(cube, i, j, k);
        float[] v;
        if (snap) {
            v = new float[]{(i + 1 - LO) / (float) N, (j + 1 - LO) / (float) N, (k + 1 - LO) / (float) N};
        } else {
            float sx = 0, sy = 0, sz = 0;
            int cnt = 0;
            for (int axis = 0; axis < 3; axis++) for (int u = 0; u < 2; u++) for (int w = 0; w < 2; w++) {
                int ai = i, aj = j, ak = k;
                if (axis == 0) {
                    aj += u;
                    ak += w;
                } else if (axis == 1) {
                    ai += u;
                    ak += w;
                } else {
                    ai += u;
                    aj += w;
                }
                int bi = ai + (axis == 0 ? 1 : 0), bj = aj + (axis == 1 ? 1 : 0), bk = ak + (axis == 2 ? 1 : 0);
                if (solid[sample(ai, aj, ak)] == solid[sample(bi, bj, bk)]) continue;
                sx += (ai + bi + 1) * 0.5F;
                sy += (aj + bj + 1) * 0.5F;
                sz += (ak + bk + 1) * 0.5F;
                cnt++;
            }
            v = new float[]{(sx / cnt - LO) / N, (sy / cnt - LO) / N, (sz / cnt - LO) / N};
        }
        return vert[c] = v;
    }
}
