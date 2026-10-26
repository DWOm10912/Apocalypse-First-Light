package com.antaurora.apofirstlight.worldgen.network.road;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Road drainage D1 (docs/worldgen/road_drainage_v1_design.md 3, 8): over the built roads of cached networks, the design
 * pavement water now runs on (RoadSurfaceQuery#ground: GroundView's drawn road tops) on a 0.25 m grid in each road's own
 * station / offset frame, every paved band: no vertex lower than all eight around it (a crown, cross slope and grade
 * always lead somewhere; a sag curve's lowest station still falls across), and flat spots (all eight within 1 mm, where
 * a film of water would stand) counted. Then what the ground's road cells cost: RoadSurfaceQuery#height at a column's 25
 * vertices, at random paved points. Junction pads, towns and the driveway are not swept (their own surfaces). Pure Java:
 * the network JSON only. Argument: cached network files; none: the first SEEDS of run/afl_cache/road_network (always
 * including the terrain V2 test seed).
 */
public final class RoadDrainageCheck {
    private RoadDrainageCheck() {}

    static final double STEP = 0.25, FLAT = 0.001;
    static final int SEEDS = 6;
    static final String DIR = "run/afl_cache/road_network", TEST_SEED = "18151365494840402103";

    public static void main(String[] args) throws Exception {
        List<Path> files = new ArrayList<>();
        for (String a : args) files.add(Path.of(a));
        if (files.isEmpty()) {
            TreeMap<String, Path> found = new TreeMap<>();
            try (Stream<Path> list = Files.list(Path.of(DIR))) {
                list.filter(p -> p.getFileName().toString().matches(RoadSurfaceCheck.PATTERN)).forEach(p -> found.put(p.getFileName().toString(), p));
            }
            for (Path p : found.values()) if (p.getFileName().toString().contains("_" + TEST_SEED + "_")) files.add(p);
            for (Path p : found.values()) if (files.size() < SEEDS && !files.contains(p)) files.add(p);
        }
        if (files.isEmpty()) {
            System.out.println("ROAD DRAINAGE CHECK: no cached road network in " + DIR + " (open a federal world once)");
            System.exit(2);
        }
        long lows = 0;
        for (Path f : files) lows += check(f);
        System.out.println(lows == 0 ? "ROAD DRAINAGE CHECK PASS (" + files.size() + " networks: no low vertex, no inverted crown)" : "ROAD DRAINAGE CHECK FAIL: " + lows + " low vertices, inverted-crown stations or frame misses");
        if (lows > 0) System.exit(1);
    }

    /** One network: low vertices found (printed with the first few). */
    static long check(Path file) throws Exception {
        RoadNetworkBuild.Result built = RoadNetworkBuild.build(Files.readString(file));
        List<RoadDesign> roads = RoadNetworkJson.designs(built.json());
        RoadWorks works = new RoadWorks(roads, RoadWorks.Water.NONE);
        long vertices = 0, lows = 0, vLows = 0, flats = 0, vStations = 0;
        int attachedRoads = 0, hosted = 0;
        double attachedKm = 0;
        for (RoadDesign d : roads) if (d.attach != null) {
            attachedRoads++;
            attachedKm += d.length / 1000;
            if (works.attachedHost(d) != null) hosted++;
        }
        System.out.printf(Locale.ROOT, "  attached ramps: %d (%.1f km), %d with a host%n", attachedRoads, attachedKm, hosted);
        double km = 0;
        StringBuilder where = new StringBuilder();
        for (RoadDesign d : roads) {
            CrossSection sec = d.section;
            km += d.length / 1000;
            // inverted crown: the halves' crown below 0 (both falling toward the centreline: a two-lane road between
            // reverse curves; CrossSection#noInvertedCrown, 2026-10-25, keeps none)
            if (!sec.oneWay && !sec.divided) for (double s = 0; s <= d.length; s += RoadProfile.DS) {
                CrossSection.Cross c = d.cross(s);
                if (c.laneLeft() + c.laneRight() < -1e-12) vStations++;
            }
            int ns = (int) Math.floor(d.length / STEP) + 1;
            // each side's band (an undivided road's band runs through its centre: one band)
            double[][] bands = sec.divided ? new double[][]{{-sec.edge, -sec.halfMedian}, {sec.halfMedian, sec.edge}} : new double[][]{{-sec.edge, sec.edge}};
            for (double[] band : bands) {
                int nu = (int) Math.floor((band[1] - band[0]) / STEP) + 1;
                double[] prev = null, cur = row(d, 0, band[0], nu), next;
                for (int i = 0; i < ns; i++) {
                    next = i + 1 < ns ? row(d, (i + 1) * STEP, band[0], nu) : null;
                    if (prev != null && next != null) {
                        for (int j = 1; j < nu - 1; j++) {
                            double h = cur[j];
                            boolean lowest = true, flat = true;
                            for (double[] r : new double[][]{prev, cur, next})
                                for (int k = j - 1; k <= j + 1; k++) {
                                    if (r == cur && k == j) continue;
                                    if (r[k] <= h + 1e-9) lowest = false;
                                    if (Math.abs(r[k] - h) > FLAT) flat = false;
                                }
                            vertices++;
                            if (flat) flats++;
                            if (!lowest) continue;
                            CrossSection.Cross c = d.cross(i * STEP);
                            boolean v = !sec.oneWay && !sec.divided && c.laneLeft() + c.laneRight() < -1e-12;
                            if (v) vLows++;
                            if (lows++ < 6)
                                where.append(String.format(Locale.ROOT, "%n  low%s: %s s=%.2f u=%.2f h=%.3f lanes %.4f / %.4f", v ? " (inverted crown)" : "", d.id, i * STEP, band[0] + j * STEP, h, c.laneLeft(), c.laneRight()));
                        }
                    }
                    prev = cur;
                    cur = next;
                }
            }
        }
        // the road column (RoadSurfaceQuery#design: one search, the vertices by the centre's frame) at random paved points:
        // its time, and every vertex against the plain query; points by paved area (a ramp is short and narrow: picked by road,
        // half the points would be on ramps, which ask every vertex)
        java.util.Random r = new java.util.Random(20261025L);
        double[] area = new double[roads.size()];
        double total = 0;
        for (int i = 0; i < roads.size(); i++) {
            CrossSection sec = roads.get(i).section;
            total += roads.get(i).length * 2 * (sec.edge - sec.halfMedian);
            area[i] = total;
        }
        RoadSurfaceQuery.Out o = new RoadSurfaceQuery.Out();
        double[] rel = new double[25];
        int columns = 0;
        long t0 = 0, compared = 0, missed = 0;
        double worst = 0;
        for (int pass = 0; pass < 3; pass++) {
            if (pass == 1) t0 = System.nanoTime();
            columns = 0;
            for (int n = 0; n < 4000; n++) {
                int pick = java.util.Arrays.binarySearch(area, r.nextDouble() * total);
                RoadDesign d = roads.get(Math.min(roads.size() - 1, pick < 0 ? -pick - 1 : pick));
                CrossSection sec = d.section;
                double s = r.nextDouble() * d.length, v = sec.halfMedian + r.nextDouble() * (sec.edge - sec.halfMedian), u = r.nextBoolean() ? v : -v;
                double[] p = d.point(s, u);
                int x0 = (int) Math.floor(p[0]), z0 = (int) Math.floor(p[1]);
                double top = d.pavementY(s, u);
                int y = (int) Math.floor(top);
                java.util.Arrays.fill(rel, top - y);
                RoadSurfaceQuery.design(works, x0, y, z0, top, rel);
                columns++;
                if (pass != 2) continue;
                for (int j = 0; j <= 4; j++) for (int i = 0; i <= 4; i++) {
                    if (!RoadSurfaceQuery.height(works, x0 + i / 4.0, z0 + j / 4.0, top, o)) continue;
                    if (Math.abs(o.height - top) > 0.09) continue;   // beyond the cover: the block's own top stays, as in the game
                    compared++;
                    double diff = Math.abs(rel[j * 5 + i] + y - o.height);
                    if (diff > 1.0 / 256 && missed++ < 4) {   // one height quantum (the plain query's own station search is mm-level)
                        double[] l = d.curve.local(x0 + i / 4.0, z0 + j / 4.0, sec.edge + 0.5, 0);
                        RoadDesign[] nr = works.roadsNear(x0 + i / 4.0, z0 + j / 4.0);
                        // the column's frame at the vertex (as RoadSurfaceQuery#design: the centre's station, offset and tangent)
                        double[] c = d.curve.local(x0 + 0.5, z0 + 0.5, sec.edge + 0.5, 0), f = new double[5];
                        if (c == null) c = new double[]{s, u};
                        d.curve.frame(c[0], f);
                        double ddx = i / 4.0 - 0.5, ddz = j / 4.0 - 0.5, al = Math.max(0.2, 1 - f[4] * c[1]);
                        double fs = c[0] + (ddx * f[2] + ddz * f[3]) / al, fu = c[1] - ddx * f[3] + ddz * f[2];
                        double[] back = d.point(fs, fu), lb = l == null ? null : d.point(l[0], l[1]);
                        where.append(String.format(Locale.ROOT, "%n    frame s %.3f u %.3f -> (%.4f, %.4f) h %.4f; plain local -> %s; vertex (%.4f, %.4f)",
                                fs, fu, back[0], back[1], d.section.paved(Math.abs(fu)) ? d.pavementYAt(fs, fu) : Double.NaN,
                                lb == null ? "none" : String.format(Locale.ROOT, "(%.4f, %.4f) h %.4f", lb[0], lb[1], d.section.paved(Math.abs(l[1])) ? d.pavementYAt(l[0], l[1]) : Double.NaN),
                                x0 + i / 4.0, z0 + j / 4.0));
                        where.append(String.format(Locale.ROOT, "%n  miss: %s (%s) column s=%.2f u=%.2f, vertex %d,%d: column %.4f, plain %.4f (%s), local %s, %d roads near",
                                d.id, sec.name, s, u, i, j, rel[j * 5 + i] + y, o.height, o.road == null ? "not a road" : o.road == d ? "same road" : o.road.id,
                                l == null ? "none" : String.format(Locale.ROOT, "s %.3f u %.3f", l[0], l[1]), nr == null ? 0 : nr.length));
                    }
                    worst = Math.max(worst, diff);
                }
            }
            if (pass == 1) t0 = System.nanoTime() - t0;
        }
        double us = t0 / 1000.0 / columns;
        System.out.printf(Locale.ROOT, "%s: %d roads, %.1f km, %d paved vertices: %d low (%d on inverted crowns, %d stations of 2 m inverted), %d flat (%.3f%%); road column %.1f us, all-road chunk %.2f ms; %d vertices against the plain query: worst %.2f mm, %d over one quantum%s%n",
                file.getFileName(), roads.size(), km, vertices, lows, vLows, vStations, flats, 100.0 * flats / Math.max(1, vertices), us, us * 256 / 1000, compared, worst * 1000, missed, where);
        if (missed > 0) System.out.println("  FAIL: the column's frame differs from the plain query");
        return lows + vStations + missed;
    }

    private static double[] row(RoadDesign d, double s, double u0, int nu) {
        double[] h = new double[nu];
        for (int j = 0; j < nu; j++) h[j] = d.pavementYAt(Math.min(s, d.length), u0 + j * STEP);
        return h;
    }
}
