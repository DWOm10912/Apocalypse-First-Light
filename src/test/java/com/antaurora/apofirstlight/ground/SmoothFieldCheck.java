package com.antaurora.apofirstlight.ground;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Terrain rework R2 (docs/worldgen/terrain_rework_v1_design.md 13): SmoothField and GroundBlend on random ground, offline.
 * <ul>
 *   <li>a plane in gives the plane out;</li>
 *   <li>two blocks sharing an edge whose end corners are not pinned compute it bit for bit alike, missing corner points
 *   round them or not;</li>
 *   <li>edge vertices lie between the edge's end corners, inside ones within the block's corners;</li>
 *   <li>a fixed neighbour's edge comes out exactly, and a free block beside the bent one still meets it;</li>
 *   <li>the material pieces tile every triangle, the blocks sharing a vertex weigh it alike, a block's centre shows its
 *   own material, and a drawn material lies within about half a block of a column of that material;</li>
 *   <li>triangles per block: plain, curved, at a boundary.</li>
 * </ul>
 */
public final class SmoothFieldCheck {
    private static int fails, checks;

    private SmoothFieldCheck() {}

    public static void main(String[] args) {
        plane();
        edges();
        fixedEdges();
        blend();
        System.out.println(fails == 0 ? "SMOOTH FIELD CHECK PASS (" + checks + " checks)" : "SMOOTH FIELD CHECK FAIL (" + fails + " of " + checks + ")");
        if (fails > 0) System.exit(1);
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            fails++;
            System.out.println("  FAIL " + what);
        }
    }

    // ---- a corner field: G[i][j] at corner point (i, j), NaN where pinned; block (x, z)'s floor is floor(min own corner)

    static final int N = 24;

    static double[][] field(Random r, double pinShare) {
        double[][] g = new double[N + 1][N + 1];
        double a = r.nextDouble() * 6, b = r.nextDouble() * 6;
        for (int i = 0; i <= N; i++)
            for (int j = 0; j <= N; j++) {
                double h = 70 + 1.5 * Math.sin(i * 0.31 + a) + 1.2 * Math.cos(j * 0.27 + b) + 0.6 * Math.sin((i + j) * 0.5);
                g[i][j] = Math.round(h * 4) / 4.0;
            }
        if (pinShare > 0)
            for (int i = 0; i <= N; i++) for (int j = 0; j <= N; j++) if (r.nextDouble() < pinShare) g[i][j] = Double.NaN;
        return g;
    }

    /** A block's own corner at (i, j): the field's value, or the block's own fallback (its top) when pinned. */
    static double own(double[][] g, int i, int j, double top) {
        return Double.isNaN(g[i][j]) ? top : g[i][j];
    }

    /** Block (x, z)'s 25 vertices in world height (floor added back). */
    static double[] block(double[][] g, int x, int z, double top, double[][] edges, double[] corners) {
        double floor = Math.floor(top) - 1;
        float[] ring = new float[16];
        for (int j = 0; j < 4; j++)
            for (int i = 0; i < 4; i++) {
                int ci = x - 1 + i, cj = z - 1 + j;
                boolean mine = (i == 1 || i == 2) && (j == 1 || j == 2);
                double v = ci < 0 || cj < 0 || ci > N || cj > N ? Double.NaN : mine ? own(g, ci, cj, top) : g[ci][cj];
                ring[j * 4 + i] = (float) (v - floor);
            }
        double[][] rel = null;
        if (edges != null) {
            rel = new double[4][];
            for (int e = 0; e < 4; e++) if (edges[e] != null) {
                rel[e] = new double[5];
                for (int t = 0; t < 5; t++) rel[e][t] = edges[e][t] - floor;
            }
        }
        double[] cr = null;
        if (corners != null) {
            cr = new double[4];
            for (int k = 0; k < 4; k++) cr[k] = corners[k] - floor;
        }
        double[] out = new double[25];
        SmoothField.vertices(ring, rel, cr, out);
        for (int k = 0; k < 25; k++) out[k] += floor;
        return out;
    }

    static double top(double[][] g, int x, int z) {
        double t = Double.NEGATIVE_INFINITY;
        for (int j = 0; j < 2; j++) for (int i = 0; i < 2; i++) if (!Double.isNaN(g[x + i][z + j])) t = Math.max(t, g[x + i][z + j]);
        return t == Double.NEGATIVE_INFINITY ? 70 : Math.ceil(t);
    }

    static void plane() {
        double[][] g = new double[N + 1][N + 1];
        for (int i = 0; i <= N; i++) for (int j = 0; j <= N; j++) g[i][j] = 60 + 0.25 * i - 0.5 * j;
        double worst = 0;
        for (int x = 1; x < N - 1; x++)
            for (int z = 1; z < N - 1; z++) {
                double[] v = block(g, x, z, top(g, x, z), null, null);
                for (int j = 0; j < 5; j++) for (int i = 0; i < 5; i++)
                    worst = Math.max(worst, Math.abs(v[j * 5 + i] - (60 + 0.25 * (x + i / 4.0) - 0.5 * (z + j / 4.0))));
                check(SmoothField.planar(subtract(v, Math.floor(v[0])), 1e-6), "plane block " + x + " " + z + " not planar");
            }
        System.out.printf(Locale.ROOT, "  plane: worst %.2e blocks off%n", worst);
        check(worst < 1e-6, "plane reproduced");
    }

    static double[] subtract(double[] v, double d) {
        double[] o = v.clone();
        for (int k = 0; k < o.length; k++) o[k] -= d;
        return o;
    }

    static void edges() {
        Random r = new Random(7);
        int shared = 0, bad = 0, range = 0;
        double maxDiff = 0;
        for (int trial = 0; trial < 40; trial++) {
            double[][] g = field(r, trial < 10 ? 0 : 0.15);
            double[][][] v = new double[N][N][];
            for (int x = 0; x < N; x++) for (int z = 0; z < N; z++) v[x][z] = block(g, x, z, top(g, x, z), null, null);
            for (int x = 0; x < N; x++)
                for (int z = 0; z < N; z++) {
                    double[] b = v[x][z];
                    double[] own = {own(g, x, z, top(g, x, z)), own(g, x + 1, z, top(g, x, z)), own(g, x, z + 1, top(g, x, z)), own(g, x + 1, z + 1, top(g, x, z))};
                    double lo = Math.min(Math.min(own[0], own[1]), Math.min(own[2], own[3])) - 1e-9, hi = Math.max(Math.max(own[0], own[1]), Math.max(own[2], own[3])) + 1e-9;
                    for (int k = 0; k < 25; k++) if (b[k] < lo || b[k] > hi) range++;
                    // east neighbour: this block's east edge against its west edge, when both end points are not pinned
                    if (x + 1 < N && !Double.isNaN(g[x + 1][z]) && !Double.isNaN(g[x + 1][z + 1])) {
                        shared++;
                        for (int t = 0; t < 5; t++) {
                            double d = Math.abs(b[SmoothField.edgeIndex(SmoothField.EAST, t)] - v[x + 1][z][SmoothField.edgeIndex(SmoothField.WEST, t)]);
                            maxDiff = Math.max(maxDiff, d);
                            if (d > 1e-9) bad++;
                        }
                    }
                    if (z + 1 < N && !Double.isNaN(g[x][z + 1]) && !Double.isNaN(g[x + 1][z + 1])) {
                        shared++;
                        for (int t = 0; t < 5; t++) {
                            double d = Math.abs(b[SmoothField.edgeIndex(SmoothField.SOUTH, t)] - v[x][z + 1][SmoothField.edgeIndex(SmoothField.NORTH, t)]);
                            maxDiff = Math.max(maxDiff, d);
                            if (d > 1e-9) bad++;
                        }
                    }
                }
        }
        System.out.printf(Locale.ROOT, "  edges: %d shared edges, %d vertices apart (worst %.2e), %d vertices outside the block's corners%n", shared, bad, maxDiff, range);
        check(bad == 0, "shared edges alike");
        check(range == 0, "vertices within the corners");
    }

    static void fixedEdges() {
        Random r = new Random(11);
        int bad = 0, cases = 0;
        for (int trial = 0; trial < 200; trial++) {
            double[][] g = field(r, 0);
            int x = 5 + r.nextInt(N - 10), z = 5 + r.nextInt(N - 10);
            // the block north of (x, z) is an edited column: its south edge wavy
            double[] fixed = new double[5];
            for (int t = 0; t < 5; t++) fixed[t] = g[x][z] + (g[x + 1][z] - g[x][z]) * t / 4.0 + (t == 0 || t == 4 ? 0 : 0.1 * (r.nextDouble() * 2 - 1));
            fixed[0] += 0.2 * (r.nextDouble() - 0.5);
            fixed[4] += 0.2 * (r.nextDouble() - 0.5);
            double[][] edges = {fixed, null, null, null};
            double[] corners = {fixed[0], fixed[4], Double.NaN, Double.NaN};
            double[] b = block(g, x, z, top(g, x, z), edges, corners);
            for (int t = 0; t < 5; t++) if (Math.abs(b[SmoothField.edgeIndex(SmoothField.NORTH, t)] - fixed[t]) > 1e-9) bad++;
            // the free block east of it: the edited column is its north-west diagonal, touching its nw corner
            double[] e = block(g, x + 1, z, top(g, x + 1, z), null, new double[]{fixed[4], Double.NaN, Double.NaN, Double.NaN});
            for (int t = 0; t < 5; t++)
                if (Math.abs(b[SmoothField.edgeIndex(SmoothField.EAST, t)] - e[SmoothField.edgeIndex(SmoothField.WEST, t)]) > 1e-9) bad++;
            // and the free block south of it: neither touches the column, their shared edge untouched
            double[] s = block(g, x, z + 1, top(g, x, z + 1), null, null);
            for (int t = 0; t < 5; t++)
                if (Math.abs(b[SmoothField.edgeIndex(SmoothField.SOUTH, t)] - s[SmoothField.edgeIndex(SmoothField.NORTH, t)]) > 1e-9) bad++;
            cases++;
        }
        System.out.printf(Locale.ROOT, "  fixed edges: %d cases, %d vertices apart%n", cases, bad);
        check(bad == 0, "fixed edge kept and free neighbours joined");
    }

    // ---- materials

    static void blend() {
        Random r = new Random(3);
        int n = 40;
        int[][] mat = new int[n][n];
        // a bank: water side gravel with sand patches, a sand/gravel beach, gravel patches, then grass; a rock column here and there
        for (int x = 0; x < n; x++)
            for (int z = 0; z < n; z++) {
                double d = z - 12 - 3 * Math.sin(x * 0.3);
                double u = r.nextDouble();
                int m = d < 0 ? (u < 0.2 ? 2 : 1) : d < 3 ? (u < 0.5 ? 2 : 1) : d < 5 && u < 0.35 ? 1 : 0;
                if (r.nextDouble() < 0.02) m = GroundBlend.NONE;
                mat[x][z] = m;
            }
        // surfaces: a gentle rise with a 3-block step (a pinned cliff) across part of the bank (R2b: no blend across it)
        int[][] hgt = new int[n][n];
        for (int x = 0; x < n; x++) for (int z = 0; z < n; z++) hgt[x][z] = 60 + z / 6 + (x > 25 && z > 20 ? 3 : 0);
        int vertexBad = 0, tileBad = 0, centreBad = 0, driftBad = 0, blocks = 0, boundaryBlocks = 0;
        int[] slivers = {0}, stepBad = {0};
        long tris = 0, boundaryTris = 0;
        double worstDrift = 0;
        double[][][] weightsAt = new double[n][n][];
        int[][][] idsAt = new int[n][n][];
        int[][] countAt = new int[n][n];
        double[] flat = new double[25];
        for (int x = 1; x < n - 1; x++)
            for (int z = 1; z < n - 1; z++) {
                if (mat[x][z] < 0) continue;
                int[] mats = new int[9];
                int[] surf = new int[9];
                for (int j = 0; j < 3; j++) for (int i = 0; i < 3; i++) {
                    mats[j * 3 + i] = mat[x - 1 + i][z - 1 + j];
                    surf[j * 3 + i] = hgt[x - 1 + i][z - 1 + j];
                }
                int[] ids = new int[GroundBlend.MAX];
                int k = GroundBlend.materials(mats, ids);
                double[] w = new double[k * 25];
                GroundBlend.weights(x, z, mats, surf, ids, k, w);
                weightsAt[x][z] = w;
                idsAt[x][z] = ids;
                countAt[x][z] = k;
                int[] lead = new int[25];
                GroundBlend.leaders(w, k, lead);
                if (lead[12] != 0) centreBad++;
                double[] area = new double[32];
                int[] pieces = {0};
                final int bx = x, bz = z;
                List<double[]> drift = new ArrayList<>();
                GroundBlend.pieces(flat, w, k, (m, pts, c) -> {
                    double a = GroundBlend.area(pts, c);
                    if (a < 0.002 && a > 0) slivers[0]++;
                    // which triangle: by the centroid
                    double cx = 0, cz = 0;
                    for (int q = 0; q < c; q++) { cx += pts[q][0]; cz += pts[q][2]; }
                    cx /= c;
                    cz /= c;
                    int ci = Math.min(3, (int) (cx * 4)), cj = Math.min(3, (int) (cz * 4));
                    double ux = cx * 4 - ci, uz = cz * 4 - cj;
                    // the cell's diagonal runs as the fan quarters' (GroundBlend#nwSe)
                    area[(cj * 4 + ci) * 2 + (GroundBlend.nwSe(ci, cj) ? (uz >= ux ? 0 : 1) : (ux + uz <= 1 ? 0 : 1))] += a;
                    pieces[0] += c - 2;
                    int id = ids[m];
                    if (id != mat[bx][bz]) for (int q = 0; q < c; q++) drift.add(new double[]{bx + pts[q][0], bz + pts[q][2], id});
                });
                for (double a : area) if (Math.abs(a - 1.0 / 32) > 1e-9) tileBad++;
                for (double[] p : drift) {
                    double d = nearest(mat, (int) p[2], p[0], p[1]);
                    worstDrift = Math.max(worstDrift, d);
                    if (d > 0.6) driftBad++;
                    // across the 3-block step the lower side's material never shows on the upper side and back
                    int px = (int) Math.floor(p[0]), pz = (int) Math.floor(p[1]);
                    if (nearestOver(mat, hgt, (int) p[2], p[0], p[1], hgt[px][pz]) > 0.6) stepBad[0]++;
                }
                blocks++;
                tris += pieces[0];
                boolean boundary = false;
                for (int v = 1; v < 25; v++) if (lead[v] != lead[0]) boundary = true;
                if (boundary) {
                    boundaryBlocks++;
                    boundaryTris += pieces[0];
                }
            }
        // shared vertices: the east neighbour's west edge, the south neighbour's north edge, by material id
        for (int x = 1; x < n - 2; x++)
            for (int z = 1; z < n - 2; z++) {
                if (weightsAt[x][z] == null) continue;
                // where a pinned step parts the tops (columns round the edge three or more apart) the blocks may differ
                if (weightsAt[x + 1][z] != null && spread(hgt, x, z - 1, x + 1, z + 1) < GroundBlend.STEP)
                    for (int t = 0; t < 5; t++) vertexBad += differ(weightsAt[x][z], idsAt[x][z], countAt[x][z], SmoothField.edgeIndex(SmoothField.EAST, t),
                            weightsAt[x + 1][z], idsAt[x + 1][z], countAt[x + 1][z], SmoothField.edgeIndex(SmoothField.WEST, t));
                if (weightsAt[x][z + 1] != null && spread(hgt, x - 1, z, x + 1, z + 1) < GroundBlend.STEP)
                    for (int t = 0; t < 5; t++) vertexBad += differ(weightsAt[x][z], idsAt[x][z], countAt[x][z], SmoothField.edgeIndex(SmoothField.SOUTH, t),
                            weightsAt[x][z + 1], idsAt[x][z + 1], countAt[x][z + 1], SmoothField.edgeIndex(SmoothField.NORTH, t));
            }
        System.out.printf(Locale.ROOT, "  blend: %d blocks, %d at a boundary; triangles a block %.1f (a boundary block %.1f, a plain curved top 32); slivers < 0.002 m2: %d%n",
                blocks, boundaryBlocks, tris / (double) blocks, boundaryBlocks == 0 ? 0 : boundaryTris / (double) boundaryBlocks, slivers[0]);
        System.out.printf(Locale.ROOT, "  blend: triangles not tiled %d, shared vertices weighed apart %d, centres not their own %d, drawn material farthest %.2f m from its column (%d past 0.6 m)%n",
                tileBad, vertexBad, centreBad, worstDrift, driftBad);
        check(tileBad == 0, "pieces tile every triangle");
        check(vertexBad == 0, "shared vertices weighed alike");
        check(centreBad == 0, "a block's centre shows its own material");
        check(driftBad == 0, "drawn materials within 0.6 m of their columns");
        System.out.printf(Locale.ROOT, "  blend: material drawn from across a pinned step: %d points%n", stepBad[0]);
        check(stepBad[0] == 0, "no material across a pinned step (R2b)");
    }

    /** Whether the two blocks' weights at a shared vertex differ for any material both see. */
    static int differ(double[] wa, int[] ia, int na, int va, double[] wb, int[] ib, int nb, int vb) {
        int bad = 0;
        for (int a = 0; a < na; a++)
            for (int b = 0; b < nb; b++)
                if (ia[a] == ib[b] && Math.abs(wa[a * 25 + va] - wb[b * 25 + vb]) > 1e-12) bad++;
        return bad;
    }

    /** Distance from (px, pz) to the nearest column of material id. */
    /** Highest minus lowest surface over columns x0..x1, z0..z1. */
    static int spread(int[][] hgt, int x0, int z0, int x1, int z1) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) { lo = Math.min(lo, hgt[x][z]); hi = Math.max(hi, hgt[x][z]); }
        return hi - lo;
    }

    /** #nearest among the columns less than GroundBlend.STEP off surface h. */
    static double nearestOver(int[][] mat, int[][] hgt, int id, double px, double pz, int h) {
        double best = Double.MAX_VALUE;
        for (int x = 0; x < mat.length; x++)
            for (int z = 0; z < mat[x].length; z++) {
                if (mat[x][z] != id || Math.abs(hgt[x][z] - h) >= GroundBlend.STEP) continue;
                double dx = Math.max(0, Math.max(x - px, px - (x + 1))), dz = Math.max(0, Math.max(z - pz, pz - (z + 1)));
                best = Math.min(best, Math.hypot(dx, dz));
            }
        return best;
    }

    static double nearest(int[][] mat, int id, double px, double pz) {
        double best = Double.MAX_VALUE;
        for (int x = 0; x < mat.length; x++)
            for (int z = 0; z < mat[x].length; z++) {
                if (mat[x][z] != id) continue;
                double dx = Math.max(0, Math.max(x - px, px - (x + 1))), dz = Math.max(0, Math.max(z - pz, pz - (z + 1)));
                best = Math.min(best, Math.hypot(dx, dz));
            }
        return best;
    }
}
