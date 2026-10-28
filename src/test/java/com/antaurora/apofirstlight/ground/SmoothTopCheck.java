package com.antaurora.apofirstlight.ground;

import com.antaurora.apofirstlight.terrainsmooth.SmoothRules;
import com.antaurora.apofirstlight.terrainsmooth.SmoothTop;

import java.util.Locale;
import java.util.Random;

/**
 * Terrain rework R2b (docs/worldgen/terrain_rework_v1_design.md 13.9): SmoothTop on synthetic ground (rolling columns,
 * steps of one to three, dug holes, built and rock columns), as the game computes it (SmoothRules#geometry keys, the
 * neighbours' keys, the corner points from the columns' surfaces).
 * <ul>
 *   <li>Every edge two blocks share with the same corners has the same midpoint on both sides (no crack, no T step).</li>
 *   <li>Every midpoint lies between its edge's corners, every centre between the block's lowest and highest corner.</li>
 *   <li>GroundBlend's pieces on a smooth top lie on SmoothRules#surface (the cells' diagonals follow the fan's).</li>
 * </ul>
 */
public final class SmoothTopCheck {
    private SmoothTopCheck() {}

    private static int fails;

    public static void main(String[] args) {
        int fans = 0, seams = 0, seamBad = 0, sloped = 0, curved = 0, edges = 0, rangeBad = 0, blocks = 0;
        double worst = 0;
        for (int world = 0; world < 6; world++) {
            Random r = new Random(17 + world);
            int w = 72, x0 = 0, z0 = 0, y0 = 50;
            GroundClassCheck.Grid c = new GroundClassCheck.Grid(x0, y0, z0, w, 24);
            int[][] h = new int[w][w];
            double fx = 0.05 + 0.05 * r.nextDouble(), fz = 0.05 + 0.05 * r.nextDouble(), amp = 2 + world;
            for (int x = 0; x < w; x++)
                for (int z = 0; z < w; z++) {
                    h[x][z] = 62 + (int) Math.round(amp * Math.sin(x * fx + world) * Math.cos(z * fz) + (x > 40 && z > 30 && world % 2 == 0 ? 3 : 0));
                    if (r.nextDouble() < 0.02) h[x][z] -= 1 + r.nextInt(2);   // a dug hole
                }
            for (int x = 0; x < w; x++)
                for (int z = 0; z < w; z++) {
                    double u = r.nextDouble();
                    int top = u < 0.03 ? SmoothRules.OTHER : u < 0.05 ? SmoothRules.ROCK : SmoothRules.NATURAL;
                    for (int y = y0; y < y0 + 24; y++)
                        c.c[c.i(x, y, z)] = (byte) (y < h[x][z] ? SmoothRules.NATURAL : y == h[x][z] ? top : SmoothRules.AIR);
                }
            c.surfaces();
            // every column's drawn edges (N, S, W, E: corner a, midpoint, corner b; absolute), NaN where it draws none
            float[][][] edge = new float[w][w][];
            for (int x = 3; x < w - 3; x++)
                for (int z = 3; z < w - 3; z++) {
                    int y = c.surface(x, z);
                    if (y == SmoothRules.NO_SURFACE) continue;
                    blocks++;
                    long g = SmoothRules.natural(c.cell(x, y, z)) ? SmoothRules.geometry(c, SmoothRules.MODE_B, x, y, z) : -1;
                    float[] t;
                    if (g < 0) t = new float[]{1, 1, 1, 1, 1, 1, 1, 1, 1};
                    else {
                        long[] keys = new long[4];
                        int[] floors = new int[4];
                        SmoothTop.neighbours(new SmoothTop.Keys() {
                            @Override public long key(int nx, int nz) {
                                int s = c.surface(nx, nz);
                                return s == SmoothRules.NO_SURFACE || !SmoothRules.natural(c.cell(nx, s, nz)) ? -1 : SmoothRules.geometry(c, SmoothRules.MODE_B, nx, s, nz);
                            }

                            @Override public int floor(int nx, int nz) { return c.surface(nx, nz); }
                        }, x, z, keys, floors);
                        t = SmoothTop.top(g, y, SmoothTop.points(c, x, z), keys, floors);
                        if (t.length < 9) {   // a fan comes back as is; a plain top never does
                            fail("plain top at " + x + " " + z);
                            continue;
                        }
                        sloped++;
                        if ((g & SmoothRules.FAN) != 0) { fans++; sloped--; }
                        else {
                        float lo = Math.min(Math.min(t[0], t[1]), Math.min(t[2], t[3])), hi = Math.max(Math.max(t[0], t[1]), Math.max(t[2], t[3]));
                        if (t[4] < lo - 1e-6 || t[4] > hi + 1e-6) rangeBad++;
                        int[][] ends = {{0, 1}, {2, 3}, {0, 2}, {1, 3}};
                        for (int s = 0; s < 4; s++) {
                            float a = t[ends[s][0]], b = t[ends[s][1]], m = t[5 + s];
                            if (m < Math.min(a, b) - 1e-6 || m > Math.max(a, b) + 1e-6) rangeBad++;
                            edges++;
                            if (Math.abs(m - (a + b) / 2) > 1e-6) curved++;
                        }
                        }
                    }
                    int[][] ends = {{0, 1}, {2, 3}, {0, 2}, {1, 3}};
                    float[] e = new float[12];
                    for (int s = 0; s < 4; s++) {
                        e[s * 3] = y + t[ends[s][0]];
                        e[s * 3 + 1] = y + t[5 + s];
                        e[s * 3 + 2] = y + t[ends[s][1]];
                    }
                    edge[x][z] = e;
                }
            // shared edges: A's east against B's west, A's south against B's north
            for (int x = 3; x < w - 4; x++)
                for (int z = 3; z < w - 4; z++) {
                    float[] a = edge[x][z];
                    if (a == null) continue;
                    for (int[] p : new int[][]{{1, 0, 3, 2}, {0, 1, 1, 0}}) {
                        float[] b = edge[x + p[0]][z + p[1]];
                        if (b == null) continue;
                        int sa = p[2] * 3, sb = p[3] * 3;
                        if (Math.abs(a[sa] - b[sb]) > 1e-4 || Math.abs(a[sa + 2] - b[sb + 2]) > 1e-4) continue;   // a step: its band
                        seams++;
                        double d = Math.abs(a[sa + 1] - b[sb + 1]);
                        worst = Math.max(worst, d);
                        if (d > 1e-4) seamBad++;
                    }
                }
        }
        System.out.printf(Locale.ROOT, "  tops: %d blocks, %d smooth, %d fans (dug holes, as before); %.1f %% of the smooth tops' edges curve%n", blocks, sloped, fans, 100.0 * curved / Math.max(1, edges));
        System.out.printf(Locale.ROOT, "  seams: %d shared edges, %d midpoints apart (worst %.2e); %d points outside their corners%n", seams, seamBad, worst, rangeBad);
        check(sloped > 0 && curved > 0, "smooth tops and curved edges exist");
        check(seamBad == 0, "shared edges meet");
        check(rangeBad == 0, "midpoints and centres within their corners");
        pieces();
        System.out.println(fails == 0 ? "SMOOTH TOP CHECK PASS" : "SMOOTH TOP CHECK FAIL (" + fails + ")");
        if (fails > 0) System.exit(1);
    }

