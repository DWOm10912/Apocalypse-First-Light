package com.antaurora.apofirstlight.worldgen.terrain.v2;

import com.antaurora.apofirstlight.worldgen.federal.FederalMap;
import com.antaurora.apofirstlight.worldgen.federal.FederalStateId;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * C2 offline preview of the federal experimental plan (tools/federal-worldgen/run.sh c2; docs/worldgen/federal_six_state_c2.md).
 * Pure Java, the runtime planner classes themselves (no copy). Everything goes under build/federal-worldgen/c2/.
 *
 * <pre>
 *   parity SEED          r1 TerrainPlanV2 digests (must stay 8d712b1dd53f3bd3/90287ece05fe7a5c for the test seed)
 *   plan SEED OUT [twice] one federal build: time, heap, cache size, digests, per-state statistics, the state-border
 *                        ecology test, national and per-state maps (twice: a second build must give the same digests)
 *   lakes SEED           the runtime lake fill (RiverCarver) on the planned surface round every lake: water area, dry
 *                        water faces (water beside air over lower ground) and steps down to lower water (outlets)</pre>
 * <pre>
 * </pre>
 */
public final class FederalPlanPreview {
    public static void main(String[] a) throws Exception {
        long seed = Long.parseLong(a[1]);
        switch (a[0]) {
            case "parity" -> parity(seed);
            case "r1eco" -> r1eco(seed);
            case "plan" -> plan(seed, new File(a[2]), a.length > 3 && a[3].equals("twice"));
            case "lakes" -> lakeAudit(seed);
            default -> throw new IllegalArgumentException(a[0]);
        }
    }

    static void parity(long seed) {
        long t0 = System.nanoTime();
        TerrainPlanFingerprint.Result r = TerrainPlanFingerprint.of(new TerrainPlanV2(seed).toSurface());
        System.out.printf(Locale.ROOT, "r1 %s/%s (%.1f s)%n", r.planDigest(), r.queryDigest(), (System.nanoTime() - t0) / 1e9);
    }

