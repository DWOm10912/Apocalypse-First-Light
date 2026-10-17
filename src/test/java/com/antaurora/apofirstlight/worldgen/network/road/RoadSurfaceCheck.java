package com.antaurora.apofirstlight.worldgen.network.road;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Offline regression of the road ground query's pavement layer (RoadSurfaceQuery#pavement; docs/worldgen/
 * road_surface_query_v1.md): along every road of a built network, every 0.25 m of station on lines 0.5 m apart across
 * the paved band (lanes, lane lines, shoulders, both carriageways), with yHint the wheel's last height like a vehicle:
 * no gaps, the design's own pavement (not another road's), no height steps or normal jumps beyond the design's grade
 * breaks, the analytic normal against the surface's own finite differences, and the time per call. Pure Java: the
 * road network JSON only (the client's copy: built, written, read back). Argument: one cached network file; none:
 * every cached network the checkout's game directories hold (CACHES, one per seed and plan; the game writes one on a
 * federal world's first load), so the check runs in any checkout that has opened a federal world.
 */
public final class RoadSurfaceCheck {
    private RoadSurfaceCheck() {}

    static final double DS = 0.25, DU = 0.5;
    /** Thresholds: height second difference over one step (m), normal turn per step (deg), normal error (deg). */
    static final double KINK = 0.01, TURN = 1.0, NORMAL_ERR = 0.25;

    /** The game directories' road network caches (the dev client, the isolated C2 harness client). */
    static final String[] CACHES = {"run/afl_cache/road_network", "build/federal-c2-client/afl_cache/road_network"};

    public static void main(String[] args) throws Exception {
        List<Path> files = new ArrayList<>();
        if (args.length > 0) files.add(Path.of(args[0]));
        else {
            // one per file name (the same seed and plan cached in two game directories is the same network)
            TreeMap<String, Path> found = new TreeMap<>();
            for (String dir : CACHES) {
                Path d = Path.of(dir);
                if (!Files.isDirectory(d)) continue;
                try (Stream<Path> list = Files.list(d)) {
                    list.filter(p -> p.getFileName().toString().matches("afl_road_network_c3_v1_.*\\.json")).forEach(p -> found.putIfAbsent(p.getFileName().toString(), p));
                }
            }
            files.addAll(found.values());
        }
        if (files.isEmpty()) {
            System.out.println("ROAD SURFACE CHECK: no cached road network in " + String.join(", ", CACHES)
                    + " (open a federal world once, or pass -ProadNetwork=<file>)");
            System.exit(2);
        }
        boolean all = true;
        for (Path file : files) {
            System.out.println("== " + file);
            all &= check(file);
        }
        System.out.println(all ? "ROAD SURFACE CHECK PASS (" + files.size() + " networks)" : "ROAD SURFACE CHECK FAIL");
        if (!all) System.exit(1);
    }

    /** The check over one cached network; true: pass. */
    static boolean check(Path file) throws Exception {
        RoadNetworkBuild.Result built = RoadNetworkBuild.build(Files.readString(file));
        List<RoadDesign> roads = RoadNetworkJson.designs(built.json());
        RoadWorks works = new RoadWorks(roads, RoadWorks.Water.NONE);
        RoadSurfaceQuery.Out o = new RoadSurfaceQuery.Out(), q = new RoadSurfaceQuery.Out();
        long samples = 0, gaps = 0, other = 0, kinks = 0, turns = 0, normalBad = 0, normalChecks = 0, calls = 0, fastDiff = 0;
        RoadCurve.Scratch ks = new RoadCurve.Scratch();
        double maxStep = 0, maxKink = 0, maxTurn = 0, maxNormalErr = 0;
        String gapAt = "", otherAt = "", kinkAt = "", turnAt = "", normalAt = "";
        long t0 = System.nanoTime();
        for (RoadDesign d : roads) {
            CrossSection sec = d.section;
            // lines across the band: from the inner edge (or the far edge on an undivided road) out, both sides
            double lo = sec.divided ? sec.halfMedian + 0.05 : -sec.edge + 0.05, hi = sec.edge - 0.05;
            int lines = (int) Math.floor((hi - lo) / DU) + 1;
            for (int side : sec.divided ? new int[]{-1, 1} : new int[]{1}) {
                for (int li = 0; li <= lines; li++) {
                    double v = li == lines ? hi : lo + li * DU, u = side * v;
                    double prevH = Double.NaN, prev2H = Double.NaN, pnx = 0, pny = 1, pnz = 0;
                    double yHint = d.pavementY(0, u);
                    for (double s = 0; s <= d.length; s += DS) {
                        double[] p = d.point(s, u);
                        samples++;
                        calls++;
                        if (!RoadSurfaceQuery.pavement(works, p[0], p[1], yHint, o)) {
                            if (gaps++ == 0) gapAt = String.format(Locale.ROOT, "%s s=%.2f u=%.2f (%.1f, %.1f)", d.id, s, u, p[0], p[1]);
                            prevH = prev2H = Double.NaN;
                            continue;
                        }
                        double want = d.pavementY(s, u);
                        // the query's allocation-free paths give exactly the plain ones (worldgen keeps the plain ones)
                        double[] l = d.curve.local(p[0], p[1], d.section.edge + 0.5, 0);
                        boolean got = d.curve.local(p[0], p[1], d.section.edge + 0.5, 0, ks);
                        if (d.pavementYAt(s, u) != want || (l == null) == got || (l != null && (l[0] != ks.s || l[1] != ks.u))) fastDiff++;
                        if (Math.abs(o.height - want) > 0.005) {
                            if (other++ == 0) otherAt = String.format(Locale.ROOT, "%s s=%.2f u=%.2f: %.3f vs %.3f", d.id, s, u, o.height, want);
                            prevH = prev2H = Double.NaN;
                            yHint = o.height;
                            continue;
                        }
                        if (!Double.isNaN(prevH)) maxStep = Math.max(maxStep, Math.abs(o.height - prevH));
                        if (!Double.isNaN(prev2H)) {
                            double k = Math.abs(o.height - 2 * prevH + prev2H);
                            if (k > maxKink) { maxKink = k; kinkAt = String.format(Locale.ROOT, "%s s=%.2f u=%.2f", d.id, s, u); }
                            if (k > KINK) kinks++;
                        }
                        if (!Double.isNaN(prevH)) {
                            double turn = Math.toDegrees(Math.acos(Math.min(1, o.nx * pnx + o.ny * pny + o.nz * pnz)));
                            if (turn > maxTurn) { maxTurn = turn; turnAt = String.format(Locale.ROOT, "%s s=%.2f u=%.2f", d.id, s, u); }
                            if (turn > TURN) turns++;
                        }
                        // every 4 m: the normal against the surface's own differences in world x and z (1 cm)
                        if (((int) Math.round(s / DS)) % 16 == 0) {
                            double e = 0.01, hx0, hx1, hz0, hz1;
                            calls += 4;
                            if (RoadSurfaceQuery.pavement(works, p[0] + e, p[1], o.height, q) && same(q, d, p[0] + e, p[1])) hx1 = q.height; else hx1 = Double.NaN;
                            if (RoadSurfaceQuery.pavement(works, p[0] - e, p[1], o.height, q) && same(q, d, p[0] - e, p[1])) hx0 = q.height; else hx0 = Double.NaN;
                            if (RoadSurfaceQuery.pavement(works, p[0], p[1] + e, o.height, q) && same(q, d, p[0], p[1] + e)) hz1 = q.height; else hz1 = Double.NaN;
                            if (RoadSurfaceQuery.pavement(works, p[0], p[1] - e, o.height, q) && same(q, d, p[0], p[1] - e)) hz0 = q.height; else hz0 = Double.NaN;
                            if (!Double.isNaN(hx0 + hx1 + hz0 + hz1)) {
                                double gx = (hx1 - hx0) / (2 * e), gz = (hz1 - hz0) / (2 * e), inv = 1 / Math.sqrt(gx * gx + 1 + gz * gz);
                                double err = Math.toDegrees(Math.acos(Math.min(1, o.nx * -gx * inv + o.ny * inv + o.nz * -gz * inv)));
                                normalChecks++;
                                if (err > maxNormalErr) { maxNormalErr = err; normalAt = String.format(Locale.ROOT, "%s s=%.2f u=%.2f", d.id, s, u); }
                                if (err > NORMAL_ERR) normalBad++;
                            }
                        }
                        prev2H = prevH;
                        prevH = o.height;
                        pnx = o.nx;
                        pny = o.ny;
                        pnz = o.nz;
                        yHint = o.height;
                    }
                }
            }
        }
        double ms = (System.nanoTime() - t0) / 1e6;
        // the time of one call alone (warm): a lane of the first road, again
        RoadDesign d0 = roads.get(0);
        int n = 200_000;
        long t1 = System.nanoTime();
        double sink = 0;
        for (int i = 0; i < n; i++) {
            double s = (i * 0.37) % d0.length, u = d0.section.divided ? d0.section.halfMedian + 1.8 : 1.8;
            double[] p = d0.point(s, u);
            if (RoadSurfaceQuery.pavement(works, p[0], p[1], d0.pavementY(s, u), o)) sink += o.height;
        }
        double perCall = (System.nanoTime() - t1) / (double) n;
        System.out.printf(Locale.ROOT, "roads %d, samples %d (%d calls) in %.0f ms%n", roads.size(), samples, calls, ms);
        System.out.printf(Locale.ROOT, "gaps %d %s%n", gaps, gapAt);
        System.out.printf(Locale.ROOT, "allocation-free paths differing from the plain ones: %d%n", fastDiff);
        System.out.printf(Locale.ROOT, "another road's pavement nearer %d %s%n", other, otherAt);
        System.out.printf(Locale.ROOT, "height step per 0.25 m max %.4f m; kinks > %.3f: %d (max %.4f at %s)%n", maxStep, KINK, kinks, maxKink, kinkAt);
        System.out.printf(Locale.ROOT, "normal turn per 0.25 m > %.1f deg: %d (max %.2f at %s)%n", TURN, turns, maxTurn, turnAt);
        System.out.printf(Locale.ROOT, "normal vs differences: %d checked, > %.2f deg: %d (max %.3f at %s)%n", normalChecks, NORMAL_ERR, normalBad, maxNormalErr, normalAt);
        System.out.printf(Locale.ROOT, "one call (with the sample point): %.2f us (%s)%n", perCall / 1000, sink > 0 ? "ok" : "none");
        boolean pass = gaps == 0 && fastDiff == 0 && normalBad == 0 && turns == 0 && kinks == 0;
        System.out.println(pass ? "pass" : "FAIL");
        return pass;
    }

    /** Whether the query's hit at (x, z) is road d's own pavement there. */
    private static boolean same(RoadSurfaceQuery.Out q, RoadDesign d, double x, double z) {
        double[] l = d.local(x, z, d.section.edge + 0.5);
        return l != null && l[0] >= 0 && l[0] <= d.length && d.section.paved(Math.abs(l[1])) && Math.abs(q.height - d.pavementY(l[0], l[1])) < 1e-6;
    }
}
