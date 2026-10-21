package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.federal.FederalMap;
import com.antaurora.apofirstlight.worldgen.home.ForestHomeLayout;
import com.antaurora.apofirstlight.worldgen.home.StartAreaPlanner;
import com.antaurora.apofirstlight.worldgen.network.contract.Polyline;
import com.antaurora.apofirstlight.worldgen.network.contract.RoadNode;
import com.antaurora.apofirstlight.worldgen.network.contract.RotatedRect;
import com.antaurora.apofirstlight.worldgen.network.contract.Shape;
import com.antaurora.apofirstlight.worldgen.network.contract.StructureReservation;
import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson.RouteData;
import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson.Structure;
import com.antaurora.apofirstlight.worldgen.network.route.RoadClassSpec;
import com.antaurora.apofirstlight.worldgen.network.route.RouteDesigner;
import com.antaurora.apofirstlight.worldgen.network.route.RouteGrid;
import com.antaurora.apofirstlight.worldgen.network.route.RoutePolyline;
import com.antaurora.apofirstlight.worldgen.network.route.RouteTerrain;
import com.antaurora.apofirstlight.worldgen.network.town.TownPlan;
import com.antaurora.apofirstlight.worldgen.network.town.TownStreets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * County road G3 (2026-10-18, docs/worldgen/county_road_g3.md; the contract's G2 / G3, docs/worldgen/main_c3_interface_contract_v1.md):
 * the forest home's site, the county road that runs past it and the gravel driveway that joins it from the side (the side
 * join, 2026-10-19, docs/worldgen/county_road_g3_side_join_plan.md; user: "改成侧接：县道路过老宅附近，车道沿溪从侧面 T 字接入"),
 * planned at network build after the towns (RoadNetworkBuild#build, with the terrain). Pure Java, deterministic per
 * seed.
 * <ul>
 *   <li>G2 (Main's StartAreaPlanner, a pure function) picks up to eight sites in the start region with the built roads and
 *   the town limits as zones: hard ROAD_HARD beyond a road's paved edge and TOWN_HARD beyond a limit, soft SOFT_R4 /
 *   SOFT_OTHER round a road and TOWN_SOFT round a limit; only shapes within ZONE_REACH of the region go in;</li>
 *   <li>for each site the driveway's search first (DriveSearch: a 4 m grid from the home's node H, along the creek, never
 *   over water or its banks, DRIVE_MIN .. DRIVE_MAX long): every node it reaches far enough is a possible mouth;</li>
 *   <li>leg A of the county road: A* on the 32 m route grid (class COUNTY) from all the mouths' cells at once, each starting
 *   at its driveway's cost, to a town (an outer intersection's free arm, TownPlan.Entry) or else a state road (a T junction,
 *   STATE_PENALTY); every built road's band and town limit avoided, the home's plot kept PLOT_HARD clear (PLOT_SOFT
 *   priced); the cell it starts from is the mouth's;</li>
 *   <li>leg B on from the mouth: within CONE of the way leg A leaves it reversed (a road running past, not a bend back),
 *   never on leg A's cells, to the kind leg A did not reach (a state road FAR_JOINT from leg A's junction, a town), or else
 *   a dead end DEAD_END on (a ROAD ENDS barrier anchor, DEAD_END_PENALTY);</li>
 *   <li>the two legs one road (the town's end its end, else leg A's): smoothed, designed (RouteDesigner, no tunnel), its
 *   state road ends T junctions (RoadJunctions#find: realigned when skewed, landed on the host's edge), its town end blended
 *   into the arm and pinned to the intersection's datum;</li>
 *   <li>the mouth on the designed road (#mouth): MOUTH_END from its ends, its junctions' zones and its structures, radius at
 *   least MOUTH_RADIUS and grade at most MOUTH_GRADE over +- MOUTH_RUN, SIGHT clear both ways; the driveway from H along the
 *   search's path to a point DRIVE_RUN off the paved edge, then straight and square to the road to its edge, its profile on
 *   the smoothed ground pinned to the yard and to the edge, at most DRIVE_GRADE, its last DRIVE_STRAIGHT at most
 *   DRIVE_END_GRADE (Driveway draws its apron);</li>
 *   <li>the site with the lowest score (road length, driveway cost, STATE_PENALTY without a town, DEAD_END_PENALTY) wins;
 *   a site with no road keeps G2's first site, no road and no driveway;</li>
 *   <li>the home (Home): its site, the driveway's datum points, the farm gate, mailbox and barrier anchors (Main places
 *   them), the built JSON's "home" member.</li>
 * </ul>
 * G2 takes 7 .. 10 s and the network is built on every load (RoadNetworkRuntime): the chosen site is remembered (Memo,
 * keyed by the plan's digest and RULES) and a later build tries that site alone; only when it no longer links as it did is
 * the whole search run again.
 */
public final class RoadCounty {
    private RoadCounty() {}

    /**
     * The rules' version: part of the memo's key (bump it with any change to the zones, the search, the choice or the
     * design, or a remembered site outlives the rules that chose it).
     */
    public static final String RULES = "g3.4";

    /** Where the chosen site is remembered between builds (#plan); NONE: nowhere. */
    public interface Memo {
        /** The remembered site, or null. */
        String load();

        void store(String site);

        Memo NONE = new Memo() {
            @Override public String load() { return null; }
            @Override public void store(String site) {}
        };
    }

    /** The county road's route id (its corridor "county": no route name). */
    public static final String ID = "county:home";
    /** G2's zones (contract G1), m: hard beyond a road's paved edge and a town limit, soft half widths, a limit's soft margin; their reach. */
    static final double ROAD_HARD = 15, SOFT_R4 = 60, SOFT_OTHER = 30, TOWN_HARD = 20, TOWN_SOFT = 300, ZONE_REACH = 300;
    /** Route grid cells within a built road's paved edge + AVOID_ROAD of its centreline, or a town limit + AVOID_TOWN, are avoided, m. */
    static final double AVOID_ROAD = 40, AVOID_TOWN = 10;
    /** The search reach round H and the longest county road kept, m. */
    static final double MAX_REACH = 5000, MAX_LENGTH = 10000;
    /**
     * Town entries: the approach point beyond the limit, the Bezier blends tried into the arm's line (400 and 480 added
     * 2026-10-19: with the town grid on the block grid a line can reach the approach point well off the arm's line), the
     * end beyond the box, m.
     */
    static final double APPROACH_OUT = 60, END_OUT = 2;
    static final double[] BLEND = {150, 200, 260, 320, 400, 480};
    /** The town end's landing: its length and largest grade. */
    static final double TOWN_LANDING = 20, TOWN_LANDING_GRADE = 0.03;
    /** A road with no town at either end counts this much longer, m. */
    static final double STATE_PENALTY = 1500;
    /** A state road host: away from its ends and from other roads' ends, clear of its structures, m. */
    static final double HOST_END = 150, HOST_OTHER_END = 200, HOST_STRUCTURE = 80;
    /**
     * The driveway, m: its shortest and longest length (the house out of sight of the road, the contract's limit), its last
     * stretch straight and square to the road and the straight's shortest run from where the search's path turns into it
     * (the path's smoothing ends on the straight at most 8 m on; longer runs tried in DRIVE_RUNS), its largest grade, over
     * the last stretch, its half width.
     */
    public static final double DRIVE_MIN = 100, DRIVE_MAX = 250, DRIVE_STRAIGHT = 20, DRIVE_RUN = 28, DRIVE_GRADE = 0.12, DRIVE_END_GRADE = 0.05,
            DRIVE_HALF = 1.5;
    static final double[] DRIVE_RUNS = {DRIVE_RUN, 36, 44, 52};
    /** The driveway's cost counts this much in a site's score; its ground smoothed over +- DRIVE_SMOOTH and pinned over DRIVE_PIN at each end, m. */
    static final double DRIVE_WEIGHT = 1, DRIVE_SMOOTH = 10, DRIVE_PIN = 60;
    /**
     * The apron (Driveway): the gravel widens over its last APRON m before the paved edge, each side a fillet of radius
     * APRON_R onto the edge, m.
     */
    public static final double APRON = 8, APRON_R = 6;
    /**
     * The mouth on the county road, m: from the road's ends, junction zones and structures; straight or a radius of at
     * least MOUTH_RADIUS and at most MOUTH_GRADE over +- MOUTH_RUN; SIGHT clear both ways (a driver's eye SIGHT_EYE over the
     * pavement 3 m off the edge, an oncoming car SIGHT_OBJECT high, the sight line within SIGHT_CLEAR of the paved edge); clear of
     * a town limit by MOUTH_TOWN; the driveway's turn within MOUTH_NODE of its straight's start; at most MOUTH_TRIES tried.
     */
    static final double MOUTH_END = 150, MOUTH_RUN = 60, MOUTH_RADIUS = 300, MOUTH_GRADE = 0.05, SIGHT = 150, SIGHT_EYE = 1.08, SIGHT_OBJECT = 1.08,
            SIGHT_CLEAR = 10, MOUTH_TOWN = 50, MOUTH_NODE = 16;
    static final int MOUTH_TRIES = 24;
    /** The line is held to the mouth's radius this far each way from its vertex nearest the mouth's cell, m. */
    static final double MOUTH_WINDOW = 320;
    /** The county road's centreline off the home's plot: at least PLOT_HARD, priced PLOT_SOFT_COST a metre within PLOT_SOFT, m. */
    static final double PLOT_HARD = 80, PLOT_SOFT = 120;
    static final float PLOT_SOFT_COST = 2;
    /**
     * Leg B: within CONE degrees of leg A's way reversed up to CONE_R from the mouth; a state road at least FAR_JOINT from
     * leg A's junction; else a dead end in a STUB ring from the mouth (the first that routes), scored DEAD_END_PENALTY.
     */
    static final double CONE = 35, CONE_R = 250, FAR_JOINT = 1500, DEAD_END_PENALTY = 1000;
    /** A dead end runs on about this far past the mouth (user 2026-10-19): a mouth nearer the dead end is priced 2 a metre short of it, m. */
    static final double DEAD_END_RUN = 300;
    static final double[][] STUB = {{288, 352}, {160, 224}};
    /**
     * The farm gate across the driveway, its centre GATE_MIN .. GATE_MAX from the paved edge (the first in GATE_STEP steps
     * whose whole Y the profile can reach); the mailbox MAILBOX_OFF beyond the paved edge, MAILBOX_ALONG along the road on
     * the right seen from the driveway; a dead end's barrier BARRIER_IN from the road's start, m.
     */
    static final double GATE_MIN = 13, GATE_MAX = 17, GATE_STEP = 0.5, MAILBOX_OFF = 1.2, MAILBOX_ALONG = DRIVE_HALF + APRON_R + 2, BARRIER_IN = 1;
    /**
     * The pads (Main 2026-10-19: the gate and the mailbox stand on whole blocks, Driveway#padY), m: the driveway's datum
     * level at the gate's whole Y over +- GATE_FLAT (and the next datum point beyond, so the line between them stays
     * level), its crown fading back in over GATE_BLEND; the gate's box GATE_ALONG along and GATE_ACROSS across its centre
     * (its posts); the mailbox's Y the paved edge's plus MAILBOX_ROUND, rounded down (the pad never stands proud of the
     * asphalt by more); its neighbours within MAILBOX_KEEP of the paved edge keep the road's turf. GATE_MIN - GATE_FLAT -
     * GATE_BLEND >= APRON: the level stretch never reaches the apron.
     */
    public static final double GATE_FLAT = 3, GATE_BLEND = 2, GATE_ALONG = 1.5, GATE_ACROSS = 2.2, MAILBOX_ROUND = 0.3, MAILBOX_KEEP = 0.3;
    /** The gate's level stretch blends into the profile over this much toward the house (toward the road: an even grade to the edge), m. */
    static final double GATE_PIN = 30;
    /** The county road keeps this clear of every other road's formation (its hosts' mouths excepted), m. */
    static final double CLEAR = 6;

    /**
     * The home G3 settled: the site (G2's pivot, rotation, house ground Y), its node H {x, y, z, heading}, the link (the
     * county road's end: "town", "state" or "none"), the county road's id (null: none), the driveway's datum {x, z, y}
     * every 2 m or so from H to the county road's paved edge, the farm gate {x, y, z, along x, along z} (its centre on the
     * driveway, the driveway's direction toward the road: the gate spans across it), the mailbox {x, y, z, facing x, facing
     * z} (beside the mouth, facing the road), what lies beyond the mouth the other way (the county road's start: "town",
     * "state", "end" or "none") and the ROAD ENDS barrier at a dead end {x, y, z, facing x, facing z} (null: none).
     */
    public record Home(int x, int z, int rotation, int groundY, double[] node, String link, String road, double[] drive, double[] gate, double[] mailbox,
                       String beyond, double[] barrier) {
        /** The site's reservation (ForestHomeLayout, the same G2 gives). */
        public StructureReservation reservation() {
            int[] o = ForestHomeLayout.origin(x, z, rotation);
            return ForestHomeLayout.reservation(o[0], groundY, o[1], rotation);
        }
    }

    /** The network with the county road, the towns with its entry, the home (null: G2 found no site). */
    public record Result(List<RouteData> roads, List<TownPlan> towns, Home home) {}

    /** One way into a town: the node, its arm, the road's end point and its along coordinate, the approach point. */
    private record Way(TownPlan town, int node, int dir, double[] end, double endAlong, double[] approach) {}

    /** A leg's goal cells ("town", "state" or "end") with the goal disk; per cell its way or its host {road index, JSON station}. */
    private record Goals(String kind, boolean[] goal, double gx, double gz, double gr, Map<Integer, Way> ways, Map<Integer, double[]> hosts) {}

    /** A leg from the mouth's cell: its kind, its cells (the mouth's first), its end point, its way or host {road index, JSON station}. */
    private record Leg(String kind, int[] cells, double[] end, Way way, double[] host) {}

    /** The driveway: its datum points {x, z, y}, length, the gate and mailbox anchors, the mouth's station on the road. */
    private record Drive(double[] points, double length, double[] gate, double[] mailbox, double station) {}

    /** A site's county road: its road (trimmed to its junctions), the town entry (null without a town), its driveway, its score. */
    private record Option(int site, String link, String beyond, RouteData road, TownPlan.Entry entry, TownPlan town, Drive drive, double[] barrier,
                          double length, double score) {}

    public static Result plan(List<RouteData> roads, List<TownPlan> towns, RouteTerrain t, List<String> report) {
        return plan(roads, towns, t, report, Memo.NONE);
    }

    /**
     * The home and its county road; memo: the site a former build chose ("x z rotation groundY link beyond"), tried alone
     * when present, stored after a whole search.
     */
    public static Result plan(List<RouteData> roads, List<TownPlan> towns, RouteTerrain t, List<String> report, Memo memo) {
        String m = memo.load();
        if (m != null) {
            try {
                String[] f = m.trim().split(" ");
                int[] site = {Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3])};
                Result r = plan(roads, towns, t, report, site, f[4], f[5]);
                if (r != null) return r;
                report.add("county road: the remembered site " + m.trim() + " no longer links; searching again");
            } catch (RuntimeException e) {
                report.add("county road: the remembered site '" + m.trim() + "' does not read (" + e + "); searching again");
            }
        }
        Result r = plan(roads, towns, t, report, null, null, null);
        Home h = r.home();
        if (h != null) memo.store(h.x() + " " + h.z() + " " + h.rotation() + " " + h.groundY() + " " + h.link() + " " + h.beyond());
        return r;
    }

    /**
     * The search (remembered null) or the remembered site {x, z, rotation, groundY} alone, which must link as it did
     * (link, beyond; null when it does not).
     */
    private static Result plan(List<RouteData> roads, List<TownPlan> towns, RouteTerrain t, List<String> report, int[] remembered, String link, String beyond) {
        long t0 = System.nanoTime();
        FederalMap.Start st = t.map.start();
        List<RoadDesign> designs = new ArrayList<>();
        List<RouteData> built = new ArrayList<>();
        for (RouteData r : roads) {
            try {
                designs.add(RoadNetworkJson.design(r));
                built.add(r);
            } catch (IllegalArgumentException e) {
                report.add("county road: " + r.id() + " has no design (" + e.getMessage() + "); not a zone");
            }
        }
        // G2 with the zones near the start region
        List<Shape> hard = new ArrayList<>(), soft = new ArrayList<>();
        double reach = st.radius() + ZONE_REACH;
        for (RoadDesign d : designs) {
            for (double[] piece : near(d, st.x(), st.z(), reach + d.section.edge + SOFT_R4)) {
                hard.add(new Polyline(piece, d.section.edge + ROAD_HARD));
                soft.add(new Polyline(piece, d.section == CrossSection.R4 ? SOFT_R4 : SOFT_OTHER));
            }
        }
        for (TownPlan p : towns) {
            RotatedRect limit = limit(p);
            if (limit.grow(TOWN_SOFT).distance(st.x(), st.z()) > reach) continue;
            hard.add(limit.grow(TOWN_HARD));
            soft.add(limit.grow(TOWN_SOFT));
        }
        // the candidates {x, z, rotation, groundY} and their reservations: G2's, or the remembered one
        List<int[]> sites = new ArrayList<>();
        List<StructureReservation> reservations = new ArrayList<>();
        if (remembered != null) {
            sites.add(remembered);
            int[] o = ForestHomeLayout.origin(remembered[0], remembered[1], remembered[2]);
            reservations.add(ForestHomeLayout.reservation(o[0], remembered[3], o[1], remembered[2]));
            report.add(String.format(Locale.ROOT, "county road: the remembered site (%d, %d) r%d", remembered[0], remembered[1], remembered[2]));
        } else {
            StartAreaPlanner.Result g2 = StartAreaPlanner.plan(new StartAreaPlanner.Context(t.plan.seed, t.plan, st.x(), st.z(), st.radius(), hard, soft));
            report.add(String.format(Locale.ROOT, "county road: G2 found %d sites (%d woodland, %d riparian) with %d hard and %d soft zones in %d ms",
                    g2.sites().size(), g2.woodland(), g2.riparian(), hard.size(), soft.size(), g2.millis()));
            for (StartAreaPlanner.Site s : g2.sites()) sites.add(new int[]{s.x(), s.z(), s.rotation(), s.groundY()});
            reservations.addAll(g2.reservations());
        }
        if (sites.isEmpty()) return new Result(roads, towns, null);
        long tg = System.nanoTime();
        // the cells within reach of any site (the rest is avoided anyway, #avoid): round the start region, or round the
        // remembered site's H alone (the same cells within reach, so the same road)
        RoadNode h0 = reservations.get(0).nodes().get(0);
        RouteGrid g = remembered != null ? new RouteGrid(t, h0.x(), h0.z(), MAX_REACH + 2 * RouteGrid.CELL)
                : new RouteGrid(t, st.x(), st.z(), st.radius() + MAX_REACH + 2 * RouteGrid.CELL);
        long tb = System.nanoTime();
        boolean[] band = band(g, designs, towns);
        List<Way> ways = new ArrayList<>();
        for (TownPlan p : towns) ways.addAll(ways(p, g, band, designs));
        report.add(String.format(Locale.ROOT, "county road: route grid %d ms, road band %d ms", (tb - tg) / 1_000_000, (System.nanoTime() - tb) / 1_000_000));
        Option best = null;
        for (int k = 0; k < sites.size(); k++) {
            Option o = site(k, reservations.get(k), ways, g, band, built, designs, towns, t, report);
            if (o != null && (best == null || better(o, best))) best = o;
        }
        // a remembered site must link as it did
        if (remembered != null && !(best == null ? "none none" : best.link() + " " + best.beyond()).equals(link + " " + beyond)) return null;
        int pick = best == null ? 0 : best.site();
        int[] site = sites.get(pick);
        StructureReservation res = reservations.get(pick);
        RoadNode h = res.nodes().get(0);
        double[] node = {h.x(), h.y(), h.z(), h.heading()};
        if (best == null) {
            report.add(String.format(Locale.ROOT, "county road: no site of %d links to a town or a state road; the home at site 1 (%d, %d) has no road (%d ms)",
                    sites.size(), site[0], site[1], (System.nanoTime() - t0) / 1_000_000));
            return new Result(roads, towns, new Home(site[0], site[1], site[2], site[3], node, "none", null, new double[0], null, null, "none", null));
        }
        List<RouteData> out = new ArrayList<>(roads);
        out.add(best.road());
        List<TownPlan> tout = new ArrayList<>();
        for (TownPlan p : towns) {
            if (p != best.town()) { tout.add(p); continue; }
            List<TownPlan.Entry> es = new ArrayList<>(p.entries);
            es.add(best.entry());
            tout.add(p.withEntries(es));
        }
        Drive dr = best.drive();
        report.add(String.format(Locale.ROOT, "county road: site %d of %d (%d, %d) r%d: %.0f m of county road from %s to %s, the mouth at s %.0f, a %.0f m driveway (%d ms)",
                pick + 1, sites.size(), site[0], site[1], site[2], best.length(), best.beyond(), best.link().equals("town") ? best.town().name : best.link(),
                dr.station(), dr.length(), (System.nanoTime() - t0) / 1_000_000));
        return new Result(out, tout, new Home(site[0], site[1], site[2], site[3], node, best.link(), best.road().id(), dr.points(), dr.gate(), dr.mailbox(),
                best.beyond(), best.barrier()));
    }

    /** Whether a beats b: a road running through beats a dead end (the dead end is the fallback, user 2026-10-19), then the lower score. */
    private static boolean better(Option a, Option b) {
        boolean ea = a.beyond().equals("end"), eb = b.beyond().equals("end");
        return ea != eb ? !ea : a.score() < b.score();
    }

    // ------------------------------------------------------------------ zones and the grid

    /** A design's centreline every 10 m in the pieces that come within reach of (cx, cz), {x0, z0, x1, z1, ...} each. */
    private static List<double[]> near(RoadDesign d, double cx, double cz, double reach) {
        List<double[]> out = new ArrayList<>();
        List<Double> cur = new ArrayList<>();
        double step = Math.min(10, d.length);
        for (double s = 0; s <= d.length + 1e-9; s += step) {
            double[] p = d.point(Math.min(s, d.length), 0);
            if (Math.hypot(p[0] - cx, p[1] - cz) <= reach) {
                cur.add(p[0]);
                cur.add(p[1]);
            } else if (!cur.isEmpty()) {
                // the piece runs one sample beyond the reach (its last segment covers the edge)
                cur.add(p[0]);
                cur.add(p[1]);
                flush(cur, out);
            }
        }
        flush(cur, out);
        return out;
    }

    private static void flush(List<Double> cur, List<double[]> out) {
        if (cur.size() >= 4) {
            double[] a = new double[cur.size()];
            for (int i = 0; i < a.length; i++) a[i] = cur.get(i);
            out.add(a);
        }
        cur.clear();
    }

    /** A town's limit as a world rectangle. */
    static RotatedRect limit(TownPlan p) {
        double[] c = p.world((p.limit[0] + p.limit[1]) / 2, (p.limit[2] + p.limit[3]) / 2);
        return new RotatedRect(c[0], c[1], (p.limit[1] - p.limit[0]) / 2, (p.limit[3] - p.limit[2]) / 2, Math.toDegrees(Math.atan2(p.az, p.ax)));
    }

    /** The cells no county road enters: every road's band (its paved edge + AVOID_ROAD) and every town limit (+ AVOID_TOWN). */
    private static boolean[] band(RouteGrid g, List<RoadDesign> designs, List<TownPlan> towns) {
        boolean[] out = new boolean[g.cells()];
        for (RoadDesign d : designs) {
            double r = d.section.edge + AVOID_ROAD;
            for (double s = 0; s <= d.length + 1e-9; s += 8) {
                double[] p = d.point(Math.min(s, d.length), 0);
                mark(g, out, p[0], p[1], r);
            }
        }
        for (TownPlan p : towns) {
            RotatedRect lim = limit(p).grow(AVOID_TOWN);
            double[] b = lim.bounds();
            for (double x = b[0]; x <= b[2] + RouteGrid.CELL; x += RouteGrid.CELL / 2)
                for (double z = b[1]; z <= b[3] + RouteGrid.CELL; z += RouteGrid.CELL / 2)
                    if (lim.distance(x, z) <= RouteGrid.CELL / 2) out[g.cellOf(x, z)] = true;
        }
        return out;
    }

    /** Marks the cells whose centres lie within r of (x, z). */
    private static void mark(RouteGrid g, boolean[] m, double x, double z, double r) {
        int nx = g.nx(), nz = g.cells() / nx;
        int c0 = g.cellOf(x, z);
        int cc = c0 % nx, rr = c0 / nx, k = (int) Math.ceil(r / RouteGrid.CELL) + 1;
        for (int dr = -k; dr <= k; dr++)
            for (int dc = -k; dc <= k; dc++) {
                int c = cc + dc, w = rr + dr;
                if (c < 0 || w < 0 || c >= nx || w >= nz) continue;
                if (Math.hypot(g.cx(c) - x, g.cz(w) - z) <= r) m[w * nx + c] = true;
            }
    }

    /** The road band, the cells whose centres come within PLOT_HARD + half a cell of the home's plot, and those beyond MAX_REACH of H. */
    private static boolean[] avoid(RouteGrid g, boolean[] band, RotatedRect plot, RoadNode h) {
        boolean[] a = band.clone();
        int nx = g.nx();
        for (int c = 0; c < a.length; c++) {
            double x = g.cx(c % nx), z = g.cz(c / nx);
            if (Math.hypot(x - h.x(), z - h.z()) > MAX_REACH || plot.distance(x, z) < PLOT_HARD + RouteGrid.CELL / 2) a[c] = true;
        }
        return a;
    }

    /** PLOT_SOFT_COST on the cells whose centres come within PLOT_SOFT + half a cell of the home's plot. */
    private static float[] penalty(RouteGrid g, RotatedRect plot, RoadNode h) {
        float[] p = new float[g.cells()];
        int nx = g.nx();
        double r = PLOT_SOFT + RouteGrid.CELL;
        int c0 = g.cellOf(h.x(), h.z()), k = (int) Math.ceil((r + 200) / RouteGrid.CELL);
        for (int dr = -k; dr <= k; dr++)
            for (int dc = -k; dc <= k; dc++) {
                int c = c0 % nx + dc, w = c0 / nx + dr;
                if (c < 0 || w < 0 || c >= nx || w * nx + c >= p.length) continue;
                if (plot.distance(g.cx(c), g.cz(w)) < PLOT_SOFT + RouteGrid.CELL / 2) p[w * nx + c] = PLOT_SOFT_COST;
            }
        return p;
    }

    // ------------------------------------------------------------------ goals

    /** The ways into the towns as goals, or null without any. */
    private static Goals townGoals(List<Way> ways, RouteGrid g) {
        if (ways.isEmpty()) return null;
        boolean[] goal = new boolean[g.cells()];
        Map<Integer, Way> at = new TreeMap<>();
        double gx = 0, gz = 0;
        for (Way w : ways) {
            int c = g.cellOf(w.approach()[0], w.approach()[1]);
            goal[c] = true;
            at.putIfAbsent(c, w);
            gx += w.approach()[0] / ways.size();
            gz += w.approach()[1] / ways.size();
        }
        double gr = 0;
        for (Way w : ways) gr = Math.max(gr, Math.hypot(w.approach()[0] - gx, w.approach()[1] - gz));
        return new Goals("town", goal, gx, gz, gr + RouteGrid.CELL, at, null);
    }

    /**
     * The state roads within reach of H as goals: away from their ends, other roads' ends and their structures, farther
     * than far from (fx, fz) (far 0: anywhere); null without any.
     */
    private static Goals stateGoals(List<RouteData> roads, List<RoadDesign> designs, RouteGrid g, RoadNode h, double fx, double fz, double far) {
        boolean[] goal = new boolean[g.cells()];
        Map<Integer, double[]> at = new TreeMap<>();
        List<double[]> ends = new ArrayList<>();
        for (RoadDesign d : designs) { ends.add(d.point(0, 0)); ends.add(d.point(d.length, 0)); }
        for (int i = 0; i < roads.size(); i++) {
            RouteData r = roads.get(i);
            RoadDesign d = designs.get(i);
            if (!r.roadClass().equals("state") || d.attach != null) continue;
            double off = r.trim() == null ? 0 : r.trim()[0];
            double[] e0 = d.point(0, 0), e1 = d.point(d.length, 0);
            for (double s = HOST_END; s <= d.length - HOST_END; s += 16) {
                double js = s + off;
                double[] p = d.point(s, 0);
                if (Math.hypot(p[0] - h.x(), p[1] - h.z()) > MAX_REACH || far > 0 && Math.hypot(p[0] - fx, p[1] - fz) < far) continue;
                boolean near = false;
                for (Structure x : r.structures()) if (x.s1() >= js - HOST_STRUCTURE && x.s0() <= js + HOST_STRUCTURE) near = true;
                // another road's end near the mouth (its own ends are HOST_END away)
                for (double[] e : ends)
                    if (Math.hypot(e[0] - p[0], e[1] - p[1]) < HOST_OTHER_END && !same(e, e0) && !same(e, e1)) near = true;
                if (near) continue;
                int c = g.cellOf(p[0], p[1]);
                goal[c] = true;
                at.putIfAbsent(c, new double[]{i, js});
            }
        }
        return at.isEmpty() ? null : new Goals("state", goal, h.x(), h.z(), MAX_REACH, null, at);
    }

    /** A dead end's goals: the open cells (not in block) within lo .. hi of the mouth's cell m, inside the cone toward way. */
    private static Goals stubGoals(RouteGrid g, boolean[] block, int m, double[] way, double lo, double hi) {
        int nx = g.nx();
        double mx = g.cx(m % nx), mz = g.cz(m / nx);
        boolean[] goal = new boolean[g.cells()];
        boolean any = false;
        int k = (int) Math.ceil(hi / RouteGrid.CELL) + 1;
        for (int dr = -k; dr <= k; dr++)
            for (int dc = -k; dc <= k; dc++) {
                int c = m % nx + dc, w = m / nx + dr;
                if (c < 0 || w < 0 || c >= nx || w * nx + c >= goal.length) continue;
                int i = w * nx + c;
                double dx = g.cx(c) - mx, dz = g.cz(w) - mz, d = Math.hypot(dx, dz);
                if (d < lo || d > hi || block[i]) continue;
                if ((dx * way[0] + dz * way[1]) / d < Math.cos(Math.toRadians(CONE))) continue;
                goal[i] = true;
                any = true;
            }
        double r = (lo + hi) / 2;
        return any ? new Goals("end", goal, mx + way[0] * r, mz + way[1] * r, hi, null, null) : null;
    }

    private static boolean same(double[] a, double[] b) { return Math.abs(a[0] - b[0]) < 1e-9 && Math.abs(a[1] - b[1]) < 1e-9; }

    /** The leg a route found to the goals: its kind and end point, its way or host. */
    private static Leg leg(Goals gs, int[] cells, RouteGrid g, List<RoadDesign> designs, List<RouteData> roads) {
        int last = cells[cells.length - 1];
        return switch (gs.kind()) {
            case "town" -> {
                Way w = gs.ways().get(last);
                yield new Leg("town", cells, w.approach(), w, null);
            }
            case "state" -> {
                double[] hit = gs.hosts().get(last);
                RouteData host = roads.get((int) hit[0]);
                double off = host.trim() == null ? 0 : host.trim()[0];
                yield new Leg("state", cells, designs.get((int) hit[0]).point(hit[1] - off, 0), null, hit);
            }
            default -> new Leg("end", cells, new double[]{g.cx(last % g.nx()), g.cz(last / g.nx())}, null, null);
        };
    }

    // ------------------------------------------------------------------ the town

    /**
     * The ways into a town: every outer intersection's free arm out of the town (no street on it and no node beyond it on
     * its line; never -v, toward the interstate; never an exit's corner), its approach point APPROACH_OUT beyond the limit
     * on open ground, the stretch from there into the arm clear of every road (#armClear).
     */
    private static List<Way> ways(TownPlan p, RouteGrid g, boolean[] band, List<RoadDesign> designs) {
        int n = p.nodes.size();
        boolean[][] arm = new boolean[n][4];
        double[] extS = new double[n], extV = new double[n];
        for (TownPlan.Street st : p.streets) {
            TownPlan.Node a = p.nodes.get(st.a()), b = p.nodes.get(st.b());
            boolean par = Math.abs(a.v() - b.v()) < 1e-6;
            double hr = TownStreets.layout(st.cls()).halfRow();
            for (int e = 0; e < 2; e++) {
                int i = e == 0 ? st.a() : st.b();
                TownPlan.Node me = e == 0 ? a : b, other = e == 0 ? b : a;
                arm[i][par ? (other.s() > me.s() ? 0 : 1) : (other.v() > me.v() ? 2 : 3)] = true;
                // a node's box: the cross streets' rows along s, the parallel streets' along v (TownWorks)
                if (par) extV[i] = Math.max(extV[i], hr);
                else extS[i] = Math.max(extS[i], hr);
            }
        }
        List<Way> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            boolean exit = false;
            for (double[] e : p.exitEnds) if ((int) e[0] == i) exit = true;
            for (TownPlan.Entry e : p.entries) if (e.node() == i) exit = true;
            if (exit) continue;
            TownPlan.Node q = p.nodes.get(i);
            for (int dir = 0; dir < 3; dir++) {
                if (arm[i][dir]) continue;
                double[] u = TownPlan.arm(dir);
                boolean beyond = false;
                for (TownPlan.Node o : p.nodes) {
                    double ds = o.s() - q.s(), dv = o.v() - q.v();
                    if (u[0] != 0 ? Math.abs(dv) < 1 && ds * u[0] > 1 : Math.abs(ds) < 1 && dv * u[1] > 1) beyond = true;
                }
                if (beyond) continue;
                boolean par = dir <= TownPlan.DIR_S_DOWN;
                double ext = par ? (extS[i] > 0 ? extS[i] : extV[i]) : (extV[i] > 0 ? extV[i] : extS[i]);
                double c = par ? q.s() : q.v(), sign = par ? u[0] : u[1];
                double endAlong = c + sign * (ext + END_OUT);
                double toLimit = switch (dir) {
                    case TownPlan.DIR_S_UP -> p.limit[1] - q.s();
                    case TownPlan.DIR_S_DOWN -> q.s() - p.limit[0];
                    default -> p.limit[3] - q.v();
                };
                double out2 = toLimit + APPROACH_OUT;
                double[] end = par ? p.world(endAlong, q.v()) : p.world(q.s(), endAlong);
                double[] ap = p.world(q.s() + u[0] * out2, q.v() + u[1] * out2);
                if (band[g.cellOf(ap[0], ap[1])] || !armClear(ap, end, designs)) continue;
                out.add(new Way(p, i, dir, end, endAlong, ap));
            }
        }
        return out;
    }

    /**
     * Whether the straight from a way's approach point to its end keeps the county road's clearance (#clear) from every
     * road. Since the town grid runs on the block grid (2026-10-19) an outer arm at the town's front can head toward the
     * interstate slanting past the town; the route grid only reaches the approach point, so a way like that failed every
     * leg that took it (seed 11823271242869393782: the home had no road).
     */
    private static boolean armClear(double[] ap, double[] end, List<RoadDesign> designs) {
        double reach = CrossSection.C2.edge + 2 + CLEAR, len = Math.hypot(end[0] - ap[0], end[1] - ap[1]);
        for (RoadDesign q : designs) {
            double need = q.section.edge + reach + 2;
            for (double d = 0; d <= len + 1e-9; d += 4) {
                double t = len < 1e-9 ? 0 : Math.min(1, d / len), x = ap[0] + (end[0] - ap[0]) * t, z = ap[1] + (end[1] - ap[1]) * t;
                double[] l = q.local(x, z, need);
                if (l != null && Math.abs(l[1]) <= need) return false;
            }
        }
        return true;
    }

    /**
     * The line's last stretch into the town's arm: a cubic Bezier from BLEND back along the line to the approach point,
     * heading along the arm into the town, then straight to the end; null when no blend keeps the class's radius.
     */
    private static double[][] intoArm(double[][] line, Way w) {
        double[] xs = line[0], zs = line[1];
        double len = RoutePolyline.length(xs, zs);
        double[] u = TownPlan.arm(w.dir());
        TownPlan p = w.town();
        // the arm's way out of the town in world xz
        double ox = u[0] * p.ax + u[1] * p.bx, oz = u[0] * p.az + u[1] * p.bz;
        double[] a = w.approach();
        for (double b : BLEND) {
            if (len < b + 40) continue;
            double[] p0 = RoutePolyline.at(xs, zs, len - b), pa = RoutePolyline.at(xs, zs, len - b + 2), pb = RoutePolyline.at(xs, zs, len - b - 2);
            double tl = Math.hypot(pa[0] - pb[0], pa[1] - pb[1]);
            double tx = (pa[0] - pb[0]) / tl, tz = (pa[1] - pb[1]) / tl;
            double[][] c = {p0, {p0[0] + tx * b / 3, p0[1] + tz * b / 3}, {a[0] + ox * b / 3, a[1] + oz * b / 3}, a};
            if (RoadJunctions.bezierMinRadius(c) < RoadClassSpec.COUNTY.radius) continue;
            double[][] head = RoutePolyline.cut(xs, zs, 0, len - b);
            List<double[]> pts = new ArrayList<>();
            for (int i = 0; i < head[0].length; i++) pts.add(new double[]{head[0][i], head[1][i]});
            List<double[]> bz = RoadJunctions.bezierPoints(c, RoadJunctions.POINT);
            for (int i = 1; i < bz.size(); i++) pts.add(bz.get(i));
            double[] e = w.end();
            double run = Math.hypot(e[0] - a[0], e[1] - a[1]);
            int m = Math.max(1, (int) Math.ceil(run / RoadJunctions.POINT));
            for (int i = 1; i <= m; i++) pts.add(new double[]{a[0] + (e[0] - a[0]) * i / m, a[1] + (e[1] - a[1]) * i / m});
            double[][] out = new double[2][pts.size()];
            for (int i = 0; i < pts.size(); i++) { out[0][i] = pts.get(i)[0]; out[1][i] = pts.get(i)[1]; }
            return out;
        }
        return null;
    }

    // ------------------------------------------------------------------ a site

    /**
     * A site's county road past it and its driveway (the class comment), or null: leg A to a town, else to a state road;
     * for each, leg B to the other kind, else a dead end; the first pair that designs, joins and finds a mouth.
     */
    private static Option site(int k, StructureReservation res, List<Way> ways, RouteGrid g, boolean[] band, List<RouteData> roads, List<RoadDesign> designs,
                               List<TownPlan> towns, RouteTerrain t, List<String> report) {
        RoadNode h = res.nodes().get(0);
        String what = String.format(Locale.ROOT, "county road: site %d (%.0f, %.0f)", k + 1, h.x(), h.z());
        RotatedRect plot = ForestHomeLayout.plot(res);
        DriveSearch ds = new DriveSearch(t, h, plot, designs);
        if (ds.first < 0) {
            report.add(what + ": the yard's way out is closed");
            return null;
        }
        boolean[] avoid = avoid(g, band, plot, h);
        float[] penalty = penalty(g, plot, h);
        // the mouths' cells: where the driveway's search gets far enough, each at its cheapest driveway
        Map<Integer, Double> mouths = new TreeMap<>();
        for (int i : ds.reachedNodes()) {
            double l = ds.length(i);
            if (l < DRIVE_MIN - DRIVE_RUN || l > DRIVE_MAX - DRIVE_RUN) continue;
            int c = g.cellOf(ds.x(i), ds.z(i));
            if (!avoid[c]) mouths.merge(c, ds.cost(i) * DRIVE_WEIGHT, Math::min);
        }
        if (mouths.isEmpty()) {
            report.add(String.format(Locale.ROOT, "%s: the driveway reaches no cell %.0f m off the plot (%d nodes reached)", what, PLOT_HARD, ds.reachedNodes().length));
            return null;
        }
        int[] from = new int[mouths.size()];
        double[] fromCost = new double[mouths.size()];
        int q = 0;
        for (Map.Entry<Integer, Double> e : mouths.entrySet()) { from[q] = e.getKey(); fromCost[q++] = e.getValue(); }
        for (String ka : new String[]{"town", "state"}) {
            Goals ga = ka.equals("town") ? townGoals(ways, g) : stateGoals(roads, designs, g, h, 0, 0, 0);
            if (ga == null) continue;
            boolean[] av = open(avoid, ga.goal(), null);
            int[] ca = g.route(from, fromCost, ga.goal(), ga.gx(), ga.gz(), ga.gr(), RoadClassSpec.COUNTY, av, penalty);
            if (ca == null) {
                report.add(what + ": leg A: no route to a " + ka);
                continue;
            }
            Leg a = leg(ga, ca, g, designs, roads);
            int m = ca[0];
            double[] way = away(g, a);
            boolean[] block = block(g, m, way, ca);
            List<Goals> bs = new ArrayList<>();
            if (ka.equals("state")) {
                Goals gt = townGoals(ways, g);
                if (gt != null) bs.add(gt);
                Goals gs = stateGoals(roads, designs, g, h, a.end()[0], a.end()[1], FAR_JOINT);
                if (gs != null) bs.add(gs);
            } else {
                Goals gs = stateGoals(roads, designs, g, h, 0, 0, 0);
                if (gs != null) bs.add(gs);
            }
            boolean[] avb = open(avoid, null, block);
            for (double[] ring : STUB) {
                Goals gs = stubGoals(g, avb, m, way, ring[0], ring[1]);
                if (gs != null) bs.add(gs);
            }
            for (Goals gb : bs) {
                int[] cb = g.route(new int[]{m}, null, gb.goal(), gb.gx(), gb.gz(), gb.gr(), RoadClassSpec.COUNTY, open(avoid, gb.goal(), block), penalty);
                String legs = "leg A to a " + ka + ", leg B to " + (gb.kind().equals("end") ? "a dead end" : "a " + gb.kind());
                if (cb == null) {
                    report.add(what + ": " + legs + ": no route");
                    continue;
                }
                Option o = assemble(k, what + ": " + legs, h, plot, ds, a, leg(gb, cb, g, designs, roads), g, roads, designs, towns, t, report);
                if (o != null) return o;
            }
        }
        return null;
    }

    /**
     * The line held to 1.1 x MOUTH_RADIUS within MOUTH_WINDOW of its vertex nearest the mouth's cell centre (mx, mz), then
     * the class's radius everywhere again (the window's ends): the A* line keeps only the class's radius, the mouth wants
     * a straight or a gentle bend.
     */
    private static void straighten(double[][] line, double mx, double mz) {
        double[] xs = line[0], zs = line[1];
        int k = 0;
        for (int i = 1; i < xs.length; i++) if (Math.hypot(xs[i] - mx, zs[i] - mz) < Math.hypot(xs[k] - mx, zs[k] - mz)) k = i;
        int i0 = k, i1 = k;
        double back = 0, on = 0;
        while (i0 > 0 && back < MOUTH_WINDOW) { back += Math.hypot(xs[i0] - xs[i0 - 1], zs[i0] - zs[i0 - 1]); i0--; }
        while (i1 < xs.length - 1 && on < MOUTH_WINDOW) { on += Math.hypot(xs[i1 + 1] - xs[i1], zs[i1 + 1] - zs[i1]); i1++; }
        RoutePolyline.relax(xs, zs, i0, i1, 1.1 * MOUTH_RADIUS);
        RoutePolyline.relax(xs, zs, 0, xs.length - 1, 1.1 * RoadClassSpec.COUNTY.radius);
    }

    /** The avoided cells with the goal cells open (goal null: none) except where block holds (null: nowhere); block's cells avoided. */
    private static boolean[] open(boolean[] avoid, boolean[] goal, boolean[] block) {
        boolean[] a = avoid.clone();
        for (int c = 0; c < a.length; c++) {
            if (goal != null && goal[c]) a[c] = false;
            if (block != null && block[c]) a[c] = true;
        }
        return a;
    }

    /** The way on from the mouth's cell opposite to leg A's: away from A's path about 100 m on (its end when shorter), unit {x, z}. */
    private static double[] away(RouteGrid g, Leg a) {
        int nx = g.nx(), m = a.cells()[0];
        double mx = g.cx(m % nx), mz = g.cz(m / nx), px = a.end()[0], pz = a.end()[1];
        for (int c : a.cells()) {
            double x = g.cx(c % nx), z = g.cz(c / nx);
            if (Math.hypot(x - mx, z - mz) >= 3 * RouteGrid.CELL) { px = x; pz = z; break; }
        }
        double l = Math.hypot(px - mx, pz - mz);
        return l < 1e-9 ? new double[]{1, 0} : new double[]{(mx - px) / l, (mz - pz) / l};
    }

    /** The cells leg B may not enter: within CONE_R of the mouth's cell m outside CONE of way, and leg A's cells and their neighbours. */
    private static boolean[] block(RouteGrid g, int m, double[] way, int[] legA) {
        int nx = g.nx(), nz = g.cells() / nx;
        boolean[] b = new boolean[g.cells()];
        double mx = g.cx(m % nx), mz = g.cz(m / nx), cos = Math.cos(Math.toRadians(CONE));
        int k = (int) Math.ceil(CONE_R / RouteGrid.CELL);
        for (int dr = -k; dr <= k; dr++)
            for (int dc = -k; dc <= k; dc++) {
                int c = m % nx + dc, w = m / nx + dr;
                if (c < 0 || w < 0 || c >= nx || w >= nz || dc == 0 && dr == 0) continue;
                double dx = g.cx(c) - mx, dz = g.cz(w) - mz, d = Math.hypot(dx, dz);
                if (d <= CONE_R && (dx * way[0] + dz * way[1]) / d < cos) b[w * nx + c] = true;
            }
        for (int i = 1; i < legA.length; i++)
            for (int dr = -1; dr <= 1; dr++)
                for (int dc = -1; dc <= 1; dc++) {
                    int c = legA[i] % nx + dc, w = legA[i] / nx + dr;
                    if (c < 0 || w < 0 || c >= nx || w >= nz) continue;
                    if (w * nx + c != m) b[w * nx + c] = true;
                }
        return b;
    }

    /**
     * The county road of two legs (the town's leg its end, else leg A's; the other its start), designed and joined, with the
     * mouth and the driveway; null (reported) when any of it fails.
     */
    private static Option assemble(int k, String what, RoadNode h, RotatedRect plot, DriveSearch ds, Leg a, Leg b, RouteGrid g, List<RouteData> roads,
                                   List<RoadDesign> designs, List<TownPlan> towns, RouteTerrain t, List<String> report) {
        boolean swap = b.kind().equals("town");
        Leg s = swap ? a : b, e = swap ? b : a;
        int[] cells = new int[s.cells().length + e.cells().length - 1];
        for (int i = 0; i < s.cells().length; i++) cells[i] = s.cells()[s.cells().length - 1 - i];
        System.arraycopy(e.cells(), 1, cells, s.cells().length, e.cells().length - 1);
        double[][] line = RoutePolyline.smooth(g, cells, s.end()[0], s.end()[1], e.end()[0], e.end()[1], 12, RoadClassSpec.COUNTY.radius);
        straighten(line, g.cx(a.cells()[0] % g.nx()), g.cz(a.cells()[0] / g.nx()));
        if (e.kind().equals("town")) {
            line = intoArm(line, e.way());
            if (line == null) {
                Way w = e.way();
                report.add(String.format(Locale.ROOT, "%s: %s node %d: no blend into the arm keeps the radius (arm %d, approach %.0f %.0f)",
                        what, w.town().name, w.node(), w.dir(), w.approach()[0], w.approach()[1]));
                return null;
            }
        }
        RoadCurve curve = new RoadCurve(line[0], line[1]);
        if (curve.length > MAX_LENGTH) {
            report.add(String.format(Locale.ROOT, "%s: %.0f m long, longer than %.0f m", what, curve.length, MAX_LENGTH));
            return null;
        }
        RouteData hostS = s.kind().equals("state") ? roads.get((int) s.host()[0]) : null, hostE = e.kind().equals("state") ? roads.get((int) e.host()[0]) : null;
        RoadDesign hds = hostS == null ? null : designs.get((int) s.host()[0]), hde = hostE == null ? null : designs.get((int) e.host()[0]);
        String why = clear(curve, designs, hds, hde);
        if (why != null) {
            report.add(what + ": " + why);
            return null;
        }
        String from = hostS != null ? RoadNames.corridor(hostS.id()) : "dead_end";
        String to = hostE != null ? RoadNames.corridor(hostE.id()) : e.way().town().town;
        Built bt = build(curve, e.kind().equals("town") ? e.way().town().nodes.get(e.way().node()).h() : Double.NaN, t, from, to);
        if (bt.why != null) {
            report.add(what + ": " + bt.why);
            return null;
        }
        // the T junctions (RoadJunctions#find on the county road and its hosts alone)
        RouteData r = bt.road;
        double end = (r.profile().length - 1) * RoadProfile.DS;
        double[] trim = {0, end};
        String[] hosts = {null, null};
        RoadJunctions.Spec js = null, je = null;
        if (hostS != null || hostE != null) {
            List<RouteData> list = new ArrayList<>();
            list.add(r.withTrim(null));
            if (hostS != null) list.add(hostS);
            if (hostE != null && hostE != hostS) list.add(hostE);
            List<String> jr = new ArrayList<>();
            RoadJunctions.Result j = RoadJunctions.find(list, jr);
            for (RoadJunctions.Spec sp : j.junctions())
                if (sp.minor().equals(ID)) {
                    if (sp.atEnd()) je = sp;
                    else js = sp;
                }
            if (hostS != null && js == null || hostE != null && je == null) {
                String l = jr.isEmpty() ? "no junction" : jr.get(jr.size() - 1);
                report.add(what + ": " + l);
                return null;
            }
            r = j.roads().get(0);
            if (js != null) { trim[0] = js.endLine(); hosts[0] = hostS.id(); }
            if (je != null) { trim[1] = je.endLine(); hosts[1] = hostE.id(); }
        }
        RouteData m = r.withTrim(trim);
        if (hostS != null || hostE != null) m = m.withHosts(hosts);
        RoadDesign d = RoadNetworkJson.design(m);
        if (js != null && Junction.of(hds, d, false) == null || je != null && Junction.of(hde, d, true) == null) {
            report.add(what + ": a junction's pad cannot be built");
            return null;
        }
        // the county road off the home's plot
        for (double q = 0; q <= d.length; q += 4) {
            double[] p = d.point(q, 0);
            if (plot.distance(p[0], p[1]) < PLOT_HARD) {
                report.add(String.format(Locale.ROOT, "%s: %.0f m from the home's plot at s %.0f", what, plot.distance(p[0], p[1]), q));
                return null;
            }
        }
        String[] whyNot = new String[1];
        Drive dr = mouth(d, m, js, je, ds, h, towns, t, whyNot);
        if (dr == null) {
            report.add(what + ": " + whyNot[0]);
            return null;
        }
        // the town's entry: the design's end in town coordinates
        TownPlan.Entry entry = null;
        if (e.kind().equals("town")) {
            double[] pe = d.point(d.length, 0), le = e.way().town().local(pe[0], pe[1]);
            double endAlong = e.way().dir() <= TownPlan.DIR_S_DOWN ? le[0] : le[1];
            entry = new TownPlan.Entry(e.way().node(), e.way().dir(), RoadNetworkJson.round2(endAlong), CrossSection.C2.edge, ID);
        }
        double[] barrier = null;
        if (s.kind().equals("end")) {
            double[] p = d.point(BARRIER_IN, 0), p1 = d.point(BARRIER_IN + 1, 0);
            double l = Math.hypot(p1[0] - p[0], p1[1] - p[1]);
            barrier = new double[]{p[0], RoadNetworkJson.round2(d.pavementY(BARRIER_IN, 0)), p[1], (p1[0] - p[0]) / l, (p1[1] - p[1]) / l};
        }
        double length = d.length;
        boolean town = s.kind().equals("town") || e.kind().equals("town");
        double score = length + dr.length() * DRIVE_WEIGHT + (town ? 0 : STATE_PENALTY)
                + (s.kind().equals("end") ? DEAD_END_PENALTY : 0);
        report.add(String.format(Locale.ROOT, "%s: %.0f m (from %s to %s), grade max %.1f %%, %s; the mouth at s %.0f, a %.0f m driveway", what, length, s.kind(),
                e.kind().equals("town") ? e.way().town().name : e.kind(), 100 * bt.maxGrade, bt.structures, dr.station(), dr.length()));
        return new Option(k, e.kind(), s.kind(), m, entry, e.kind().equals("town") ? e.way().town() : null, dr, barrier, length, score);
    }

    // ------------------------------------------------------------------ the mouth and the driveway

    /**
     * The mouth on the designed county road d (route m, trimmed; its start and end junctions js, je or null) and the
     * driveway to it (the class comment): the stations every 2 m clear of the ends, the junctions' zones, structures and
     * towns, keeping the radius, the grade and the sight, each side; the driveway's turn the cheapest reached node near the
     * straight's start; tried cheapest first. Null (why[0]) when none builds.
     */
    private static Drive mouth(RoadDesign d, RouteData m, RoadJunctions.Spec js, RoadJunctions.Spec je, DriveSearch ds, RoadNode h, List<TownPlan> towns,
                               RouteTerrain t, String[] why) {
        double t0 = m.trim()[0], edge = d.section.edge;
        double lo = Math.max(MOUTH_END, js == null ? 0 : js.minorZone()[1] - t0), hi = Math.min(d.length - MOUTH_END, je == null ? d.length : je.minorZone()[0] - t0);
        double reach = DRIVE_MAX + edge + DRIVE_RUN + 20;
        List<double[]> cand = new ArrayList<>();   // {score, station, side, node, run}
        int[] seen = new int[11];
        for (double s = Math.ceil(lo / RoadProfile.DS) * RoadProfile.DS; s <= hi + 1e-9; s += RoadProfile.DS) {
            double[] p = d.point(s, 0);
            if (Math.hypot(p[0] - h.x(), p[1] - h.z()) > reach) continue;
            seen[0]++;
            boolean ok = true;
            for (Structure x : m.structures()) if (x.s1() >= s + t0 - MOUTH_END && x.s0() <= s + t0 + MOUTH_END) ok = false;
            for (TownPlan tp : towns) if (limit(tp).distance(p[0], p[1]) < MOUTH_TOWN) ok = false;
            if (!ok) continue;
            seen[1]++;
            int ev = evenWhy(d, s);
            if (ev != 0) { seen[ev == 1 ? 5 : 6]++; continue; }
            seen[2]++;
            for (int side = -1; side <= 1; side += 2) {
                int node = -1;
                double run = 0;
                double[] ep = d.point(s, side * edge);
                for (double r : DRIVE_RUNS) {
                    double[] qp = d.point(s, side * (edge + r));
                    double ix = (ep[0] - qp[0]) / r, iz = (ep[1] - qp[1]) / r;
                    node = ds.best(qp[0], qp[1], MOUTH_NODE, DRIVE_MIN - r - MOUTH_NODE, DRIVE_MAX - r - MOUTH_NODE, i -> {
                        double dx = qp[0] - ds.x(i), dz = qp[1] - ds.z(i), l = Math.hypot(dx, dz);
                        // the turn into the straight: never back from nearer the road, at most 60 degrees
                        return l < 2 || (dx * ix + dz * iz) / l >= 0.5;
                    });
                    run = r;
                    if (node >= 0) break;
                }
                double[] qp = d.point(s, side * (edge + run));
                if (node < 0) {
                    if (ds.best(qp[0], qp[1], MOUTH_NODE, 0, 1e9, i -> true) >= 0) seen[7]++;
                    continue;
                }
                seen[3]++;
                int sw = sightWhy(d, s, side);
                if (sw != 0) { seen[7 + sw]++; continue; }
                seen[4]++;
                double shortRun = js == null ? Math.max(0, DEAD_END_RUN - s) * 2 : 0;
                cand.add(new double[]{ds.cost(node) + Math.hypot(qp[0] - ds.x(node), qp[1] - ds.z(node)) + run + shortRun, s, side, node, run});
            }
        }
        cand.sort((x, y) -> x[0] != y[0] ? Double.compare(x[0], y[0]) : x[1] != y[1] ? Double.compare(x[1], y[1]) : Double.compare(x[2], y[2]));
        String last = "";
        for (int i = 0; i < Math.min(MOUTH_TRIES, cand.size()); i++) {
            double[] c = cand.get(i);
            String[] w = new String[1];
            Drive dr = drive(d, c[1], (int) c[2], (int) c[3], c[4], ds, h, t, w);
            if (dr != null) return dr;
            last = w[0];
        }
        why[0] = String.format(Locale.ROOT, "no mouth (%d stations near, %d clear, %d straight and even (%d bent, %d steep), %d reached (%d too short or turned), %d in sight (%d crest, %d bend, %d off)%s)",
                seen[0], seen[1], seen[2], seen[5], seen[6], seen[3], seen[7], seen[4], seen[8], seen[9], seen[10], last.isEmpty() ? "" : "; the last driveway: " + last);
        return null;
    }

    /** Whether d keeps a radius of at least MOUTH_RADIUS and a grade of at most MOUTH_GRADE over s +- MOUTH_RUN. */
    static boolean even(RoadDesign d, double s) { return evenWhy(d, s) == 0; }

    /** #even: 0, or why not: 1 bent, 2 steep. */
    private static int evenWhy(RoadDesign d, double s) {
        double a = Math.max(0, s - MOUTH_RUN), b = Math.min(d.length, s + MOUTH_RUN);
        for (double q = a; q <= b + 1e-9; q += 4) if (Math.abs(d.curve.curvature(q)) > 1 / MOUTH_RADIUS) return 1;
        for (double q = a; q + RoadProfile.DS <= b + 1e-9; q += RoadProfile.DS)
            if (Math.abs(d.pavementY(q + RoadProfile.DS, 0) - d.pavementY(q, 0)) / RoadProfile.DS > MOUTH_GRADE + 1e-4) return 2;
        return 0;
    }

    /**
     * Whether a driver at the mouth (3 m off the paved edge on side, the eye SIGHT_EYE over it) sees an object SIGHT_OBJECT
     * high on the centreline SIGHT on each way (or the road's end when nearer): the sight line over the pavement every 4 m
     * and within SIGHT_CLEAR of the paved edge.
     */
    static boolean sight(RoadDesign d, double s, int side) { return sightWhy(d, s, side) == 0; }

    /** #sight: 0, or why not: 1 a crest, 2 a bend, 3 off the road. */
    private static int sightWhy(RoadDesign d, double s, int side) {
        double edge = d.section.edge;
        double[] eye = d.point(s, side * (edge + 3));
        double ey = d.pavementY(s, side * edge) + SIGHT_EYE;
        for (int dir = -1; dir <= 1; dir += 2) {
            double far = Math.max(0, Math.min(d.length, s + dir * SIGHT));
            if (Math.abs(far - s) < 8) continue;
            double[] o = d.point(far, 0);
            double oy = d.pavementY(far, 0) + SIGHT_OBJECT;
            for (double q = s + dir * 4; dir * (far - q) > 1e-9; q += dir * 4) {
                double f = (q - s) / (far - s);
                double x = eye[0] + (o[0] - eye[0]) * f, z = eye[1] + (o[1] - eye[1]) * f;
                if (ey + (oy - ey) * f < d.pavementY(q, 0)) return 1;
                double[] l = d.local(x, z, edge + SIGHT_CLEAR + 20);
                if (l == null) return 3;
                if (Math.abs(l[1]) > edge + SIGHT_CLEAR) return 2;
            }
        }
        return 0;
    }

    /**
     * The driveway from H along the search's path to node, on to the straight's start Q (run off the paved edge at
     * station s on side) and square to the road to its edge E: the path and its first 8 m of straight Chaikin-smoothed (H
     * and that point fixed), resampled about every RoadProfile.DS; its profile the ground smoothed over +- DRIVE_SMOOTH,
     * pinned to H's height and the paved edge's over DRIVE_PIN, then held to DRIVE_GRADE (DRIVE_END_GRADE over the last
     * DRIVE_STRAIGHT) from both ends. Null (why[0]) when it runs into water, falls outside DRIVE_MIN .. DRIVE_MAX or cannot
     * keep the grades.
     */
    private static Drive drive(RoadDesign d, double s, int side, int node, double run, DriveSearch ds, RoadNode h, RouteTerrain t, String[] why) {
        double edge = d.section.edge;
        double[] qp = d.point(s, side * (edge + run)), ep = d.point(s, side * edge);
        double ix = (ep[0] - qp[0]) / run, iz = (ep[1] - qp[1]) / run;
        double[] path = ds.path(node);
        List<double[]> p = new ArrayList<>();
        for (int i = 0; i < path.length; i += 2) p.add(new double[]{path[i], path[i + 1]});
        if (Math.hypot(p.get(p.size() - 1)[0] - qp[0], p.get(p.size() - 1)[1] - qp[1]) > 1) p.add(qp);
        else p.set(p.size() - 1, qp);
        p.add(new double[]{qp[0] + ix * 8, qp[1] + iz * 8});
        for (int it = 0; it < 4; it++) p = chaikin(p);
        p.add(ep);
        // resampled with E last
        double total = 0;
        for (int i = 1; i < p.size(); i++) total += Math.hypot(p.get(i)[0] - p.get(i - 1)[0], p.get(i)[1] - p.get(i - 1)[1]);
        if (total < DRIVE_MIN || total > DRIVE_MAX) {
            why[0] = String.format(Locale.ROOT, "%.0f m long", total);
            return null;
        }
        int n = Math.max(2, (int) Math.round(total / RoadProfile.DS));
        double step = total / n;
        double[] xs = new double[n + 1], zs = new double[n + 1], along = new double[n + 1];
        int seg = 0;
        double segStart = 0;
        for (int i = 0; i <= n; i++) {
            double a = i == n ? total : i * step;
            while (seg < p.size() - 2) {
                double l = Math.hypot(p.get(seg + 1)[0] - p.get(seg)[0], p.get(seg + 1)[1] - p.get(seg)[1]);
                if (segStart + l >= a) break;
                segStart += l;
                seg++;
            }
            double[] u = p.get(seg), v = p.get(seg + 1);
            double l = Math.hypot(v[0] - u[0], v[1] - u[1]), f = l < 1e-9 ? 0 : Math.max(0, Math.min(1, (a - segStart) / l));
            xs[i] = u[0] + (v[0] - u[0]) * f;
            zs[i] = u[1] + (v[1] - u[1]) * f;
            along[i] = a;
        }
        xs[n] = ep[0];
        zs[n] = ep[1];
        // never into water or its bank (the search kept BANK off; the smoothing cuts corners)
        double[] ground = new double[n + 1];
        for (int i = 0; i <= n; i++) {
            if (t.water(xs[i], zs[i]) != RouteTerrain.DRY) {
                why[0] = String.format(Locale.ROOT, "water at %.0f m", along[i]);
                return null;
            }
            int c = ds.node(xs[i], zs[i]);
            if (c >= 0 && ds.water(c) < DriveSearch.BANK / 2) {
                why[0] = String.format(Locale.ROOT, "%.0f m from water at %.0f m", ds.water(c), along[i]);
                return null;
            }
            ground[i] = t.height(xs[i], zs[i]);
        }
        // the profile
        int w = (int) Math.round(DRIVE_SMOOTH / step);
        double[] y = new double[n + 1];
        for (int i = 0; i <= n; i++) {
            double sum = 0;
            int cnt = 0;
            for (int j = Math.max(0, i - w); j <= Math.min(n, i + w); j++) { sum += ground[j]; cnt++; }
            y[i] = sum / cnt;
        }
        double yh = h.y(), ye = d.pavementY(s, side * edge), pin = Math.min(DRIVE_PIN, total / 2);
        double dh = yh - y[0], de = ye - y[n];
        for (int i = 0; i <= n; i++) {
            y[i] += dh * (1 - CrossSection.smooth(Math.min(1, along[i] / pin))) + de * (1 - CrossSection.smooth(Math.min(1, (total - along[i]) / pin)));
        }
        y[0] = yh;
        y[n] = ye;
        // the gate's pad: the datum level at a whole Y round the gate; of the gate distances and the two whole Ys nearest
        // the profile there, the one whose steepest grade is the least (the level stretch pushes the ground's rise beside
        // it; the first in order on a tie)
        double[] base = y.clone();
        double gIn = Double.NaN, gBest = Double.POSITIVE_INFINITY;
        int gY = 0;
        String fail = null;
        for (double g = GATE_MIN; g <= GATE_MAX + 1e-9; g += GATE_STEP) {
            int k = Math.max(0, Math.min(n, (int) Math.round((total - g) / step)));
            int y0 = (int) Math.round(base[k]), y1 = base[k] >= y0 ? y0 + 1 : y0 - 1;
            for (int yy : new int[]{y0, y1}) {
                double[] c = base.clone();
                boolean[] fix = new boolean[n + 1];
                fix[0] = fix[n] = true;
                double lev = GATE_FLAT + step + 1e-6, dyg = yy - base[k];
                for (int i = 1; i < n; i++) {
                    double te = total - along[i], e = Math.abs(te - g) - lev;
                    if (e <= 0) {
                        c[i] = yy;
                        fix[i] = true;
                    } else if (te > g) c[i] += dyg * (1 - CrossSection.smooth(Math.min(1, e / GATE_PIN)));
                    else c[i] = ye + (yy - ye) * te / (g - lev);
                }
                for (int i = 1; i < n; i++) if (!fix[i]) c[i] = clampGrade(c[i], c[i - 1], limit(along[i], total) * step);
                for (int i = n - 1; i >= 1; i--) if (!fix[i]) c[i] = clampGrade(c[i], c[i + 1], limit(along[i + 1], total) * step);
                String bad = null;
                double steep = 0;
                for (int i = 1; i <= n && bad == null; i++) {
                    steep = Math.max(steep, Math.abs(c[i] - c[i - 1]) / step);
                    if (Math.abs(c[i] - c[i - 1]) > limit(along[i], total) * step + 1e-6)
                        bad = String.format(Locale.ROOT, "%.1f %% at %.0f m (yard Y%.1f, road edge Y%.1f, gate Y%d %.1f m in, over %.0f m)", 100 * Math.abs(c[i] - c[i - 1]) / step,
                                along[i], yh, ye, yy, g, total);
                }
                if (bad == null && steep < gBest - 1e-4) {
                    y = c;
                    gIn = g;
                    gY = yy;
                    gBest = steep;
                }
                if (bad != null && fail == null) fail = bad;
            }
        }
        if (Double.isNaN(gIn)) {
            why[0] = fail;
            return null;
        }
        double[] pts = new double[3 * (n + 1)];
        for (int i = 0; i <= n; i++) {
            pts[3 * i] = RoadNetworkJson.round2(xs[i]);
            pts[3 * i + 1] = RoadNetworkJson.round2(zs[i]);
            pts[3 * i + 2] = RoadNetworkJson.round2(y[i]);
        }
        // the gate across the driveway gIn from the paved edge, spanning the straight, on its pad's whole Y
        double[] gp = {ep[0] - ix * gIn, ep[1] - iz * gIn};
        double[] gate = {RoadNetworkJson.round2(gp[0]), gY, RoadNetworkJson.round2(gp[1]), ix, iz};
        // the mailbox on the right seen from the driveway (facing the road), MAILBOX_OFF beyond the paved edge, facing the road
        double[] f = new double[5];
        d.curve.frame(s, f);
        double rx = -iz, rz = ix, sm = s + Math.signum(rx * f[2] + rz * f[3]) * MAILBOX_ALONG;
        sm = Math.max(0, Math.min(d.length, sm));
        double[] mp = d.point(sm, side * (edge + MAILBOX_OFF)), mr = d.point(sm, 0);
        double ml = Math.hypot(mr[0] - mp[0], mr[1] - mp[1]);
        double[] mailbox = {RoadNetworkJson.round2(mp[0]), Math.floor(d.pavementY(sm, side * edge) + MAILBOX_ROUND), RoadNetworkJson.round2(mp[1]), (mr[0] - mp[0]) / ml,
                (mr[1] - mp[1]) / ml};
        return new Drive(pts, total, gate, mailbox, s);
    }

    /** The driveway's largest grade at along of total: DRIVE_END_GRADE over the last DRIVE_STRAIGHT, else DRIVE_GRADE. */
    private static double limit(double along, double total) { return total - along < DRIVE_STRAIGHT + 1e-6 ? DRIVE_END_GRADE : DRIVE_GRADE; }

    private static double clampGrade(double y, double ref, double rise) { return Math.max(ref - rise, Math.min(ref + rise, y)); }

    /** One Chaikin pass, the ends kept. */
    private static List<double[]> chaikin(List<double[]> p) {
        List<double[]> q = new ArrayList<>();
        q.add(p.get(0));
        for (int i = 0; i + 1 < p.size(); i++) {
            double[] a = p.get(i), b = p.get(i + 1);
            if (i > 0) q.add(new double[]{0.75 * a[0] + 0.25 * b[0], 0.75 * a[1] + 0.25 * b[1]});
            if (i + 2 < p.size()) q.add(new double[]{0.25 * a[0] + 0.75 * b[0], 0.25 * a[1] + 0.75 * b[1]});
        }
        q.add(p.get(p.size() - 1));
        return q;
    }

    // ------------------------------------------------------------------ the road

    /** Why the curve comes too close to another road's formation (its hosts' stretches by their junctions excepted), or null. */
    private static String clear(RoadCurve c, List<RoadDesign> designs, RoadDesign hostStart, RoadDesign hostEnd) {
        double[] f = new double[5];
        double reach = CrossSection.C2.edge + 2 + CLEAR, zone = RoadJunctions.ZONE_MINOR + RoadJunctions.BACK[RoadJunctions.BACK.length - 1];
        for (RoadDesign q : designs) {
            double need = q.section.edge + reach + 2;
            for (double s = 0; s <= c.length; s += 4) {
                // the junctions join the county road's ends to their hosts
                if (q == hostStart && s < zone || q == hostEnd && s > c.length - zone) continue;
                c.frame(s, f);
                double[] l = q.local(f[0], f[1], need);
                if (l != null && Math.abs(l[1]) <= need) return String.format(Locale.ROOT, "within %.0f m of %s at %.0f m", Math.abs(l[1]), q.id, s);
            }
        }
        return null;
    }

    /** A designed county road (why null) or why not: its route, largest grade and structures. */
    private static final class Built {
        RouteData road;
        double maxGrade;
        String structures = "", why;
    }

    /** The county road's design on the curve: the class's design, no tunnel, the profile pinned to endH at the end (NaN: the end left to the junction's landing). */
    private static Built build(RoadCurve curve, double endH, RouteTerrain t, String from, String to) {
        Built b = new Built();
        if (curve.length < 2 * MOUTH_END + 40) { b.why = String.format(Locale.ROOT, "too short (%.0f m)", curve.length); return b; }
        RouteDesigner.Result d = RouteDesigner.design(RoadClassSpec.COUNTY, curve, t);
        int nb = 0, nc = 0;
        List<Structure> ss = new ArrayList<>();
        for (RouteDesigner.Structure s : d.structures()) {
            if (s.kind() == RouteDesigner.Kind.TUNNEL) { b.why = "a tunnel"; return b; }
            if (s.kind() == RouteDesigner.Kind.BRIDGE || s.kind() == RouteDesigner.Kind.VIADUCT) nb++;
            if (s.kind() == RouteDesigner.Kind.CULVERT) nc++;
            ss.add(new Structure(s.kind().name(), RoadNetworkJson.round2(s.s0()), RoadNetworkJson.round2(s.s1()), RoadNetworkJson.round2(s.height())));
        }
        b.structures = nb + " bridges, " + nc + " culverts";
        double[] e = d.profile().samples();
        if (!Double.isNaN(endH) && !pinEnd(e, endH)) { b.why = String.format(Locale.ROOT, "the town's datum (Y%.1f) cannot be met within the grade (road Y%.1f)", endH, e[e.length - 1]); return b; }
        double worst = 0;
        for (int i = 0; i < e.length; i++) e[i] = RoadNetworkJson.round2(e[i]);
        for (int i = 1; i < e.length; i++) worst = Math.max(worst, Math.abs(e[i] - e[i - 1]) / RoadProfile.DS);
        b.maxGrade = worst;
        double end = (e.length - 1) * RoadProfile.DS;
        b.road = new RouteData(ID, "county", "county", from, to, curve.controlX(), curve.controlZ(), e, List.copyOf(ss), new double[]{0, end});
        return b;
    }

    /**
     * The profile pinned to the town's datum at its end: the last TOWN_LANDING m at the old grade held to
     * TOWN_LANDING_GRADE, ending at y, blended into the old profile over the shortest of 40 .. 400 m within the class's
     * mountain grade; false if none.
     */
    static boolean pinEnd(double[] e, double y) {
        int n = e.length;
        double len = (n - 1) * RoadProfile.DS;
        RoadProfile op = RoadProfile.of(e);
        double gOld = (op.at(len - TOWN_LANDING) - op.at(len)) / TOWN_LANDING;
        double gl = Math.max(-TOWN_LANDING_GRADE, Math.min(TOWN_LANDING_GRADE, gOld));
        double[] base = e.clone();
        for (double lb = 40; lb <= 400 + 1e-9; lb += 10) {
            if (TOWN_LANDING + lb > len - MOUTH_END) break;
            double[] q = base.clone();
            for (int i = 0; i < n; i++) {
                double back = len - i * RoadProfile.DS, lin = y + gl * back;
                if (back <= TOWN_LANDING) q[i] = lin;
                else if (back < TOWN_LANDING + lb) q[i] = base[i] + (lin - base[i]) * (1 - CrossSection.smooth((back - TOWN_LANDING) / lb));
            }
            boolean ok = true;
            for (int i = 1; i < n && ok; i++)
                if (Math.abs(q[i] - q[i - 1]) / RoadProfile.DS > RoadClassSpec.COUNTY.gradeMountain + 1e-4) ok = false;
            if (ok) {
                System.arraycopy(q, 0, e, 0, n);
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ JSON

    /** The built network JSON with the home appended (a "home" member after the routes and towns); unchanged when null. */
    public static String append(String json, Home h) {
        if (h == null) return json;
        StringBuilder b = new StringBuilder();
        b.append("{\"x\":").append(h.x()).append(",\"z\":").append(h.z()).append(",\"rotation\":").append(h.rotation()).append(",\"ground\":").append(h.groundY())
                .append(",\"node\":").append(arr(h.node())).append(",\"link\":\"").append(h.link()).append('"')
                .append(",\"beyond\":\"").append(h.beyond()).append('"')
                .append(",\"road\":").append(h.road() == null ? "null" : "\"" + h.road() + "\"").append(",\"drive\":").append(arr(h.drive()))
                .append(",\"gate\":").append(h.gate() == null ? "null" : arr(h.gate())).append(",\"mailbox\":").append(h.mailbox() == null ? "null" : arr(h.mailbox()))
                .append(",\"barrier\":").append(h.barrier() == null ? "null" : arr(h.barrier()))
                .append('}');
        int end = json.lastIndexOf('}');
        return json.substring(0, end) + ",\"home\":" + b + json.substring(end);
    }

    private static String arr(double[] a) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) b.append(i > 0 ? "," : "").append(String.format(Locale.ROOT, "%.4f", a[i]).replaceAll("0+$", "").replaceAll("\\.$", ""));
        return b.append(']').toString();
    }

    /** The home of a built network JSON, or null (none: no "home" member). */
    @SuppressWarnings("unchecked")
    public static Home read(String json) {
        if (!json.contains("\"home\":")) return null;
        Map<String, Object> root = (Map<String, Object>) RoadNetworkJson.parse(json);
        if (!(root.get("home") instanceof Map<?, ?> m)) return null;
        Object beyond = m.get("beyond");
        return new Home((int) num(m.get("x")), (int) num(m.get("z")), (int) num(m.get("rotation")), (int) num(m.get("ground")), nums(m.get("node")),
                (String) m.get("link"), (String) m.get("road"), nums(m.get("drive")), nums(m.get("gate")), nums(m.get("mailbox")),
                beyond instanceof String b ? b : "none", nums(m.get("barrier")));
    }

    private static double num(Object o) { return (Double) o; }

    private static double[] nums(Object o) {
        if (!(o instanceof List<?> l)) return null;
        double[] a = new double[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = (Double) l.get(i);
        return a;
    }
}
