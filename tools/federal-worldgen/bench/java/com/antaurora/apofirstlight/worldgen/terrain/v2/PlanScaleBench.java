package com.antaurora.apofirstlight.worldgen.terrain.v2;

import com.antaurora.apofirstlight.worldgen.federal.FederalMap;
import com.antaurora.apofirstlight.worldgen.federal.FederalStateId;
import com.antaurora.apofirstlight.worldgen.federal.FederalTemplate;
import com.antaurora.apofirstlight.worldgen.federal.NationalRoadPlan;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeography;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;

/**
 * C1 national-scale bench (tools/federal-worldgen/bench.sh; docs/worldgen/federal_six_state_c1.md). Runs the Terrain V2
 * planner (a copy whose grid size and mask are parameterised by bench.sh, otherwise byte-identical) on the r1 map or the
 * federal map and measures build time, heap, the runtime surface, drainage, heights, islands and the open-ocean ring.
 *
 * <pre>
 *   size SEED SCALE...        the r1 ocean margin and the grid each federal scale needs
 *   run SEED OUT LABEL [roads] one plan build with measurements (+ the road plan routed on the planned heights)
 *   fingerprint SEED TIMES    TerrainPlanFingerprint digests of TIMES independent builds (determinism, patch parity)
 * </pre>
 */
public final class PlanScaleBench {
    public static void main(String[] a) throws Exception {
        long seed = Long.parseLong(a[1]);
        switch (a[0]) {
            case "size" -> size(seed, Arrays.copyOfRange(a, 2, a.length));
            case "run" -> run(seed, new File(a[2]), a[3], a.length > 4 && a[4].equals("roads"));
            case "fingerprint" -> fingerprint(seed, Integer.parseInt(a[2]));
            default -> throw new IllegalArgumentException(a[0]);
        }
    }

    // ------------------------------------------------------------------ size
    static void size(long seed, String[] scales) {
        MacroGeography geo = MacroGeography.forSeed(seed);
        double half = 11264, maxAbs = 0, mx0 = 1e9, mx1 = -1e9, mz0 = 1e9, mz1 = -1e9;
        long land = 0, mainland = 0;
        for (double z = -half + 8; z < half; z += 16) for (double x = -half + 8; x < half; x += 16) {
            var s = geo.sample((int) x, (int) z);
            if (!s.isLand()) continue;
            land++;
            maxAbs = Math.max(maxAbs, Math.max(Math.abs(x), Math.abs(z)));
            if (s.landmassId() == 0) {
                mainland++;
                mx0 = Math.min(mx0, x); mx1 = Math.max(mx1, x); mz0 = Math.min(mz0, z); mz1 = Math.max(mz1, z);
            }
        }
        double margin = half - maxAbs;
        System.out.printf(Locale.ROOT, "r1: N=1408 domain 22.5 km; land %.1f km2 (mainland %.1f km2, bbox %.1f x %.1f km); farthest land %.0f m; ocean margin %.0f m%n",
                land * 256 / 1e6, mainland * 256 / 1e6, (mx1 - mx0) / 1000, (mz1 - mz0) / 1000, maxAbs, margin);
        for (String sc : scales) {
            double scale = Double.parseDouble(sc);
            FederalMap m = FederalMap.of(seed, scale);
            FederalMap.Bounds b = m.landBounds();
            double far = Math.max(Math.max(-b.minX(), b.maxX()), Math.max(-b.minZ(), b.maxZ()));
            int n = (int) Math.ceil(2 * (far + margin) / 16 / 32) * 32;
            System.out.printf(Locale.ROOT, "federal scale %.2f: land bbox %.1f x %.1f km, farthest %.0f m -> N=%d (%.1f km, %.2f M cells, %.2fx r1)%n",
                    scale, (b.maxX() - b.minX()) / 1000, (b.maxZ() - b.minZ()) / 1000, far, n, n * 16 / 1000.0, n * (double) n / 1e6, n * (double) n / (1408.0 * 1408));
            System.out.println("N " + sc + " " + n);
        }
        FederalMap one = FederalMap.of(seed, 1.0);
        FederalMap.Bounds b = one.landBounds();
        double far = Math.max(Math.max(-b.minX(), b.maxX()), Math.max(-b.minZ(), b.maxZ()));
        System.out.printf(Locale.ROOT, "fits N=1408 with the r1 margin: federal scale <= %.2f (mainland about %.1f x %.1f km)%n",
                (half - margin) / far, 20 * (half - margin) / far, 16 * (half - margin) / far);
    }