    /** GroundBlend's pieces over random nine-point tops lie on the drawn top. */
    static void pieces() {
        Random r = new Random(5);
        double worst = 0;
        int bad = 0, n = 0;
        for (int k = 0; k < 400; k++) {
            float[] t = new float[9];
            for (int i = 0; i < 4; i++) t[i] = r.nextInt(9) / 4F;
            float lo = Math.min(Math.min(t[0], t[1]), Math.min(t[2], t[3])), hi = Math.max(Math.max(t[0], t[1]), Math.max(t[2], t[3]));
            for (int i = 4; i < 9; i++) t[i] = lo + (hi - lo) * r.nextFloat();
            double[] h = new double[25];
            for (int j = 0; j < 5; j++) for (int i = 0; i < 5; i++) h[j * 5 + i] = SmoothRules.surface(t, i / 4.0, j / 4.0);
            int[] mats = new int[9], surf = new int[9];
            for (int i = 0; i < 9; i++) { mats[i] = r.nextInt(3); surf[i] = 60; }
            int[] ids = new int[GroundBlend.MAX];
            int m = GroundBlend.materials(mats, ids);
            double[] w = new double[m * 25];
            GroundBlend.weights(k * 7, k * 3, mats, surf, ids, m, w);
            final double[] wst = {0};
            final int[] b = {0}, cnt = {0};
            GroundBlend.pieces(h, w, m, (mat, pts, c) -> {
                for (int q = 0; q < c; q++) {
                    double d = Math.abs(pts[q][1] - SmoothRules.surface(t, Math.min(pts[q][0], 1 - 1e-9), Math.min(pts[q][2], 1 - 1e-9)));
                    wst[0] = Math.max(wst[0], d);
                    if (d > 1e-5) b[0]++;
                    cnt[0]++;
                }
            });
            worst = Math.max(worst, wst[0]);
            bad += b[0];
            n += cnt[0];
        }
        System.out.printf(Locale.ROOT, "  pieces on the drawn top: %d points, %d off it (worst %.2e)%n", n, bad, worst);
        check(bad == 0, "pieces lie on the drawn top");
    }

    private static void check(boolean ok, String what) {
        if (!ok) fail(what);
    }

    private static void fail(String what) {
        fails++;
        System.out.println("  FAIL " + what);
    }
}
