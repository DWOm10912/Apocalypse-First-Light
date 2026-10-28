package com.antaurora.apofirstlight.ground;

import com.antaurora.apofirstlight.terrainsmooth.SmoothRules;
import com.antaurora.apofirstlight.worldgen.terrain.v2.FederalTerrainPlan;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.zip.InflaterInputStream;

/**
 * Terrain rework R2b sizing (docs/worldgen/terrain_rework_v1_design.md 13.6): on the survey seeds' planned ground (whole
 * columns at the plan's height, all loose ground, mode B corners as SmoothRules), how far SmoothField's curved tops lie
 * from today's two triangles and how many top quads they take at 0.25 m and 0.5 m vertices.
 */
public final class SmoothTopStats {
    private SmoothTopStats() {}

    static final String[] BINS = {
            "build/federal-c2-client/afl_cache/terrain_plan/afl_federal_plan_c2_v1_4.bin",
            "build/federal-c2-client/afl_cache/terrain_plan/afl_federal_plan_c2_v1_18151365494840402103.bin",
            "../Apocalypse First Light/run/afl_cache/terrain_plan/afl_federal_plan_c2_v1_10132636411566409605.bin",
            "../Apocalypse First Light/run/afl_cache/terrain_plan/afl_federal_plan_c2_v1_10164326571583364449.bin",
            "../Apocalypse First Light/run/afl_cache/terrain_plan/afl_federal_plan_c2_v1_11823271242869393782.bin",
            "../Apocalypse First Light/run/afl_cache/terrain_plan/afl_federal_plan_c2_v1_16060040187792812410.bin",
    };

