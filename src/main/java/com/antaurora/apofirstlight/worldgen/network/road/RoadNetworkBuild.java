package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson.Node;
import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson.RouteData;
import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson.Structure;
import com.antaurora.apofirstlight.worldgen.network.route.RoadClassSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The national rollout's built network (C3.3a, C3.3b group 1; docs/worldgen/federal_highways_c3.md, 11;
 * docs/worldgen/federal_junctions_c3_3b.md): every planned route of the C3.1 plan, turned into the routes the world
 * builds, as one JSON (RoadNetworkJson.write) the server's works and every client read. Pure Java, deterministic: a
 * function of the plan JSON only.
 * <ul>
 *   <li>through towns: legs of one corridor that meet end to start at a town node (not a join) with a kink of at most
 *   MAX_THROUGH_KINK are one road; around each such node the headings of the control polyline are smoothed (a Gaussian
 *   of sigma points, blended in over 4 sigma, the small position error spread over the window), sigma growing until the
 *   curve there keeps the class's minimum radius; the profile is taken from the legs (each sample on a leg takes its
 *   height, across the corner linear) and smoothed over the node with the class's vertical sigma;</li>
 *   <li>town corners (C3.3b): state roads also join up to MAX_TOWN_KINK at TOWN_RADIUS, across corridors at a shared
 *   town node and where a join lies within NEAR_END of a town end; sharper corners leave one road running STUB on; a
 *   town corner's grade is spread (#regrade) or the corner kept apart (#through);</li>
 *   <li>T junctions (C3.3b): a state road ending on another state road's centreline becomes the minor of a T junction
 *   (RoadJunctions: realigned under its skew, landed, its end line at the pad), its conflicts with the host ignored;</li>
 *   <li>trims: until C3.3b groups 2 and 3 build the interchanges, a road ending at or near another (a join, a town two
 *   roads share, a sharper kink) stops short: where the conflicts with another road (its works reaching the other's formation,
 *   or its formation the other's works: RoadWorks.REACH, the end tapers RoadDesign.END) run from the end itself
 *   (beginning within TOUCH of it), the end moves inward past them by RoadDesign.END + TRIM_MARGIN; checked against every
 *   road of the same or a higher class; conflicts in the middle of a road are the other road's end (it trims); an end
 *   never stops inside a bridge or a culvert (STRUCTURE_CLEAR);</li>
 *   <li>verify: the trimmed network is checked again, every pair both ways; what remains is reported (the offline
 *   check prints it, the server logs it).</li>
 * </ul>
 */
public final class RoadNetworkBuild {
    private RoadNetworkBuild() {}

    public static final String STAGE = "c3.3b";
    /**
     * C3.3b town corners (docs/worldgen/federal_junctions_c3_3b.md 4): the corner radius at a town (50 km/h) for kinks
     * over MAX_THROUGH_KINK up to MAX_TOWN_KINK (degrees); sharper corners keep apart, one road running STUB m on.
     */
    public static final double TOWN_RADIUS = 80, MAX_TOWN_KINK = 120, STUB = 64;
    /** A join this close (m) to another state road's town end turns into that road (a corner, not a T). */
    public static final double NEAR_END = 150;
    /** Town corner regrade: the reach of the grade check around the node, the farthest the grade may be spread, m. */
    public static final double GRADE_CHECK = 300, REGRADE_REACH = 1500;
    /** The smallest vertical curve K at a town corner: AASHTO's sag K at its 50 km/h (13 m per %; crest 7). */
    public static final double TOWN_K = 13;
    /** Same-corridor legs with at most this kink (degrees) at a shared town node are one road. */
    public static final double MAX_THROUGH_KINK = 45;
    /** The heading smoothing's sigma at a through node, in control points: 1 .. MAX_SIGMA. */
    public static final int MAX_SIGMA = 14;
    /** Projection reach of node lookups on a merged road, m. */
    public static final double MAX_CORNER = 600;
    /** The planner's control point spacing, m (the smoothed corner reaches 4 sigma points of it each way). */
    public static final double POINT_SPACING = 40;
    /** A merged road's sample within this of a leg's centreline takes that leg's height, m. */
    public static final double ON_LEG = 3;
    /**
     * Trims: a run of conflict stations trims an end when it begins within TOUCH of it (stations from -END), and runs on
     * with gaps of at most RUN_GAP; the extra margin; the clearance from structures, m.
     */
    public static final double TOUCH = 80, RUN_GAP = 10, TRIM_MARGIN = 4, STRUCTURE_CLEAR = 10;
    /**
     * A tunnel's clearance from a road's end (Tunnel V1): its portal approaches (TunnelWorks.APPROACH) and a margin; an
     * end within it (also an untrimmed end: a route's town) moves past the tunnel, m.
     */
    public static final double TUNNEL_CLEAR = TunnelWorks.APPROACH + 10;
    /** The shortest piece of a road kept after trims and splits, m; the most splits of one network. */
    public static final double MIN_PIECE = 200;
    public static final int MAX_SPLITS = 32;

    /**
     * The built network: its JSON (routes only), the routes, the report lines, the conflicts left, and the framework towns
     * laid out on it (Town Framework V1: appended to the JSON after RoadStructures, TownPlan.append).
     */
    public record Result(String json, List<RouteData> routes, List<String> report, int conflicts, List<com.antaurora.apofirstlight.worldgen.network.town.TownPlan> towns) {
        public Result(String json, List<RouteData> routes, List<String> report, int conflicts) { this(json, routes, report, conflicts, List.of()); }
    }

    public static Result build(String planJson) { return build(planJson, null); }

    /**
     * The built network; with terrain (the world's planned ground) the framework towns' exits are added after the roads
     * through towns are joined (Town Framework V1, TownExits: two state stubs the diamond builder pairs into one crossroad).
     * Without terrain no town is laid out (the offline highway checks).
     */
    public static Result build(String planJson, com.antaurora.apofirstlight.worldgen.network.route.RouteTerrain terrain) {
        List<RouteData> legs = new ArrayList<>();
        List<Node> nodes = RoadNetworkJson.nodes(planJson);
        String version = planVersion(planJson);
        List<String> report = new ArrayList<>();
        // a route the stage cannot build (a tunnel) is left out whole, never an error: the plan depends on the seed
        for (RouteData r : RoadNetworkJson.routes(planJson)) {
            String why = RoadNetworkJson.buildable(r);
            if (why == null) legs.add(r);
            else report.add("left out: " + r.id() + " (" + why + ")");
        }
        List<RouteData> roads = through(legs, nodes, report);
        // C3.3b group 3: the system interchanges (an interstate's end on another as a trumpet), before the diamonds
        RoadSystems.Result sr = RoadSystems.build(roads, nodes, report);
        roads = sr.roads();
        // Town Framework V1: each framework town's exit, the first of its options the diamond builder builds
        if (terrain != null) roads = townExits(roads, legs, terrain, sr.claims(), report);
        // C3.3b group 2: the diamond interchanges (state roads over the interstates, the ramps, their terminals)
        RoadInterchanges.Result ir = RoadInterchanges.build(roads, sr.claims(), report);
        roads = ir.roads();
        List<Zone> extra = new ArrayList<>(sr.zones());
        extra.addAll(ir.zones());
        // C3.3b: the T junctions (their minors realigned and landed), the conflicts they join ignored
        RoadJunctions.Result jr = RoadJunctions.find(roads, report);
        roads = jr.roads();
        List<RoadJunctions.Spec> specs = new ArrayList<>(jr.junctions());
        specs.addAll(ir.terminals());
        List<Zone> zones = zones(specs, extra);
        List<RouteData> trimmed = splits(trims(roads, specs, zones, report), zones, report);
        List<RoadJunctions.Spec> kept = new ArrayList<>();
        trimmed = RoadJunctions.resolve(trimmed, specs, kept, report);
        int conflicts = verify(trimmed, zones(kept, extra), report);
        // Town Framework V1: each town with an exit gets its grid; its crossroad stops at the Front St corners
        List<com.antaurora.apofirstlight.worldgen.network.town.TownPlan> towns = new ArrayList<>();
        if (terrain != null) trimmed = towns(trimmed, terrain, towns, report);
        return new Result(RoadNetworkJson.write(version, STAGE, trimmed), trimmed, report, conflicts, towns);
    }

    /**
     * Town Framework V1 (TownExits): every framework town's exit options tried in turn with the diamond builder on their
     * own roads only, each one that builds a crossroad of the town laid out with TownPlanner on the trial roads (2026-10-17:
     * the exits' own land measure sees small slopes, not a hillside whose every block climbs more than the planner's relief
     * limit): the first whose town reaches GOOD_TOWN blocks is kept, else the one with the most blocks (the earlier on a
     * tie); its stubs go first in the road list, so the full build pairs them the same way. A town none of whose options
     * builds with a town round it keeps no stub (reported).
     */
    private static List<RouteData> townExits(List<RouteData> roads, List<RouteData> legs, com.antaurora.apofirstlight.worldgen.network.route.RouteTerrain terrain,
                                             List<RoadSystems.Claim> claims, List<String> report) {
        List<RouteData> out = roads;
        for (com.antaurora.apofirstlight.worldgen.network.town.TownExits.Site site : com.antaurora.apofirstlight.worldgen.network.town.TownExits.sites(roads, legs, terrain, report)) {
            String corridor = com.antaurora.apofirstlight.worldgen.network.town.TownSites.corridor(site.town());
            List<RouteData> best = null;
            int bestBlocks = 0, bestK = 0;
            String bestLine = null;
            int k = 0;
            for (com.antaurora.apofirstlight.worldgen.network.town.TownExits.Option o : site.options()) {
                k++;
                List<RouteData> trial = new ArrayList<>(o.stubs());
                trial.addAll(out);
                List<String> tr = new ArrayList<>();
                RoadInterchanges.Result ir = RoadInterchanges.build(trial, claims, tr, o.roads()::contains);
                boolean built = false;
                for (RouteData r : ir.roads())
                    if (r.id().contains(corridor + ":")) for (Structure st : r.structures()) if (st.kind().equals("OVERPASS")) built = true;
                if (!built) {
                    String why = "";
                    // the skip line: "interchange skipped (trimmed): <site>: <why per shift>"
                    for (String l : tr) if (l.startsWith("interchange skipped")) why = l.substring(l.indexOf(": ", "interchange skipped (trimmed): ".length()) + 2);
                    report.add(o.line() + ": not built" + (why.isEmpty() ? "" : " (" + (why.length() > 400 ? why.substring(0, 400) + "..." : why) + ")"));
                    continue;
                }
                String[] noTown = new String[1];
                int blocks = townBlocks(site.town(), ir.roads(), terrain, noTown);
                if (blocks <= 0) {
                    report.add(o.line() + String.format(Locale.ROOT, ": built, but no town round it (%s; option %d of %d)", noTown[0], k, site.options().size()));
                    continue;
                }
                if (blocks > bestBlocks) { best = trial; bestBlocks = blocks; bestK = k; bestLine = o.line(); }
                if (blocks >= GOOD_TOWN) break;
                report.add(o.line() + String.format(Locale.ROOT, ": built, a town of %d blocks round it (option %d of %d; looking on for %d)", blocks, k, site.options().size(), GOOD_TOWN));
            }
            if (best == null) report.add("town " + site.town() + ": no exit option builds with a town round it; the town has no interchange");
            else {
                report.add(bestLine + String.format(Locale.ROOT, ": built (option %d of %d, a town of %d blocks)", bestK, site.options().size(), bestBlocks));
                out = best;
            }
        }
        return out;
    }

    /** A town of this many blocks round an exit option ends the search (TownPlanner.MAX_BLOCKS is the most). */
    static final int GOOD_TOWN = 20;

    /**
     * The blocks of the town laid out round its built exit in these roads, or 0 (why[0] says why: the planner's last
     * report line).
     */
    private static int townBlocks(String town, List<RouteData> roads, com.antaurora.apofirstlight.worldgen.network.route.RouteTerrain terrain, String[] why) {
        RouteData[] xh = exitAndHost(town, roads);
        if (xh == null) { why[0] = "the exit's crossroad or interstate is not found"; return 0; }
        try {
            com.antaurora.apofirstlight.worldgen.network.town.TownPlanner.Result tr = com.antaurora.apofirstlight.worldgen.network.town.TownPlanner.plan(town, xh[0], xh[1], roads, terrain);
            if (tr == null) { why[0] = "the exit's crossroad is not a town stub"; return 0; }
            if (tr.plan() != null) return tr.plan().blocks.size();
            List<String> r = tr.report();
            why[0] = r.isEmpty() ? "no plan" : r.get(r.size() - 1).replaceFirst("^town [a-z_]+: ", "");
            return 0;
        } catch (RuntimeException e) {
            why[0] = "layout failed (" + e + ")";
            return 0;
        }
    }

    /** The town's exit crossroad (its corridor's route with an OVERPASS) and the interstate under it, or null. */
    private static RouteData[] exitAndHost(String town, List<RouteData> routes) {
        String corridor = com.antaurora.apofirstlight.worldgen.network.town.TownSites.corridor(town);
        RouteData x = null;
        for (RouteData r : routes) {
            if (!r.id().contains(corridor + ":")) continue;
            for (Structure st : r.structures()) if (st.kind().equals("OVERPASS")) x = r;
        }
        if (x == null) return null;
        RoadDesign xd = RoadNetworkJson.design(x);
        double off = x.trim() == null ? 0 : x.trim()[0], xc = 0;
        for (Structure st : x.structures()) if (st.kind().equals("OVERPASS")) xc = (st.s0() + st.s1()) / 2;
        double[] pc = xd.point(xc - off, 0);
        for (RouteData r : routes)
            if (r.roadClass().equals("interstate") && RoadNetworkJson.design(r).local(pc[0], pc[1], 30) != null) return new RouteData[]{x, r};
        return null;
    }

    /**
     * Town Framework V1 (TownPlanner): every framework town whose exit was built (a crossroad of its corridor over an
     * interstate) laid out round it; the crossroad replaced by its trimmed self. Towns without an exit get no grid.
     */
    private static List<RouteData> towns(List<RouteData> routes, com.antaurora.apofirstlight.worldgen.network.route.RouteTerrain terrain,
                                         List<com.antaurora.apofirstlight.worldgen.network.town.TownPlan> towns, List<String> report) {
        List<RouteData> out = new ArrayList<>(routes);
        for (String town : com.antaurora.apofirstlight.worldgen.network.town.TownSites.FRAMEWORK) {
            String corridor = com.antaurora.apofirstlight.worldgen.network.town.TownSites.corridor(town);
            int xi = -1;
            for (int i = 0; i < out.size(); i++) {
                RouteData r = out.get(i);
                if (!r.id().contains(corridor + ":")) continue;
                for (Structure st : r.structures()) if (st.kind().equals("OVERPASS")) xi = i;
            }
            if (xi < 0) { report.add("town " + town + ": no exit; no street framework"); continue; }
            RouteData x = out.get(xi);
            // the interstate under the overpass
            RoadDesign xd = RoadNetworkJson.design(x);
            double off = x.trim() == null ? 0 : x.trim()[0], xc = 0;
            for (Structure st : x.structures()) if (st.kind().equals("OVERPASS")) xc = (st.s0() + st.s1()) / 2;
            double[] pc = xd.point(xc - off, 0);
            RouteData host = null;
            for (RouteData r : out)
                if (r.roadClass().equals("interstate") && RoadNetworkJson.design(r).local(pc[0], pc[1], 30) != null) host = r;
            if (host == null) { report.add("town " + town + ": the exit's interstate is not found; no street framework"); continue; }
            try {
                com.antaurora.apofirstlight.worldgen.network.town.TownPlanner.Result tr =
                        com.antaurora.apofirstlight.worldgen.network.town.TownPlanner.plan(town, x, host, out, terrain);
                if (tr == null) { report.add("town " + town + ": the exit's crossroad is not a town stub; no street framework"); continue; }
                report.addAll(tr.report());
                if (tr.plan() == null) continue;
                towns.add(tr.plan());
                if (tr.crossroad() != null) out.set(xi, tr.crossroad());
            } catch (RuntimeException e) {
                // a town that cannot be laid out never costs the world its roads
                report.add("town " + town + ": layout failed (" + e + "); no street framework");
            }
        }
        return out;
    }

    private static String planVersion(String json) {
        int i = json.indexOf("\"version\":\"");
        if (i < 0) return "unknown";
        i += "\"version\":\"".length();
        return json.substring(i, json.indexOf('"', i));
    }

    // ------------------------------------------------------------------ through towns

    private static double heading(double[] xs, double[] zs, boolean end) {
        int n = xs.length;
        return end ? Math.atan2(zs[n - 1] - zs[n - 2], xs[n - 1] - xs[n - 2]) : Math.atan2(zs[1] - zs[0], xs[1] - xs[0]);
    }

    private static double kink(double a, double b) {
        double d = Math.toDegrees(b - a);
        while (d > 180) d -= 360;
        while (d < -180) d += 360;
        return Math.abs(d);
    }

    private static boolean joinAt(List<Node> nodes, double x, double z) {
        for (Node n : nodes) if (n.id().contains("/join/") && Math.hypot(n.x() - x, n.z() - z) < 1) return true;
        return false;
    }

    /** A route driven the other way: control points, profile and structures reversed, its towns swapped. */
    static RouteData reverse(RouteData r) {
        int n = r.xs().length;
        double[] xs = new double[n], zs = new double[n];
        for (int i = 0; i < n; i++) { xs[i] = r.xs()[n - 1 - i]; zs[i] = r.zs()[n - 1 - i]; }
        double len = new RoadCurve(r.xs(), r.zs()).length;
        RoadProfile p = RoadProfile.of(r.profile());
        double[] e = new double[r.profile().length];
        for (int i = 0; i < e.length; i++) e[i] = RoadNetworkJson.round2(p.at(len - i * RoadProfile.DS));
        List<Structure> ss = new ArrayList<>();
        for (int i = r.structures().size() - 1; i >= 0; i--) {
            Structure s = r.structures().get(i);
            ss.add(new Structure(s.kind(), RoadNetworkJson.round2(len - s.s1()), RoadNetworkJson.round2(len - s.s0()), s.height()));
        }
        return new RouteData(r.id(), r.roadClass(), r.corridor(), r.to(), r.from(), xs, zs, e, List.copyOf(ss), null);
    }

    /** Legs to become one road, in driving order, with the corner radius at each node between two legs. */
    private static final class Chain {
        final List<RouteData> legs = new ArrayList<>();
        final List<Double> radius = new ArrayList<>();

        Chain(RouteData leg) { legs.add(leg); }

        RouteData first() { return legs.get(0); }

        RouteData last() { return legs.get(legs.size() - 1); }

        boolean state() { return first().roadClass().equals("state"); }

        /** The chain's start (end false) or end point. */
        double[] point(boolean end) {
            RouteData r = end ? last() : first();
            int i = end ? r.xs().length - 1 : 0;
            return new double[]{r.xs()[i], r.zs()[i]};
        }

        /** Heading arriving at its end (end) or at its start driven backwards (the way into the node). */
        double arriving(boolean end) {
            return end ? heading(last().xs(), last().zs(), true) : heading(first().xs(), first().zs(), false) + Math.PI;
        }

        /** Heading leaving its start (start) or its end driven backwards (the way out of the node). */
        double leaving(boolean end) {
            return end ? heading(last().xs(), last().zs(), true) + Math.PI : heading(first().xs(), first().zs(), false);
        }

        void reverse() {
            java.util.Collections.reverse(legs);
            legs.replaceAll(RoadNetworkBuild::reverse);
            java.util.Collections.reverse(radius);
        }

        /** This chain (its end) then b (its start), the corner at the node radius r. */
        void append(Chain b, double r) {
            radius.add(r);
            legs.addAll(b.legs);
            radius.addAll(b.radius);
        }
    }

    /** The corner radius of a kink at a town (degrees): the class's up to MAX_THROUGH_KINK, the town radius up to MAX_TOWN_KINK, else NaN. */
    private static double cornerRadius(RoadClassSpec k, double kink) {
        return kink <= MAX_THROUGH_KINK ? k.radius : kink <= MAX_TOWN_KINK ? TOWN_RADIUS : Double.NaN;
    }

    /**
     * Roads through towns (C3.3a; C3.3b town corners, docs/worldgen/federal_junctions_c3_3b.md 4): legs of one corridor
     * meeting end to start at a town node (not a join) are one road, at the class radius up to MAX_THROUGH_KINK and the
     * town radius up to MAX_TOWN_KINK; then state roads of different corridors ending at one town node pair up (the
     * smallest kink first); a state road whose join lies within NEAR_END of a state road's town end turns into it there;
     * two state roads left at a town with a sharper corner keep apart, one runs STUB on (the other joins it as a T).
     * Each chain is merged (#merge); a town corner whose grade cannot be spread keeps its roads apart.
     */
    private static List<RouteData> through(List<RouteData> legs, List<Node> nodes, List<String> report) {
        int n = legs.size();
        int[] next = new int[n], prev = new int[n];
        double[] rad = new double[n];
        java.util.Arrays.fill(next, -1);
        java.util.Arrays.fill(prev, -1);
        for (int i = 0; i < n; i++) {
            RouteData a = legs.get(i);
            double ax = a.xs()[a.xs().length - 1], az = a.zs()[a.zs().length - 1];
            if (joinAt(nodes, ax, az)) continue;
            for (int j = 0; j < n; j++) {
                RouteData b = legs.get(j);
                if (i == j || !a.corridor().equals(b.corridor()) || !a.roadClass().equals(b.roadClass())) continue;
                if (Math.hypot(b.xs()[0] - ax, b.zs()[0] - az) > 0.5 || prev[j] >= 0) continue;
                double k = kink(heading(a.xs(), a.zs(), true), heading(b.xs(), b.zs(), false));
                // C3.3b: the town radius for the state roads (the interstate's corners are group 3)
                double r = a.roadClass().equals("state") ? cornerRadius(spec(a.roadClass()), k) : k <= MAX_THROUGH_KINK ? spec(a.roadClass()).radius : Double.NaN;
                if (Double.isNaN(r)) {
                    report.add(String.format(Locale.ROOT, "town %s: %s and %s kink %.1f deg, kept apart (trimmed)", a.to(), a.id(), b.id(), k));
                    continue;
                }
                next[i] = j;
                prev[j] = i;
                rad[i] = r;
                break;
            }
        }
        List<Chain> chains = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (prev[i] >= 0) continue;
            Chain c = new Chain(legs.get(i));
            for (int k = i; next[k] >= 0; k = next[k]) {
                c.radius.add(rad[k]);
                c.legs.add(legs.get(next[k]));
            }
            chains.add(c);
        }
        pairTowns(chains, nodes, report);
        nearEnds(chains, nodes, report);
        stubs(chains, nodes, report);
        List<RouteData> out = new ArrayList<>();
        for (Chain c : chains) {
            List<Chain> work = new ArrayList<>(List.of(c));
            while (!work.isEmpty()) {
                Chain w = work.remove(0);
                if (w.legs.size() == 1) { out.add(w.first()); continue; }
                int[] failed = new int[]{-1};
                RouteData m = merge(w, report, failed);
                if (m != null) { out.add(m); continue; }
                // a town corner whose grade cannot be spread: its two sides keep apart (trimmed)
                int f = failed[0];
                report.add(String.format(Locale.ROOT, "town %s: corner of %s and %s kept apart (its grade cannot be spread)", w.legs.get(f).to(),
                        w.legs.get(f).id(), w.legs.get(f + 1).id()));
                Chain a = new Chain(w.legs.get(0)), b = new Chain(w.legs.get(f + 1));
                for (int q = 1; q <= f; q++) { a.radius.add(w.radius.get(q - 1)); a.legs.add(w.legs.get(q)); }
                for (int q = f + 2; q < w.legs.size(); q++) { b.radius.add(w.radius.get(q - 1)); b.legs.add(w.legs.get(q)); }
                work.add(0, b);
                work.add(0, a);
            }
        }
        return out;
    }

    /** Whether a chain's end (end) or start is free at a town: not a join node. */
    private static boolean townEnd(Chain c, boolean end, List<Node> nodes) {
        double[] p = c.point(end);
        return !joinAt(nodes, p[0], p[1]);
    }

    /**
     * State roads of different corridors (or one corridor's legs a corner apart) ending at the same town node pair up,
     * the smallest kink first, up to MAX_TOWN_KINK (C3.3b: mill_town, farm_town_north on seed 4).
     */
    private static void pairTowns(List<Chain> chains, List<Node> nodes, List<String> report) {
        while (true) {
            double best = Double.POSITIVE_INFINITY;
            int bi = -1, bj = -1;
            boolean be = false, bf = false;
            for (int i = 0; i < chains.size(); i++) for (int j = 0; j < chains.size(); j++) {
                Chain a = chains.get(i), b = chains.get(j);
                if (i == j || !a.state() || !b.state()) continue;
                for (boolean ea : new boolean[]{true, false}) for (boolean eb : new boolean[]{false, true}) {
                    double[] pa = a.point(ea), pb = b.point(eb);
                    if (Math.hypot(pa[0] - pb[0], pa[1] - pb[1]) > 0.5 || !townEnd(a, ea, nodes)) continue;
                    // each pair once: a arrives at the node, b leaves it
                    double k = kink(a.arriving(ea), b.leaving(eb));
                    if (k <= MAX_TOWN_KINK && k < best - 1e-9) { best = k; bi = i; bj = j; be = ea; bf = eb; }
                }
            }
            if (bi < 0) return;
            Chain a = chains.get(bi), b = chains.get(bj);
            if (!be) a.reverse();
            if (bf) b.reverse();
            double r = cornerRadius(RoadClassSpec.STATE, best);
            report.add(String.format(Locale.ROOT, "town %s: %s and %s meet at %.1f deg, one road (corner R %.0f)", a.last().to(), a.last().id(), b.first().id(), best, r));
            a.append(b, r);
            chains.remove(bj);
        }
    }

    /**
     * A state road whose start or end is a join lying on another state road within NEAR_END of that road's end at a
     * town turns into it: the other road is cut back to the join (its last stretch to the town node dropped) and the two
     * become one road through the corner (C3.3b: farm_town_south on seed 4).
     */
    private static void nearEnds(List<Chain> chains, List<Node> nodes, List<String> report) {
        while (true) {
            double best = Double.POSITIVE_INFINITY, bs = 0;
            int bi = -1, bj = -1;
            boolean be = false, bf = false;
            for (int i = 0; i < chains.size(); i++) for (int j = 0; j < chains.size(); j++) {
                Chain x = chains.get(i), y = chains.get(j);
                if (i == j || !x.state() || !y.state()) continue;
                for (boolean ex : new boolean[]{true, false}) for (boolean ey : new boolean[]{false, true}) {
                    if (!townEnd(x, ex, nodes)) continue;
                    double[] jp = y.point(ey);
                    if (!joinAt(nodes, jp[0], jp[1])) continue;
                    RouteData leg = ex ? x.last() : reverse(x.first());
                    RoadCurve c = new RoadCurve(leg.xs(), leg.zs());
                    double[] l = c.local(jp[0], jp[1], 10, 0);
                    if (l == null) continue;
                    double back = c.length - l[0];
                    if (back > NEAR_END) continue;
                    double[] f = new double[5];
                    c.frame(l[0], f);
                    double k = kink(Math.atan2(f[3], f[2]), y.leaving(ey));
                    if (k <= MAX_TOWN_KINK && k < best - 1e-9) { best = k; bi = i; bj = j; be = ex; bf = ey; bs = l[0]; }
                }
            }
            if (bi < 0) return;
            Chain x = chains.get(bi), y = chains.get(bj);
            if (!be) x.reverse();
            if (bf) y.reverse();
            RouteData leg = x.last();
            double[] jp = y.point(false);
            // a join at the town node itself (within 20 m: the planner's join beside the node) needs no cut
            double legLen = new RoadCurve(leg.xs(), leg.zs()).length;
            if (legLen - bs >= 20) x.legs.set(x.legs.size() - 1, cut(leg, bs, jp, report));
            double r = cornerRadius(RoadClassSpec.STATE, best);
            report.add(String.format(Locale.ROOT, "town %s: %s cut back %.0f m to the join of %s, one road through the corner (%.1f deg, R %.0f)", leg.to(),
                    leg.id(), new RoadCurve(leg.xs(), leg.zs()).length - bs, y.first().id(), best, r));
            x.append(y, r);
            chains.remove(bj);
        }
    }

    /**
     * A leg cut back to station s, ending exactly at point p (a join on it): its control points up to s - 20, then p; the
     * profile and the structures before the cut kept.
     */
    private static RouteData cut(RouteData r, double s, double[] p, List<String> report) {
        RoadCurve c = new RoadCurve(r.xs(), r.zs());
        List<double[]> pts = new ArrayList<>();
        for (int i = 0; i < r.xs().length; i++) {
            double[] l = c.local(r.xs()[i], r.zs()[i], 50, 0);
            double st = l == null ? (i == 0 ? 0 : c.length) : l[0];
            if (i == 0 || st <= s - 20) pts.add(new double[]{r.xs()[i], r.zs()[i]});
        }
        pts.add(p);
        double[] xs = new double[pts.size()], zs = new double[pts.size()];
        for (int i = 0; i < xs.length; i++) { xs[i] = RoadNetworkJson.round2(pts.get(i)[0]); zs[i] = RoadNetworkJson.round2(pts.get(i)[1]); }
        double len = new RoadCurve(xs, zs).length;
        RoadProfile old = RoadProfile.of(r.profile());
        double[] e = new double[RoadProfile.samples(len)];
        for (int i = 0; i < e.length; i++) e[i] = RoadNetworkJson.round2(old.at(i * RoadProfile.DS));
        List<Structure> ss = new ArrayList<>();
        for (Structure st : r.structures()) {
            if (st.s1() <= len - clearance(st)) ss.add(st);
            else report.add("structure " + st.kind() + " " + st.s0() + ".." + st.s1() + " of " + r.id() + " dropped with the cut");
        }
        return new RouteData(r.id(), r.roadClass(), r.corridor(), r.from(), r.to(), xs, zs, e, List.copyOf(ss), null);
    }

    /**
     * Two state roads left ending at one town node with a corner sharper than MAX_TOWN_KINK: one runs STUB on straight
     * past the node (later the town's street), so the other meets it as a T junction (C3.3b, option B).
     */
    private static void stubs(List<Chain> chains, List<Node> nodes, List<String> report) {
        while (stub(chains, nodes, report)) { }
    }

    private static boolean stub(List<Chain> chains, List<Node> nodes, List<String> report) {
        for (int i = 0; i < chains.size(); i++) for (int j = i + 1; j < chains.size(); j++) {
            Chain a = chains.get(i), b = chains.get(j);
            if (!a.state() || !b.state()) continue;
            for (boolean ea : new boolean[]{true, false}) for (boolean eb : new boolean[]{false, true}) {
                double[] pa = a.point(ea), pb = b.point(eb);
                if (Math.hypot(pa[0] - pb[0], pa[1] - pb[1]) > 0.5 || !townEnd(a, ea, nodes)) continue;
                if (kink(a.arriving(ea), b.leaving(eb)) <= MAX_TOWN_KINK) continue;
                if (!ea) a.reverse();
                RouteData leg = a.last();
                a.legs.set(a.legs.size() - 1, extend(leg, STUB));
                report.add(String.format(Locale.ROOT, "town %s: %s runs %.0f m on past the node (stub), %s meets it", leg.to(), leg.id(), STUB, b.legs.get(0).id()));
                return true;
            }
        }
        return false;
    }

    /**
     * A leg run on straight past its end by len (control points every 20 m), the profile on at its end grade: from the
     * lattice sample two before its last, at the grade of the full lattice step before that (a leg's last steps at its
     * town node can be off its grade, which the stub would carry on as a crease).
     */
    private static RouteData extend(RouteData r, double len) {
        int n = r.xs().length, k = (int) Math.ceil(len / 20);
        double h = heading(r.xs(), r.zs(), true);
        double[] xs = java.util.Arrays.copyOf(r.xs(), n + k), zs = java.util.Arrays.copyOf(r.zs(), n + k);
        for (int i = 1; i <= k; i++) {
            xs[n - 1 + i] = RoadNetworkJson.round2(r.xs()[n - 1] + Math.cos(h) * len * i / k);
            zs[n - 1 + i] = RoadNetworkJson.round2(r.zs()[n - 1] + Math.sin(h) * len * i / k);
        }
        double full = new RoadCurve(xs, zs).length;
        double[] e0 = r.profile();
        int i0 = Math.max(1, e0.length - 3);
        double g = (e0[i0] - e0[i0 - 1]) / RoadProfile.DS;
        double[] e = new double[RoadProfile.samples(full)];
        for (int i = 0; i < e.length; i++) e[i] = i <= i0 ? e0[i] : RoadNetworkJson.round2(e0[i0] + g * (i - i0) * RoadProfile.DS);
        return new RouteData(r.id(), r.roadClass(), r.corridor(), r.from(), r.to(), xs, zs, e, r.structures(), null);
    }

    private static RoadClassSpec spec(String roadClass) {
        for (RoadClassSpec k : RoadClassSpec.values()) if (k.id.equals(roadClass)) return k;
        throw new IllegalArgumentException("unknown road class " + roadClass);
    }

    /**
     * One road from a chain of legs: corners smoothed at the shared nodes (each at its radius), profile from the legs,
     * structures mapped; a town corner (the town radius) gets its grade spread (#regrade). Null when a town corner's
     * grade cannot be spread (failed[0]: its node's index).
     */
    private static RouteData merge(Chain chain, List<String> report, int[] failed) {
        RouteData first = chain.first(), last = chain.last();
        RoadClassSpec k = spec(first.roadClass());
        List<double[]> pts = new ArrayList<>();
        List<double[]> nodePts = new ArrayList<>();
        for (int c = 0; c < chain.legs.size(); c++) {
            RouteData r = chain.legs.get(c);
            for (int q = c == 0 ? 0 : 1; q < r.xs().length; q++) pts.add(new double[]{r.xs()[q], r.zs()[q]});
            if (c + 1 < chain.legs.size()) nodePts.add(new double[]{r.xs()[r.xs().length - 1], r.zs()[r.zs().length - 1]});
        }
        double[] corner = new double[nodePts.size()];
        List<String> local = new ArrayList<>();
        // from the last node back, so the earlier nodes' indices stay valid
        for (int q = nodePts.size() - 1; q >= 0; q--) corner[q] = smoothCorner(pts, nodePts.get(q), chain.radius.get(q), local, first.corridor());
        double[] xs = new double[pts.size()], zs = new double[pts.size()];
        for (int q = 0; q < xs.length; q++) { xs[q] = RoadNetworkJson.round2(pts.get(q)[0]); zs[q] = RoadNetworkJson.round2(pts.get(q)[1]); }
        RoadCurve merged = new RoadCurve(xs, zs);
        // the legs' own curves and profiles
        List<RoadCurve> lc = new ArrayList<>();
        List<RoadProfile> lp = new ArrayList<>();
        double legGrade = 0;
        for (RouteData r : chain.legs) {
            lc.add(new RoadCurve(r.xs(), r.zs()));
            RoadProfile p = RoadProfile.of(r.profile());
            lp.add(p);
            legGrade = Math.max(legGrade, p.maxGrade());
        }
        // each sample on a leg (within ON_LEG of it) takes the leg's height; across the smoothed corners, where the
        // merged road leaves the legs, the height runs linearly between the last matched samples
        int m = RoadProfile.samples(merged.length);
        double[] e = new double[m];
        for (int i = 0; i < m; i++) {
            double[] p = merged.point(Math.min(merged.length, i * RoadProfile.DS), 0);
            double best = Double.POSITIVE_INFINITY, h = Double.NaN;
            for (int c = 0; c < chain.legs.size(); c++) {
                double[] l = lc.get(c).local(p[0], p[1], ON_LEG, 0);
                if (l != null && Math.abs(l[1]) < best) { best = Math.abs(l[1]); h = lp.get(c).at(l[0]); }
            }
            e[i] = h;
        }
        for (int i = 0; i < m; ) {
            if (!Double.isNaN(e[i])) { i++; continue; }
            int j = i;
            while (j < m && Double.isNaN(e[j])) j++;
            if (i == 0 || j == m) throw new IllegalStateException("merged road off its legs at an end: " + first.corridor());
            for (int q = i; q < j; q++) e[q] = e[i - 1] + (e[j] - e[i - 1]) * (q - i + 1) / (j - i + 1);
            i = j;
        }
        // smooth over each node (the legs meet there with their own grades)
        double sigma = k.vSigma() / RoadProfile.DS;
        double[] sm = RoadProfile.smooth(e, sigma);
        double[] w = new double[m];
        double[] nodeS = new double[nodePts.size()];
        for (int q = 0; q < nodePts.size(); q++) {
            double[] l = nodeStation(merged, nodePts.get(q)[0], nodePts.get(q)[1], MAX_CORNER);
            if (l == null) {
                double near = Double.POSITIVE_INFINITY;
                for (int i = 0; i < xs.length; i++) near = Math.min(near, Math.hypot(xs[i] - nodePts.get(q)[0], zs[i] - nodePts.get(q)[1]));
                StringBuilder ids = new StringBuilder();
                for (RouteData r : chain.legs) ids.append(' ').append(r.id());
                throw new IllegalStateException(String.format(Locale.ROOT, "node off the merged road: %s node %d at %.1f, %.1f (nearest control point %.1f m, sigma %.0f, legs%s)",
                        first.corridor(), q, nodePts.get(q)[0], nodePts.get(q)[1], near, corner[q], ids));
            }
            nodeS[q] = l[0];
            double reach = 4 * corner[q] * POINT_SPACING + 2 * k.vSigma(), fade = 2 * k.vSigma();
            for (int i = 0; i < m; i++) {
                double d = Math.abs(i * RoadProfile.DS - l[0]);
                w[i] = Math.max(w[i], d <= reach ? 1 : d >= reach + fade ? 0 : CrossSection.smooth(1 - (d - reach) / fade));
            }
        }
        for (int i = 0; i < m; i++) e[i] = e[i] + (sm[i] - e[i]) * w[i];
        // structures at their places on the merged road
        List<Structure> ss = new ArrayList<>();
        for (int c = 0; c < chain.legs.size(); c++) {
            for (Structure s : chain.legs.get(c).structures()) {
                double[] p0 = lc.get(c).point(s.s0(), 0), p1 = lc.get(c).point(s.s1(), 0);
                double[] l0 = merged.local(p0[0], p0[1], 20, 0), l1 = merged.local(p1[0], p1[1], 20, 0);
                if (l0 == null || l1 == null) {
                    local.add("structure " + s.kind() + " " + s.s0() + ".." + s.s1() + " of " + chain.legs.get(c).id() + " lost in the merge (inside a smoothed corner)");
                    continue;
                }
                double a = RoadNetworkJson.round2(Math.min(l0[0], l1[0])), b = RoadNetworkJson.round2(Math.max(l0[0], l1[0]));
                for (int i = 0; i < m; i++) if (w[i] > 0 && i * RoadProfile.DS >= a - 20 && i * RoadProfile.DS <= b + 20) {
                    local.add("structure " + s.kind() + " of " + chain.legs.get(c).id() + " near a smoothed node: check the merged profile");
                    break;
                }
                ss.add(new Structure(s.kind(), a, b, s.height()));
            }
        }
        // town corners: the grade spread over the corner (C3.3b), the stations kept for the curve warning signs
        List<Double> corners = new ArrayList<>();
        double limit = Math.min(k.gradeMountain, Math.max(k.grade, legGrade));
        for (int q = 0; q < nodePts.size(); q++) {
            if (chain.radius.get(q) != TOWN_RADIUS) continue;
            if (!regrade(e, nodeS[q], limit, TOWN_K, k.vSigma(), ss, local)) {
                for (String line : local) if (line.startsWith("town corner")) report.add(line);
                failed[0] = q;
                return null;
            }
            corners.add(RoadNetworkJson.round2(nodeS[q]));
        }
        for (int i = 0; i < m; i++) e[i] = RoadNetworkJson.round2(e[i]);
        report.addAll(local);
        java.util.LinkedHashSet<String> corridors = new java.util.LinkedHashSet<>();
        for (RouteData r : chain.legs) corridors.add(r.corridor());
        String id = String.join("+", corridors) + ":" + first.from() + "-" + last.to();
        StringBuilder joined = new StringBuilder();
        for (int c = 0; c < chain.legs.size(); c++) joined.append(c > 0 ? " + " : "").append(chain.legs.get(c).id());
        StringBuilder cs = new StringBuilder();
        for (int q = 0; q < corner.length; q++)
            cs.append(q > 0 ? ", " : "").append(String.format(Locale.ROOT, "%s R %.0f sigma %.0f points", chain.legs.get(q).to(), chain.radius.get(q), corner[q]));
        report.add(String.format(Locale.ROOT, "through: %s = %s (%.0f m; corners %s)", id, joined, merged.length, cs));
        double[] cArr = corners.stream().mapToDouble(Double::doubleValue).toArray();
        return new RouteData(id, first.roadClass(), first.corridor(), first.from(), last.to(), xs, zs, e, List.copyOf(ss), null, List.of(), List.of(),
                new String[2], cArr);
    }

    /**
     * A town corner's grade (C3.3b): within GRADE_CHECK of the node the merged profile keeps limit; where it does not (the
     * legs reach the town at different heights), the step is spread both ways at the limit (the mean of the forward-first
     * and the backward-first grade clamps, itself within the limit), the vertical curves rounded to kMin
     * (RoadProfile#vertical) and blended in over 2 vSigma beyond the changed stretch; within REGRADE_REACH of the node, never
     * over a bridge, viaduct or tunnel. False when it does not fit.
     */
    private static boolean regrade(double[] e, double sn, double limit, double kMin, double vSigma, List<Structure> ss, List<String> report) {
        int m = e.length, c = (int) Math.round(sn / RoadProfile.DS), r = (int) Math.round(GRADE_CHECK / RoadProfile.DS);
        int i0 = Math.max(1, c - r), i1 = Math.min(m - 1, c + r);
        double before = maxGrade(e, i0, i1);
        if (before <= limit + 1e-4) return true;
        int w = (int) Math.round(REGRADE_REACH / RoadProfile.DS), a = Math.max(0, c - w), b = Math.min(m - 1, c + w), n = b - a + 1;
        double[] slice = java.util.Arrays.copyOfRange(e, a, b + 1), grade = new double[n];
        java.util.Arrays.fill(grade, limit);
        double[] fb = slice.clone(), bf = slice.clone();
        double st = limit * RoadProfile.DS;
        for (int i = 1; i < n; i++) fb[i] = Math.max(fb[i - 1] - st, Math.min(fb[i - 1] + st, fb[i]));
        for (int i = n - 2; i >= 0; i--) fb[i] = Math.max(fb[i + 1] - st, Math.min(fb[i + 1] + st, fb[i]));
        for (int i = n - 2; i >= 0; i--) bf[i] = Math.max(bf[i + 1] - st, Math.min(bf[i + 1] + st, bf[i]));
        for (int i = 1; i < n; i++) bf[i] = Math.max(bf[i - 1] - st, Math.min(bf[i - 1] + st, bf[i]));
        double[] f = new double[n];
        int lo = -1, hi = -1;
        for (int i = 0; i < n; i++) {
            f[i] = (fb[i] + bf[i]) / 2;
            if (Math.abs(f[i] - slice[i]) > 0.005) { if (lo < 0) lo = i; hi = i; }
        }
        int fade = (int) Math.round(2 * vSigma / RoadProfile.DS);
        if (lo < 0 || lo - 2 * fade <= 0 || hi + 2 * fade >= n - 1) {
            report.add(String.format(Locale.ROOT, "town corner at s %.0f: the grade %.1f %% cannot be spread within %.0f m (limit %.1f %%)", sn, before * 100,
                    REGRADE_REACH, limit * 100));
            return false;
        }
        double s0 = (a + lo) * RoadProfile.DS, s1 = (a + hi) * RoadProfile.DS;
        for (Structure s : ss)
            if (!s.kind().equals("CULVERT") && !s.kind().equals("DEEP_CUT") && s.s1() >= s0 - 2 * vSigma - clearance(s) && s.s0() <= s1 + 2 * vSigma + clearance(s)) {
                report.add(String.format(Locale.ROOT, "town corner at s %.0f: spreading the grade over %.0f .. %.0f would move a %s", sn, s0, s1, s.kind()));
                return false;
            }
        f = RoadProfile.vertical(f, grade, vSigma, kMin);
        double[] out = e.clone();
        for (int i = Math.max(0, lo - 2 * fade); i <= Math.min(n - 1, hi + 2 * fade); i++) {
            double d = i < lo ? lo - i : i > hi ? i - hi : 0;
            double t = d <= fade ? 1 : CrossSection.smooth(1 - (d - fade) / fade);
            out[a + i] = slice[i] + (f[i] - slice[i]) * t;
        }
        int c0 = Math.max(1, a + lo - 2 * fade - 1), c1 = Math.min(m - 1, a + hi + 2 * fade + 1);
        double after = maxGrade(out, c0, c1), kk = minK(out, c0, c1);
        if (after > limit + 1e-3 || kk < kMin - 1e-6) {
            report.add(String.format(Locale.ROOT, "town corner at s %.0f: spread grade %.2f %% (limit %.1f %%), K %.1f (min %.0f)", sn, after * 100, limit * 100, kk, kMin));
            return false;
        }
        System.arraycopy(out, 0, e, 0, m);
        report.add(String.format(Locale.ROOT, "town corner at s %.0f: grade %.1f %% spread over %.0f .. %.0f m (now %.1f %%, limit %.1f %%)", sn, before * 100,
                s0 - sn, s1 - sn, after * 100, limit * 100));
        return true;
    }

    private static double maxGrade(double[] e, int i0, int i1) {
        double g = 0;
        for (int i = Math.max(1, i0); i <= Math.min(e.length - 1, i1); i++) g = Math.max(g, Math.abs(e[i] - e[i - 1]) / RoadProfile.DS);
        return g;
    }

    /** The smallest vertical curve K (RoadProfile.K_CHORD chords) over samples a .. b. */
    private static double minK(double[] e, int a, int b) {
        int c = (int) Math.round(RoadProfile.K_CHORD / RoadProfile.DS);
        double k = Double.POSITIVE_INFINITY;
        for (int i = Math.max(c, a - c); i <= Math.min(e.length - 1 - c, b + c); i++) {
            double second = Math.abs(e[i + c] - 2 * e[i] + e[i - c]) / (RoadProfile.K_CHORD * RoadProfile.K_CHORD);
            if (second > 1e-12) k = Math.min(k, 1 / (100 * second));
        }
        return k;
    }

    /**
     * Smooth the corner of the control polyline at a node: the segment headings within 4 sigma points of it take their
     * Gaussian average (blended in smoothly), the points are rebuilt from the headings and the segment lengths, and the
     * position error at the window's far end is spread linearly over the window; sigma grows until the curve keeps the
     * radius near the node. Returns sigma (points).
     */
    private static double smoothCorner(List<double[]> pts, double[] node, double radius, List<String> report, String corridor) {
        int k = -1;
        for (int i = 0; i < pts.size(); i++) if (Math.hypot(pts.get(i)[0] - node[0], pts.get(i)[1] - node[1]) < 0.5) { k = i; break; }
        if (k < 0) throw new IllegalStateException("node not on the control polyline: " + corridor);
        int n = pts.size();
        // unwrapped segment headings and lengths
        double[] h = new double[n - 1], len = new double[n - 1];
        for (int j = 0; j + 1 < n; j++) {
            h[j] = Math.atan2(pts.get(j + 1)[1] - pts.get(j)[1], pts.get(j + 1)[0] - pts.get(j)[0]);
            len[j] = Math.hypot(pts.get(j + 1)[1] - pts.get(j)[1], pts.get(j + 1)[0] - pts.get(j)[0]);
            if (j > 0) while (h[j] - h[j - 1] > Math.PI) h[j] -= 2 * Math.PI;
            if (j > 0) while (h[j] - h[j - 1] < -Math.PI) h[j] += 2 * Math.PI;
        }
        List<double[]> best = pts;
        double minR = 0;
        int used = MAX_SIGMA;
        for (int sigma = 1; sigma <= MAX_SIGMA; sigma++) {
            int w = 4 * sigma, a = Math.max(0, k - w), b = Math.min(n - 1, k + w);
            double[] hs = h.clone();
            for (int j = a; j < b; j++) {
                double sum = 0, ws = 0;
                for (int q = Math.max(0, j - 3 * sigma); q <= Math.min(n - 2, j + 3 * sigma); q++) {
                    double g = Math.exp(-0.5 * (q - j) * (q - j) / ((double) sigma * sigma));
                    sum += g * h[q];
                    ws += g;
                }
                // blend: full at the node, none at the window's ends
                double t = 1 - Math.abs(j + 0.5 - k) / (w + 0.5);
                hs[j] = h[j] + (sum / ws - h[j]) * CrossSection.smooth(t * 2);
            }
            List<double[]> cand = new ArrayList<>(pts.size());
            for (int i = 0; i <= a; i++) cand.add(pts.get(i).clone());
            for (int j = a; j < b; j++) {
                double[] p = cand.get(cand.size() - 1);
                cand.add(new double[]{p[0] + len[j] * Math.cos(hs[j]), p[1] + len[j] * Math.sin(hs[j])});
            }
            double ex = cand.get(b)[0] - pts.get(b)[0], ez = cand.get(b)[1] - pts.get(b)[1];
            for (int i = a; i <= b; i++) {
                double f = b > a ? (double) (i - a) / (b - a) : 0;
                cand.get(i)[0] -= ex * f;
                cand.get(i)[1] -= ez * f;
            }
            for (int i = b + 1; i < n; i++) cand.add(pts.get(i).clone());
            // the radius over the window, on a local piece of the polyline (the spline is local)
            int w0 = Math.max(0, a - 4), w1 = Math.min(n - 1, b + 4);
            double[] wx = new double[w1 - w0 + 1], wz = new double[w1 - w0 + 1];
            for (int q = w0; q <= w1; q++) { wx[q - w0] = RoadNetworkJson.round2(cand.get(q)[0]); wz[q - w0] = RoadNetworkJson.round2(cand.get(q)[1]); }
            RoadCurve c = new RoadCurve(wx, wz);
            double maxK = 0;
            double[] f = new double[5];
            for (double s = 0; s <= c.length; s += 2) {
                c.frame(s, f);
                // inside the window (the local piece's own natural ends lie outside it)
                if (s >= arcTo(cand, w0, a) && s <= arcTo(cand, w0, b)) maxK = Math.max(maxK, Math.abs(f[4]));
            }
            best = cand;
            used = sigma;
            minR = maxK > 0 ? 1 / maxK : Double.POSITIVE_INFINITY;
            if (maxK * radius <= 1) break;
        }
        if (minR < radius)
            report.add(String.format(Locale.ROOT, "corner of %s at %.0f, %.0f: radius %.0f m below the class's %.0f m at sigma %d", corridor, node[0], node[1],
                    minR, radius, used));
        pts.clear();
        pts.addAll(best);
        return used;
    }

    /**
     * A node's station on the merged road (RoadCurve#local); where that projection does not settle (a node far outside a
     * tight town corner: its fixed-point steps shrink only by the distance over the radius each, 2026-10-17 seed
     * 10164326571583364449 shore_town 68 m off an 80 m corner) the nearest point sampled every metre, refined; null
     * beyond maxDist.
     */
    static double[] nodeStation(RoadCurve c, double x, double z, double maxDist) {
        double[] l = c.local(x, z, maxDist, 0);
        if (l != null) return l;
        double best = Double.POSITIVE_INFINITY, bs = 0;
        for (double s = 0; s <= c.length; s += 1) {
            double[] p = c.point(s, 0);
            double d = Math.hypot(p[0] - x, p[1] - z);
            if (d < best) { best = d; bs = s; }
        }
        double lo = Math.max(0, bs - 1), hi = Math.min(c.length, bs + 1);
        for (int it = 0; it < 40; it++) {
            double m1 = lo + (hi - lo) / 3, m2 = hi - (hi - lo) / 3;
            double[] p1 = c.point(m1, 0), p2 = c.point(m2, 0);
            if (Math.hypot(p1[0] - x, p1[1] - z) < Math.hypot(p2[0] - x, p2[1] - z)) hi = m2; else lo = m1;
        }
        double s = (lo + hi) / 2;
        double[] f = new double[5];
        c.frame(s, f);
        double u = -(x - f[0]) * f[3] + (z - f[1]) * f[2];
        return Math.hypot(x - f[0], z - f[1]) > maxDist ? null : new double[]{s, u};
    }

    /** Polyline length from point i0 to point i1. */
    private static double arcTo(List<double[]> p, int i0, int i1) {
        double s = 0;
        for (int i = i0; i < i1; i++) s += Math.hypot(p.get(i + 1)[0] - p.get(i)[0], p.get(i + 1)[1] - p.get(i)[1]);
        return s;
    }

    // ------------------------------------------------------------------ trims

    /** How far a road's end keeps from a structure: TUNNEL_CLEAR from a tunnel, STRUCTURE_CLEAR from the others. */
    static double clearance(Structure s) { return s.kind().equals("TUNNEL") ? TUNNEL_CLEAR : STRUCTURE_CLEAR; }

    private static int rank(String roadClass) { return roadClass.equals("interstate") ? 2 : roadClass.equals("state") ? 1 : 0; }

    /**
     * Conflict stations ignored between two roads (C3.3b: a minor's last stretch and its host's mouth stretch, which the
     * junction joins; group 2: an interchange's ramps, crossroad and interstate, RoadInterchanges): road and other by their
     * ids before the splits, JSON stations s0 .. s1 on road.
     */
    record Zone(String road, String other, double s0, double s1) {}

    private static List<Zone> zones(List<RoadJunctions.Spec> specs, List<Zone> extra) {
        List<Zone> out = new ArrayList<>(extra);
        for (RoadJunctions.Spec j : specs) {
            out.add(new Zone(j.minor(), j.host(), j.minorZone()[0], j.minorZone()[1]));
            out.add(new Zone(j.host(), j.minor(), j.hostZone()[0], j.hostZone()[1]));
        }
        return out;
    }

    /** The conflict stations of r (its curve rc) with q, without those a junction joins (zones). */
    private static List<Double> conflicts(RouteData r, RoadCurve rc, RouteData q, RoadCurve qc, List<Zone> zones) {
        List<Double> bad = conflicts(rc, CrossSection.of(r.roadClass()), qc, CrossSection.of(q.roadClass()));
        if (bad.isEmpty() || zones.isEmpty()) return bad;
        String a = RoadJunctions.base(r.id()), b = RoadJunctions.base(q.id());
        double off = r.trim() == null ? 0 : r.trim()[0];
        for (Zone z : zones) if (z.road().equals(a) && z.other().equals(b)) bad.removeIf(t -> off + t >= z.s0() && off + t <= z.s1());
        return bad;
    }

    /** The stations (2 m, -END .. length + END) of road r whose works or formation conflict with road q. */
    private static List<Double> conflicts(RoadCurve r, CrossSection rs, RoadCurve q, CrossSection qs) {
        List<Double> out = new ArrayList<>();
        double reach = RoadWorks.REACH;
        for (double t = -RoadDesign.END; t <= r.length + RoadDesign.END + 1e-9; t += RoadProfile.DS) {
            double[] c = r.point(t, 0);
            if (q.local(c[0], c[1], 2 * reach + 2, RoadDesign.END) == null) continue;
            boolean bad = false;
            // r's works (and end tapers) reaching q's formation
            for (double u = -reach; u <= reach + 1e-9 && !bad; u += 2) {
                double[] p = r.point(t, u);
                if (q.local(p[0], p[1], qs.edge + 1, 0) != null) bad = true;
            }
            // r's formation reaching q's works
            if (!bad && t >= 0 && t <= r.length)
                for (double u = -(rs.edge + 1); u <= rs.edge + 1 + 1e-9 && !bad; u += 1) {
                    double[] p = r.point(t, u);
                    if (q.local(p[0], p[1], reach + 1, RoadDesign.END) != null) bad = true;
                }
            if (bad) out.add(t);
        }
        return out;
    }

    private static boolean near(RouteData a, RouteData b, double margin) {
        double[] ba = bbox(a), bb = bbox(b);
        return ba[0] - margin <= bb[2] && bb[0] - margin <= ba[2] && ba[1] - margin <= bb[3] && bb[1] - margin <= ba[3];
    }

    private static double[] bbox(RouteData r) {
        double x0 = Double.POSITIVE_INFINITY, z0 = Double.POSITIVE_INFINITY, x1 = Double.NEGATIVE_INFINITY, z1 = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < r.xs().length; i++) { x0 = Math.min(x0, r.xs()[i]); x1 = Math.max(x1, r.xs()[i]); z0 = Math.min(z0, r.zs()[i]); z1 = Math.max(z1, r.zs()[i]); }
        return new double[]{x0, z0, x1, z1};
    }

    private static RoadCurve curve(RouteData r) {
        return r.trim() == null ? new RoadCurve(r.xs(), r.zs()) : new RoadCurve(r.xs(), r.zs(), r.trim()[0], r.trim()[1]);
    }

    /**
     * Each road's trim window against the untrimmed roads of the same or a higher class; a T junction's minor ends at its
     * end line (C3.3b). A road built with a window (a ramp, a system interchange's piece) keeps it unless a conflict moves
     * an end; an open end (C3.3b group 3) stays where it is (never rounded to the lattice, never left out for its length).
     */
    private static List<RouteData> trims(List<RouteData> roads, List<RoadJunctions.Spec> junctions, List<Zone> zones, List<String> report) {
        List<RoadCurve> cs = new ArrayList<>();
        for (RouteData r : roads) cs.add(curve(r));
        double margin = 2 * RoadWorks.REACH + 2 * RoadDesign.END;
        List<RouteData> out = new ArrayList<>();
        for (int i = 0; i < roads.size(); i++) {
            RouteData r = roads.get(i);
            RoadCurve rc = cs.get(i);
            CrossSection rs = CrossSection.of(r.roadClass());
            double profileLen = (r.profile().length - 1) * RoadProfile.DS;
            // the window as built (JSON stations); conflict stations are rc's, from a0
            double a0 = r.trim() == null ? 0 : r.trim()[0], len = Math.min(rc.length, profileLen - a0), b0 = a0 + len;
            double a = a0, b = b0;
            for (RoadJunctions.Spec jn : junctions)
                if (jn.minor().equals(r.id())) {
                    if (jn.atEnd()) b = Math.min(b, jn.endLine());
                    else a = Math.max(a, jn.endLine());
                }
            for (int j = 0; j < roads.size(); j++) {
                RouteData q = roads.get(j);
                if (i == j || rank(q.roadClass()) < rank(r.roadClass()) || !near(r, q, margin)) continue;
                List<Double> bad = conflicts(r, rc, q, cs.get(j), zones);
                if (bad.isEmpty()) continue;
                // the run from the start, and the run from the end
                int i0 = 0, i1 = bad.size() - 1;
                double lastStart = Double.NEGATIVE_INFINITY, firstEnd = Double.POSITIVE_INFINITY;
                if (bad.get(0) <= -RoadDesign.END + TOUCH) {
                    while (i0 + 1 < bad.size() && bad.get(i0 + 1) - bad.get(i0) <= RUN_GAP) i0++;
                    lastStart = bad.get(i0);
                    i0++;
                }
                if (bad.get(i1) >= len + RoadDesign.END - TOUCH && i1 >= i0) {
                    while (i1 - 1 >= i0 && bad.get(i1) - bad.get(i1 - 1) <= RUN_GAP) i1--;
                    firstEnd = bad.get(i1);
                    i1--;
                }
                int mid = Math.max(0, i1 - i0 + 1);
                if (lastStart > Double.NEGATIVE_INFINITY) a = Math.max(a, a0 + lastStart + RoadDesign.END + TRIM_MARGIN);
                if (firstEnd < Double.POSITIVE_INFINITY) b = Math.min(b, a0 + firstEnd - RoadDesign.END - TRIM_MARGIN);
                if (mid > 0) report.add(String.format(Locale.ROOT, "mid-route: %s meets %s on %d stations (the other end trims)", r.id(), q.id(), mid));
            }
            boolean openA = r.openAt(false) && a <= a0 + 1e-9, openB = r.openAt(true) && b >= b0 - 1e-9;
            if (!openA) a = Math.ceil(a / RoadProfile.DS - 1e-9) * RoadProfile.DS;
            if (!openB) b = Math.floor(b / RoadProfile.DS + 1e-9) * RoadProfile.DS;
            // never inside a structure: move the end past it; a tunnel keeps its approaches clear of any end, the route's own
            // too (a portal never faces a town or a trimmed end)
            for (Structure s : r.structures()) {
                boolean tunnel = s.kind().equals("TUNNEL");
                double clear = clearance(s);
                if ((a > a0 || tunnel) && a >= s.s0() - clear && a <= s.s1() + clear) a = Math.ceil((s.s1() + clear) / RoadProfile.DS) * RoadProfile.DS;
                if ((b < b0 || tunnel) && b >= s.s0() - clear && b <= s.s1() + clear) b = Math.floor((s.s0() - clear) / RoadProfile.DS) * RoadProfile.DS;
            }
            // a window only rounded to the lattice is the road as built, whatever its length
            boolean asBuilt = a <= a0 + 1e-9 && b >= b0 - RoadProfile.DS;
            if (b - a < MIN_PIECE && !(asBuilt && r.trim() != null)) {
                report.add(String.format(Locale.ROOT, "left out: %s (trimmed to %.0f .. %.0f)", r.id(), a, b));
                continue;
            }
            if (a > a0 + 1e-9 || b < b0 - 1e-9) {
                if (r.trim() == null)
                    report.add(String.format(Locale.ROOT, "trim: %s keeps %.0f .. %.0f of %.0f m (start -%.0f, end -%.0f)", r.id(), a + 0.0, b, len, a + 0.0, len - b));
                else if (!asBuilt)
                    report.add(String.format(Locale.ROOT, "trim: %s keeps %.0f .. %.0f of %.0f .. %.0f m (start -%.0f, end -%.0f)", r.id(), a, b, a0, b0,
                            a - a0, b0 - b));
                out.add(r.withTrim(new double[]{a, b}));
            } else out.add(r);
        }
        return out;
    }

    /**
     * A road that still runs into another's works in the middle after the trims (two corridors side by side for a
     * stretch: the lower class, or of one class the shorter road) is split there: its first conflict run is cut out with
     * RoadDesign.END + TRIM_MARGIN on each side, clear of structures, into two pieces that end like trimmed ends (ids
     * ~1, ~2; pieces shorter than MIN_PIECE are left out); repeated until nothing conflicts or MAX_SPLITS (2026-10-15:
     * on seed 7212345678901234567 a state road runs beside the interstate for 500 m).
     */
    private static List<RouteData> splits(List<RouteData> roads, List<Zone> zones, List<String> report) {
        List<RouteData> cur = new ArrayList<>(roads);
        double margin = 2 * RoadWorks.REACH + 2 * RoadDesign.END;
        for (int pass = 0; pass < MAX_SPLITS; pass++) {
            List<RoadCurve> cs = new ArrayList<>();
            for (RouteData r : cur) cs.add(curve(r));
            int split = -1;
            List<RouteData> pieces = null;
            for (int i = 0; i < cur.size() && split < 0; i++) for (int j = 0; j < cur.size() && split < 0; j++) {
                RouteData r = cur.get(i), q = cur.get(j);
                if (i == j || !near(r, q, margin)) continue;
                int rr = rank(r.roadClass()), rq = rank(q.roadClass());
                double lr = cs.get(i).length, lq = cs.get(j).length;
                if (rr > rq || rr == rq && (lr > lq || lr == lq && i < j)) continue;
                List<Double> bad = conflicts(r, cs.get(i), q, cs.get(j), zones);
                if (bad.isEmpty()) continue;
                int k = 0;
                while (k + 1 < bad.size() && bad.get(k + 1) - bad.get(k) <= RUN_GAP) k++;
                double off = r.trim() == null ? 0 : r.trim()[0];
                double full = r.trim() != null ? r.trim()[1] : Math.min(cs.get(i).length, (r.profile().length - 1) * RoadProfile.DS);
                double c0 = off + bad.get(0), c1 = off + bad.get(k);
                double b0 = Math.floor((c0 - RoadDesign.END - TRIM_MARGIN) / RoadProfile.DS) * RoadProfile.DS;
                double a1 = Math.ceil((c1 + RoadDesign.END + TRIM_MARGIN) / RoadProfile.DS) * RoadProfile.DS;
                for (Structure st : r.structures()) {
                    double clear = clearance(st);
                    if (b0 >= st.s0() - clear && b0 <= st.s1() + clear) b0 = Math.floor((st.s0() - clear) / RoadProfile.DS) * RoadProfile.DS;
                    if (a1 >= st.s0() - clear && a1 <= st.s1() + clear) a1 = Math.ceil((st.s1() + clear) / RoadProfile.DS) * RoadProfile.DS;
                }
                pieces = new ArrayList<>();
                double[][] w = {{off, b0}, {a1, full}};
                StringBuilder kept = new StringBuilder();
                for (int p = 0; p < 2; p++) {
                    if (w[p][1] - w[p][0] < MIN_PIECE) continue;
                    pieces.add(r.piece(r.id() + "~" + (p + 1), w[p]));
                    kept.append(String.format(Locale.ROOT, " %.0f .. %.0f", w[p][0], w[p][1]));
                }
                report.add(String.format(Locale.ROOT, "split: %s runs into %s at %.0f .. %.0f; keeps%s", r.id(), q.id(), c0, c1,
                        kept.length() == 0 ? " nothing" : kept));
                split = i;
            }
            if (split < 0) break;
            cur.remove(split);
            cur.addAll(split, pieces);
        }
        return cur;
    }

    /** Remaining conflicts of the trimmed network (every pair, both ways), reported; returns their station count. */
    private static int verify(List<RouteData> roads, List<Zone> zones, List<String> report) {
        List<RoadCurve> cs = new ArrayList<>();
        for (RouteData r : roads) cs.add(curve(r));
        double margin = 2 * RoadWorks.REACH + 2 * RoadDesign.END;
        int total = 0;
        for (int i = 0; i < roads.size(); i++) for (int j = 0; j < roads.size(); j++) {
            if (i == j || !near(roads.get(i), roads.get(j), margin)) continue;
            List<Double> bad = conflicts(roads.get(i), cs.get(i), roads.get(j), cs.get(j), zones);
            if (bad.isEmpty()) continue;
            total += bad.size();
            report.add(String.format(Locale.ROOT, "CONFLICT: %s vs %s on %d stations (%.0f .. %.0f)", roads.get(i).id(), roads.get(j).id(), bad.size(),
                    bad.get(0), bad.get(bad.size() - 1)));
        }
        return total;
    }
}
