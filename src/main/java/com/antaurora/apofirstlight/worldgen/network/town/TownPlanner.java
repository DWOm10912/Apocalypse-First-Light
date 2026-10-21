package com.antaurora.apofirstlight.worldgen.network.town;

import com.antaurora.apofirstlight.worldgen.federal.FederalStateId;
import com.antaurora.apofirstlight.worldgen.network.road.RoadDesign;
import com.antaurora.apofirstlight.worldgen.network.road.RoadNames;
import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson;
import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson.RouteData;
import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson.Structure;
import com.antaurora.apofirstlight.worldgen.network.route.RouteTerrain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Lays out a framework town round its exit (Town Framework V1, docs/worldgen/federal_towns_v1.md 2-5): a straight grid on
 * the block grid (TownGrid, 2026-10-19: s along the world axis nearest the interstate's tangent at the exit crossing, v
 * across it toward the town; before, the grid lay on the tangent itself, up to 45 degrees off the blocks), the exit's
 * crossroad as one of its cross streets where it runs straight on the grid.
 * <ul>
 *   <li>grid: cross streets every CROSS m (the exit's where the crossroad runs on the grid, each side its own: #straight);
 *   parallel streets from Front St (FRONT_CLEAR beyond the crossroad's turn onto the grid, TownGrid#frontOf), a block
 *   (two LOT_DEPTH rows back to back with an ALLEY, ROWS deep) between each two, on the town's side (the side with the
 *   more buildable land, of the crossroad's town stubs) up to BANDS blocks deep and on the far side (a town stub too) up
 *   to FAR_BANDS;</li>
 *   <li>blocks: a cell between four street centrelines is buildable when it is dry, its ground varies by MAX_RELIEF at
 *   most and no other built road's paved band comes within ROAD_CLEAR; from the two cells at the exit's Front St corner
 *   on each side the buildable cells are taken nearest first (path length over shared streets) up to MAX_BLOCKS;</li>
 *   <li>streets: every side of a kept block; Main St (the second parallel street on the town's side) and the exit's
 *   cross street are C14, a street along industry only I12, the rest R12 (TownStreets); the crossroad is cut back to the
 *   Front St corners (#trim), the town's streets go on from there;</li>
 *   <li>heights: each intersection takes its ground (a disc of NODE_DISC) and the network is relaxed until no street
 *   is steeper than MAX_GRADE, the crossroad's ends fixed; streets are straight between intersections; a lot's pad is its
 *   street's height at its frontage plus PAD (rows step along a sloping street; the alley takes the step between
 *   rows);</li>
 *   <li>zones (T2's pattern round the exit): highway commerce on the exit's Front St corners, downtown on Main St from the
 *   exit toward the side with more blocks, its parking behind on Front St, the town square and civic blocks off Main St,
 *   a school two blocks merged, industry and a mobile home park on the far side, houses elsewhere;</li>
 *   <li>names (T4): Front St, Main St, then the state's trees by distance from Main St (town side first), cross
 *   streets numbered from the low-s end.</li>
 * </ul>
 */
public final class TownPlanner {
    private TownPlanner() {}

    /**
     * Cross street spacing, the first parallel street's least offset from the crossing (TownGrid#frontOf: beyond the
     * crossroad's turn onto the grid), m.
     */
    public static final double CROSS = 150, FRONT = 150;
    /** A block's depth between rights of way: two LOT_DEPTH rows and the ALLEY between them, m. */
    public static final double LOT_DEPTH = 32, ALLEY = 6, ROWS = 2 * LOT_DEPTH + ALLEY;
    /** Cross intervals each way from the exit, blocks deep on the town's side and on the far side, blocks at most. */
    public static final int MAX_K = 6, BANDS = 4, FAR_BANDS = 2, MAX_BLOCKS = 44;
    /** Ground sampling, the most relief of a buildable cell, the clearance from other roads' paved edges, m. */
    static final double SAMPLE = 8, MAX_RELIEF = 20, ROAD_CLEAR = 45;
    /**
     * The fallback's most relief of a buildable cell (2026-10-20: every town is built): a town that grows fewer than
     * TownExits.MIN_BLOCKS blocks within MAX_RELIEF takes steeper cells too, its lots graded deeper, m.
     */
    public static final double FALLBACK_RELIEF = 35;
    /** Intersection heights: the ground disc averaged, the steepest street, relaxation passes; a lot pad over its street, m. */
    static final double NODE_DISC = 12, MAX_GRADE = 0.07, PAD = 0.3;
    static final int RELAX = 400;
    /** The widest intersection box's half length along a street (C14's half right of way), m. */
    static final double BOX = 13;

    /** A lot row: widths along the frontage (repeated, then stretched to fill), depth. */
    record RowSpec(double[] widths, double depth) {}

    /**
     * A civic row's depth (T3: city hall, police and fire 48 x 48, church and clinic 40 x 48), m: deeper than a block's
     * two rows, so a civic block side has the block's one row and the other side none (2026-10-18).
     */
    public static final double CIVIC_DEPTH = 48;
    /** How far a row's lots may be stretched or squeezed to fill their frontage (T3: +-15 %). */
    static final double FIT = 0.15;

    static final Map<String, RowSpec> ROW = Map.of(
            // houses 24 wide, one in five 32 (a duplex or a big house, T3; 2026-10-18), its place varying by block
            "res", new RowSpec(new double[]{24, 24, 32, 24, 24}, LOT_DEPTH),
            "down", new RowSpec(new double[]{12, 12, 16, 12, 24, 12, 16}, LOT_DEPTH),
            "hwy", new RowSpec(new double[]{64, 48, 40}, 64),
            "ind", new RowSpec(new double[]{80, 64}, 64),
            "mobile", new RowSpec(new double[]{16}, 28),
            "civic", new RowSpec(new double[]{48, 40}, CIVIC_DEPTH),
            "park_lot", new RowSpec(new double[]{999}, LOT_DEPTH));

    /** Each state's tree names for the parallel streets beyond Main St (T4). */
    static final Map<FederalStateId, String[]> TREES = Map.of(
            FederalStateId.S1, new String[]{"Oak", "Maple", "Elm", "Chestnut", "Walnut", "Cedar"},
            FederalStateId.S2, new String[]{"Walnut", "Cottonwood", "Hickory", "Locust", "Sycamore"},
            FederalStateId.S3, new String[]{"Aspen", "Spruce", "Pine", "Juniper", "Fir"},
            FederalStateId.S4, new String[]{"Birch", "Ash", "Beech", "Poplar", "Hemlock"},
            FederalStateId.S5, new String[]{"Magnolia", "Cypress", "Live Oak", "Pecan", "Willow"},
            FederalStateId.S6, new String[]{"Mesquite", "Saguaro", "Palo Verde", "Piñon", "Yucca"});

    /** A planned town and the crossroad trimmed to it (null when the crossroad is unchanged). */
    public record Result(TownPlan plan, RouteData crossroad, List<String> report) {}

    /** A cell: side (1 the town's, -1 the far one), band j (0 next to the interstate), interval k (cross lines k .. k + 1). */
    private record Cell(int side, int j, int k) {}

    /**
     * The town's plan round its exit's crossroad (a built route with an OVERPASS over host), or null when nothing is
     * buildable round the exit. routes: the whole built network (the other roads it keeps clear of).
     */
    public static Result plan(String town, RouteData crossroad, RouteData host, List<RouteData> routes, RouteTerrain terrain) {
        List<String> report = new ArrayList<>();
        RoadDesign xd = RoadNetworkJson.design(crossroad), hd = RoadNetworkJson.design(host);
        // stations: the route's (structures, trims) are the design's (from its trim window's start) plus off
        double off = crossroad.trim() == null ? 0 : crossroad.trim()[0], xc = Double.NaN;
        for (Structure st : crossroad.structures()) if (st.kind().equals("OVERPASS")) xc = (st.s0() + st.s1()) / 2;
        if (Double.isNaN(xc)) return null;
        double[] pc = xd.point(xc - off, 0), hl = hd.local(pc[0], pc[1], 30);
        if (hl == null) return null;
        double sc = hl[0];
        double[] f = new double[5];
        hd.curve.frame(sc, f);
        double ox = f[0], oz = f[1], tx = f[2], tz = f[3];
        // the crossroad's two halves: which are the town's stubs (left: u < 0 of the host), each one's land
        String corridor = TownSites.corridor(town);
        boolean[] stub = new boolean[2];   // [0]: host left, [1]: host right
        for (String part : crossroad.id().split("\\+")) {
            if (!part.startsWith(corridor + ":")) continue;
            if (part.endsWith("-exit_l")) stub[0] = true;
            if (part.endsWith("-exit_r")) stub[1] = true;
        }
        if (!stub[0] && !stub[1]) return null;
        // the right-hand normal of the host (u > 0) is (-tz, tx)
        // the town's side: the more town blocks reachable from the exit (TownExits#reach, the count the exit was chosen by)
        double landL = stub[0] ? TownExits.reach(hd.curve, sc, -1, terrain, MAX_RELIEF) : -1, landR = stub[1] ? TownExits.reach(hd.curve, sc, 1, terrain, MAX_RELIEF) : -1;
        if (Math.max(landL, landR) < TownExits.MIN_BLOCKS) {
            // a fallback exit (TownExits#site): its side by the steeper count
            landL = stub[0] ? TownExits.reach(hd.curve, sc, -1, terrain, FALLBACK_RELIEF) : -1;
            landR = stub[1] ? TownExits.reach(hd.curve, sc, 1, terrain, FALLBACK_RELIEF) : -1;
        }
        int uTown = landR > landL ? 1 : -1;
        boolean farStub = stub[0] && stub[1];
        FederalStateId state = terrain.state(ox, oz);
        if (state == null) state = FederalStateId.S1;
        // the grid on the block grid (TownGrid, 2026-10-19): s along the world axis nearest the tangent, v across it toward
        // the town, its origin the block corner nearest the crossing; s turns with the town's side, so (s, v) is always a
        // quarter-turn rotation of the same plan, never its mirror
        double[] ga = TownGrid.axis(tx, tz), go = TownGrid.origin(ox, oz);
        double ax = uTown * ga[0], az = uTown * ga[1], bx = -az, bz = ax;
        Frame fr = new Frame(go[0], go[1], ax, az, bx, bz);
        // each side's exit cross street and Front St: where the built crossroad runs straight on the grid
        double[] frontTown = straight(xd, xc - off, fr, 1), frontFar = farStub ? straight(xd, xc - off, fr, -1) : null;
        if (frontTown == null) {
            report.add(String.format(Locale.ROOT, "town %s: the exit's crossroad never runs straight on the block grid on the town's side; no town", town));
            return new Result(null, null, report);
        }
        if (farStub && frontFar == null) {
            report.add(String.format(Locale.ROOT, "town %s: the exit's crossroad never runs straight on the block grid on the far side; no far side", town));
            farStub = false;
        }
        double[] shift = {frontTown[0], frontFar == null ? frontTown[0] : frontFar[0]};   // the exit cross street's s: [town side, far side]

        // other roads near the town (the crossroad excepted)
        List<RoadDesign> others = new ArrayList<>();
        for (RouteData r : routes) {
            if (r.id().equals(crossroad.id())) continue;
            boolean near = false;
            for (int i = 0; i < r.xs().length && !near; i++) near = Math.hypot(r.xs()[i] - ox, r.zs()[i] - oz) < 2500;
            if (near) others.add(RoadNetworkJson.design(r));
        }
        // parallel street lines per side: v of each (the town side positive), the rights of way between
        double[] vTown = lines(BANDS, frontTown[1]), vFar = lines(FAR_BANDS, frontFar == null ? FRONT : frontFar[1]);
        // cells: buildable?
        Map<Cell, Double> ok = new HashMap<>();
        int[] why = new int[4];   // water, relief, road, out of state
        for (int side : farStub ? new int[]{1, -1} : new int[]{1})
            for (int j = 0; j < (side > 0 ? BANDS : FAR_BANDS); j++)
                for (int k = -MAX_K; k < MAX_K; k++) {
                    double[] vv = side > 0 ? vTown : vFar;
                    double va = side * vv[j], vb = side * vv[j + 1];
                    double s0 = sh(shift, side) + k * CROSS, s1 = s0 + CROSS, v0 = Math.min(va, vb), v1 = Math.max(va, vb);
                    double r = cell(fr, s0, s1, v0, v1, terrain, others, why);
                    if (r >= 0) ok.put(new Cell(side, j, k), r);
                }
        // nearest first from the exit's Front St corners; within MAX_RELIEF first, the steeper cells too (FALLBACK_RELIEF)
        // when that grows fewer than TownExits.MIN_BLOCKS (2026-10-20: every town is built)
        Map<Cell, Double> dist = new HashMap<>();
        PriorityQueue<Object[]> q = new PriorityQueue<>((a, b) -> {
            int c = Double.compare((Double) a[0], (Double) b[0]);
            if (c != 0) return c;
            Cell x = (Cell) a[1], y = (Cell) b[1];
            return x.side != y.side ? Integer.compare(y.side, x.side) : x.j != y.j ? Integer.compare(x.j, y.j) : Integer.compare(x.k, y.k);
        });
        List<Cell> kept = new ArrayList<>();
        Set<Cell> done = new HashSet<>();
        boolean steep = false;
        for (int pass = 0; pass < 2; pass++) {
            double most = pass == 0 ? MAX_RELIEF : FALLBACK_RELIEF;
            q.clear();
            kept.clear();
            done.clear();
            for (int side : new int[]{1, -1})
                for (int k : new int[]{-1, 0}) {
                    Cell c = new Cell(side, 0, k);
                    if (ok.containsKey(c) && ok.get(c) <= most) q.add(new Object[]{0.0, c});
                }
            while (!q.isEmpty() && kept.size() < MAX_BLOCKS) {
                Object[] e = q.poll();
                Cell c = (Cell) e[1];
                if (!done.add(c)) continue;
                kept.add(c);
                double d = (Double) e[0];
                for (Cell n : new Cell[]{new Cell(c.side, c.j, c.k - 1), new Cell(c.side, c.j, c.k + 1), new Cell(c.side, c.j - 1, c.k), new Cell(c.side, c.j + 1, c.k)}) {
                    if (!ok.containsKey(n) || ok.get(n) > most || done.contains(n)) continue;
                    // a step across a cross street costs its interval, across a parallel street its band; rough cells cost more
                    double step = (n.k != c.k ? CROSS : ROWS + 22) * (1 + ok.get(n) / MAX_RELIEF);
                    q.add(new Object[]{d + step, n});
                }
            }
            if (kept.size() >= TownExits.MIN_BLOCKS) break;
            steep = pass == 0;
        }
        if (steep) {
            // the steeper pass ran: did it take a steeper cell?
            steep = false;
            for (Cell c : kept) if (ok.get(c) > MAX_RELIEF) steep = true;
        }
        if (kept.isEmpty()) {
            report.add(String.format(Locale.ROOT, "town %s: nothing buildable at the exit (cells: %d water, %d relief, %d roads)", town, why[0], why[1], why[2]));
            return new Result(null, null, report);
        }
        Set<Cell> keep = new HashSet<>(kept);
        boolean farSide = false;
        for (Cell c : kept) if (c.side < 0) farSide = true;

        // zones
        int townWest = 0, townEast = 0;
        for (Cell c : kept) if (c.side > 0 && c.j <= 1) { if (c.k < 0) townWest++; else townEast++; }
        int dir = townEast > townWest ? 1 : -1;   // downtown's direction along s
        int c0 = dir < 0 ? -1 : 0, c1 = dir < 0 ? 0 : -1; // the cells at the exit on downtown's side and the other
        Map<Cell, String[]> zone = new LinkedHashMap<>();  // {near, far} or {whole, null}: whole when far == "*"
        // the town square: the first kept Main St block of SQUARE_AT (downtown's second block first)
        Cell square = null;
        for (int at : SQUARE_AT) {
            Cell c = new Cell(1, 1, c0 + at * dir);
            if (keep.contains(c)) { square = c; break; }
        }
        for (Cell c : kept) {
            int at = (c.k - c0) * dir;                     // 0 at the exit on downtown's side, 1, 2 .. away; -1 the other side's first
            String[] z;
            if (c.equals(square)) z = new String[]{"park", "*"};
            else if (c.side > 0) {
                if (c.j == 0 && (at == 0 || at == -1)) z = new String[]{"hwy", null};
                else if (c.j == 0 && (at == 1 || at == 2)) z = new String[]{"park_lot", "down"};
                else if (c.j == 1 && at == -1) z = new String[]{"down", "res"};
                else if (c.j == 1 && at == 0) z = new String[]{"down", "res"};
                else if (c.j == 1 && at == 2) z = new String[]{"down", "res"};
                // civic rows CIVIC_DEPTH deep take the whole block (the far side's row left out), so they sit one band
                // behind Main St, never behind a downtown row (2026-10-18)
                else if (c.j == 2 && at >= 0 && at <= 2) z = new String[]{"civic", "res"};
                else if (!farSide && c.j == 0 && at <= -3) z = new String[]{"ind", null};
                else z = new String[]{"res", "res"};
            } else {
                if (c.j == 0 && (at == 0 || at == -1)) z = new String[]{"hwy", null};
                else if (c.j == 0 && at <= -2 && at >= -1 - FAR_INDUSTRY) z = new String[]{"ind", null};
                else if (at == 2 && c.j == 0) z = new String[]{"mobile", "mobile"};
                else z = new String[]{"res", "res"};
            }
            zone.put(c, z);
        }
        // landmarks (TownSites#landmarks, 2026-10-20): each the whole block its place ranks first (#landmarkRank), before
        // the school; a town of one block gives it the square's
        for (TownSites.Landmark lm : TownSites.landmarks(town)) {
            Cell at = null;
            double bestRank = Double.POSITIVE_INFINITY;
            int sqK = square != null ? square.k : c0 + dir;
            for (Cell c : kept) {
                if (zone.get(c)[0].startsWith(TownPlan.LANDMARK)) continue;
                double r = landmarkRank(lm, c, zone.get(c)[0], sqK, c0, dir);
                if (r < bestRank) { bestRank = r; at = c; }
            }
            if (at == null) { report.add(String.format(Locale.ROOT, "town %s: no block left for its landmark %s", town, lm.id())); continue; }
            zone.put(at, new String[]{TownPlan.LANDMARK + lm.id(), "*"});
            report.add(String.format(Locale.ROOT, "town %s: landmark %s on block %s/%d/%d (%s)", town, lm.id(), at.side > 0 ? "town" : "far", at.j, at.k, lm.where()));
        }
        // the school: two neighbouring house blocks two bands out on the town's side, farthest along downtown's side
        Cell schoolA = null;
        for (Cell c : kept) {
            if (c.side < 0 || c.j != 2) continue;
            Cell n = new Cell(1, 2, c.k + dir);
            if (!keep.contains(n) || !"res".equals(zone.get(c)[0]) || !"res".equals(zone.get(n)[0]) || (c.k - c0) * dir < 3) continue;
            if (schoolA == null || (c.k - schoolA.k) * dir > 0) schoolA = c;
        }

        // nodes and streets
        Map<Long, Integer> nodeAt = new HashMap<>();
        List<double[]> nodePos = new ArrayList<>();       // s, v
        Map<String, int[]> segs = new LinkedHashMap<>();   // key -> {a, b, kind (0 parallel, 1 cross), side, j or k, line}
        Map<String, List<String>> segZones = new HashMap<>();
        for (Cell c : kept) {
            double[] vv = c.side > 0 ? vTown : vFar;
            int ja = c.j, jb = c.j + 1;
            double sh = sh(shift, c.side);
            int n00 = node(nodeAt, nodePos, c.k, c.side, ja, vv, sh), n10 = node(nodeAt, nodePos, c.k + 1, c.side, ja, vv, sh);
            int n01 = node(nodeAt, nodePos, c.k, c.side, jb, vv, sh), n11 = node(nodeAt, nodePos, c.k + 1, c.side, jb, vv, sh);
            String[] z = zone.get(c);
            boolean schoolInner = schoolA != null && c.side > 0 && c.j == 2 && (c.k == schoolA.k || c.k == schoolA.k + dir);
            seg(segs, segZones, n00, n10, z[0], c.side, ja, 0);
            seg(segs, segZones, n01, n11, "*".equals(z[1]) ? z[0] : z[1] == null ? z[0] : z[1], c.side, jb, 0);
            int innerK = schoolA == null ? Integer.MIN_VALUE : Math.max(schoolA.k, schoolA.k + dir);
            if (!(schoolInner && c.k == innerK)) seg(segs, segZones, n00, n01, z[0], c.side, c.k, 1);
            if (!(schoolInner && c.k + 1 == innerK)) seg(segs, segZones, n10, n11, z[0], c.side, c.k + 1, 1);
        }
        // heights: ground, the crossroad's corners fixed, relaxed to MAX_GRADE
        int nn = nodePos.size();
        double[] h = new double[nn];
        boolean[] fixed = new boolean[nn];
        double[][] trimAt = new double[2][];   // per side: crossroad station where it ends at the Front St corner, the node
        List<double[]> exitEnds = new ArrayList<>();
        Set<Integer> corners = new HashSet<>();
        for (int side : new int[]{1, -1}) {
            Integer i = nodeAt.get(key(0, side, 0));
            if (i != null) corners.add(i);
        }
        for (int i = 0; i < nn; i++) {
            double[] p = nodePos.get(i), w = fr.world(p[0], p[1]);
            h[i] = disc(terrain, w[0], w[1]);
            if (corners.contains(i)) {
                double[] l = xd.local(w[0], w[1], 5);
                if (l == null) continue;
                // the crossroad stops at the corner's box (Front St's right of way), on the profile's 2 m lattice short of
                // it; the corner takes the crossroad's datum there, so its flat box meets the road's end level
                double t = off + l[0], half = TownStreets.R12.halfRow();
                double te = t > xc ? Math.floor((t - half) / 2) * 2 : Math.ceil((t + half) / 2) * 2;
                h[i] = xd.datum(te - off);
                fixed[i] = true;
                trimAt[p[1] > 0 ? 0 : 1] = new double[]{te, i};
                double[] pe = xd.point(te - off, 0);
                exitEnds.add(new double[]{i, r2(fr.v(pe))});
            }
        }
        List<int[]> edges = new ArrayList<>(segs.values());
        for (int pass = 0; pass < RELAX; pass++) {
            boolean moved = false;
            for (int[] e : edges) {
                double[] pa = nodePos.get(e[0]), pb = nodePos.get(e[1]);
                // the grade runs between the intersections' flat boxes (TownWorks: half the crossing streets' rights of way), its breaks rounded inside (TownWorks.ROUND)
                double len = Math.hypot(pa[0] - pb[0], pa[1] - pb[1]), lim = MAX_GRADE * (len - 2 * BOX - TownWorks.ROUND), dh = h[e[1]] - h[e[0]];
                double over = Math.abs(dh) - lim;
                if (over <= 1e-4) continue;
                moved = true;
                double sg = Math.signum(dh);
                if (fixed[e[0]] && fixed[e[1]]) continue;
                if (fixed[e[0]]) h[e[1]] -= sg * over;
                else if (fixed[e[1]]) h[e[0]] += sg * over;
                else { h[e[0]] += sg * over / 2; h[e[1]] -= sg * over / 2; }
            }
            if (!moved) break;
        }
        List<TownPlan.Node> nodes = new ArrayList<>();
        for (int i = 0; i < nn; i++) nodes.add(new TownPlan.Node(RoadNetworkJson.round2(nodePos.get(i)[0]), RoadNetworkJson.round2(nodePos.get(i)[1]), RoadNetworkJson.round2(h[i])));

        // names
        String[] trees = TREES.get(state);
        int kMin = Integer.MAX_VALUE, townTrees = 0;
        for (int[] e : edges) if (e[2] == 1) kMin = Math.min(kMin, e[4]);
        for (int[] e : edges) if (e[2] == 0 && e[3] > 0) townTrees = Math.max(townTrees, e[4] - 1);
        List<TownPlan.Street> streets = new ArrayList<>();
        int[] classes = new int[3];
        for (Map.Entry<String, int[]> en : segs.entrySet()) {
            int[] e = en.getValue();
            String name, cls;
            boolean industry = true;
            for (String z : segZones.get(en.getKey())) if (!"ind".equals(z)) industry = false;
            if (e[2] == 1) {
                name = ordinal(e[4] - kMin + 1) + " St";
                cls = e[4] == 0 ? "C14" : industry ? "I12" : "R12";
            } else {
                int j = e[4];
                if (j == 0) name = "Front St";
                else if (e[3] > 0 && j == 1) name = "Main St";
                else {
                    int t = e[3] > 0 ? j - 2 : townTrees + j - 1;
                    name = trees[Math.min(t, trees.length - 1)] + " St";
                }
                cls = e[3] > 0 && j == 1 ? "C14" : industry ? "I12" : "R12";
            }
            classes[cls.equals("C14") ? 0 : cls.equals("I12") ? 1 : 2]++;
            streets.add(new TownPlan.Street(name, cls, e[0], e[1]));
        }

        // blocks and lots
        List<TownPlan.Block> blocks = new ArrayList<>();
        List<TownPlan.Lot> lots = new ArrayList<>();
        Map<String, Integer> lotZones = new LinkedHashMap<>();
        for (Cell c : kept) {
            boolean school = schoolA != null && c.side > 0 && c.j == 2 && (c.k == schoolA.k || c.k == schoolA.k + dir);
            if (school && c.k != schoolA.k) continue;
            double[] vv = c.side > 0 ? vTown : vFar;
            int k0 = school ? Math.min(c.k, c.k + dir) : c.k, k1 = school ? Math.max(c.k, c.k + dir) + 1 : c.k + 1;
            double sh = sh(shift, c.side);
            double s0 = sh + k0 * CROSS + crossRow(k0, c.side) / 2, s1 = sh + k1 * CROSS - crossRow(k1, c.side) / 2;
            double vn = vv[c.j] + parRow(c.j, c.side) / 2, vf = vv[c.j + 1] - parRow(c.j + 1, c.side) / 2;
            double v0 = c.side > 0 ? vn : -vf, v1 = c.side > 0 ? vf : -vn;
            String[] z = zone.get(c);
            boolean whole = school || "*".equals(z[1]);
            String near = school ? "school" : z[0], far = whole ? null : z[1];
            blocks.add(new TownPlan.Block(r2(s0), r2(s1), r2(v0), r2(v1), near, far, whole));
            // the streets along the block's near and far sides: heights at both ends
            int nNear0 = nodeAt.get(key(k0, c.side, c.j)), nNear1 = nodeAt.get(key(k1, c.side, c.j));
            int nFar0 = nodeAt.get(key(k0, c.side, c.j + 1)), nFar1 = nodeAt.get(key(k1, c.side, c.j + 1));
            double sA = sh + k0 * CROSS, sB = sh + k1 * CROSS;
            if (whole) {
                double pad = (h[nNear0] + h[nNear1] + h[nFar0] + h[nFar1]) / 4 + PAD;
                lots.add(new TownPlan.Lot(r2(s0), r2(s1), r2(v0), r2(v1), near, c.side > 0 ? TownPlan.FACE_V0 : TownPlan.FACE_V1, r2(pad)));
                lotZones.merge(near, 1, Integer::sum);
                continue;
            }
            RowSpec rn = near == null ? null : ROW.get(near), rf = far == null ? null : ROW.get(far);
            double depth = vf - vn;
            if (rn != null && rf != null && rn.depth + rf.depth > depth - ALLEY + 1e-6) {
                // one deep row: the deeper zone faces its street
                if (rf.depth > rn.depth) rn = null; else rf = null;
            }
            if (rn != null) row(lots, lotZones, near, s0, s1, vn, Math.min(rn.depth, depth), c.side, true, rn, h[nNear0], h[nNear1], sA, sB, start(near, k0, c.j, 0));
            if (rf != null) row(lots, lotZones, far, s0, s1, vf, Math.min(rf.depth, depth), c.side, false, rf, h[nFar0], h[nFar1], sA, sB, start(far, k0, c.j, 2));
        }

        // the limit: the kept cells' street centrelines and a margin
        double ls0 = Double.POSITIVE_INFINITY, ls1 = Double.NEGATIVE_INFINITY, lv0 = Double.POSITIVE_INFINITY, lv1 = Double.NEGATIVE_INFINITY;
        for (double[] p : nodePos) { ls0 = Math.min(ls0, p[0]); ls1 = Math.max(ls1, p[0]); lv0 = Math.min(lv0, p[1]); lv1 = Math.max(lv1, p[1]); }
        double[] limit = {r2(ls0 - LIMIT_MARGIN), r2(ls1 + LIMIT_MARGIN), r2(Math.min(lv0, 0) - LIMIT_MARGIN), r2(lv1 + LIMIT_MARGIN)};

        // the crossroad cut back to the Front St corners where the town has streets
        RouteData x = crossroad;
        // (the window's start on the profile's 2 m lattice, its end too: the road stops short of the corner, never past it)
        double[] tr = crossroad.trim() != null ? crossroad.trim().clone() : new double[]{0, xd.length};
        for (int side = 0; side < 2; side++) {
            if (trimAt[side] == null) continue;
            double t = trimAt[side][0];
            if (t > xc) tr[1] = Math.min(tr[1], t);
            else tr[0] = Math.max(tr[0], t);
        }
        tr = new double[]{r2(tr[0]), r2(tr[1])};
        if (crossroad.trim() == null || tr[0] != crossroad.trim()[0] || tr[1] != crossroad.trim()[1]) x = crossroad.withTrim(tr);

        String name = RoadNames.town(town);
        TownPlan plan = new TownPlan(town, name == null ? town : name, state.key(), fr.ox, fr.oz, ax, az, bx, bz, crossroad.id(), limit, xd.section.edge, exitEnds, nodes, streets, blocks, lots);
        int far = 0;
        for (Cell c : kept) if (c.side < 0) far++;
        report.add(String.format(Locale.ROOT, "town %s (%s, %s): exit at s %.0f of %s, town on the host's %s; %d blocks (%d far side), %d lots %s, %d streets (C14 %d, I12 %d, R12 %d), %d intersections; %.0f x %.0f m; crossroad kept %.0f .. %.0f of %.0f m",
                town, plan.name, state.key(), sc, host.id(), uTown < 0 ? "left" : "right", blocks.size(), far, lots.size(), lotZones, streets.size(), classes[0], classes[1], classes[2], nn,
                limit[1] - limit[0], limit[3] - limit[2], tr[0], tr[1], off + xd.length));
        report.add(String.format(Locale.ROOT, "town %s: cells not buildable: %d water, %d relief over %.0f m, %d near roads%s", town, why[0], why[1], FALLBACK_RELIEF, why[2],
                steep ? String.format(Locale.ROOT, "; fewer than %d blocks within %.0f m relief, steeper ones taken", TownExits.MIN_BLOCKS, MAX_RELIEF) : ""));
        report.add(String.format(Locale.ROOT, "town %s: grid on the block grid, s along (%.0f, %.0f), %.1f degrees off the interstate; exit cross street at s %.2f, Front St %.0f m out (town side)%s",
                town, ax, az, Math.toDegrees(Math.acos(Math.min(1, Math.abs(ax * tx + az * tz)))), frontTown[0], frontTown[1],
                frontFar == null ? "" : String.format(Locale.ROOT, ", s %.2f, %.0f m (far side)", frontFar[0], frontFar[1])));
        return new Result(plan, x == crossroad ? null : x, report);
    }

    /**
     * A landmark's rank on a kept block, lowest first: "main" by Main St (the town's side, band 1, then 2, then Front St's),
     * nearest the town square's interval; "edge" the town's farthest block from the exit; a highway, park or downtown
     * block only when nothing else is left.
     */
    static double landmarkRank(TownSites.Landmark lm, Cell c, String zone, int sqK, int c0, int dir) {
        double taken = switch (zone) { case "park" -> 4000; case "hwy" -> 2000; case "down", "park_lot" -> 1000; default -> 0; };
        if ("edge".equals(lm.where())) return taken - 10 * Math.abs((c.k - c0) * dir + 0.5) - 20 * c.j + (c.side > 0 ? 0 : 5);
        double band = c.side < 0 ? 500 : c.j == 1 ? 0 : c.j == 2 ? 100 : c.j == 0 ? 200 : 300;
        return taken + band + 10 * Math.abs(c.k - sqK);
    }

    /** Where the town square goes: Main St blocks by their place from the exit on downtown's side (0 next to it). */
    static final int[] SQUARE_AT = {1, 0, 2, -1, 3, -2};
    /** Industry blocks on the far side's Front St, beyond its highway commerce, at most. */
    static final int FAR_INDUSTRY = 3;

    /** The town limit's margin round the outermost street centrelines, m. */
    public static final double LIMIT_MARGIN = 40;

    /** A parallel street's right of way: Main St (town side, line 1) C14, the others R12 (TownStreets). */
    static double parRow(int j, int side) { return side > 0 && j == 1 ? TownStreets.C14.row() : TownStreets.R12.row(); }

    /** A cross street's right of way: the exit's (k = 0) C14, the others R12. */
    static double crossRow(int k, int side) { return k == 0 ? TownStreets.C14.row() : TownStreets.R12.row(); }

    /** Parallel street centrelines across the grid, |v|: Front St at front, then a block (ROWS) and the rights of way between each two. */
    static double[] lines(int bands, double front) {
        double[] v = new double[bands + 1];
        v[0] = front;
        for (int j = 1; j <= bands; j++) v[j] = v[j - 1] + parRow(j - 1, 1) / 2 + ROWS + parRow(j, 1) / 2;
        return v;
    }

    /** Where a row starts in its zone's widths: houses vary by block and side (the wide lot moves), others from the first. */
    static int start(String zone, int k, int j, int side) {
        return "res".equals(zone) ? Math.floorMod(2 * k + 3 * j + side, ROW.get("res").widths().length) : 0;
    }

    /**
     * One row of lots along a block side: frontage s0 .. s1 at the street edge vEdge (|v|), depth inward. The widths are
     * taken from start on, as many as bring the stretch closest to 1 (never squeezed by more than FIT), then scaled to
     * the frontage.
     */
    private static void row(List<TownPlan.Lot> lots, Map<String, Integer> lotZones, String zone, double s0, double s1, double vEdge, double depth, int side,
                            boolean near, RowSpec spec, double hA, double hB, double sA, double sB, int start) {
        double len = s1 - s0, sum = 0;
        List<Double> base = new ArrayList<>();
        for (int i = start; ; i++) {
            double w = Math.min(spec.widths()[i % spec.widths().length], len);
            if (!base.isEmpty() && sum + w > len + 1e-6) {
                // one more lot squeezed in when that fits the frontage better (126 m beside the exit's C14 takes the
                // five houses of 128 m)
                if (len / (sum + w) >= 1 - FIT && Math.abs(len / (sum + w) - 1) < Math.abs(len / sum - 1)) {
                    base.add(w);
                    sum += w;
                }
                break;
            }
            base.add(w);
            sum += w;
            if (sum >= len - 1e-6) break;
        }
        double k = len / sum, s = s0;
        for (double w : base) {
            double a = s, b = s + w * k;
            double vi = near ? vEdge + depth : vEdge - depth;
            double v0 = side > 0 ? Math.min(vEdge, vi) : -Math.max(vEdge, vi), v1 = side > 0 ? Math.max(vEdge, vi) : -Math.min(vEdge, vi);
            // the street is at the lot's smaller |v| side when near
            int face = (near == side > 0) ? TownPlan.FACE_V0 : TownPlan.FACE_V1;
            // the pad: the frontage street's datum (as the works build it: flat over the boxes, TownWorks#datum) at the
            // lot's first driveway, so the driveway enters level (2026-10-18), else at the lot's middle
            TownPlan.Lot bare = new TownPlan.Lot(r2(a), r2(b), r2(v0), r2(v1), zone, face, 0);
            double[][] dw = TownPlan.driveways(bare, r2(s0), r2(s1));
            double at = dw.length > 0 ? (dw[0][0] + dw[0][1]) / 2 : (a + b) / 2;
            double hs = TownWorks.datum(at, sA, sB, hA, hB, s0 - sA, sB - s1);
            lots.add(new TownPlan.Lot(bare.s0(), bare.s1(), bare.v0(), bare.v1(), zone, face, r2(hs + PAD)));
            lotZones.merge(zone, 1, Integer::sum);
            s = b;
        }
    }

    private static long key(int k, int side, int j) { return ((long) k << 32) ^ ((long) side << 16) ^ j; }

    /** A side's exit cross street s (shift: [town side, far side]). */
    private static double sh(double[] shift, int side) { return side > 0 ? shift[0] : shift[1]; }

    /** Within this of the grid's v axis (the sine of the angle) the crossroad runs straight on the grid; for at least ALIGNED_RUN m. */
    static final double ALIGNED = 0.004, ALIGNED_RUN = 40;

    /**
     * Where the built crossroad xd (its crossing at station tc) runs straight on the grid on side (1 the town's, -1 the
     * far one; TownGrid#path, 2026-10-19): {s of the crossroad at Front St, |v| of Front St (TownGrid#frontOf from where
     * it runs straight)}; null when it never does.
     */
    static double[] straight(RoadDesign xd, double tc, Frame fr, int side) {
        double[] p0 = xd.point(Math.max(0, tc - 20), 0), p1 = xd.point(Math.min(xd.length, tc + 20), 0);
        int dir = (fr.v(p1) - fr.v(p0)) * side > 0 ? 1 : -1;
        double start = Double.NaN, run = 0;
        for (double t = tc; dir > 0 ? t <= xd.length : t >= 0; t += dir * 2) {
            double[] a = xd.point(t - 1, 0), b = xd.point(t + 1, 0);
            double hx = b[0] - a[0], hz = b[1] - a[1], across = Math.abs(hx * fr.ax + hz * fr.az) / Math.hypot(hx, hz);
            if (across < ALIGNED) {
                if (Double.isNaN(start)) start = t;
                if ((run += 2) >= ALIGNED_RUN) break;
            } else {
                start = Double.NaN;
                run = 0;
            }
        }
        if (Double.isNaN(start) || run < ALIGNED_RUN) return null;
        double front = TownGrid.frontOf(side * fr.v(xd.point(start, 0)));
        for (double t = start; dir > 0 ? t <= xd.length : t >= 0; t += dir * 0.25) {
            double[] p = xd.point(t, 0);
            if (side * fr.v(p) >= front) return new double[]{r2(fr.s(p)), front};
        }
        return null;
    }

    private static int node(Map<Long, Integer> at, List<double[]> pos, int k, int side, int j, double[] vv, double sh) {
        return at.computeIfAbsent(key(k, side, j), q -> {
            pos.add(new double[]{sh + k * CROSS, side * vv[j]});
            return pos.size() - 1;
        });
    }

    /** A street segment once: {a, b, kind (0 parallel, 1 cross), side, line (j for parallel, k for cross)}; the zones along it. */
    private static void seg(Map<String, int[]> segs, Map<String, List<String>> zones, int a, int b, String zone, int side, int line, int kind) {
        String k = Math.min(a, b) + ":" + Math.max(a, b);
        segs.putIfAbsent(k, new int[]{a, b, kind, side, line});
        zones.computeIfAbsent(k, q -> new ArrayList<>()).add(zone == null ? "res" : zone);
    }

    /**
     * A cell between street centrelines: its relief (max - min of the ground), or -1 when it is not buildable (counted in
     * why: water, relief, roads, -).
     */
    private static double cell(Frame fr, double s0, double s1, double v0, double v1, RouteTerrain terrain, List<RoadDesign> others, int[] why) {
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (double s = s0; s <= s1 + 1e-6; s += SAMPLE)
            for (double v = v0; v <= v1 + 1e-6; v += SAMPLE) {
                double[] w = fr.world(Math.min(s, s1), Math.min(v, v1));
                if (terrain.water(w[0], w[1]) != RouteTerrain.DRY) { why[0]++; return -1; }
                double g = terrain.height(w[0], w[1]);
                lo = Math.min(lo, g);
                hi = Math.max(hi, g);
                for (RoadDesign d : others) {
                    double[] l = d.local(w[0], w[1], d.section.edge + ROAD_CLEAR);
                    if (l != null && Math.abs(l[1]) <= d.section.edge + ROAD_CLEAR && l[0] >= 0 && l[0] <= d.length) { why[2]++; return -1; }
                }
            }
        if (hi - lo > FALLBACK_RELIEF) { why[1]++; return -1; }
        return hi - lo;
    }

    /** The mean ground over a disc of NODE_DISC. */
    private static double disc(RouteTerrain terrain, double x, double z) {
        double sum = 0;
        int n = 0;
        for (double dx = -NODE_DISC; dx <= NODE_DISC; dx += 4)
            for (double dz = -NODE_DISC; dz <= NODE_DISC; dz += 4) {
                if (dx * dx + dz * dz > NODE_DISC * NODE_DISC) continue;
                sum += terrain.height(x + dx, z + dz);
                n++;
            }
        return sum / n;
    }

    static String ordinal(int n) {
        int m = n % 100;
        String suf = m >= 11 && m <= 13 ? "th" : switch (n % 10) { case 1 -> "st"; case 2 -> "nd"; case 3 -> "rd"; default -> "th"; };
        return n + suf;
    }

    private static double r2(double v) { return RoadNetworkJson.round2(v); }

    /** Town coordinates to the world. */
    private record Frame(double ox, double oz, double ax, double az, double bx, double bz) {
        double[] world(double s, double v) { return new double[]{ox + s * ax + v * bx, oz + s * az + v * bz}; }

        double s(double[] p) { return (p[0] - ox) * ax + (p[1] - oz) * az; }

        double v(double[] p) { return (p[0] - ox) * bx + (p[1] - oz) * bz; }
    }
}