    /** The r1 world's land biome shares (the reference for S1, which reuses Terrain V2's forest). */
    static void r1eco(long seed) {
        TerrainPlanSurface s = new TerrainPlanV2(seed).toSurface();
        long land = 0;
        long[] b = new long[FederalEcologyBiomes.COUNT];
        double o = s.gridOrigin, e = s.gridOrigin + s.gridN * TerrainPlanSurface.CELL;
        for (double z = o + STEP / 2; z < e; z += STEP) for (double x = o + STEP / 2; x < e; x += STEP) {
            int k = EcologyBiomes.biomeAt(s, x, z);
            if (k == EcologyBiomes.SEA) continue;
            land++;
            b[k]++;
        }
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < b.length; k++) if (b[k] > 0) sb.append(String.format(Locale.ROOT, "%s %.1f%%  ", EcologyBiomes.id(k), pct(b[k], land)));
        System.out.printf(Locale.ROOT, "r1 land %.0f km2  forest %.1f%%%n    %s%n", land * STEP * STEP / 1e6, pct(forest(b), land), sb);
    }

    /**
     * The lake fill audit: every column within two cells of a lake cell, its generated top taken as the highest block
     * under the planned surface (ceil(h) - 1; the game's 4 x 4 density interpolation is not modelled), run through
     * RiverCarver.plan; then every pair of neighbouring columns is checked for water standing beside air.
     */
    static void lakeAudit(long seed) {
        long t0 = System.nanoTime();
        TerrainPlanSurface s = new FederalTerrainPlan(seed).toSurface();
        System.out.printf(Locale.ROOT, "federal surface %.1f s%n", (System.nanoTime() - t0) / 1e9);
        int n = s.gridN;
        boolean[] near = new boolean[n * n];
        int lakeCells = 0;
        for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) {
            if (s.water[r * n + c] != TerrainPlanSurface.LAKE) continue;
            lakeCells++;
            for (int dr = -2; dr <= 2; dr++) for (int dc = -2; dc <= 2; dc++)
                near[s.clampCell(r + dr) * n + s.clampCell(c + dc)] = true;
        }
        int[] cells = java.util.stream.IntStream.range(0, n * n).filter(i -> near[i]).toArray();
        java.util.concurrent.atomic.LongAdder lakeCols = new java.util.concurrent.atomic.LongAdder(), riverCols = new java.util.concurrent.atomic.LongAdder(),
                dry = new java.util.concurrent.atomic.LongAdder(), dry2 = new java.util.concurrent.atomic.LongAdder(),
                steps = new java.util.concurrent.atomic.LongAdder(), bankIn = new java.util.concurrent.atomic.LongAdder();
        List<String> samples = java.util.Collections.synchronizedList(new ArrayList<>());
        int[] worst = new int[1];
        java.util.Arrays.stream(cells).parallel().forEach(i -> {
            int c = i % n, r = i / n;
            int x0 = (int) Math.floor(s.gridOrigin + c * TerrainPlanSurface.CELL), z0 = (int) Math.floor(s.gridOrigin + r * TerrainPlanSurface.CELL);
            int m = (int) TerrainPlanSurface.CELL + 2;
            int[] ground = new int[m * m], water = new int[m * m], kind = new int[m * m];
            RiverNetwork.Column col = new RiverNetwork.Column();
            RiverCarver.Edit e = new RiverCarver.Edit();
            double[] share = new double[1];
            for (int dz = 0; dz < m; dz++) for (int dx = 0; dx < m; dx++) {
                int x = x0 + dx - 1, z = z0 + dz - 1;
                int top = (int) Math.ceil(s.heightAt(x, z)) - 1;
                RiverCarver.plan(s, x, z, top, col, e);
                int k = dz * m + dx;
                ground[k] = e.ground;
                water[k] = e.water;
                kind[k] = e.kind;
                boolean inner = dx >= 1 && dz >= 1 && dx <= m - 2 && dz <= m - 2;
                if (!inner) continue;
                if (e.kind == RiverCarver.LAKE) lakeCols.increment();
                if (e.kind == RiverCarver.RIVER) riverCols.increment();
                if (e.kind == RiverCarver.BANK) {
                    int lt = s.lakeTopAt(x + 0.5, z + 0.5, share);
                    if (lt != Integer.MIN_VALUE && share[0] >= 0.5) bankIn.increment();
                }
            }
            // each pair once: the inner column and its east / south neighbour (the ring covers the cell's own border)
            for (int dz = 1; dz <= m - 2; dz++) for (int dx = 1; dx <= m - 2; dx++) {
                for (int d = 0; d < 2; d++) {
                    int a = dz * m + dx, b = d == 0 ? a + 1 : a + m;
                    for (int q = 0; q < 2; q++) {
                        int hi = q == 0 ? a : b, lo = q == 0 ? b : a;
                        if (water[hi] == Integer.MIN_VALUE) continue;
                        if (kind[hi] != RiverCarver.LAKE && kind[lo] != RiverCarver.LAKE) continue;
                        if (ground[lo] >= water[hi]) continue;
                        if (water[lo] != Integer.MIN_VALUE && water[lo] >= water[hi]) continue;
                        if (water[lo] != Integer.MIN_VALUE) { steps.increment(); continue; }
                        int drop = water[hi] - ground[lo];
                        dry.increment();
                        if (drop >= 2) dry2.increment();
                        synchronized (worst) {
                            if (drop > worst[0]) worst[0] = drop;
                        }
                        if (samples.size() < 30) {
                            int hx = x0 + (hi % m) - 1, hz = z0 + (hi / m) - 1, lx = x0 + (lo % m) - 1, lz = z0 + (lo / m) - 1;
                            samples.add(String.format(Locale.ROOT, "water %d (kind %d) at %d %d beside ground %d (kind %d) at %d %d",
                                    water[hi], kind[hi], hx, hz, ground[lo], kind[lo], lx, lz));
                        }
                    }
                }
            }
        });
        System.out.printf(Locale.ROOT, "lake cells %d (%.1f km2), audited cells %d%n", lakeCells, lakeCells * 256 / 1e6, cells.length);
        System.out.printf(Locale.ROOT, "lake water columns %d (%.2f km2), river columns near lakes %d, bank columns inside a lake (share >= 0.5) %d%n",
                lakeCols.sum(), lakeCols.sum() / 1e6, riverCols.sum(), bankIn.sum());
        System.out.printf(Locale.ROOT, "dry water faces %d (2+ blocks: %d, worst %d), steps down to lower water %d (%.1f s)%n",
                dry.sum(), dry2.sum(), worst[0], steps.sum(), (System.nanoTime() - t0) / 1e9);
        for (String l : samples) System.out.println("  " + l);
    }

    // ------------------------------------------------------------------ plan
    static void plan(long seed, File out, boolean twice) throws Exception {
        out.mkdirs();
        PrintStream log = new PrintStream(new java.io.FileOutputStream(new File(out, "c2_plan_stats.txt")), true, StandardCharsets.UTF_8);
        PrintStream both = new PrintStream(new java.io.OutputStream() {
            @Override public void write(int b) { System.out.write(b); log.write(b); }
            @Override public void write(byte[] b, int o, int l) { System.out.write(b, o, l); log.write(b, o, l); }
        }, true, StandardCharsets.UTF_8);
        resetPeaks();
        System.gc();
        long base = usedHeap();
        long t0 = System.nanoTime();
        FederalTerrainPlan p = new FederalTerrainPlan(seed);
        long t1 = System.nanoTime();
        TerrainPlanSurface s = p.toSurface();
        long t2 = System.nanoTime();
        long peak = peakHeap();
        both.printf(Locale.ROOT, "federal plan %s seed %d grid %d x %d (%.0f m) origin %.0f%n", FederalTerrainPlan.VERSION, seed,
                s.gridN, s.gridN, s.gridN * TerrainPlanSurface.CELL, s.gridOrigin);
        both.printf(Locale.ROOT, "build: planner %.1f s, surface + ecology %.1f s, peak heap %.0f MB (baseline %.0f MB)%n",
                (t1 - t0) / 1e9, (t2 - t1) / 1e9, peak / 1048576.0, base / 1048576.0);
        for (Map.Entry<String, Object> e : p.report.entrySet()) both.println("  report " + e.getKey() + " = " + e.getValue());
        p = null;
        System.gc();
        both.printf(Locale.ROOT, "runtime surface retained: %.0f MB of heap above the baseline%n", (usedHeap() - base) / 1048576.0);
        ByteArrayOutputStream bin = new ByteArrayOutputStream();
        try (DataOutputStream d = new DataOutputStream(new java.util.zip.DeflaterOutputStream(bin))) { s.write(d); }
        both.printf(Locale.ROOT, "plan cache (deflated, as TerrainPlanStore writes it): %.1f MB%n", bin.size() / 1048576.0);
        long tq = System.nanoTime();
        TerrainPlanFingerprint.Result fp = TerrainPlanFingerprint.of(s);
        both.printf(Locale.ROOT, "digests %s/%s (%.1f s)%n", fp.planDigest(), fp.queryDigest(), (System.nanoTime() - tq) / 1e9);
        if (twice) {
            TerrainPlanFingerprint.Result fp2 = TerrainPlanFingerprint.of(new FederalTerrainPlan(seed).toSurface());
            both.println("second build: " + fp2.planDigest() + "/" + fp2.queryDigest()
                    + (fp2.planDigest().equals(fp.planDigest()) && fp2.queryDigest().equals(fp.queryDigest()) ? "  identical" : "  DIFFERENT"));
        }
        FederalMap map = FederalMap.forSeed(seed);
        Stats st = stats(s, map, both);
        borders(s, map, both);
        speed(s, both);
        maps(s, map, st, out);
        both.println("maps: " + out);
        log.close();
    }

    // ------------------------------------------------------------------ statistics
    static final double STEP = 32;

    record Stats(double[] cx, double[] cz, long[] n) {}

    static Stats stats(TerrainPlanSurface s, FederalMap map, PrintStream out) {
        int S = 6, B = FederalEcologyBiomes.COUNT;
        long[] land = new long[S], lake = new long[S], steep = new long[S], high = new long[S];
        long[][] biome = new long[S][B];
        long[][] zoneN = new long[S][FederalEcology.NAMES.length];
        double[] openSum = new double[S], tSum = new double[S], mSum = new double[S], phys = new double[S * 6];
        double[] pw = new double[6];
        double[] sx = new double[S], sz = new double[S];
        List<List<Float>> hs = new ArrayList<>();
        for (int k = 0; k < S; k++) hs.add(new ArrayList<>());
        double o = s.gridOrigin, e = s.gridOrigin + s.gridN * TerrainPlanSurface.CELL;
        double[] share = new double[1];
        for (double z = o + STEP / 2; z < e; z += STEP) for (double x = o + STEP / 2; x < e; x += STEP) {
            double h = s.heightAt(x, z);
            int b = EcologyBiomes.biomeAt(s, x, z);
            if (b == EcologyBiomes.SEA) continue;
            FederalStateId id = map.stateAt(x, z);
            if (id == null) continue;
            int k = id.ordinal();
            land[k]++;
            sx[k] += x;
            sz[k] += z;
            biome[k][b]++;
            int zc = s.ecology.zone()[s.cellAt(x, z)];
            if (zc >= 0) zoneN[k][zc]++;
            tSum[k] += map.temperature(x, z);
            mSum[k] += map.moisture(x, z);
            map.physiography(x, z, pw);
            for (int q = 0; q < 6; q++) phys[k * 6 + q] += pw[q];
            openSum[k] += (s.bilinear(s.layers.open(), (x - s.gridOrigin) / TerrainPlanSurface.CELL - 0.5, (z - s.gridOrigin) / TerrainPlanSurface.CELL - 0.5) / 127.5 - 1);
            int top = s.lakeTopAt(x, z, share);
            if (top != Integer.MIN_VALUE && share[0] >= 0.5 && h < top + 0.5) lake[k]++;
            if (s.slopeAt(x, z) > 0.3) steep[k]++;
            if (h > 160) high[k]++;
            hs.get(k).add((float) h);
        }
        double km2 = STEP * STEP / 1e6;
        double[] cx = new double[S], cz = new double[S];
        out.println();
        out.println("per state (land sampled every 32 m; heights in Y; shares of the state's land)");
        for (int k = 0; k < S; k++) {
            if (land[k] == 0) continue;
            cx[k] = sx[k] / land[k];
            cz[k] = sz[k] / land[k];
            float[] h = new float[hs.get(k).size()];
            for (int i = 0; i < h.length; i++) h[i] = hs.get(k).get(i);
            Arrays.sort(h);
            out.printf(Locale.ROOT, "%s  land %.0f km2  centre %.0f %.0f  Y p10 %.0f p50 %.0f p90 %.0f p99 %.0f max %.0f  slope>0.3 %.1f%%  Y>160 %.1f%%  lake %.1f%%  forest %.1f%%%n",
                    FederalStateId.values()[k], land[k] * km2, cx[k], cz[k], q(h, 0.1), q(h, 0.5), q(h, 0.9), q(h, 0.99), h[h.length - 1],
                    pct(steep[k], land[k]), pct(high[k], land[k]), pct(lake[k], land[k]), pct(forest(biome[k]), land[k]));
            StringBuilder sb = new StringBuilder("    ");
            Integer[] order = new Integer[B];
            for (int b = 0; b < B; b++) order[b] = b;
            final int kk = k;
            Arrays.sort(order, (u, v) -> Long.compare(biome[kk][v], biome[kk][u]));
            for (int b : order) if (biome[k][b] > 0 && pct(biome[k][b], land[k]) >= 0.5)
                sb.append(String.format(Locale.ROOT, "%s %.1f%%  ", EcologyBiomes.id(b), pct(biome[k][b], land[k])));
            out.println(sb);
            StringBuilder zb = new StringBuilder(String.format(Locale.ROOT, "    climate t %.2f m %.2f  physiography", tSum[k] / land[k], mSum[k] / land[k]));
            for (int q = 0; q < 6; q++) zb.append(String.format(Locale.ROOT, " %s %.2f", com.antaurora.apofirstlight.worldgen.federal.FederalTemplate.Physiography.values()[q].name().toLowerCase(Locale.ROOT), phys[k * 6 + q] / land[k]));
            out.println(zb);
            zb = new StringBuilder(String.format(Locale.ROOT, "    zones (open %.2f): ", openSum[k] / land[k]));
            for (int zc = 0; zc < FederalEcology.NAMES.length; zc++) if (pct(zoneN[k][zc], land[k]) >= 0.5)
                zb.append(String.format(Locale.ROOT, "%s %.1f%%  ", FederalEcology.NAMES[zc], pct(zoneN[k][zc], land[k])));
            out.println(zb);
        }
        return new Stats(cx, cz, land);
    }

    static final int[] FOREST = {EcologyBiomes.WOODLAND, EcologyBiomes.SLOPE_FOREST, EcologyBiomes.RIDGE_FOREST,
            EcologyBiomes.HEMLOCK_HOLLOW, EcologyBiomes.RIPARIAN_FOREST, EcologyBiomes.COASTAL_PINE, EcologyBiomes.SWAMP_FOREST,
            FederalEcologyBiomes.MONTANE_CONIFER, FederalEcologyBiomes.LAKESHORE_FOREST};

    static long forest(long[] b) { long n = 0; for (int f : FOREST) n += b[f]; return n; }

    static double q(float[] h, double f) { return h[Math.min(h.length - 1, (int) (f * h.length))]; }

    static double pct(long a, long b) { return b == 0 ? 0 : 100.0 * a / b; }

    /**
     * The state-border ecology test: how often the biome changes across a 96 m step that crosses a state border, against
     * the same step 1 km to either side of it (the same landscape, no border). A ratio near 1: the border is not an
     * ecological edge. Height jumps the same way.
     */
    static void borders(TerrainPlanSurface s, FederalMap map, PrintStream out) {
        double D = 96, SHIFT = 1024, o = s.gridOrigin + SHIFT + D, e = s.gridOrigin + s.gridN * TerrainPlanSurface.CELL - SHIFT - D;
        long[] nB = new long[64], dB = new long[64], nC = new long[64], dC = new long[64];
        double[] hB = new double[64], hC = new double[64];
        for (double z = o; z < e; z += 64) for (double x = o; x < e; x += 64) {
            for (int dir = 0; dir < 2; dir++) {
                double dx = dir == 0 ? D : 0, dz = dir == 0 ? 0 : D;
                FederalStateId a = map.stateAt(x, z), b = map.stateAt(x + dx, z + dz);
                if (a == null || b == null || a == b) continue;
                int b0 = EcologyBiomes.biomeAt(s, x, z), b1 = EcologyBiomes.biomeAt(s, x + dx, z + dz);
                if (b0 == EcologyBiomes.SEA || b1 == EcologyBiomes.SEA) continue;
                int key = Math.min(a.ordinal(), b.ordinal()) * 8 + Math.max(a.ordinal(), b.ordinal());
                nB[key]++;
                if (b0 != b1) dB[key]++;
                hB[key] += Math.abs(s.heightAt(x, z) - s.heightAt(x + dx, z + dz));
                for (int sgn = -1; sgn <= 1; sgn += 2) {
                    double ox = x + sgn * (dir == 0 ? SHIFT : 0), oz = z + sgn * (dir == 0 ? 0 : SHIFT);
                    int c0 = EcologyBiomes.biomeAt(s, ox, oz), c1 = EcologyBiomes.biomeAt(s, ox + dx, oz + dz);
                    if (c0 == EcologyBiomes.SEA || c1 == EcologyBiomes.SEA) continue;
                    nC[key]++;
                    if (c0 != c1) dC[key]++;
                    hC[key] += Math.abs(s.heightAt(ox, oz) - s.heightAt(ox + dx, oz + dz));
                }
            }
        }
        out.println();
        out.println("state borders: biome change across a 96 m step on the border vs the same step 1 km to either side");
        long tb = 0, tdb = 0, tc = 0, tdc = 0;
        for (int key = 0; key < 64; key++) {
            if (nB[key] < 20) continue;
            tb += nB[key]; tdb += dB[key]; tc += nC[key]; tdc += dC[key];
            out.printf(Locale.ROOT, "  %s|%s  steps %d  change on border %.1f%%  beside %.1f%%  ratio %.2f   |dY| on %.1f beside %.1f%n",
                    FederalStateId.values()[key / 8], FederalStateId.values()[key % 8], nB[key], pct(dB[key], nB[key]), pct(dC[key], nC[key]),
                    pct(dB[key], nB[key]) / Math.max(0.1, pct(dC[key], nC[key])), hB[key] / nB[key], hC[key] / Math.max(1, nC[key]));
        }
        out.printf(Locale.ROOT, "  all borders: change on %.1f%% beside %.1f%% ratio %.2f%n", pct(tdb, tb), pct(tdc, tc),
                pct(tdb, tb) / Math.max(0.1, pct(tdc, tc)));
    }

    static void speed(TerrainPlanSurface s, PrintStream out) {
        java.util.Random r = new java.util.Random(7);
        int n = 400_000;
        double span = s.gridN * TerrainPlanSurface.CELL;
        double[] xs = new double[n], zs = new double[n];
        for (int i = 0; i < n; i++) { xs[i] = s.gridOrigin + r.nextDouble() * span; zs[i] = s.gridOrigin + r.nextDouble() * span; }
        double sink = 0;
        for (int i = 0; i < 50_000; i++) sink += s.heightAt(xs[i], zs[i]) + EcologyBiomes.biomeAt(s, xs[i], zs[i]);
        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) sink += s.heightAt(xs[i], zs[i]);
        long t1 = System.nanoTime();
        for (int i = 0; i < n; i++) sink += EcologyBiomes.biomeAt(s, xs[i], zs[i]);
        long t2 = System.nanoTime();
        double[] share = new double[1];
        for (int i = 0; i < n; i++) sink += s.lakeTopAt(xs[i], zs[i], share);
        long t3 = System.nanoTime();
        out.printf(Locale.ROOT, "%nquery cost (single thread, random columns): heightAt %.2f us, biomeAt %.2f us, lakeTopAt %.2f us (%s)%n",
                (t1 - t0) / 1e3 / n, (t2 - t1) / 1e3 / n, (t3 - t2) / 1e3 / n, sink == 1.2345 ? "" : "ok");
    }

    // ------------------------------------------------------------------ maps
    static final int LAKE_COLOUR = 0xFF3F78A8;

    static void maps(TerrainPlanSurface s, FederalMap map, Stats st, File out) throws Exception {
        double mpp = 32, o = s.gridOrigin;
        int px = (int) (s.gridN * TerrainPlanSurface.CELL / mpp);
        BufferedImage phys = TerrainPlanMaps.render(s, o, o, px, px, mpp);
        lakes(s, phys, o, o, mpp);
        BufferedImage eco = TerrainPlanMaps.renderEcology(s, o, o, px, px, mpp);
        for (BufferedImage img : List.of(phys, eco)) states(map, img, o, o, mpp, st);
        ImageIO.write(phys, "png", new File(out, "c2_national_physical.png"));
        ImageIO.write(eco, "png", new File(out, "c2_national_ecology.png"));
        ImageIO.write(legend(eco), "png", new File(out, "c2_national_ecology_legend.png"));
        // per state: a 4 km window at 4 m/px round the state's land centre (physical | ecology)
        for (int k = 0; k < 6; k++) {
            if (st.n()[k] == 0) continue;
            double w = 4096, mp = 4, x0 = Math.floor((st.cx()[k] - w / 2) / 16) * 16, z0 = Math.floor((st.cz()[k] - w / 2) / 16) * 16;
            int n = (int) (w / mp);
            BufferedImage a = TerrainPlanMaps.render(s, x0, z0, n, n, mp);
            lakes(s, a, x0, z0, mp);
            BufferedImage b = TerrainPlanMaps.renderEcology(s, x0, z0, n, n, mp);
            BufferedImage pair = new BufferedImage(2 * n + 8, n + 28, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = pair.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, pair.getWidth(), pair.getHeight());
            g.drawImage(a, 0, 28, null);
            g.drawImage(b, n + 8, 28, null);
            g.setColor(Color.BLACK);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(new Font("SansSerif", Font.BOLD, 15));
            g.drawString(String.format(Locale.ROOT, "%s  x %.0f..%.0f  z %.0f..%.0f  (4 m/px, north up)", FederalStateId.values()[k],
                    x0, x0 + w, z0, z0 + w), 6, 19);
            g.dispose();
            ImageIO.write(pair, "png", new File(out, "c2_state_" + FederalStateId.values()[k].key() + ".png"));
        }
    }

    static void lakes(TerrainPlanSurface s, BufferedImage img, double x0, double z0, double mpp) {
        double[] share = new double[1];
        for (int py = 0; py < img.getHeight(); py++) for (int px = 0; px < img.getWidth(); px++) {
            double x = x0 + (px + 0.5) * mpp, z = z0 + (py + 0.5) * mpp;
            int top = s.lakeTopAt(x, z, share);
            if (top != Integer.MIN_VALUE && share[0] >= 0.5 && s.heightAt(x, z) < top + 0.5) img.setRGB(px, py, LAKE_COLOUR);
        }
    }

    static void states(FederalMap map, BufferedImage img, double x0, double z0, double mpp, Stats st) {
        int w = img.getWidth(), h = img.getHeight();
        int[] id = new int[w * h];
        for (int py = 0; py < h; py++) for (int px = 0; px < w; px++) {
            FederalStateId s = map.stateAt(x0 + (px + 0.5) * mpp, z0 + (py + 0.5) * mpp);
            id[py * w + px] = s == null ? -1 : s.ordinal();
        }
        for (int py = 0; py < h - 1; py++) for (int px = 0; px < w - 1; px++) {
            int a = id[py * w + px];
            if (a < 0) continue;
            int r = id[py * w + px + 1], d = id[(py + 1) * w + px];
            if ((r >= 0 && r != a) || (d >= 0 && d != a)) img.setRGB(px, py, 0xFF202020);
        }
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        for (int k = 0; k < 6; k++) {
            if (st.n()[k] == 0) continue;
            int cx = (int) ((st.cx()[k] - x0) / mpp), cz = (int) ((st.cz()[k] - z0) / mpp);
            String t = FederalStateId.values()[k].name();
            g.setColor(new Color(255, 255, 255, 200));
            g.setStroke(new BasicStroke(1));
            g.fillRoundRect(cx - 18, cz - 18, 40, 26, 8, 8);
            g.setColor(Color.BLACK);
            g.drawString(t, cx - 13, cz + 2);
        }
        g.dispose();
    }

    static BufferedImage legend(BufferedImage eco) {
        int rows = FederalEcologyBiomes.COUNT, w = eco.getWidth() + 300;
        BufferedImage img = new BufferedImage(w, Math.max(eco.getHeight(), rows * 26 + 20), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.drawImage(eco, 0, 0, null);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font("SansSerif", Font.PLAIN, 15));
        for (int b = 0; b < rows; b++) {
            int y = 14 + b * 26;
            g.setColor(new Color(EcologyBiomes.colour(b)));
            g.fillRect(eco.getWidth() + 14, y, 22, 18);
            g.setColor(Color.BLACK);
            g.drawRect(eco.getWidth() + 14, y, 22, 18);
            g.drawString(EcologyBiomes.id(b), eco.getWidth() + 44, y + 14);
        }
        g.setColor(new Color(LAKE_COLOUR));
        g.dispose();
        return img;
    }

    // ------------------------------------------------------------------ heap
    static long usedHeap() { Runtime r = Runtime.getRuntime(); return r.totalMemory() - r.freeMemory(); }

    static void resetPeaks() { for (MemoryPoolMXBean m : ManagementFactory.getMemoryPoolMXBeans()) m.resetPeakUsage(); }

    static long peakHeap() {
        long n = 0;
        for (MemoryPoolMXBean m : ManagementFactory.getMemoryPoolMXBeans()) if (m.getType() == MemoryType.HEAP) n += m.getPeakUsage().getUsed();
        return n;
    }
}
