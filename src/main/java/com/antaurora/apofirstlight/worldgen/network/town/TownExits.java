package com.antaurora.apofirstlight.worldgen.network.town;

import com.antaurora.apofirstlight.worldgen.network.road.RoadCurve;
import com.antaurora.apofirstlight.worldgen.network.road.RoadInterchanges;
import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson;
import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson.RouteData;
import com.antaurora.apofirstlight.worldgen.network.road.RoadProfile;
import com.antaurora.apofirstlight.worldgen.network.route.RoadClassSpec;
import com.antaurora.apofirstlight.worldgen.network.route.RouteTerrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A framework town's exit (Town Framework V1, docs/worldgen/federal_towns_v1.md 3): a diamond interchange on the
 * interstate that passes the town, whose crossroad becomes the town's exit cross street (TownPlanner lays the grid round
 * it). The options, best first, each a set of state-class stubs the diamond builder (RoadInterchanges) pairs into one
 * crossroad over the interstate; RoadNetworkBuild tries them in turn and keeps the first it builds:
 * <ul>
 *   <li>a state road that already ends on the interstate within JOIN_SEARCH of the town point (the planner's join: its own
 *   diamond): one stub on the other side at its station, so its diamond is the town's exit and the state road comes
 *   into town over it;</li>
 *   <li>own crossings: two stubs from both sides along the interstate's normal, at the stations along SEARCH (every
 *   STEP) with dry stubs (then, after all of those, the stations from SEARCH out to WIDE_SEARCH), ranked by the earthwork under their designed profiles near the interstate (the state class at
 *   EXIT_GRADE, under the diamond's terminal limit), the interstate's own height over its ground at the crossing, the
 *   buildable land round it (#land) and the distance from the town point; MAX_OPTIONS of them at least SPACING apart.</li>
 * </ul>
 * The town point is where the interstate's legs meet at the town (the planner's town node on the line). Where the line
 * kinks there too sharply to be joined into one curve, both legs end at the point and both are searched (each on its own
 * side of the point, the best crossing of either taken). Stubs are
 * ordinary planned state roads (corridor town_ + the town, from and to the town) and go first in the road list, so the
 * diamond builder pairs them before any other road.
 */
public final class TownExits {
    private TownExits() {}

    /**
     * The stubs' lengths off the interstate's centreline, m: on the town's side (the side with more buildable land round
     * the town point) LENGTH, on the far side FAR_LENGTH (beyond the diamond's crossroad realignment, RoadInterchanges.BACK,
     * and the far side's first parallel street).
     */
    public static final double LENGTH = 460, FAR_LENGTH = 280;
    /**
     * The search each way from the town point along the interstate: for a state road's join JOIN_SEARCH, for an own
     * crossing SEARCH (a diamond keeps RoadInterchanges.FOOT off interstate structures each way), every STEP, m.
     */
    public static final double JOIN_SEARCH = 900, SEARCH = 1500, STEP = 20;
    /**
     * The own crossings' second band: from SEARCH out to WIDE_SEARCH, tried after every crossing within SEARCH (a line
     * whose first SEARCH m are all bridges, culverts, a tunnel or too near its end at a kink still gets its town), m.
     */
    public static final double WIDE_SEARCH = 3000;
    /**
     * The stubs' grade limits: EXIT_GRADE within TERMINAL_REACH of the interstate (below the diamond's crossroad grade at a
     * ramp terminal, RoadInterchanges.TERMINAL_GRADE), STREET_GRADE beyond (a town street). A stub is designed over a
     * guide line, not its ground: the interstate's own height within TERMINAL_REACH (the ramps climb from the interstate to
     * the terminals), then the ground followed outward at STREET_GRADE at most (a hill steeper than that is cut, it does
     * not lift the terminal).
     */
    public static final double EXIT_GRADE = 0.03, STREET_GRADE = 0.07, TERMINAL_REACH = 130;
    /** Own crossings tried at most, at least SPACING apart, m. */
    public static final int MAX_OPTIONS = 8;
    public static final double SPACING = 80;
    /** The second band's options at least this far apart (its eight spread over the band, not one hillside), m. */
    public static final double WIDE_SPACING = 300;
    /** A crossing from which fewer town blocks are reachable (#reach) is no town's exit. */
    static final int MIN_BLOCKS = 4;
    /** The earthwork counted within NEAR of the interstate (the diamond's crossroad and terminals), m. */
    static final double NEAR = 300;
    /** The land round a crossing counted by #land: along the interstate each way, and across from FRONT to DEEP each side, m. */
    static final double LAND_ALONG = 600, FRONT = 150, DEEP = 550, CELL = 25, CELL_RELIEF = 3;
    /** Weights: the buildable share and the distance from the town point (per m) against the mean earthwork depth (m). */
    static final double LAND_WEIGHT = 25, DISTANCE_WEIGHT = 0.006, DATUM_WEIGHT = 0.5;
    /** A state road's end this close to the interstate's centreline is a join (RoadInterchanges.TOUCH), m. */
    static final double TOUCH = 10;
    /** Stub control point spacing, m. */
    static final double POINT = 20;
    /** An interstate whose centreline passes this close to the town point is a host (both legs where the line kinks), m. */
    static final double HOST_REACH = 30;

    /** One way to give a town its exit: the stubs to add, the ids the diamond builder is to try, a report line. */
    public record Option(String town, List<RouteData> stubs, List<String> roads, String line) {}

    /** A town's exit options, best first (empty when no interstate passes it or nothing fits). */
    public record Site(String town, List<Option> options) {}

    /** Every framework town's exit options (TownSites.FRAMEWORK order). */
    public static List<Site> sites(List<RouteData> roads, List<RouteData> legs, RouteTerrain terrain, List<String> report) {
        List<Site> out = new ArrayList<>();
        for (String town : TownSites.FRAMEWORK) out.add(site(town, roads, legs, terrain, report));
        return out;
    }

    private static Site site(String town, List<RouteData> roads, List<RouteData> legs, RouteTerrain terrain, List<String> report) {
        List<Option> options = new ArrayList<>();
        // the town point: where two interstate legs meet at the town, one ending and one starting there (a leg named for
        // the town may start at a join elsewhere: its corridor meets another interstate away from the town)
        double[] tp = null;
        for (RouteData a : legs) {
            if (!a.roadClass().equals("interstate") || !town.equals(a.to())) continue;
            int n = a.xs().length;
            for (RouteData b : legs)
                if (b != a && b.roadClass().equals("interstate") && town.equals(b.from()) && Math.hypot(b.xs()[0] - a.xs()[n - 1], b.zs()[0] - a.zs()[n - 1]) < 1)
                    tp = new double[]{a.xs()[n - 1], a.zs()[n - 1]};
        }
        if (tp == null) { report.add("town " + town + ": no interstate passes it; no exit"); return new Site(town, options); }
        // the interstates through the town point: one road, or two legs where the line kinks too sharply to be joined into
        // one curve (each searched on its own side of the point)
        List<RouteData> hosts = new ArrayList<>();
        List<Double> stations = new ArrayList<>();
        RouteData nearest = null;
        double ns = 0, best = Double.POSITIVE_INFINITY;
        for (RouteData r : roads) {
            if (!r.roadClass().equals("interstate")) continue;
            double[] l = new RoadCurve(r.xs(), r.zs()).local(tp[0], tp[1], 100, 0);
            if (l == null) continue;
            if (Math.abs(l[1]) < HOST_REACH) { hosts.add(r); stations.add(l[0]); }
            if (Math.abs(l[1]) < best) { best = Math.abs(l[1]); nearest = r; ns = l[0]; }
        }
        // a joined curve rounding the town point may pass it wider: the nearest within 100 m
        if (hosts.isEmpty() && nearest != null) { hosts.add(nearest); stations.add(ns); }
        if (hosts.isEmpty()) { report.add("town " + town + ": its interstate is not in the built network; no exit"); return new Site(town, options); }
        // own crossings, over every host: {cost, s, earthwork, land, datum, host, town side}
        List<double[]> ranked = new ArrayList<>();
        int dry = 0, tried = 0, bridged = 0;
        for (int hi = 0; hi < hosts.size(); hi++) {
            RouteData host = hosts.get(hi);
            double hs = stations.get(hi);
            RoadCurve c = new RoadCurve(host.xs(), host.zs());
            RoadProfile hp = RoadProfile.of(host.profile());
            // the town's side: more buildable land round the town point
            int townSide = land(c, hs, -1, terrain) >= land(c, hs, 1, terrain) ? -1 : 1;
            // a state road joining the interstate near the town: its diamond is the exit
            for (RouteData r : roads) {
                if (!r.roadClass().equals("state")) continue;
                int n = r.xs().length;
                for (int e = 0; e < 2; e++) {
                    double px = e == 0 ? r.xs()[0] : r.xs()[n - 1], pz = e == 0 ? r.zs()[0] : r.zs()[n - 1];
                    double[] l = c.local(px, pz, TOUCH, 0);
                    if (l == null || Math.abs(l[0] - hs) > JOIN_SEARCH) continue;
                    // the side it comes from: its point 60 m back
                    double[] back = new RoadCurve(r.xs(), r.zs()).point(e == 0 ? 60 : new RoadCurve(r.xs(), r.zs()).length - 60, 0), lb = c.local(back[0], back[1], 400, 0);
                    if (lb == null) continue;
                    int side = lb[1] > 0 ? -1 : 1;
                    RouteData st = stub(town, side, c, l[0], side == townSide ? LENGTH : FAR_LENGTH, hp.at(l[0]), terrain);
                    if (st == null) continue;
                    options.add(new Option(town, List.of(st), List.of(st.id(), r.id()), String.format(Locale.ROOT,
                            "town %s: exit over the diamond of %s at s %.0f (town point s %.0f), one stub", town, r.id(), l[0], hs)));
                }
            }
            for (double s = Math.max(LENGTH, hs - WIDE_SEARCH); s <= Math.min(c.length - LENGTH, hs + WIDE_SEARCH); s += STEP) {
                boolean wide = Math.abs(s - hs) > SEARCH;
                if (!wide) tried++;
                // the diamond takes no interstate bridge, culvert or tunnel within its footprint
                boolean structure = false;
                for (RoadNetworkJson.Structure q : host.structures())
                    if (q.s1() >= s - RoadInterchanges.FOOT && q.s0() <= s + RoadInterchanges.FOOT) structure = true;
                if (structure) { if (!wide) bridged++; continue; }
                // the town's side here: the more town blocks reachable from the crossing (the planner's own count)
                int bl = reach(c, s, -1, terrain), br = reach(c, s, 1, terrain), side = bl >= br ? -1 : 1, blocks = Math.max(bl, br);
                if (blocks < MIN_BLOCKS) continue;
                RouteData a = stub(town, side, c, s, LENGTH, hp.at(s), terrain), b = stub(town, -side, c, s, FAR_LENGTH, hp.at(s), terrain);
                if (a == null || b == null) continue;
                if (!wide) dry++;
                double[] pc = c.point(s, 0);
                double fill = (earthwork(a, terrain) + earthwork(b, terrain)) / 2, land = Math.min(1, blocks / (double) TownPlanner.MAX_BLOCKS),
                        datum = Math.abs(hp.at(s) - terrain.height(pc[0], pc[1]));
                ranked.add(new double[]{fill + DATUM_WEIGHT * datum - LAND_WEIGHT * land + DISTANCE_WEIGHT * Math.abs(s - hs), s, fill, blocks, datum, hi, side, wide ? 1 : 0});
            }
            if (Boolean.getBoolean("afl.debugTowns")) {
                System.out.printf(Locale.ROOT, "DEBUG town %s: host %s at s %.0f of %.0f, foot %.0f, structures %s%n", town, host.id(), hs, c.length, RoadInterchanges.FOOT, host.structures());
                for (double s = Math.max(0, hs - 6000); s <= Math.min(c.length, hs + 6000); s += 200)
                    System.out.printf(Locale.ROOT, "DEBUG blocks %s s %6.0f: reachable left %2d right %2d%n", host.id(), s, reach(c, s, -1, terrain), reach(c, s, 1, terrain));
            }
        }
        // the near band first, each band by its cost
        ranked.sort((p, q) -> p[7] != q[7] ? Double.compare(p[7], q[7]) : Double.compare(p[0], q[0]));
        if (Boolean.getBoolean("afl.debugTowns"))
            for (double[] q : ranked) System.out.printf(Locale.ROOT, "DEBUG town %s: %s s %.0f cost %.2f earthwork %.2f blocks %.0f datum %.2f%n", town, hosts.get((int) q[5]).id(), q[1], q[0], q[2], q[3], q[4]);
        List<double[]> taken = new ArrayList<>();
        int[] perBand = new int[2];
        for (double[] q : ranked) {
            if (perBand[(int) q[7]] >= MAX_OPTIONS) continue;
            boolean near = false;
            for (double[] t : taken) if (t[5] == q[5] && Math.abs(t[1] - q[1]) < (q[7] > 0 ? WIDE_SPACING : SPACING)) near = true;
            if (near) continue;
            taken.add(q);
            perBand[(int) q[7]]++;
            RouteData host = hosts.get((int) q[5]);
            RoadCurve c = new RoadCurve(host.xs(), host.zs());
            RoadProfile hp = RoadProfile.of(host.profile());
            int townSide = (int) q[6];
            RouteData a = stub(town, townSide, c, q[1], LENGTH, hp.at(q[1]), terrain), b = stub(town, -townSide, c, q[1], FAR_LENGTH, hp.at(q[1]), terrain);
            options.add(new Option(town, List.of(a, b), List.of(a.id(), b.id()), String.format(Locale.ROOT,
                    "town %s: exit on %s at s %.0f (town point s %.0f%s, town %s; earthwork %.2f m, interstate %.2f m off its ground, %.0f town blocks reachable; within %.0f m %d of %d crossings dry, %d by interstate structures)",
                    town, host.id(), q[1], stations.get((int) q[5]), q[7] > 0 ? ", beyond " + (int) SEARCH + " m" : "", townSide < 0 ? "left" : "right",
                    q[2], q[4], q[3], SEARCH, dry, tried, bridged)));
        }
        if (options.isEmpty()) report.add(String.format(Locale.ROOT, "town %s: no dry crossing within %.0f m of its point on %d interstate leg(s) (within %.0f m %d of %d stations by interstate structures); no exit",
                town, WIDE_SEARCH, hosts.size(), SEARCH, bridged, tried));
        return new Site(town, options);
    }

    /**
     * One stub: from length off the interstate (side u: -1 its left, 1 its right) to its centreline at station s, designed
     * over the guide line from the interstate's height there (datum); null over water.
     */
    static RouteData stub(String town, int side, RoadCurve host, double s, double length, double datum, RouteTerrain terrain) {
        int k = (int) Math.round(length / POINT);
        double[] xs = new double[k + 1], zs = new double[k + 1];
        for (int i = 0; i <= k; i++) {
            double[] p = host.point(s, side * (length - i * POINT));
            xs[i] = RoadNetworkJson.round2(p[0]);
            zs[i] = RoadNetworkJson.round2(p[1]);
        }
        RoadCurve c = new RoadCurve(xs, zs);
        RoadClassSpec spec = RoadClassSpec.STATE;
        int n = RoadProfile.samples(c.length);
        double[] g = new double[n], grade = new double[n];
        // the guide line, from the interstate outward (the stub runs toward it: its last sample is on the centreline)
        for (int i = n - 1; i >= 0; i--) {
            double[] p = c.point(Math.min(c.length, i * RoadProfile.DS), 0);
            if (terrain.water(p[0], p[1]) != RouteTerrain.DRY) return null;
            boolean near = c.length - i * RoadProfile.DS <= TERMINAL_REACH;
            double step = STREET_GRADE * RoadProfile.DS;
            g[i] = near ? datum : Math.max(g[i + 1] - step, Math.min(g[i + 1] + step, terrain.height(p[0], p[1])));
            grade[i] = near ? EXIT_GRADE : STREET_GRADE;
        }
        double[] e = RoadProfile.design(g, spec.sigma, spec.raise, spec.vSigma(), spec.kMin(), grade, null).samples();
        for (int i = 0; i < n; i++) e[i] = RoadNetworkJson.round2(e[i]);
        String id = TownSites.corridor(town) + ":" + town + "-exit_" + (side < 0 ? "l" : "r");
        return new RouteData(id, "state", TownSites.corridor(town), town, town, xs, zs, e, List.of(), null);
    }

    /** Mean |E - ground| of a stub's profile from TERMINAL_REACH to NEAR off the interstate (its end), m. */
    static double earthwork(RouteData r, RouteTerrain terrain) {
        RoadCurve c = new RoadCurve(r.xs(), r.zs());
        double sum = 0;
        int n = r.profile().length, k = 0;
        for (int i = 0; i < n; i++) {
            double t = Math.min(c.length, i * RoadProfile.DS);
            if (c.length - t > NEAR || c.length - t < TERMINAL_REACH) continue;
            double[] p = c.point(t, 0);
            sum += Math.abs(r.profile()[i] - terrain.height(p[0], p[1]));
            k++;
        }
        return k == 0 ? 0 : sum / k;
    }

    /**
     * The buildable share of the land on one side of a crossing at s: CELL squares in a frame along the interstate
     * (LAND_ALONG each way, FRONT .. DEEP off it) whose corners and centre are dry and within CELL_RELIEF of each other.
     */
    /**
     * The town blocks reachable from a crossing at s on one side, as TownPlanner grows its town (2026-10-17): its cells
     * (CROSS along, MAX_K each way, by its BANDS from FRONT, on the straight frame of the host's tangent at s) whose ground
     * stays dry and within MAX_RELIEF (sampled every REACH_SAMPLE m), connected to the two cells next to the crossing.
     * Roads are not counted (the planner keeps clear of them afterwards). At most TownPlanner.MAX_BLOCKS.
     */
    static int reach(RoadCurve host, double s, int side, RouteTerrain terrain) {
        double[] v = TownPlanner.lines(TownPlanner.BANDS), f = new double[5];
        host.frame(s, f);
        int nk = 2 * TownPlanner.MAX_K, nj = TownPlanner.BANDS;
        byte[] ok = new byte[nk * nj];   // 0 unknown, 1 buildable, 2 not
        java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
        boolean[] seen = new boolean[nk * nj];
        for (int k : new int[]{-1, 0}) { int i = (k + TownPlanner.MAX_K) * nj; seen[i] = true; q.add(i); }
        int n = 0;
        while (!q.isEmpty() && n < TownPlanner.MAX_BLOCKS) {
            int i = q.poll(), k = i / nj - TownPlanner.MAX_K, j = i % nj;
            if (ok[i] == 0) ok[i] = (byte) (cellOk(f, k, v[j], v[j + 1], side, terrain) ? 1 : 2);
            if (ok[i] != 1) continue;
            n++;
            int[][] next = {{k - 1, j}, {k + 1, j}, {k, j - 1}, {k, j + 1}};
            for (int[] m : next) {
                if (m[0] < -TownPlanner.MAX_K || m[0] >= TownPlanner.MAX_K || m[1] < 0 || m[1] >= nj) continue;
                int e = (m[0] + TownPlanner.MAX_K) * nj + m[1];
                if (!seen[e]) { seen[e] = true; q.add(e); }
            }
        }
        return n;
    }

    /** reach's sampling step, m. */
    static final double REACH_SAMPLE = 16;

    private static boolean cellOk(double[] f, int k, double v0, double v1, int side, RouteTerrain terrain) {
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (double a = 0; a <= TownPlanner.CROSS + 1e-6; a += REACH_SAMPLE)
            for (double w = v0; w <= v1 + 1e-6; w += REACH_SAMPLE) {
                double t = k * TownPlanner.CROSS + Math.min(a, TownPlanner.CROSS), u = side * Math.min(w, v1);
                // the right-hand normal of the tangent (tx, tz) is (-tz, tx): u > 0 to the right
                double x = f[0] + f[2] * t - f[3] * u, z = f[1] + f[3] * t + f[2] * u;
                if (terrain.water(x, z) != RouteTerrain.DRY) return false;
                double h = terrain.height(x, z);
                lo = Math.min(lo, h);
                hi = Math.max(hi, h);
                if (hi - lo > TownPlanner.MAX_RELIEF) return false;
            }
        return true;
    }

    static double land(RoadCurve host, double s, int side, RouteTerrain terrain) {
        int good = 0, all = 0;
        for (double a = -LAND_ALONG; a < LAND_ALONG; a += CELL)
            for (double v = FRONT; v < DEEP; v += CELL) {
                double t = Math.max(0, Math.min(host.length, s + a));
                double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
                boolean wet = false;
                for (double[] q : new double[][]{{0, 0}, {CELL, 0}, {0, CELL}, {CELL, CELL}, {CELL / 2, CELL / 2}}) {
                    double[] p = host.point(Math.max(0, Math.min(host.length, t + q[0])), side * (v + q[1]));
                    if (terrain.water(p[0], p[1]) != RouteTerrain.DRY) wet = true;
                    double h = terrain.height(p[0], p[1]);
                    lo = Math.min(lo, h);
                    hi = Math.max(hi, h);
                }
                all++;
                if (!wet && hi - lo <= CELL_RELIEF) good++;
            }
        return all == 0 ? 0 : (double) good / all;
    }
}
