package com.antaurora.apofirstlight.worldgen.federal;

import com.antaurora.apofirstlight.worldgen.federal.FederalTemplate.CorridorSpec;
import com.antaurora.apofirstlight.worldgen.federal.FederalTemplate.Physiography;
import com.antaurora.apofirstlight.worldgen.federal.FederalTemplate.RoadClass;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The federal national road plan, PROTOTYPE (C1, 2026-10-14; docs/worldgen/federal_six_state_c1.md): corridors between
 * the template's city candidates ({@link FederalTemplate#CORRIDORS}), routed on a 64 m cost grid, with their nodes
 * (cities, interchanges, junctions, border crossings, passes, bridges) and reservation widths. Deterministic (fixed
 * corridor order, a stable heap tie-break), pure Java, reproducible as JSON ({@link #toJson}). Not used by world
 * generation. It is the corridor layer only: the drivable alignment (R4: radius >= 300 m, grade <= 3 %, earthworks,
 * structures, chunk-slice recipes) is designed on these corridors later (C3).
 *
 * <p>Costs: length times a province factor (the range 4x, wetland 2.5x, tableland 1.6x ...), a coast margin (250 m),
 * and a 0.75x discount on cells an earlier corridor already uses (shared alignments, so routes meet at interchanges
 * instead of running side by side). With {@link Terrain} (a national plan's heights), each step also pays for its
 * grade (above 2 % rising, above 5 % steeply) and for every water cell it crosses (a bridge).
 */
public final class NationalRoadPlan {
    public static final String VERSION = "national_road_plan_v1_proto";
    public static final double GRID = 64;
    /** Corridor reservation half-widths (m): what forests, lots and structures must keep clear of later. */
    public static final double INTERSTATE_RESERVE = 60, STATE_HIGHWAY_RESERVE = 30, BRIDGE_RESERVE = 30;

    /** Optional terrain for grade-aware routing (a Terrain V2 plan of this map). */
    public interface Terrain {
        double height(double x, double z);

        boolean water(double x, double z);
    }

    public enum NodeKind { CITY, INTERCHANGE, JUNCTION, BORDER_CROSSING, PASS, BRIDGE }

    public record Node(String id, NodeKind kind, double x, double z, String note) {}

    public record Stats(double length, double maxGrade, double meanGrade, double minRadius, int borderCrossings,
                        double mountainShare, double wetlandShare, int waterCrossings, boolean graded) {}

    public record Corridor(String id, RoadClass roadClass, String from, String to, double[] xs, double[] zs, Stats stats,
                           double reserve) {}

    private final FederalMap map;
    private final List<Corridor> corridors;
    private final List<Node> nodes;
    private final boolean graded;

    public List<Corridor> corridors() { return corridors; }

    public List<Node> nodes() { return nodes; }

    private NationalRoadPlan(FederalMap map, List<Corridor> corridors, List<Node> nodes, boolean graded) {
        this.map = map;
        this.corridors = corridors;
        this.nodes = nodes;
        this.graded = graded;
    }

    // ------------------------------------------------------------------ build
    public static NationalRoadPlan build(FederalMap m, Terrain terrain) {
        Grid g = new Grid(m, terrain);
        List<Corridor> out = new ArrayList<>();
        Map<String, Integer> use = new LinkedHashMap<>();
        for (CorridorSpec spec : FederalTemplate.CORRIDORS) {
            for (int k = 0; k + 1 < spec.via().size(); k++) {
                String a = spec.via().get(k), b = spec.via().get(k + 1);
                FederalMap.City ca = m.city(a), cb = m.city(b);
                int[] cells = g.route(g.landCell(ca.x(), ca.z()), g.landCell(cb.x(), cb.z()));
                g.markUsed(cells);
                double[][] line = smooth(g, cells, ca.x(), ca.z(), new double[]{cb.x(), cb.z()});
                double reserve = spec.roadClass() == RoadClass.INTERSTATE ? INTERSTATE_RESERVE : STATE_HIGHWAY_RESERVE;
                out.add(new Corridor(spec.id() + ":" + a + "-" + b, spec.roadClass(), a, b, line[0], line[1],
                        stats(m, terrain, line[0], line[1]), reserve));
                use.merge(a, spec.roadClass() == RoadClass.INTERSTATE ? 10 : 1, Integer::sum);
                use.merge(b, spec.roadClass() == RoadClass.INTERSTATE ? 10 : 1, Integer::sum);
            }
        }
        // island bridges: from the nearest city candidate to the crossing's mainland end, then over the water
        for (FederalMap.Crossing c : m.crossings()) {
            FederalMap.City near = null;
            double best = Double.MAX_VALUE;
            for (FederalMap.City city : m.cities()) {
                double d = Math.hypot(city.x() - c.fromX(), city.z() - c.fromZ());
                if (d < best) { best = d; near = city; }
            }
            int[] cells = g.route(g.landCell(near.x(), near.z()), g.landCell(c.fromX(), c.fromZ()));
            g.markUsed(cells);
            double[][] line = smooth(g, cells, near.x(), near.z(), new double[]{c.fromX(), c.fromZ(), c.toX(), c.toZ()});
            double[] xs = line[0], zs = line[1];
            out.add(new Corridor("island_bridge:" + c.islandId(), RoadClass.ISLAND_BRIDGE, near.id(), "satellite_" + c.islandId(),
                    xs, zs, stats(m, terrain, xs, zs), BRIDGE_RESERVE));
            use.merge(near.id(), 1, Integer::sum);
        }
        List<Node> nodes = new ArrayList<>();
        for (FederalMap.City c : m.cities()) {
            int u = use.getOrDefault(c.id(), 0);
            if (u == 0) continue;
            int inter = u / 10, state = u % 10;
            // interstates crossing, or an interstate meeting a state highway: interchange; three state highway ends: junction
            NodeKind kind = inter >= 3 || (inter >= 1 && state >= 1) ? NodeKind.INTERCHANGE : state + inter >= 3 ? NodeKind.JUNCTION : NodeKind.CITY;
            nodes.add(new Node(c.id(), kind, c.x(), c.z(), c.state().key() + " " + c.role()));
        }
        double[] w = new double[Physiography.values().length];
        for (Corridor c : out) {
            FederalStateId prev = null;
            double passW = 0.45, px = 0, pz = 0;
            boolean pass = false;
            for (int i = 0; i < c.xs().length; i++) {
                FederalStateId s = m.governingStateAt(c.xs()[i], c.zs()[i]);
                if (prev != null && s != prev && !near(nodes, NodeKind.BORDER_CROSSING, c.xs()[i], c.zs()[i], 200))
                    nodes.add(new Node(c.id() + "/border/" + prev.key() + "-" + s.key(), NodeKind.BORDER_CROSSING, c.xs()[i], c.zs()[i],
                            prev.key() + " -> " + s.key()));
                prev = s;
                m.physiography(c.xs()[i], c.zs()[i], w);
                if (w[Physiography.MOUNTAIN.ordinal()] > passW) { passW = w[Physiography.MOUNTAIN.ordinal()]; px = c.xs()[i]; pz = c.zs()[i]; pass = true; }
            }
            if (pass) nodes.add(new Node(c.id() + "/pass", NodeKind.PASS, px, pz, String.format(Locale.ROOT, "range weight %.2f", passW)));
            if (c.roadClass() == RoadClass.ISLAND_BRIDGE) {
                FederalMap.Crossing x = m.crossings().get(Integer.parseInt(c.to().substring("satellite_".length())) - 1);
                nodes.add(new Node(c.id() + "/bridge", NodeKind.BRIDGE, (x.fromX() + x.toX()) / 2, (x.fromZ() + x.toZ()) / 2,
                        String.format(Locale.ROOT, "sea span %.0f m", x.span())));
            } else if (terrain != null) {
                int k = 0;
                for (double[] run : waterRuns(terrain, c.xs(), c.zs()))
                    if (!near(nodes, NodeKind.BRIDGE, run[0], run[1], 120))
                        nodes.add(new Node(c.id() + "/bridge/" + (k++), NodeKind.BRIDGE, run[0], run[1],
                                String.format(Locale.ROOT, "river / estuary, water %.0f m", run[2])));
            }
        }
        return new NationalRoadPlan(m, List.copyOf(out), List.copyOf(nodes), terrain != null);
    }

    static boolean near(List<Node> nodes, NodeKind kind, double x, double z, double r) {
        for (Node n : nodes) if (n.kind() == kind && Math.hypot(n.x() - x, n.z() - z) < r) return true;
        return false;
    }

    /** Open-water runs along a polyline, sampled every 8 m (rivers are a few blocks wide): {centre x, centre z, length}. */
    static List<double[]> waterRuns(Terrain terrain, double[] xs, double[] zs) {
        List<double[]> runs = new ArrayList<>();
        boolean wet = false;
        double sx = 0, sz = 0, len = 0;
        int cnt = 0;
        for (int i = 1; i < xs.length; i++) {
            double l = Math.hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
            int steps = Math.max(1, (int) Math.ceil(l / 8));
            for (int k = i == 1 ? 0 : 1; k <= steps; k++) {
                double t = (double) k / steps, x = xs[i - 1] + (xs[i] - xs[i - 1]) * t, z = zs[i - 1] + (zs[i] - zs[i - 1]) * t;
                boolean water = terrain.water(x, z);
                if (water) { sx += x; sz += z; cnt++; len += l / steps; }
                if (wet && !water) { runs.add(new double[]{sx / cnt, sz / cnt, len}); sx = sz = len = 0; cnt = 0; }
                wet = water;
            }
        }
        if (wet) runs.add(new double[]{sx / cnt, sz / cnt, len});
        return runs;
    }

    // ------------------------------------------------------------------ cost grid and A*
    /** Minimum radius reported for a straight corridor, m. */
    public static final double STRAIGHT = 99999;
    /** Cost factor on cells an earlier corridor already uses. */
    static final double REUSE = 0.75;

    static final class Grid {
        final FederalMap m;
        final Terrain terrain;
        final double ox, oz;
        final int nx, nz;
        final float[] factor;      // per-cell cost factor (Float.POSITIVE_INFINITY: sea)
        final float[] height;      // NaN without terrain
        final boolean[] water, used;

        Grid(FederalMap m, Terrain terrain) {
            this.m = m;
            this.terrain = terrain;
            FederalMap.Bounds b = m.landBounds();
            ox = Math.floor((b.minX() - 512) / GRID) * GRID;
            oz = Math.floor((b.minZ() - 512) / GRID) * GRID;
            nx = (int) Math.ceil((b.maxX() + 512 - ox) / GRID);
            nz = (int) Math.ceil((b.maxZ() + 512 - oz) / GRID);
            factor = new float[nx * nz];
            height = new float[nx * nz];
            water = new boolean[nx * nz];
            used = new boolean[nx * nz];
            double[] w = new double[Physiography.values().length];
            for (int r = 0; r < nz; r++) for (int c = 0; c < nx; c++) {
                int i = r * nx + c;
                double x = cx(c), z = cz(r);
                double coast = m.mainlandDistance(x, z);
                height[i] = terrain == null ? Float.NaN : (float) terrain.height(x, z);
                water[i] = terrain != null && terrain.water(x, z);
                if (coast < 0) { factor[i] = Float.POSITIVE_INFINITY; continue; }
                m.physiography(x, z, w);
                double f = 1 + 3.0 * w[Physiography.MOUNTAIN.ordinal()] + 0.6 * w[Physiography.PLATEAU.ordinal()]
                        + 1.5 * w[Physiography.COASTAL_LOWLAND.ordinal()] + 0.3 * w[Physiography.LAKE_LOWLAND.ordinal()]
                        + 0.2 * w[Physiography.HILLS.ordinal()];
                if (coast < 250) f *= 1.6;
                factor[i] = (float) f;
            }
        }

        double cx(int c) { return ox + (c + 0.5) * GRID; }

        double cz(int r) { return oz + (r + 0.5) * GRID; }

        int cellOf(double x, double z) {
            int c = Math.max(0, Math.min(nx - 1, (int) Math.floor((x - ox) / GRID)));
            int r = Math.max(0, Math.min(nz - 1, (int) Math.floor((z - oz) / GRID)));
            return r * nx + c;
        }

        /** The cell of a point, or the nearest land cell (a shore point can fall on a sea cell's centre). */
        int landCell(double x, double z) {
            int i = cellOf(x, z);
            if (!Float.isInfinite(factor[i])) return i;
            int c0 = i % nx, r0 = i / nx, best = -1;
            double bd = Double.MAX_VALUE;
            for (int r = Math.max(0, r0 - 8); r <= Math.min(nz - 1, r0 + 8); r++)
                for (int c = Math.max(0, c0 - 8); c <= Math.min(nx - 1, c0 + 8); c++) {
                    int j = r * nx + c;
                    double d = Math.hypot(cx(c) - x, cz(r) - z);
                    if (!Float.isInfinite(factor[j]) && (d < bd || (d == bd && j < best))) { bd = d; best = j; }
                }
            if (best < 0) throw new IllegalStateException(String.format(Locale.ROOT, "no land cell near %.0f %.0f", x, z));
            return best;
        }

        void markUsed(int[] cells) { for (int i : cells) used[i] = true; }

        static final int[] DC = {1, -1, 0, 0, 1, 1, -1, -1, 2, 2, -2, -2, 1, -1, 1, -1};
        static final int[] DR = {0, 0, 1, -1, 1, -1, 1, -1, 1, -1, 1, -1, 2, 2, -2, -2};

        double stepCost(int i, int j, double len) {
            double f = 0.5 * (factor[i] + factor[j]);
            if (Double.isInfinite(f)) return Double.POSITIVE_INFINITY;
            double cost = len * f;
            if (terrain != null) {
                double g = Math.abs(height[j] - height[i]) / len;
                cost += len * (30 * Math.max(0, g - 0.02) + 800 * Math.pow(Math.max(0, g - 0.05), 2));
                if (water[j] && !water[i]) cost += 600;      // a bridge
                else if (water[j]) cost += len * 3;
            }
            if (used[i] && used[j]) cost *= REUSE;
            return cost;
        }

        /** A* (16-neighbour; admissible: no step costs less than its length times REUSE). */
        int[] route(int from, int to) {
            int n = nx * nz;
            double[] gScore = new double[n];
            Arrays.fill(gScore, Double.POSITIVE_INFINITY);
            int[] prev = new int[n];
            Arrays.fill(prev, -1);
            boolean[] closed = new boolean[n];
            Heap open = new Heap();
            gScore[from] = 0;
            open.push(h(from, to), from);
            while (open.size > 0) {
                int i = open.pop();
                if (closed[i]) continue;
                closed[i] = true;
                if (i == to) break;
                int c = i % nx, r = i / nx;
                for (int d = 0; d < DC.length; d++) {
                    int cc = c + DC[d], rr = r + DR[d];
                    if (cc < 0 || rr < 0 || cc >= nx || rr >= nz) continue;
                    int j = rr * nx + cc;
                    if (closed[j]) continue;
                    double len = GRID * Math.hypot(DC[d], DR[d]);
                    double ng = gScore[i] + stepCost(i, j, len);
                    if (ng < gScore[j]) { gScore[j] = ng; prev[j] = i; open.push(ng + h(j, to), j); }
                }
            }
            if (prev[to] < 0 && to != from) throw new IllegalStateException("no land route between cells " + from + " and " + to);
            List<Integer> path = new ArrayList<>();
            for (int i = to; i != -1; i = prev[i]) path.add(i);
            int[] out = new int[path.size()];
            for (int k = 0; k < out.length; k++) out[k] = path.get(out.length - 1 - k);
            return out;
        }

        double h(int i, int to) {
            return REUSE * GRID * Math.hypot(i % nx - to % nx, i / nx - to / nx);
        }
    }

    /** Min-heap of (double key, int id); ties broken by id, so the search order never depends on anything else. */
    static final class Heap {
        double[] key = new double[1024];
        int[] id = new int[1024];
        int size;

        void push(double k, int v) {
            if (size == key.length) { key = Arrays.copyOf(key, size * 2); id = Arrays.copyOf(id, size * 2); }
            int i = size++;
            while (i > 0) {
                int p = (i - 1) >> 1;
                if (less(key[p], id[p], k, v)) break;
                key[i] = key[p]; id[i] = id[p]; i = p;
            }
            key[i] = k; id[i] = v;
        }

        int pop() {
            int top = id[0];
            double k = key[--size];
            int v = id[size];
            int i = 0;
            while (true) {
                int l = 2 * i + 1;
                if (l >= size) break;
                int m = l + 1 < size && less(key[l + 1], id[l + 1], key[l], id[l]) ? l + 1 : l;
                if (!less(key[m], id[m], k, v)) break;
                key[i] = key[m]; id[i] = id[m]; i = m;
            }
            key[i] = k; id[i] = v;
            return top;
        }

        static boolean less(double ka, int ia, double kb, int ib) { return ka < kb || (ka == kb && ia < ib); }
    }

    // ------------------------------------------------------------------ geometry
    /** Cell path -> polyline from (ax, az) through the cells to the tail points (x, z pairs; the last cell is replaced by
     *  the first), simplified within 80 m (inside an interstate's reservation), Chaikin-smoothed, resampled at 50 m. */
    static double[][] smooth(Grid g, int[] cells, double ax, double az, double[] tail) {
        int n = cells.length - 1 + tail.length / 2;
        double[] xs = new double[n], zs = new double[n];
        for (int k = 0; k < cells.length - 1; k++) { xs[k] = g.cx(cells[k] % g.nx); zs[k] = g.cz(cells[k] / g.nx); }
        for (int k = 0; k < tail.length / 2; k++) { xs[cells.length - 1 + k] = tail[2 * k]; zs[cells.length - 1 + k] = tail[2 * k + 1]; }
        xs[0] = ax; zs[0] = az;
        double[][] p = simplify(xs, zs, 80);
        for (int it = 0; it < 5; it++) p = chaikin(p[0], p[1]);
        p = resample(p[0], p[1], 50);
        relax(p[0], p[1], 1.07 * MIN_RADIUS);
        return p;
    }

    /** Corridor curvature floor (R4: 300 m), m; the corridor keeps it everywhere except where it meets a city end. */
    public static final double MIN_RADIUS = 300;

    /** Spreads every bend tighter than {@code rmin} over more vertices (local Laplacian moves, end points fixed). */
    static void relax(double[] xs, double[] zs, double rmin) {
        for (int it = 0; it < 4000; it++) {
            boolean moved = false;
            for (int i = 1; i < xs.length - 1; i++) {
                if (radius(xs, zs, i) >= rmin) continue;
                xs[i] += 0.5 * (0.5 * (xs[i - 1] + xs[i + 1]) - xs[i]);
                zs[i] += 0.5 * (0.5 * (zs[i - 1] + zs[i + 1]) - zs[i]);
                moved = true;
            }
            if (!moved) return;
        }
    }

    /** Local radius at vertex i: mean adjacent segment length over the turn angle (infinite when straight). */
    static double radius(double[] xs, double[] zs, int i) {
        double ax = xs[i] - xs[i - 1], az = zs[i] - zs[i - 1], bx = xs[i + 1] - xs[i], bz = zs[i + 1] - zs[i];
        double turn = Math.abs(Math.atan2(ax * bz - az * bx, ax * bx + az * bz));
        return turn < 1e-9 ? Double.POSITIVE_INFINITY : 0.5 * (Math.hypot(ax, az) + Math.hypot(bx, bz)) / turn;
    }

    static double[][] simplify(double[] xs, double[] zs, double tol) {
        boolean[] keep = new boolean[xs.length];
        keep[0] = keep[xs.length - 1] = true;
        dp(xs, zs, 0, xs.length - 1, tol, keep);
        int n = 0;
        for (boolean k : keep) if (k) n++;
        double[] ox = new double[n], oz = new double[n];
        for (int i = 0, j = 0; i < xs.length; i++) if (keep[i]) { ox[j] = xs[i]; oz[j] = zs[i]; j++; }
        return new double[][]{ox, oz};
    }

    private static void dp(double[] xs, double[] zs, int a, int b, double tol, boolean[] keep) {
        if (b <= a + 1) return;
        double dx = xs[b] - xs[a], dz = zs[b] - zs[a], l = Math.hypot(dx, dz);
        int best = -1;
        double bd = tol;
        for (int i = a + 1; i < b; i++) {
            double d = l < 1e-9 ? Math.hypot(xs[i] - xs[a], zs[i] - zs[a]) : Math.abs((xs[i] - xs[a]) * dz - (zs[i] - zs[a]) * dx) / l;
            if (d > bd) { bd = d; best = i; }
        }
        if (best < 0) return;
        keep[best] = true;
        dp(xs, zs, a, best, tol, keep);
        dp(xs, zs, best, b, tol, keep);
    }

    static double[][] chaikin(double[] xs, double[] zs) {
        int n = xs.length;
        if (n < 3) return new double[][]{xs, zs};
        double[] ox = new double[2 * (n - 1)], oz = new double[2 * (n - 1)];
        int k = 0;
        ox[k] = xs[0]; oz[k++] = zs[0];
        for (int i = 0; i < n - 1; i++) {
            if (i > 0) { ox[k] = 0.75 * xs[i] + 0.25 * xs[i + 1]; oz[k++] = 0.75 * zs[i] + 0.25 * zs[i + 1]; }
            if (i < n - 2) { ox[k] = 0.25 * xs[i] + 0.75 * xs[i + 1]; oz[k++] = 0.25 * zs[i] + 0.75 * zs[i + 1]; }
        }
        ox[k] = xs[n - 1]; oz[k++] = zs[n - 1];
        return new double[][]{Arrays.copyOf(ox, k), Arrays.copyOf(oz, k)};
    }

    static double[][] resample(double[] xs, double[] zs, double step) {
        double total = 0;
        for (int i = 1; i < xs.length; i++) total += Math.hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
        int n = Math.max(2, (int) Math.round(total / step) + 1);
        double[] ox = new double[n], oz = new double[n];
        double seg = total / (n - 1), acc = 0;
        int j = 1;
        ox[0] = xs[0]; oz[0] = zs[0];
        double run = 0;
        for (int k = 1; k < n - 1; k++) {
            double target = k * seg;
            while (j < xs.length - 1 && run + Math.hypot(xs[j] - xs[j - 1], zs[j] - zs[j - 1]) < target) {
                run += Math.hypot(xs[j] - xs[j - 1], zs[j] - zs[j - 1]);
                j++;
            }
            double l = Math.hypot(xs[j] - xs[j - 1], zs[j] - zs[j - 1]), t = l < 1e-9 ? 0 : (target - run) / l;
            ox[k] = xs[j - 1] + (xs[j] - xs[j - 1]) * t;
            oz[k] = zs[j - 1] + (zs[j] - zs[j - 1]) * t;
        }
        ox[n - 1] = xs[xs.length - 1]; oz[n - 1] = zs[zs.length - 1];
        return new double[][]{ox, oz};
    }

    static Stats stats(FederalMap m, Terrain terrain, double[] xs, double[] zs) {
        double len = 0, maxG = 0, sumG = 0, minR = Double.MAX_VALUE, mountain = 0, wet = 0;
        int borders = 0, water = terrain == null ? 0 : waterRuns(terrain, xs, zs).size();
        double[] w = new double[Physiography.values().length];
        FederalStateId prev = null;
        double[] h = new double[xs.length];
        if (terrain != null) {
            for (int i = 0; i < xs.length; i++) h[i] = terrain.height(xs[i], zs[i]);
            // grade on a 40 m sigma profile (R4's profile smoothing) so 16 m plan steps are not counted as grades
            double[] s = new double[h.length];
            for (int i = 0; i < h.length; i++) {
                double a = 0, ws = 0;
                for (int k = -3; k <= 3; k++) {
                    int q = Math.max(0, Math.min(h.length - 1, i + k));
                    double wk = Math.exp(-0.5 * Math.pow(k * 50 / 40.0, 2));
                    a += wk * h[q]; ws += wk;
                }
                s[i] = a / ws;
            }
            h = s;
        }
        for (int i = 0; i < xs.length; i++) {
            if (i > 0) {
                double l = Math.hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
                len += l;
                if (terrain != null && l > 1e-6) {
                    double g = Math.abs(h[i] - h[i - 1]) / l;
                    maxG = Math.max(maxG, g);
                    sumG += g * l;
                }
            }
            if (i > 0 && i + 1 < xs.length) minR = Math.min(minR, radius(xs, zs, i));
            FederalStateId s = m.governingStateAt(xs[i], zs[i]);
            if (prev != null && s != prev) borders++;
            prev = s;
            m.physiography(xs[i], zs[i], w);
            mountain += w[Physiography.MOUNTAIN.ordinal()];
            wet += w[Physiography.COASTAL_LOWLAND.ordinal()];
        }
        return new Stats(len, maxG, len > 0 ? sumG / len : 0, Math.min(minR, STRAIGHT), borders, mountain / xs.length, wet / xs.length, water, terrain != null);
    }

    // ------------------------------------------------------------------ output
    public void report(PrintStream out) {
        double inter = 0, state = 0, bridge = 0;
        for (Corridor c : corridors) {
            if (c.roadClass() == RoadClass.INTERSTATE) inter += c.stats().length();
            else if (c.roadClass() == RoadClass.STATE_HIGHWAY) state += c.stats().length();
            else bridge += c.stats().length();
        }
        out.printf(Locale.ROOT, "road plan %s (%s): interstate %.1f km, state highways %.1f km, island links %.1f km, %d corridors, %d nodes%n",
                VERSION, graded ? "graded on plan heights" : "template cost only", inter / 1000, state / 1000, bridge / 1000, corridors.size(), nodes.size());
        for (Corridor c : corridors) {
            Stats s = c.stats();
            out.printf(Locale.ROOT, "  %-46s %-14s %6.2f km  borders %d  range %3.0f%%  wet %3.0f%%  min radius %s%s%n", c.id(), c.roadClass(),
                    s.length() / 1000, s.borderCrossings(), 100 * s.mountainShare(), 100 * s.wetlandShare(),
                    s.minRadius() >= STRAIGHT ? "straight" : String.format(Locale.ROOT, "%5.0f m", s.minRadius()),
                    s.graded() ? String.format(Locale.ROOT, "  grade max %.1f%% mean %.1f%%  water %d", 100 * s.maxGrade(), 100 * s.meanGrade(), s.waterCrossings()) : "");
        }
        Map<NodeKind, Integer> count = new LinkedHashMap<>();
        for (Node n : nodes) count.merge(n.kind(), 1, Integer::sum);
        out.println("  nodes " + count);
    }

    /** The plan as JSON (stable field order; coordinates rounded to 0.1 m). */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"version\": \"").append(VERSION).append("\",\n  \"template\": \"").append(FederalTemplate.VERSION)
                .append("\",\n  \"seed\": ").append(map.seed).append(",\n  \"scale\": ").append(map.scale)
                .append(",\n  \"graded\": ").append(graded).append(",\n  \"nodes\": [\n");
        for (int i = 0; i < nodes.size(); i++) {
            Node n = nodes.get(i);
            sb.append(String.format(Locale.ROOT, "    {\"id\": \"%s\", \"kind\": \"%s\", \"x\": %.1f, \"z\": %.1f, \"note\": \"%s\"}%s%n",
                    n.id(), n.kind(), n.x(), n.z(), n.note(), i + 1 < nodes.size() ? "," : ""));
        }
        sb.append("  ],\n  \"corridors\": [\n");
        for (int i = 0; i < corridors.size(); i++) {
            Corridor c = corridors.get(i);
            Stats s = c.stats();
            sb.append(String.format(Locale.ROOT, "    {\"id\": \"%s\", \"class\": \"%s\", \"from\": \"%s\", \"to\": \"%s\", \"reserve_half_width\": %.0f, "
                            + "\"length\": %.1f, \"border_crossings\": %d, \"min_radius\": %.0f, \"max_grade\": %.4f, \"water_crossings\": %d, \"points\": [",
                    c.id(), c.roadClass(), c.from(), c.to(), c.reserve(), s.length(), s.borderCrossings(), s.minRadius(), s.maxGrade(), s.waterCrossings()));
            for (int k = 0; k < c.xs().length; k++)
                sb.append(String.format(Locale.ROOT, "%s[%.1f, %.1f]", k == 0 ? "" : ", ", c.xs()[k], c.zs()[k]));
            sb.append("]}").append(i + 1 < corridors.size() ? "," : "").append('\n');
        }
        sb.append("  ]\n}\n");
        return sb.toString();
    }
}
