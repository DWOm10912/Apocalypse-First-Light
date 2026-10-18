package com.antaurora.apofirstlight.worldgen.network.route;

import com.antaurora.apofirstlight.worldgen.federal.FederalMap;
import com.antaurora.apofirstlight.worldgen.federal.FederalStateId;
import com.antaurora.apofirstlight.worldgen.federal.FederalTemplate;
import com.antaurora.apofirstlight.worldgen.federal.FederalTemplate.CorridorSpec;
import com.antaurora.apofirstlight.worldgen.federal.FederalTemplate.RoadClass;
import com.antaurora.apofirstlight.worldgen.network.road.RoadCurve;
import com.antaurora.apofirstlight.worldgen.network.road.RoadProfile;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The national road network plan, stage G1 of the C3 planning order (docs/worldgen/federal_highways_c3.md, 3): the C1
 * corridors ({@link FederalTemplate#CORRIDORS}) routed again on the final C2 terrain, each leg between two city
 * candidates as one route of its class:
 * <ol>
 *   <li>{@link RouteGrid}: 32 m A* with grade, side-slope and water costs (corridors in template order; running along
 *   inside an earlier route's hard corridor costs extra, so roads do not pile onto one road body);</li>
 *   <li>{@link RoutePolyline}: the cell path to a 40 m control polyline, every bend at least 1.1 x the class radius;</li>
 *   <li>{@link RoadCurve}: the B-spline alignment;</li>
 *   <li>{@link RouteDesigner}: ground survey, the profile within the class's grade limits, bridges, culverts, viaducts,
 *   tunnels and deep cuts;</li>
 *   <li>design feedback: the cells under every station that needed a viaduct, a tunnel, a deep cut or a fill / cut
 *   beyond 60 % of the class maximum get a penalty, and the leg is routed again (up to {@link #ROUNDS} rounds); the
 *   round with the lowest {@link #score} (length, earthworks, structures, mountain grade) is kept.</li>
 * </ol>
 * Joins: a leg whose end city an earlier corridor already serves (one of its legs ends there) may end on that corridor anywhere within
 * {@link #joinRadius} of the city (an interstate on an interstate: or of where that leg ends at the city, a fold-back's
 * bypass passing it, RouteCorners)
 * instead (never on its bridges, viaducts or tunnels): an interstate on an earlier
 * interstate, a state road on an earlier state road or interstate. The A* takes the cheapest meeting point, which
 * becomes an INTERCHANGE (an interstate is involved) or JUNCTION (two state roads) node with both routes and stations;
 * the corridors then share the road into the city, as real routes run concurrently (a state route signed along the
 * interstate to the next exit). A corridor never joins itself (its legs run on through their cities).
 * <p>
 * Nodes: cities, interchanges and junctions (joins, and cities where an interstate and a state road both end),
 * state-border crossings, passes, bridges and tunnels. Reservations: per 2 m station the hard half-width (formation plus the earthworks the
 * profile needs) and the class's soft half-width (C1 reserve).
 * <p>
 * Island bridges (C1 ISLAND_BRIDGE) are not in this network yet (plan-only in C1). Pure Java, no world, no chunks;
 * deterministic for a seed (fixed orders, StrictMath, no hash iteration).
 */
public final class RoadNetworkPlan {
    public static final String VERSION = "afl_road_network_c3_v3";
    /** Douglas-Peucker tolerance on the cell path, m. */
    static final double SIMPLIFY = 12;

    public enum NodeKind { CITY, INTERCHANGE, JUNCTION, BORDER_CROSSING, PASS, BRIDGE, TUNNEL }

    /** A node; route and station locate it on a route (null / NaN for cities). */
    public record Node(String id, NodeKind kind, double x, double z, String route, double station, String note) {}

    /**
     * One planned route: a leg of a C1 corridor between two city candidates. hard[i]: the hard half-width at station
     * i * 2 m (the formation and its earthworks); soft: the class's soft reservation half-width.
     */
    public record Route(String id, String corridor, RoadClassSpec roadClass, String from, String to, Join fromJoin, Join toJoin,
                        RoadCurve curve, RouteDesigner.Result design, double[] hard, double soft, int round, double score, String trace) {
        public RoadProfile profile() { return design.profile(); }
    }

    private final List<Route> routes;
    private final List<Node> nodes;
    private final List<String> failures;

    private RoadNetworkPlan(List<Route> routes, List<Node> nodes, List<String> failures) {
        this.routes = routes;
        this.nodes = nodes;
        this.failures = failures;
    }

    public List<Route> routes() { return routes; }

    public List<Node> nodes() { return nodes; }

    /** Legs the grid could not route (none expected; reported, never silently dropped). */
    public List<String> failures() { return failures; }

    public Route route(String id) {
        for (Route r : routes) if (r.id().equals(id)) return r;
        return null;
    }

    public static RoadClassSpec classOf(RoadClass c) {
        return switch (c) {
            case INTERSTATE -> RoadClassSpec.INTERSTATE;
            case STATE_HIGHWAY -> RoadClassSpec.STATE;
            case ISLAND_BRIDGE -> null;
        };
    }

    /** Where a leg meets an earlier route instead of its city: that route, the station on it, the point. */
    public record Join(String route, double station, double x, double z) {}

    /**
     * Radius round a city inside which a leg may end on an earlier route, m: the joining route's class sets it, so a
     * state road may run concurrently along an interstate as far as interstates join each other (a state route signed
     * along the freeway between two exits), but along another state road only 1200 m.
     */
    /** A route meeting its city this far off it or more passes it on a bypass (joins measured from there), m. */
    static final double BYPASS_OFF = 1000;

    static double joinRadius(RoadClassSpec onto) { return onto == RoadClassSpec.INTERSTATE ? 2500 : 1200; }

    /** Clearance of a join from the other route's bridges, viaducts and tunnels (an interchange needs ground), m. */
    static final double JOIN_CLEAR = 120;

    // ------------------------------------------------------------------ build
    public static RoadNetworkPlan build(FederalMap m, RouteTerrain t) {
        RouteGrid g = new RouteGrid(t);
        List<Route> routes = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        Map<String, Integer> use = new LinkedHashMap<>();
        for (CorridorSpec spec : FederalTemplate.CORRIDORS) {
            RoadClassSpec k = classOf(spec.roadClass());
            if (k == null) continue;
            int first = routes.size();
            for (int q = 0; q + 1 < spec.via().size(); q++) {
                String a = spec.via().get(q), b = spec.via().get(q + 1);
                FederalMap.City ca = m.city(a), cb = m.city(b);
                String id = spec.id() + ":" + a + "-" + b;
                List<Route> onto = joinable(routes, k, spec.id());
                Candidate c = leg(g, t, k, ca, cb, onto);
                if (c == null) { failures.add(id); continue; }
                if (c.curve() == null) { failures.add(id + " (both ends on one earlier route: served by it)"); continue; }
                if (k == RoadClassSpec.INTERSTATE) {
                    // C3.3b group 3: an interstate joins another at a proper angle; its legs pass a town in one bend
                    c = RouteCorners.steepen(g, t, k, c, onto, true);
                    c = RouteCorners.steepen(g, t, k, c, onto, false);
                    Route prev = routes.size() > first ? routes.get(routes.size() - 1) : null;
                    if (prev != null && prev.to().equals(a) && prev.toJoin() == null && c.fromJoin() == null) {
                        RouteCorners.Corner corner = RouteCorners.corner(g, t, k, prev, c, ca, cb, onto);
                        routes.set(routes.size() - 1, corner.a());
                        c = corner.b();
                    }
                }
                double[] hard = hardWidths(k, c.design());
                g.markTaken(c.curve(), hard, RoadProfile.DS, k);
                routes.add(new Route(id, spec.id(), k, a, b, c.fromJoin(), c.toJoin(), c.curve(), c.design(), hard, k.reserve,
                        c.round(), c.score(), c.trace()));
                if (c.fromJoin() == null) use.merge(a, k == RoadClassSpec.INTERSTATE ? 10 : 1, Integer::sum);
                if (c.toJoin() == null) use.merge(b, k == RoadClassSpec.INTERSTATE ? 10 : 1, Integer::sum);
            }
            if (k == RoadClassSpec.INTERSTATE && routes.size() > first) {
                // the legs bent through their towns are designed as one road; the corridors marked again as they now lie
                RouteCorners.designChains(t, k, routes, first);
                g.clearTaken();
                for (Route r : routes) g.markTaken(r.curve(), r.hard(), RoadProfile.DS, r.roadClass());
            }
        }
        return new RoadNetworkPlan(List.copyOf(routes), List.copyOf(nodes(m, t, routes, use)), List.copyOf(failures));
    }

    /** Earlier routes a leg of this class and corridor may join (other corridors: the same class, or interstates for a state road). */
    static List<Route> joinable(List<Route> routes, RoadClassSpec k, String corridor) {
        List<Route> out = new ArrayList<>();
        for (Route r : routes)
            if (!r.corridor().equals(corridor) && (r.roadClass() == k || k == RoadClassSpec.STATE && r.roadClass() == RoadClassSpec.INTERSTATE)) out.add(r);
        return out;
    }

    /** Design-feedback rounds per leg (the first is the plain A*). */
    static final int ROUNDS = 12;

    /** A designed leg (curve null: both ends on one earlier route, nothing to build). */
    record Candidate(int[] cells, RoadCurve curve, RouteDesigner.Result design, double score, int round, String trace, Join fromJoin, Join toJoin) {}

    /**
     * The cells of the joinable routes that serve a city (end at it) within the join radius of it (insertion order),
     * clear of their structures. A route merely passing near a city does not serve it: the leg must reach the city.
     */
    static int[] joinCells(RouteGrid g, List<Route> onto, RoadClassSpec k, FederalMap.City c) {
        LinkedHashSet<Integer> set = new LinkedHashSet<>();
        double[] f = new double[5];
        for (Route r : onto) {
            if (!r.from().equals(c.id()) && !r.to().equals(c.id())) continue;
            // measured from the city, or from where the route meets it when a fold-back's bypass passes it far off
            // (RouteCorners#bypass; a bend through the town meets nearer than BYPASS_OFF: from the city as before)
            // (a leg ending on another route there, a join, is no bypass)
            boolean toCity = r.to().equals(c.id());
            double[] end = toCity ? r.curve().point(r.curve().length, 0) : r.curve().point(0, 0);
            double ox = c.x(), oz = c.z();
            // (interstates on interstates only: a state road still goes to the city, the town's own exit serves the bypass)
            if (k == RoadClassSpec.INTERSTATE && r.roadClass() == RoadClassSpec.INTERSTATE && (toCity ? r.toJoin() : r.fromJoin()) == null
                    && Math.hypot(end[0] - ox, end[1] - oz) > BYPASS_OFF) { ox = end[0]; oz = end[1]; }
            for (double s = 0; s <= r.curve().length; s += 8) {
                if (onStructure(r, s, JOIN_CLEAR)) continue;
                r.curve().frame(s, f);
                if (Math.hypot(f[0] - ox, f[1] - oz) > joinRadius(r.roadClass())) continue;
                int cell = g.cellOf(f[0], f[1]);
                if (!g.closed[cell]) set.add(cell);
            }
        }
        int[] out = new int[set.size()];
        int i = 0;
        for (int v : set) out[i++] = v;
        return out;
    }

    static boolean onStructure(Route r, double s, double clear) {
        for (RouteDesigner.Structure st : r.design().structures())
            if (st.kind() != RouteDesigner.Kind.CULVERT && st.kind() != RouteDesigner.Kind.DEEP_CUT && s >= st.s0() - clear && s <= st.s1() + clear)
                return true;
        return false;
    }

    /** The point on a joinable route nearest to a cell's centre. */
    static Join joinAt(RouteGrid g, List<Route> onto, int cell) {
        double x = g.cx(cell % g.nx), z = g.cz(cell / g.nx);
        Join best = null;
        double bu = Double.MAX_VALUE;
        for (Route r : onto) {
            double[] su = r.curve().local(x, z, 2 * RouteGrid.CELL, 0);
            if (su == null || Math.abs(su[1]) >= bu) continue;
            bu = Math.abs(su[1]);
            double[] p = r.curve().point(su[0], 0);
            best = new Join(r.id(), su[0], p[0], p[1]);
        }
        return best;
    }

    /** One leg: route, design, penalise what fits badly, route again; the best round wins. */
    static Candidate leg(RouteGrid g, RouteTerrain t, RoadClassSpec k, FederalMap.City ca, FederalMap.City cb, List<Route> onto) {
        return leg(g, t, k, ca, null, cb, onto, null);
    }

    /**
     * #leg from a given start point instead of city ca (no joins at that end; C3.3b group 3: an interstate's leg searched
     * again from before a town), with cells the A* may not enter (or null).
     */
    static Candidate leg(RouteGrid g, RouteTerrain t, RoadClassSpec k, FederalMap.City ca, double[] start, FederalMap.City cb, List<Route> onto,
                         boolean[] avoid) {
        float[] pen = new float[g.cells()];
        int[] fromJoin = start != null ? new int[0] : joinCells(g, onto, k, ca), toJoin = joinCells(g, onto, k, cb);
        int[] from = fromJoin.length > 0 ? fromJoin : new int[]{start != null ? g.openCell(start[0], start[1]) : g.openCell(ca.x(), ca.z())};
        boolean[] goal = new boolean[g.cells()];
        if (toJoin.length > 0) for (int c : toJoin) goal[c] = true;
        else goal[g.openCell(cb.x(), cb.z())] = true;
        double gr = 0;
        if (toJoin.length > 0) for (Route r : onto) gr = Math.max(gr, joinRadius(r.roadClass()) + RouteGrid.CELL);
        Candidate best = null;
        StringBuilder trace = new StringBuilder();
        for (int round = 0; round < ROUNDS; round++) {
            int[] cells = g.route(from, goal, cb.x(), cb.z(), gr, k, avoid, pen);
            if (cells == null) break;
            Join j0 = fromJoin.length > 0 ? joinAt(g, onto, cells[0]) : null, j1 = toJoin.length > 0 ? joinAt(g, onto, cells[cells.length - 1]) : null;
            double ax = j0 != null ? j0.x() : start != null ? start[0] : ca.x(), az = j0 != null ? j0.z() : start != null ? start[1] : ca.z();
            double bx = j1 != null ? j1.x() : cb.x(), bz = j1 != null ? j1.z() : cb.z();
            if (cells.length < 3 || Math.hypot(bx - ax, bz - az) < 200) return new Candidate(cells, null, null, 0, round, "", j0, j1);
            double[][] line = RoutePolyline.smooth(g, cells, ax, az, bx, bz, SIMPLIFY, k.radius);
            RoadCurve curve = new RoadCurve(line[0], line[1]);
            RouteDesigner.Result d = RouteDesigner.design(k, curve, t);
            double sc = score(k, d);
            RouteDesigner.Stats s = d.stats();
            trace.append(String.format(Locale.ROOT, "%s%d:%.0f(%.0fm b%d/%.0f v%d/%.0f t%d/%.0f)", round == 0 ? "" : " ", round, sc, s.length(),
                    s.bridges(), s.bridgeLength(), s.viaducts(), s.viaductLength(), s.tunnels(), s.tunnelLength()));
            if (best == null || sc < best.score()) best = new Candidate(cells, curve, d, sc, round, null, j0, j1);
            if (!penalise(g, k, d, pen)) break;
        }
        return best == null ? null
                : new Candidate(best.cells(), best.curve(), best.design(), best.score(), best.round(), trace.toString(), best.fromJoin(), best.toJoin());
    }

    /** What a designed leg costs, in metres of ordinary road: length, earthworks, structures, mountain grade. */
    static double score(RoadClassSpec k, RouteDesigner.Result d) {
        RouteDesigner.Stats s = d.stats();
        double per = RouteGrid.bridgePerMetre(k), fixed = RouteGrid.bridgeFixed(k);
        return s.length() + s.earthworks() / (7 * k.formation)
                + s.bridges() * fixed + s.bridgeLength() * per + s.culverts() * 0.1 * fixed
                + s.viaducts() * fixed + s.viaductLength() * 1.5 * per
                + s.tunnels() * 4 * fixed + s.tunnelLength() * 4 * per
                + 2 * s.mountainGradeLength();
    }

    /**
     * Raises the penalty (per metre) on the cells round every station that fits badly (a disk of the formation plus a
     * cell, so the cell path the smoothed line came from is hit too): inside a viaduct, tunnel or deep
     * cut, or a fill / cut beyond 60 % of the class maximum (bridges and culverts are fine: water must be crossed).
     * False when nothing fits badly.
     */
    static boolean penalise(RouteGrid g, RoadClassSpec k, RouteDesigner.Result d, float[] pen) {
        double[] e = d.profile().samples(), gr = d.ground();
        int n = e.length;
        double[] bad = new double[n];
        boolean[] water = new boolean[n];
        for (RouteDesigner.Structure s : d.structures()) {
            int a = (int) Math.round(s.s0() / RoadProfile.DS), b = Math.min(n - 1, (int) Math.round(s.s1() / RoadProfile.DS));
            // a bridge longer than a square crossing needs (the road runs along the water or over a lake) fits badly too
            double needed = Math.max(40, 3 * s.width() + 24);
            for (int i = a; i <= b; i++) {
                if (s.kind() == RouteDesigner.Kind.CULVERT) water[i] = true;
                else if (s.kind() == RouteDesigner.Kind.BRIDGE) {
                    water[i] = true;
                    if (s.length() > needed) bad[i] = Math.max(bad[i], (s.length() - needed) / 100);
                } else bad[i] = Math.max(bad[i], 1 + s.height() / (s.kind() == RouteDesigner.Kind.VIADUCT ? k.maxFill : k.maxCut));
            }
        }
        boolean any = false;
        boolean[] hit = new boolean[pen.length];
        double[] f = new double[5];
        for (int i = 0; i < n; i += 4) {
            double x = bad[i];
            if (!water[i]) {
                x = Math.max(x, Math.max(0, e[i] - gr[i] - 0.6 * k.maxFill) / k.maxFill);
                x = Math.max(x, Math.max(0, gr[i] - e[i] - 0.6 * k.maxCut) / k.maxCut);
            }
            if (x <= 0) continue;
            any = true;
            d.curve().frame(i * RoadProfile.DS, f);
            // a disk round the station: the formation and the cell path the smoothed line was cut from
            double r = k.formation + RouteGrid.CELL;
            int c0 = g.cellOf(f[0], f[1]), cc = c0 % g.nx, cr = c0 / g.nx, rc = (int) Math.ceil(r / RouteGrid.CELL);
            for (int dr = -rc; dr <= rc; dr++) for (int dc = -rc; dc <= rc; dc++) {
                int col = cc + dc, row = cr + dr;
                if (col < 0 || row < 0 || col >= g.nx || row >= g.nz) continue;
                if (Math.hypot(g.cx(col) - f[0], g.cz(row) - f[1]) > r) continue;
                int c = row * g.nx + col;
                if (hit[c]) continue;
                hit[c] = true;
                pen[c] += (float) Math.min(6, 1 + 2 * x);
            }
        }
        return any;
    }

    /**
     * Hard half-width per station: the formation, plus the fill batter (1:4 to 6 m, then 1:3) or the cut (1 m ditch and a
     * 1:3 backslope), plus 2 m of working margin; over bridges and tunnels the formation and the margin only.
     */
    static double[] hardWidths(RoadClassSpec k, RouteDesigner.Result d) {
        double[] e = d.profile().samples(), gr = d.ground();
        double[] out = new double[e.length];
        boolean[] span = new boolean[e.length];
        for (RouteDesigner.Structure s : d.structures())
            if (s.kind() == RouteDesigner.Kind.BRIDGE || s.kind() == RouteDesigner.Kind.VIADUCT || s.kind() == RouteDesigner.Kind.TUNNEL)
                for (int i = (int) Math.round(s.s0() / RoadProfile.DS); i <= Math.min(e.length - 1, (int) Math.round(s.s1() / RoadProfile.DS)); i++) span[i] = true;
        for (int i = 0; i < e.length; i++) {
            double h = e[i] - gr[i], side;
            if (span[i]) side = 0;
            else if (h >= 0) side = 4 * Math.min(h, 6) + 3 * Math.max(0, h - 6);
            else side = 1 + 3 * -h;
            out[i] = k.formation + side + 2;
        }
        return out;
    }

    static List<Node> nodes(FederalMap m, RouteTerrain t, List<Route> routes, Map<String, Integer> use) {
        List<Node> nodes = new ArrayList<>();
        for (FederalMap.City c : m.cities()) {
            int u = use.getOrDefault(c.id(), 0);
            if (u == 0) continue;
            int inter = u / 10, state = u % 10;
            // a city an interstate leg ends at has its exit; three or more state road ends make a junction
            NodeKind kind = inter >= 1 ? NodeKind.INTERCHANGE : state >= 3 ? NodeKind.JUNCTION : NodeKind.CITY;
            nodes.add(new Node(c.id(), kind, c.x(), c.z(), null, Double.NaN, c.state().key() + " " + c.role()));
        }
        for (Route r : routes)
            for (Join j : new Join[]{r.fromJoin(), r.toJoin()}) {
                if (j == null) continue;
                Route on = null;
                for (Route o : routes) if (o.id().equals(j.route())) on = o;
                boolean inter = r.roadClass() == RoadClassSpec.INTERSTATE || on != null && on.roadClass() == RoadClassSpec.INTERSTATE;
                String note = String.format(Locale.ROOT, "%s meets %s at %.0f m", r.id(), j.route(), j.station());
                // two legs meeting one host route a few metres apart (a state route crossing the freeway at one exit): one node
                int same = -1;
                for (int q = 0; q < nodes.size(); q++) {
                    Node n = nodes.get(q);
                    if (j.route().equals(n.route()) && Math.abs(n.station() - j.station()) < JOIN_MERGE) same = q;
                }
                if (same >= 0) {
                    Node n = nodes.get(same);
                    nodes.set(same, new Node(n.id(), inter ? NodeKind.INTERCHANGE : n.kind(), n.x(), n.z(), n.route(), n.station(), n.note() + "; " + note));
                    continue;
                }
                nodes.add(new Node(r.id() + (j == r.fromJoin() ? "/join/start" : "/join/end"), inter ? NodeKind.INTERCHANGE : NodeKind.JUNCTION,
                        j.x(), j.z(), j.route(), j.station(), note));
            }
        double[] f = new double[5];
        for (Route r : routes) {
            FederalStateId prev = null;
            double[] e = r.profile().samples();
            double passS = -1, passE = Double.NEGATIVE_INFINITY;
            for (double s = 0; s <= r.curve().length; s += 10) {
                r.curve().frame(s, f);
                FederalStateId st = t.state(f[0], f[1]);
                if (prev != null && st != null && st != prev && !near(nodes, NodeKind.BORDER_CROSSING, f[0], f[1], 200))
                    nodes.add(new Node(r.id() + "/border/" + prev.key() + "-" + st.key(), NodeKind.BORDER_CROSSING, f[0], f[1], r.id(), s,
                            prev.key() + " -> " + st.key()));
                if (st != null) prev = st;
            }
            for (int i = 0; i < e.length; i++) {
                double s = i * RoadProfile.DS;
                r.curve().frame(s, f);
                // the summit of the mountain section, away from the leg's ends (a city or a join is not a pass)
                if (s < PASS_END_CLEAR || s > r.curve().length - PASS_END_CLEAR) continue;
                if (t.rugged(f[0], f[1]) >= RouteDesigner.MOUNTAIN_SHARE && e[i] > passE) { passE = e[i]; passS = s; }
            }
            if (passS >= 0) {
                r.curve().frame(passS, f);
                nodes.add(new Node(r.id() + "/pass", NodeKind.PASS, f[0], f[1], r.id(), passS, String.format(Locale.ROOT, "summit Y%.1f", passE)));
            }
            int nb = 0, nt = 0;
            for (RouteDesigner.Structure s : r.design().structures()) {
                if (s.kind() != RouteDesigner.Kind.BRIDGE && s.kind() != RouteDesigner.Kind.TUNNEL) continue;
                double mid = 0.5 * (s.s0() + s.s1());
                r.curve().frame(mid, f);
                boolean bridge = s.kind() == RouteDesigner.Kind.BRIDGE;
                nodes.add(new Node(r.id() + (bridge ? "/bridge/" + nb++ : "/tunnel/" + nt++), bridge ? NodeKind.BRIDGE : NodeKind.TUNNEL,
                        f[0], f[1], r.id(), mid, String.format(Locale.ROOT, "%.0f m, %s", s.length(), s.note())));
            }
        }
        return nodes;
    }

    static final double PASS_END_CLEAR = 300;

    /** Joins on one host route closer than this share one interchange / junction node, m. */
    static final double JOIN_MERGE = 150;

    /** Clearance from a leg's ends (cities and joins, where roads meet anyway) of the shared-corridor check, m. */
    static final double SHARED_END_CLEAR = 300;
    /** Shorter overlaps are crossings, not shared road bodies, m. */
    static final double SHARED_MIN_RUN = 150;

    /**
     * Where a route's formation runs inside another route's hard corridor (two road bodies in one place) for more than
     * {@link #SHARED_MIN_RUN} m, away from the leg's ends: metres per other route (insertion order). A plan check: the
     * C3 network should have none.
     */
    public static Map<String, Double> shared(Route r, List<Route> routes) {
        Map<String, Double> out = new LinkedHashMap<>();
        double[] f = new double[5];
        for (Route o : routes) {
            if (o == r) continue;
            double maxHard = 0;
            for (double h : o.hard()) maxHard = Math.max(maxHard, h);
            double run = 0, total = 0;
            for (double s = SHARED_END_CLEAR; s <= r.curve().length - SHARED_END_CLEAR; s += 10) {
                r.curve().frame(s, f);
                double[] su = o.curve().local(f[0], f[1], maxHard + r.roadClass().formation, 0);
                boolean in = su != null && Math.abs(su[1]) < o.hard()[Math.min(o.hard().length - 1, (int) Math.round(su[0] / RoadProfile.DS))]
                        + r.roadClass().formation;
                if (in) run += 10;
                else { if (run > SHARED_MIN_RUN) total += run; run = 0; }
            }
            if (run > SHARED_MIN_RUN) total += run;
            if (total > 0) out.put(o.id(), total);
        }
        return out;
    }

    static boolean near(List<Node> nodes, NodeKind kind, double x, double z, double r) {
        for (Node n : nodes) if (n.kind() == kind && Math.hypot(n.x() - x, n.z() - z) < r) return true;
        return false;
    }

    // ------------------------------------------------------------------ output
    public void report(PrintStream out) {
        out.printf(Locale.ROOT, "%s: %d routes, %d nodes, %d unrouted%n", VERSION, routes.size(), nodes.size(), failures.size());
        for (String f : failures) out.println("  UNROUTED " + f);
        double[] total = new double[RoadClassSpec.values().length];
        for (Route r : routes) {
            RouteDesigner.Stats s = r.design().stats();
            total[r.roadClass().ordinal()] += s.length();
            out.printf(Locale.ROOT, "%-46s %-10s %6.0f m  grade max %4.1f%% mean %4.1f%% (> design grade %4.0f m)  Rmin %5.0f  Kmin %5.0f (< %2.0f: %4.0f m)  round %d score %6.0f%n",
                    r.id(), r.roadClass().id, s.length(), 100 * s.maxGrade(), 100 * s.meanGrade(), s.mountainGradeLength(),
                    Math.min(99999, s.minRadius()), Math.min(99999, s.minK()), r.roadClass().kMin(), s.belowK(), r.round(), r.score());
            out.printf(Locale.ROOT, "%-46s cut max %4.1f m  fill max %4.1f m  earthworks %6.0f k m3  bridges %d (%4.0f m)  culverts %d  viaducts %d (%4.0f m)  tunnels %d (%4.0f m)  deep cuts %d%n",
                    "", s.maxCut(), s.maxFill(), s.earthworks() / 1000, s.bridges(), s.bridgeLength(), s.culverts(), s.viaducts(), s.viaductLength(),
                    s.tunnels(), s.tunnelLength(), s.deepCuts());
            for (RouteDesigner.Structure st : r.design().structures())
                out.printf(Locale.ROOT, "%-46s   %-8s %6.0f..%6.0f  %5.0f m  h %4.1f  %s%n", "", st.kind(), st.s0(), st.s1(), st.length(), st.height(), st.note());
            if (r.fromJoin() != null) out.printf(Locale.ROOT, "%-46s   starts on %s at %.0f m (%.0f, %.0f)%n", "", r.fromJoin().route(), r.fromJoin().station(), r.fromJoin().x(), r.fromJoin().z());
            if (r.toJoin() != null) out.printf(Locale.ROOT, "%-46s   ends on %s at %.0f m (%.0f, %.0f)%n", "", r.toJoin().route(), r.toJoin().station(), r.toJoin().x(), r.toJoin().z());
            for (Map.Entry<String, Double> e : shared(r, routes).entrySet())
                out.printf(Locale.ROOT, "%-46s   SHARES %4.0f m of road body with %s%n", "", e.getValue(), e.getKey());
            out.printf(Locale.ROOT, "%-46s   rounds %s%n", "", r.trace());
        }
        for (RoadClassSpec k : RoadClassSpec.values())
            if (total[k.ordinal()] > 0) out.printf(Locale.ROOT, "total %-10s %7.1f km%n", k.id, total[k.ordinal()] / 1000);
        for (Node n : nodes)
            out.printf(Locale.ROOT, "node %-16s %-58s %8.0f %8.0f  %s%n", n.kind(), n.id(), n.x(), n.z(), n.note());
    }

    /** The plan as JSON: routes (control polylines, profile every 2 m, structures, hard widths) and nodes. */
    public String toJson() {
        StringBuilder b = new StringBuilder(1 << 20);
        b.append("{\"version\":\"").append(VERSION).append("\",\"routes\":[");
        for (int i = 0; i < routes.size(); i++) {
            Route r = routes.get(i);
            if (i > 0) b.append(',');
            b.append("{\"id\":\"").append(r.id()).append("\",\"corridor\":\"").append(r.corridor()).append("\",\"class\":\"").append(r.roadClass().id)
                    .append("\",\"from\":\"").append(r.from()).append("\",\"to\":\"").append(r.to()).append('"');
            for (Join j : new Join[]{r.fromJoin(), r.toJoin()})
                if (j != null) b.append(j == r.fromJoin() ? ",\"fromJoin\":" : ",\"toJoin\":").append("{\"route\":\"").append(j.route()).append("\",\"station\":")
                        .append(fmt(j.station())).append('}');
            b.append(",\"length\":")
                    .append(fmt(r.curve().length)).append(",\"soft\":").append(fmt(r.soft())).append(",\"control\":[");
            double[] xs = r.curve().controlX(), zs = r.curve().controlZ();
            for (int q = 0; q < xs.length; q++) b.append(q > 0 ? "," : "").append('[').append(fmt(xs[q])).append(',').append(fmt(zs[q])).append(']');
            b.append("],\"profile\":[");
            double[] e = r.profile().samples();
            for (int q = 0; q < e.length; q++) b.append(q > 0 ? "," : "").append(fmt(e[q]));
            b.append("],\"hard\":[");
            for (int q = 0; q < r.hard().length; q++) b.append(q > 0 ? "," : "").append(fmt(r.hard()[q]));
            b.append("],\"structures\":[");
            List<RouteDesigner.Structure> ss = r.design().structures();
            for (int q = 0; q < ss.size(); q++) {
                RouteDesigner.Structure s = ss.get(q);
                b.append(q > 0 ? "," : "").append("{\"kind\":\"").append(s.kind()).append("\",\"s0\":").append(fmt(s.s0())).append(",\"s1\":")
                        .append(fmt(s.s1())).append(",\"height\":").append(fmt(s.height())).append('}');
            }
            b.append("]}");
        }
        b.append("],\"nodes\":[");
        for (int i = 0; i < nodes.size(); i++) {
            Node n = nodes.get(i);
            b.append(i > 0 ? "," : "").append("{\"id\":\"").append(n.id()).append("\",\"kind\":\"").append(n.kind()).append("\",\"x\":")
                    .append(fmt(n.x())).append(",\"z\":").append(fmt(n.z()));
            if (n.route() != null) b.append(",\"route\":\"").append(n.route()).append("\",\"station\":").append(fmt(n.station()));
            b.append('}');
        }
        b.append("]}");
        return b.toString();
    }

    /** SHA-256 (first 16 hex digits) of the JSON: the route digest the world lock compares. */
    public String digest() {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(toJson().getBytes(StandardCharsets.UTF_8));
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < 8; i++) s.append(String.format(Locale.ROOT, "%02x", h[i]));
            return s.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String fmt(double v) { return String.format(Locale.ROOT, "%.2f", v); }
}