    // ------------------------------------------------------------------ run
    static void run(long seed, File out, String label, boolean roads) throws Exception {
        out.mkdirs();
        boolean federal = BenchMask.MODE.equals("federal");
        int N = TerrainPlanV2.N, n = N * N;
        if (N != Integer.getInteger("afl.planN", 1408)) throw new IllegalStateException("the planner on the class path is not the bench's patched copy");
        Sampler sampler = new Sampler();
        gc();
        long base = used();
        sampler.start();
        long t0 = System.nanoTime();
        TerrainPlanV2 plan = new TerrainPlanV2(seed);
        long tPlan = System.nanoTime();
        TerrainPlanSurface s = plan.toSurface();
        long tAll = System.nanoTime();
        sampler.running = false;
        sampler.join();
        PrintStream o = new PrintStream(new FileOutputStream(new File(out, "bench_" + label + ".txt")), true, StandardCharsets.UTF_8);
        Tee p = new Tee(o);
        p.f("label %s  mask %s  scale %.2f  seed %d  N=%d (%.1f km, %.2f M cells)  cores %d  max heap %d MB%n", label, BenchMask.MODE,
                BenchMask.SCALE, seed, N, N * 16 / 1000.0, n / 1e6, Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory() >> 20);
        p.f("time: plan %.1f s + surface (rivers, ecology) %.1f s = %.1f s%n", (tPlan - t0) / 1e9, (tAll - tPlan) / 1e9, (tAll - t0) / 1e9);
        p.f("planner report: %s%n", fmt(plan.report));
        // land, landmasses, ocean ring
        long[] mass = new long[8];
        long landCells = 0, edgeLand = 0;
        double nearestEdge = Double.MAX_VALUE;
        float[] hs = new float[n];
        int hn = 0;
        long over100 = 0, over130 = 0, steep = 0;
        for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) {
            int i = r * N + c;
            if (plan.water[i] != 0) continue;
            landCells++;
            if (plan.landmass[i] >= 0 && plan.landmass[i] < mass.length) mass[plan.landmass[i]]++;
            double de = 16 * Math.min(Math.min(c, r), Math.min(N - 1 - c, N - 1 - r));
            nearestEdge = Math.min(nearestEdge, de);
            if (de < 1000) edgeLand++;
            hs[hn++] = plan.h[i];
            if (plan.h[i] > 100) over100++;
            if (plan.h[i] > 130) over130++;
            if (plan.slopeAt(i) > 0.30) steep++;
        }
        float[] hl = Arrays.copyOf(hs, hn);
        Arrays.sort(hl);
        p.f("land %.1f km2: mainland %.1f km2, islands %.2f / %.2f / %.2f km2; land within 1 km of the grid edge: %d cells; nearest land to edge %.0f m%n",
                landCells * 256 / 1e6, mass[0] * 256 / 1e6, mass[1] * 256 / 1e6, mass[2] * 256 / 1e6, mass[3] * 256 / 1e6, edgeLand, nearestEdge);
        p.f("land heights Y: p10 %.1f  p50 %.1f  p90 %.1f  p99 %.1f  max %.1f;  > Y100 %.1f%%, > Y130 %.2f%%;  slope > 30%%: %.2f%%%n",
                q(hl, .1), q(hl, .5), q(hl, .9), q(hl, .99), hl[hl.length - 1], 100.0 * over100 / hn, 100.0 * over130 / hn, 100.0 * steep / hn);
        if (federal) {
            FederalMap m = FederalMap.of(seed, BenchMask.SCALE);
            double[] sum = new double[6], max = new double[6];
            long[] cnt = new long[6];
            for (int r = 0; r < N; r += 2) for (int c = 0; c < N; c += 2) {
                int i = r * N + c;
                if (plan.water[i] != 0) continue;
                FederalStateId st = m.stateAt(TerrainPlanV2.cx(c), TerrainPlanV2.cx(r));
                if (st == null) continue;
                int k = st.ordinal();
                sum[k] += plan.h[i]; cnt[k]++; max[k] = Math.max(max[k], plan.h[i]);
            }
            for (FederalStateId st : FederalStateId.values())
                p.f("  %s %-30s mean Y %.1f  max Y %.1f%n", st.key(), FederalTemplate.theme(st), sum[st.ordinal()] / Math.max(1, cnt[st.ordinal()]), max[st.ordinal()]);
            p.f("  (the r1 planner places its own provinces from the mainland's axes; the federal provinces are not wired yet: C2)%n");
        }
        // memory
        long cacheBytes = cacheSize(s);
        long surfaceArrays = (long) n * (4 + 1 + 4 + 1 + 1 + 1 + 1);
        plan = null;
        gc();
        long retained = used() - base;
        p.f("heap: peak used during build %.0f MB above baseline (sampled every 10 ms); retained after build (surface + caches) %.0f MB;"
                + " surface grid arrays %.0f MB; plan cache file %.1f MB%n", (sampler.peak - base) / 1048576.0, retained / 1048576.0,
                surfaceArrays / 1048576.0, cacheBytes / 1048576.0);
        // fingerprint cost (the lock computes it at every world load)
        long tf = System.nanoTime();
        TerrainPlanFingerprint.Result fp = TerrainPlanFingerprint.of(s);
        p.f("fingerprint %s / %s in %.2f s%n", fp.planDigest(), fp.queryDigest(), (System.nanoTime() - tf) / 1e9);
        NationalRoadPlan rp = null;
        if (federal && roads) {
            FederalMap m = FederalMap.of(seed, BenchMask.SCALE);
            RiverNetwork.Column col = new RiverNetwork.Column();
            long tr = System.nanoTime();
            rp = NationalRoadPlan.build(m, new NationalRoadPlan.Terrain() {
                public double height(double x, double z) { return s.baseHeightAt(x, z); }

                public boolean water(double x, double z) {
                    if (s.waterAt(x, z)) return true;
                    s.rivers.column((int) Math.floor(x), (int) Math.floor(z), col);
                    return col.kind == RiverNetwork.Column.RIVER;
                }
            });
            p.f("road plan routed on the planned heights in %.1f s%n", (System.nanoTime() - tr) / 1e9);
            rp.report(o);
            rp.report(System.out);
            try (OutputStream js = new FileOutputStream(new File(out, "national_roads_" + label + ".json"))) {
                js.write(rp.toJson().getBytes(StandardCharsets.UTF_8));
            }
        }
        render(s, federal ? FederalMap.of(seed, BenchMask.SCALE) : null, rp, new File(out, "terrain_" + label + ".png"));
        o.close();
    }

    static long cacheSize(TerrainPlanSurface s) throws Exception {
        long[] count = {0};
        OutputStream counter = new OutputStream() {
            public void write(int b) { count[0]++; }

            public void write(byte[] b, int off, int len) { count[0] += len; }
        };
        try (DataOutputStream d = new DataOutputStream(new DeflaterOutputStream(counter))) { s.write(d); }
        return count[0];
    }

    // ------------------------------------------------------------------ fingerprint
    static void fingerprint(long seed, int times) {
        String first = null;
        for (int k = 0; k < times; k++) {
            long t0 = System.nanoTime();
            TerrainPlanSurface s = new TerrainPlanV2(seed).toSurface();
            TerrainPlanFingerprint.Result r = TerrainPlanFingerprint.of(s);
            String d = r.planDigest() + "/" + r.queryDigest();
            System.out.printf(Locale.ROOT, "build %d: %s  (%.1f s)%s%n", k + 1, d, (System.nanoTime() - t0) / 1e9,
                    first == null ? "" : d.equals(first) ? "  identical" : "  DIFFERENT");
            if (first == null) first = d;
        }
    }

    // ------------------------------------------------------------------ render
    static void render(TerrainPlanSurface s, FederalMap m, NationalRoadPlan rp, File file) throws Exception {
        int N = TerrainPlanV2.N, px = N / 2;
        double mpp = 32, o = TerrainPlanV2.ORIGIN;
        BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < px; y++) for (int x = 0; x < px; x++) {
            double wx = o + (x + 0.5) * mpp, wz = o + (y + 0.5) * mpp;
            double h = s.baseHeightAt(wx, wz);
            Color c;
            if (s.waterAt(wx, wz)) c = lerp(new Color(0x9CC3DA), new Color(0x1F3F66), Math.min(1, Math.max(0, (63 - h) / 45)));
            else {
                double hx = s.baseHeightAt(wx + 16, wz) - s.baseHeightAt(wx - 16, wz), hz = s.baseHeightAt(wx, wz + 16) - s.baseHeightAt(wx, wz - 16);
                double shade = Math.max(0.55, Math.min(1.25, 1 + 0.035 * (hx + hz)));
                c = hyps(h);
                c = new Color(clamp(c.getRed() * shade), clamp(c.getGreen() * shade), clamp(c.getBlue() * shade));
            }
            img.setRGB(x, y, c.getRGB());
        }
        RiverNetwork.Column col = new RiverNetwork.Column();
        for (int y = 0; y < px; y++) for (int x = 0; x < px; x++) {
            double wx = o + (x + 0.5) * mpp, wz = o + (y + 0.5) * mpp;
            s.rivers.column((int) wx, (int) wz, col);
            if (col.kind == RiverNetwork.Column.RIVER || (col.edge < 12 && col.edge > -100)) img.setRGB(x, y, 0x3D6FA8);
        }
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (m != null) {
            for (int y = 1; y < px; y++) for (int x = 1; x < px; x++) {
                double wx = o + (x + 0.5) * mpp, wz = o + (y + 0.5) * mpp;
                if (!m.isLand(wx, wz)) continue;
                FederalStateId a = m.governingStateAt(wx, wz);
                if (a != m.governingStateAt(wx - mpp, wz) || a != m.governingStateAt(wx, wz - mpp)) img.setRGB(x, y, 0x202020);
            }
        }
        if (rp != null) {
            for (NationalRoadPlan.Corridor c : rp.corridors()) {
                Path2D.Double path = new Path2D.Double();
                for (int k = 0; k < c.xs().length; k++) {
                    double x = (c.xs()[k] - o) / mpp, y = (c.zs()[k] - o) / mpp;
                    if (k == 0) path.moveTo(x, y); else path.lineTo(x, y);
                }
                boolean inter = c.roadClass() == FederalTemplate.RoadClass.INTERSTATE;
                g.setStroke(new BasicStroke(inter ? 3f : 1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(inter ? new Color(0xC0392B) : c.roadClass() == FederalTemplate.RoadClass.ISLAND_BRIDGE ? new Color(0x6C3483) : new Color(0x303030));
                g.draw(path);
            }
            g.setColor(new Color(0x101010));
            for (NationalRoadPlan.Node nd : rp.nodes()) {
                int x = (int) ((nd.x() - o) / mpp), y = (int) ((nd.z() - o) / mpp);
                switch (nd.kind()) {
                    case BRIDGE -> { g.setColor(new Color(0x6C3483)); g.fillRect(x - 3, y - 3, 6, 6); }
                    case PASS -> { g.setColor(new Color(0x101010)); g.fillPolygon(new int[]{x - 5, x + 5, x}, new int[]{y + 4, y + 4, y - 6}, 3); }
                    default -> { }
                }
            }
        }
        if (m != null) {
            g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
            for (FederalMap.City c : m.cities()) {
                int x = (int) ((c.x() - o) / mpp), y = (int) ((c.z() - o) / mpp), r = c.rank() == 1 ? 5 : c.rank() == 2 ? 4 : 3;
                g.setColor(Color.WHITE);
                g.fillOval(x - r - 1, y - r - 1, 2 * r + 2, 2 * r + 2);
                g.setColor(new Color(0x101010));
                g.fillOval(x - r, y - r, 2 * r, 2 * r);
                g.drawString(c.id(), x + r + 2, y + 4);
            }
        }
        g.setColor(Color.WHITE);
        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        g.drawString(String.format(Locale.ROOT, "N=%d  %.1f km  32 m/px  %s", N, N * 16 / 1000.0, m == null ? "r1 MacroGeography" : "federal scale " + m.scale), 8, px - 10);
        g.dispose();
        ImageIO.write(img, "png", file);
    }

    static Color hyps(double h) {
        double[][] stops = {{60, 0x8DB07A}, {66, 0x9DBF7A}, {78, 0xC9C88E}, {95, 0xC2A878}, {115, 0xA88B6A}, {140, 0x8E7B6B}, {170, 0xD8D4CF}};
        if (h <= stops[0][0]) return new Color((int) stops[0][1]);
        for (int k = 0; k + 1 < stops.length; k++)
            if (h <= stops[k + 1][0]) return lerp(new Color((int) stops[k][1]), new Color((int) stops[k + 1][1]), (h - stops[k][0]) / (stops[k + 1][0] - stops[k][0]));
        return new Color((int) stops[stops.length - 1][1]);
    }

    static Color lerp(Color a, Color b, double t) {
        return new Color(clamp(a.getRed() + (b.getRed() - a.getRed()) * t), clamp(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                clamp(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    static int clamp(double v) { return (int) Math.max(0, Math.min(255, Math.round(v))); }

    // ------------------------------------------------------------------ helpers
    static double q(float[] sorted, double f) { return sorted[(int) Math.min(sorted.length - 1, Math.floor(f * sorted.length))]; }

    static long used() { Runtime r = Runtime.getRuntime(); return r.totalMemory() - r.freeMemory(); }

    static void gc() throws InterruptedException {
        for (int k = 0; k < 4; k++) { System.gc(); Thread.sleep(80); }
    }

    static String fmt(Map<String, Object> report) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : report.entrySet()) {
            Object v = e.getValue();
            sb.append(e.getKey()).append('=').append(v instanceof double[] d ? Arrays.toString(Arrays.stream(d).mapToLong(Math::round).toArray()) : v).append("  ");
        }
        return sb.toString().trim();
    }

    static final class Sampler extends Thread {
        volatile boolean running = true;
        volatile long peak;

        Sampler() { setDaemon(true); }

        public void run() {
            while (running) {
                peak = Math.max(peak, used());
                try { Thread.sleep(10); } catch (InterruptedException e) { return; }
            }
        }
    }

    record Tee(PrintStream file) {
        void f(String format, Object... args) {
            String s = String.format(Locale.ROOT, format, args);
            System.out.print(s);
            file.print(s);
        }
    }
}