    public static void main(String[] args) throws Exception {
        for (String f : BINS) {
            if (!Files.isRegularFile(Path.of(f))) { System.out.println("missing " + f); continue; }
            String name = Path.of(f).getFileName().toString();
            long seed = Long.parseUnsignedLong(name.substring(name.lastIndexOf('_') + 1, name.length() - 4));
            TerrainPlanSurface s;
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(new FileInputStream(f)), 1 << 16))) {
                s = TerrainPlanSurface.read(in, seed, FederalTerrainPlan.VERSION, FederalTerrainPlan.GRID_N, FederalTerrainPlan.GRID_ORIGIN);
            }
            if (s == null) { System.out.println("stale " + f); continue; }
            run(Long.toUnsignedString(seed), s);
        }
    }

    static int H(TerrainPlanSurface s, int x, int z) { return (int) Math.floor(s.heightAt(x + 0.5, z + 0.5)); }

    /** The planned ground as cells: natural up to each column's top (R2f: SmoothRules#stairs reads them). */
    static SmoothRules.Cells cells(TerrainPlanSurface s) {
        return new SmoothRules.Cells() {
            @Override public int cell(int x, int y, int z) { return y < H(s, x, z) ? SmoothRules.NATURAL : SmoothRules.AIR; }

            @Override public int surface(int x, int z) { return H(s, x, z) - 1; }
        };
    }

    /** The corner point (px, pz)'s height (mode B, SmoothRules#corner for natural columns), NaN when pinned. */
    static float corner(TerrainPlanSurface s, int px, int pz) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, sum = 0;
        for (int dz = -1; dz <= 0; dz++) for (int dx = -1; dx <= 0; dx++) {
            int h = H(s, px + dx, pz + dz);
            lo = Math.min(lo, h);
            hi = Math.max(hi, h);
            sum += h;
        }
        if (hi - lo > 2) return Float.NaN;
        if (hi - lo == 2) return lo + 1;
        float v = SmoothRules.stairs(cells(s), px, pz);
        return v == SmoothRules.NONE ? sum * 0.25F : Math.round(Math.max(hi - 1, Math.min(lo + 1, v)) * 4) / 4F;
    }

    /** A block's drawn corners nw, ne, sw, se (absolute). */
    static float[] corners(TerrainPlanSurface s, int x, int z) {
        int h = H(s, x, z);
        float[] c = new float[4];
        int[][] at = {{0, 0}, {1, 0}, {0, 1}, {1, 1}};
        for (int k = 0; k < 4; k++) {
            float v = corner(s, x + at[k][0], z + at[k][1]);
            c[k] = Float.isNaN(v) ? h : v;
        }
        return c;
    }

    static void run(String seed, TerrainPlanSurface s) {
        long blocks = 0, sloped = 0, planar = 0, curvedEdges = 0, edges = 0;
        long quadsNow = 0, quads25 = 0, quads50 = 0, quads25m = 0;
        double[] dev = new double[2_000_000];
        int nd = 0;
        double maxDev = 0;
        double[] out = new double[25];
        for (int gx = -3; gx <= 3; gx++)
            for (int gz = -3; gz <= 3; gz++)
                for (int x = gx * 256; x < gx * 256 + 16; x++)
                    for (int z = gz * 256; z < gz * 256 + 16; z++) {
                        blocks++;
                        int h = H(s, x, z), y = h - 1;
                        float[] c = corners(s, x, z);
                        boolean flat = true;
                        for (float v : c) if (v != h) flat = false;
                        quadsNow++;
                        if (flat) { quads25++; quads50++; quads25m++; continue; }
                        sloped++;
                        float[] ring = new float[16];
                        for (int j = 0; j < 4; j++) for (int i = 0; i < 4; i++) ring[j * 4 + i] = corner(s, x + i - 1, z + j - 1) - y;
                        ring[5] = c[0] - y;
                        ring[6] = c[1] - y;
                        ring[9] = c[2] - y;
                        ring[10] = c[3] - y;
                        // edges shared with the neighbour (the same corners at both ends) curve; others straight
                        double[][] fixed = new double[4][];
                        int[][] nb = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
                        int[][] mine = {{0, 1}, {2, 3}, {0, 2}, {1, 3}}, theirs = {{2, 3}, {0, 1}, {1, 3}, {0, 2}};
                        for (int e = 0; e < 4; e++) {
                            edges++;
                            float[] n = corners(s, x + nb[e][0], z + nb[e][1]);
                            float a = c[mine[e][0]], b = c[mine[e][1]];
                            if (n[theirs[e][0]] == a && n[theirs[e][1]] == b) { curvedEdges++; continue; }
                            fixed[e] = new double[5];
                            for (int t = 0; t < 5; t++) fixed[e][t] = (a + (b - a) * t / 4.0) - y;
                        }
                        SmoothField.vertices(ring, fixed, null, out);
                        float[] rel = {c[0] - y, c[1] - y, c[2] - y, c[3] - y};
                        double bd = 0;
                        for (int j = 0; j < 5; j++) for (int i = 0; i < 5; i++) {
                            double d = Math.abs(out[j * 5 + i] - SmoothRules.surface(rel, i / 4.0, j / 4.0));
                            bd = Math.max(bd, d);
                            if (nd < dev.length) dev[nd++] = d;
                        }
                        maxDev = Math.max(maxDev, bd);
                        boolean p = SmoothField.planar(out, 2e-3);
                        if (p) { planar++; quads25++; quads50++; quads25m++; continue; }
                        quads25 += 16;
                        quads50 += 4;
                        // 2 x 2 cells merged where their nine vertices lie on one plane within 1 cm
                        int m = 0;
                        for (int bj = 0; bj < 2; bj++) for (int bi = 0; bi < 2; bi++) {
                            double[] sub = new double[25];
                            boolean ok = true;
                            double a0 = out[(bj * 2) * 5 + bi * 2], gxx = (out[(bj * 2) * 5 + bi * 2 + 2] - a0) / 2, gzz = (out[(bj * 2 + 2) * 5 + bi * 2] - a0) / 2;
                            for (int jj = 0; jj <= 2; jj++) for (int ii = 0; ii <= 2; ii++)
                                if (Math.abs(out[(bj * 2 + jj) * 5 + bi * 2 + ii] - (a0 + gxx * ii + gzz * jj)) > 0.01) ok = false;
                            m += ok ? 1 : 4;
                        }
                        quads25m += m;
                    }
        double[] d = Arrays.copyOf(dev, nd);
        Arrays.sort(d);
        System.out.printf(Locale.ROOT, "== seed %s: %d blocks (49 chunks), sloped %.1f %%, of them planar %.1f %%; edges curved %.1f %%%n",
                seed, blocks, 100.0 * sloped / blocks, sloped == 0 ? 0 : 100.0 * planar / sloped, 100.0 * curvedEdges / Math.max(1, edges));
        System.out.printf(Locale.ROOT, "  off today's two triangles (m): mean %.3f, p95 %.3f, p99 %.3f, max %.3f%n",
                mean(d), d.length == 0 ? 0 : d[(int) (d.length * 0.95)], d.length == 0 ? 0 : d[(int) (d.length * 0.99)], maxDev);
        System.out.printf(Locale.ROOT, "  top quads: today %d; 0.25 m %d (x%.2f); 0.5 m %d (x%.2f); 0.25 m with 2x2 plane merge %d (x%.2f)%n",
                quadsNow, quads25, (double) quads25 / quadsNow, quads50, (double) quads50 / quadsNow, quads25m, (double) quads25m / quadsNow);
    }

    static double mean(double[] d) {
        double t = 0;
        for (double v : d) t += v;
        return d.length == 0 ? 0 : t / d.length;
    }
}
