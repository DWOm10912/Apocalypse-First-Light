package com.antaurora.apofirstlight.worldgen.network.town;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The finished ground of the framework towns (Town Framework V1, docs/worldgen/federal_towns_v1.md 7): what every point of
 * a town's footprint is (#spot: asphalt, curb, utility strip, sidewalk, lot, yard) and its height, the 1:SLOPE slopes from
 * the footprint's edge to the natural ground, the paved columns. Pure Java over the TownPlans; RoadWorks combines it with
 * the roads' works (the density's surface, the chunk pass's columns, the forest's clearance) and TownMesh draws its
 * pavement from the same spots.
 * <ul>
 *   <li>streets: straight between their intersections, the street's datum (crown height) flat over an intersection's box
 *   and straight between, its grade breaks rounded over ROUND; the asphalt crowned TownStreets.CROWN over its edges,
 *   the curb band, the utility strip and the sidewalk at G = the asphalt edge + TownStreets.CURB_HEIGHT;</li>
 *   <li>intersections: a box of the crossing streets' rights of way at the node's height, the asphalt of every street
 *   that reaches it (the centre square and each arm), RETURN curb returns between two arms that are there;</li>
 *   <li>driveways (TownPlan#driveways, 2026-10-18): across a lot's driveway the curb and the utility strip are one
 *   concrete apron rising from LIP over the gutter to the sidewalk (the sidewalk runs on level), its ends flared over
 *   FLARE back to the curb's top;</li>
 *   <li>blocks: their lots flat at their pads LOT_BLEND inside their edges (or wider: a sidewalk or a neighbouring pad
 *   further off than BANK allows; a lot as wide as its block, a park, a school or a parking lot: no pad, graded
 *   between its streets), the margins and the yards (alleys, strips behind a row) graded smoothly between the pads and
 *   the sidewalks round the block (a relaxed GRID per block, Town#grid);</li>
 *   <li>beyond the footprint (streets and blocks): the edge's height running back to the ground at 1:SLOPE, at most
 *   REACH off.</li>
 * </ul>
 */
public final class TownWorks {
    /** Slope from the footprint to the natural ground (rise per run), its farthest reach, m. */
    public static final double SLOPE = 0.5, REACH = 60;
    /**
     * A lot's margin graded between its pad and its neighbours (the streets' sidewalks, the next lots, the yards), the
     * curb returns' radius, the grade breaks' rounding, the blocks' ground grid, m.
     */
    public static final double LOT_BLEND = 4, RETURN = 6, ROUND = 8, GRID = 2;
    /** Relaxation sweeps at most for a block's ground (#Town#grid). */
    static final int RELAX = 600;
    /**
     * A driveway's flare along the curb at each end (linear: its drop of a curb's height plus a 7 % street stays under
     * 15 %), the apron's lip over the gutter (one collision layer), m.
     */
    public static final double FLARE = 2.5, LIP = 1.0 / 32;
    /** The steepest bank from a pad down (or up) to its sidewalks a lot's margin is widened for (rise per run). */
    public static final double BANK = 0.6;
    /**
     * The margin's widening on a lot wider or deeper than WIDE m (highway, industry, civic): the relaxed bank steepens at
     * the pad's corners (a pad of 54 x 64 m on a hillside: 76 % without, 2026-10-18); narrower lots keep their pads whole.
     */
    static final double CORNER = 1.3, WIDE = 40;
    /** The index's cell, m. */
    static final double CELL = 32;

    public enum Part {
        ASPHALT, CURB, UTILITY, SIDEWALK, LOT, YARD,
        /** a driveway's apron across the curb and the utility strip (concrete) */
        DRIVEWAY;

        /** Paved: the chunk pass lays collision layers and the mesh draws asphalt or concrete. */
        public boolean paved() { return this == ASPHALT || this == CURB || this == SIDEWALK || this == DRIVEWAY; }
    }

    /** A point of a town's footprint: what it is and its finished height. */
    public record Spot(Part part, double h) {}

    private final List<Town> towns = new ArrayList<>();
    private final Map<Long, Town[]> byChunk = new HashMap<>();

    public TownWorks(List<TownPlan> plans) {
        Map<Long, List<Town>> idx = new HashMap<>();
        for (TownPlan p : plans) {
            Town t = new Town(p);
            towns.add(t);
            // the chunks the limit and the slopes reach
            double x0 = Double.POSITIVE_INFINITY, x1 = Double.NEGATIVE_INFINITY, z0 = Double.POSITIVE_INFINITY, z1 = Double.NEGATIVE_INFINITY;
            for (double s : new double[]{p.limit[0] - REACH, p.limit[1] + REACH})
                for (double v : new double[]{p.limit[2] - REACH, p.limit[3] + REACH}) {
                    double[] w = p.world(s, v);
                    x0 = Math.min(x0, w[0]); x1 = Math.max(x1, w[0]); z0 = Math.min(z0, w[1]); z1 = Math.max(z1, w[1]);
                }
            for (int cx = Math.floorDiv((int) Math.floor(x0), 16); cx <= Math.floorDiv((int) Math.floor(x1), 16); cx++)
                for (int cz = Math.floorDiv((int) Math.floor(z0), 16); cz <= Math.floorDiv((int) Math.floor(z1), 16); cz++) {
                    double[] l = p.local(cx * 16 + 8, cz * 16 + 8);
                    if (!p.inside(l[0], l[1], REACH + 12)) continue;
                    idx.computeIfAbsent(key(cx, cz), k -> new ArrayList<>()).add(t);
                }
        }
        idx.forEach((k, l) -> byChunk.put(k, l.toArray(new Town[0])));
    }

    public static final TownWorks EMPTY = new TownWorks(List.of());

    public boolean isEmpty() { return towns.isEmpty(); }

    public List<TownPlan> plans() {
        List<TownPlan> out = new ArrayList<>();
        for (Town t : towns) out.add(t.plan);
        return out;
    }

    /** Whether a town's footprint or slopes can reach chunk (cx, cz). */
    public boolean touches(int cx, int cz) { return byChunk.containsKey(key(cx, cz)); }

    private Town[] near(double x, double z) { return byChunk.get(key(Math.floorDiv((int) Math.floor(x), 16), Math.floorDiv((int) Math.floor(z), 16))); }

    /** The footprint's spot at a world point, or null off every town's footprint. */
    public Spot spot(double x, double z) {
        Town[] ts = near(x, z);
        if (ts == null) return null;
        for (Town t : ts) {
            double[] l = t.plan.local(x, z);
            Spot s = t.spot(l[0], l[1]);
            if (s != null) return s;
        }
        return null;
    }

    /** Solid ground kept under a town's finished surface against the plan's caves and the carvers, m. */
    public static final double ROCK = 8;

    /**
     * The ground kept solid under a town's footprint in block column (x, z) against the plan's caves and the carvers
     * (PlanStabilityDensity's hard floor, WorldCarverStabilityMixin, LakeFeatureStabilityMixin through RoadWorksRef#rock):
     * {lo, hi}, ROCK under the finished surface up (the density caps it at its surface); null off every footprint. The
     * stable layer hangs from the planned surface and has no hard floor in the foothills, so a lot graded into a valley
     * side could lie on one block of turf over a cave (Linden, 2026-10-17).
     */
    public double[] rock(int x, int z) {
        Spot sp = spot(x + 0.5, z + 0.5);
        return sp == null ? null : new double[]{sp.h() - ROCK, Double.POSITIVE_INFINITY};
    }

    /**
     * The towns' finished surface over the natural ground g: on a footprint its spot's height, beside it the slope back
     * to the ground; NaN where the towns leave the ground natural.
     */
    public double surface(double x, double z, double g) {
        Town[] ts = near(x, z);
        if (ts == null) return Double.NaN;
        double up = 0, down = 0;
        boolean any = false;
        for (Town t : ts) {
            double[] l = t.plan.local(x, z);
            Spot s = t.spot(l[0], l[1]);
            if (s != null) return s.h();
            double[] e = t.edge(l[0], l[1]);
            if (e == null) continue;
            double f = approach(g, e[1], e[0] * SLOPE);
            if (Math.abs(f - g) < 0.03) continue;
            any = true;
            up = Math.max(up, f - g);
            down = Math.max(down, g - f);
        }
        return any ? g + up - down : Double.NaN;
    }

    /** f moved toward g by allowed (the ground wins beyond the slope). */
    static double approach(double g, double f, double allowed) {
        double diff = f - g;
        return g + Math.copySign(Math.max(0, Math.abs(diff) - allowed), diff);
    }

    /** The pavement range {lo, hi} of block column (x, z) when it lies wholly on paved spots (nine samples), else null. */
    public double[] pavedRange(int x, int z) {
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (int a = 0; a <= 2; a++)
            for (int b = 0; b <= 2; b++) {
                Spot s = spot(x + a * 0.5, z + b * 0.5);
                if (s == null || !s.part().paved()) return null;
                lo = Math.min(lo, s.h());
                hi = Math.max(hi, s.h());
            }
        return new double[]{lo, hi};
    }

    /** Whether a point is inside a town's limit grown by extra (the forest keeps out, as off the roads). */
    public boolean clear(double x, double z, double extra) {
        Town[] ts = near(x, z);
        if (ts == null) return false;
        for (Town t : ts) {
            double[] l = t.plan.local(x, z);
            if (t.plan.inside(l[0], l[1], extra)) return true;
        }
        return false;
    }

    /**
     * Whether turf column (x, z) carries no plants: its square grown by clear reaches a street's strip (asphalt, curb,
     * utility strip, sidewalk; the lots and yards keep theirs).
     */
    public boolean bare(int x, int z, double clear) {
        if (!touches(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) return false;
        double w = 1 + 2 * clear;
        for (int b = 0; b <= 1; b++) for (int a = 0; a <= 1; a++) {
            Spot s = spot(x - clear + a * w, z - clear + b * w);
            if (s != null && s.part() != Part.LOT && s.part() != Part.YARD) return true;
        }
        return false;
    }

    /**
     * What a world point is in a town (the debug command /afl worldgen town): the town, its coordinates there, the part
     * and its height, the lot's zone or the yard, the nearest street; null outside every town's limit.
     */
    public String describe(double x, double z) {
        Town[] ts = near(x, z);
        if (ts == null) return null;
        for (Town t : ts) {
            TownPlan p = t.plan;
            double[] l = p.local(x, z);
            if (!p.inside(l[0], l[1], 0)) continue;
            Spot sp = t.spot(l[0], l[1]);
            StringBuilder b = new StringBuilder(String.format(java.util.Locale.ROOT, "%s (%s): s %.1f v %.1f", p.name, p.state, l[0], l[1]));
            if (sp == null) b.append(", off the streets and blocks");
            else {
                b.append(String.format(java.util.Locale.ROOT, ", %s at %.2f", sp.part().name().toLowerCase(java.util.Locale.ROOT), sp.h()));
                for (TownPlan.Lot lot : p.lots)
                    if (l[0] >= lot.s0() && l[0] <= lot.s1() && l[1] >= lot.v0() && l[1] <= lot.v1())
                        b.append(String.format(java.util.Locale.ROOT, ", lot %d zone %s pad %.2f (%.0f x %.0f m)", p.lots.indexOf(lot), lot.zone(), lot.pad(),
                                lot.s1() - lot.s0(), lot.v1() - lot.v0()));
            }
            String street = null;
            double best = Double.POSITIVE_INFINITY;
            for (TownPlan.Street st : p.streets) {
                TownPlan.Node a = p.nodes.get(st.a()), c = p.nodes.get(st.b());
                double ds = c.s() - a.s(), dv = c.v() - a.v(), len2 = ds * ds + dv * dv;
                double f = len2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((l[0] - a.s()) * ds + (l[1] - a.v()) * dv) / len2));
                double d = Math.hypot(l[0] - a.s() - f * ds, l[1] - a.v() - f * dv);
                if (d < best) { best = d; street = st.name() + " (" + st.cls() + ")"; }
            }
            if (street != null) b.append(String.format(java.util.Locale.ROOT, "; nearest street %s, %.0f m", street, best));
            return b.toString();
        }
        return null;
    }

    /** Whether a point lies on a footprint (streets, intersections, blocks). */
    public boolean onFootprint(double x, double z) { return spot(x, z) != null; }

    private static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xffffffffL); }

    /**
     * A street's datum at along a (Seg#datum): nodes at a0 (height h0) and a1 (h1), their boxes reaching p0 and p1 along
     * it; the planner levels the pads with it too (TownPlanner#row).
     */
    static double datum(double a, double a0, double a1, double h0, double h1, double p0, double p1) {
        double d = a1 - a0 - p0 - p1;
        if (d <= 1e-6) return (h0 + h1) / 2;
        double t = a - a0 - p0, r = Math.min(ROUND / 2, d / 4), e = d - 2 * r;
        double f = (ramp(t / e) - ramp((t - 2 * r) / e)) * e / (2 * r);
        return h0 + (h1 - h0) * f;
    }

    /** The integral of clamp(x, 0, 1). */
    private static double ramp(double x) { return x <= 0 ? 0 : x >= 1 ? x - 0.5 : x * x / 2; }

    static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    // ------------------------------------------------------------------ one town

    /**
     * An intersection: position, height, each arm's half asphalt (+s, -s parallel; +v, -v cross; 0: no arm), the centre
     * square's half asphalt across the parallel (haP, in v) and the cross streets (haX, in s), its box.
     */
    record NodeX(double s, double v, double h, double[] ha, double haP, double haX, double extS, double extV, double curb, double utility, double sidewalk) {}

    /** A block's graded ground: nodes (i, j) at (s0 + i ds, v0 + j dv), heights h[i + (ns + 1) j]; bilinear between. */
    record Grid(double s0, double v0, double ds, double dv, int ns, int nv, double[] h) {
        double at(double s, double v) {
            double x = Math.max(0, Math.min(ns, (s - s0) / ds)), y = Math.max(0, Math.min(nv, (v - v0) / dv));
            int i = Math.min(ns - 1, (int) Math.floor(x)), j = Math.min(nv - 1, (int) Math.floor(y));
            double fx = x - i, fy = y - j;
            int w = ns + 1, k = i + w * j;
            return (h[k] * (1 - fx) + h[k + 1] * fx) * (1 - fy) + (h[k + w] * (1 - fx) + h[k + w + 1] * fx) * fy;
        }
    }

    /** A street between two nodes: parallel (along s) or cross (along v), its line, its range and the boxes at its ends. */
    record Seg(boolean par, double line, double a0, double a1, double h0, double h1, double p0, double p1, TownStreets.Section sec) {
        /**
         * The datum (crown height) at along a: flat over the boxes, between them the straight grade from r past the one
         * box's edge to r before the other's, its breaks rounded over +-r (the mean of the clamped ramp over the window), so
         * the street leaves each box level.
         */
        double datum(double a) { return TownWorks.datum(a, a0, a1, h0, h1, p0, p1); }

        double along(double s, double v) { return par ? s : v; }

        double across(double s, double v) { return par ? v - line : s - line; }
    }

    /** A driveway: along s0 .. s1 at the lot's frontage edge vEdge, its street toward dir (-1: smaller v, +1: larger). */
    record Drive(double s0, double s1, double vEdge, int dir) {}

    /** A block in the index: its lots and its graded ground (null while the grids are laid out). */
    static final class BlockX {
        final TownPlan.Block b;
        final List<TownPlan.Lot> lots = new ArrayList<>();
        Grid grid;

        BlockX(TownPlan.Block b) { this.b = b; }
    }

    final class Town {
        final TownPlan plan;
        final NodeX[] nodes;
        final Seg[] segs;
        /** The index: CELL squares over the limit grown by REACH + CELL, from (i0, j0), ni x nj, lists of what reaches each. */
        final long i0, j0;
        final int ni, nj;
        final List<Object>[] cells;
        final List<BlockX> blocks = new ArrayList<>();

        @SuppressWarnings("unchecked")
        Town(TownPlan p) {
            this.plan = p;
            double m = REACH + CELL;
            i0 = (long) Math.floor((p.limit[0] - m) / CELL);
            j0 = (long) Math.floor((p.limit[2] - m) / CELL);
            ni = (int) ((long) Math.floor((p.limit[1] + m) / CELL) - i0 + 1);
            nj = (int) ((long) Math.floor((p.limit[3] + m) / CELL) - j0 + 1);
            cells = new List[ni * nj];
            int n = p.nodes.size();
            double[][] arm = new double[n][4];
            double[][] w = new double[n][7];   // haP, haX, rowP/2, rowX/2, curb, utility, sidewalk
            for (TownPlan.Street st : p.streets) {
                TownPlan.Node a = p.nodes.get(st.a()), b = p.nodes.get(st.b());
                TownStreets.Section sec = TownStreets.layout(st.cls());
                boolean par = Math.abs(a.v() - b.v()) < 1e-6;
                for (int e = 0; e < 2; e++) {
                    int i = e == 0 ? st.a() : st.b();
                    TownPlan.Node me = e == 0 ? a : b, other = e == 0 ? b : a;
                    int dir = par ? (other.s() > me.s() ? 0 : 1) : (other.v() > me.v() ? 2 : 3);
                    arm[i][dir] = sec.halfAsphalt();
                    if (par) { w[i][0] = Math.max(w[i][0], sec.halfAsphalt()); w[i][2] = Math.max(w[i][2], sec.halfRow()); }
                    else { w[i][1] = Math.max(w[i][1], sec.halfAsphalt()); w[i][3] = Math.max(w[i][3], sec.halfRow()); }
                    w[i][4] = Math.max(w[i][4], sec.curb());
                    w[i][5] = Math.max(w[i][5], sec.utility());
                    w[i][6] = Math.max(w[i][6], sec.sidewalk());
                }
            }
            // the exit crossroad's mouth: an arm of its Front St corner toward the interstate, the crossroad's paved width
            double[] mouthV = new double[n];
            java.util.Arrays.fill(mouthV, Double.NaN);
            for (double[] e : p.exitEnds) {
                int i = (int) e[0];
                if (i < 0 || i >= n || p.exitHalf <= 0) continue;
                TownPlan.Node q = p.nodes.get(i);
                int dir = e[1] > q.v() ? 2 : 3;
                arm[i][dir] = Math.max(arm[i][dir], p.exitHalf);
                w[i][1] = Math.max(w[i][1], p.exitHalf);
                mouthV[i] = e[1];
            }
            nodes = new NodeX[n];
            for (int i = 0; i < n; i++) {
                TownPlan.Node q = p.nodes.get(i);
                // a node without a cross (or parallel) street still has the other's band through its box
                double haP = w[i][0] > 0 ? w[i][0] : w[i][1], haX = w[i][1] > 0 ? w[i][1] : w[i][0];
                double extS = w[i][3] > 0 ? w[i][3] : w[i][2], extV = w[i][2] > 0 ? w[i][2] : w[i][3];
                nodes[i] = new NodeX(q.s(), q.v(), q.h(), arm[i], haP, haX, extS, extV, w[i][4], w[i][5], w[i][6]);
                add(q.s() - extS, q.s() + extS, q.v() - extV, q.v() + extV, nodes[i]);
            }
            List<Seg> sl = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (Double.isNaN(mouthV[i])) continue;
                // between the box's edge and the crossroad's end (the 2 m lattice leaves up to 2 m): flat at the corner
                NodeX x = nodes[i];
                TownStreets.Section sec = new TownStreets.Section("mouth", 2 * p.exitHalf, x.curb(), x.utility(), x.sidewalk());
                boolean up = mouthV[i] > x.v();
                Seg sg = up ? new Seg(false, x.s(), x.v(), mouthV[i], x.h(), x.h(), x.extV(), 0, sec)
                        : new Seg(false, x.s(), mouthV[i], x.v(), x.h(), x.h(), 0, x.extV(), sec);
                if (sg.a1() - sg.a0() <= x.extV()) continue;
                sl.add(sg);
                add(x.s() - sec.halfRow(), x.s() + sec.halfRow(), sg.a0(), sg.a1(), sg);
            }
            for (int k = 0; k < p.streets.size(); k++) {
                TownPlan.Street st = p.streets.get(k);
                int ia = st.a(), ib = st.b();
                TownPlan.Node a = p.nodes.get(ia), b = p.nodes.get(ib);
                boolean par = Math.abs(a.v() - b.v()) < 1e-6;
                double aa = par ? a.s() : a.v(), ab = par ? b.s() : b.v();
                if (ab < aa) { int t = ia; ia = ib; ib = t; double x = aa; aa = ab; ab = x; }
                NodeX na = nodes[ia], nb = nodes[ib];
                TownStreets.Section sec = TownStreets.layout(st.cls());
                Seg sg = new Seg(par, par ? a.v() : a.s(), aa, ab, na.h(), nb.h(), par ? na.extS() : na.extV(), par ? nb.extS() : nb.extV(), sec);
                sl.add(sg);
                double hr = sec.halfRow();
                if (par) add(aa, ab, sg.line() - hr, sg.line() + hr, sg);
                else add(sg.line() - hr, sg.line() + hr, aa, ab, sg);
            }
            segs = sl.toArray(new Seg[0]);
            for (TownPlan.Block b : p.blocks) {
                BlockX x = new BlockX(b);
                blocks.add(x);
                add(b.s0(), b.s1(), b.v0(), b.v1(), x);
            }
            for (TownPlan.Lot l : p.lots)
                for (BlockX x : blocks) {
                    TownPlan.Block b = x.b;
                    if (l.s0() >= b.s0() - 0.01 && l.s1() <= b.s1() + 0.01 && l.v0() >= b.v0() - 0.01 && l.v1() <= b.v1() + 0.01) { x.lots.add(l); break; }
                }
            // the driveways, indexed over the street strip in front of each (the widest right of way)
            for (BlockX x : blocks)
                for (TownPlan.Lot l : x.lots) {
                    double vEdge = l.face() == TownPlan.FACE_V0 ? l.v0() : l.v1();
                    int dir = l.face() == TownPlan.FACE_V0 ? -1 : 1;
                    double reach = TownStreets.C14.row();
                    for (double[] d : TownPlan.driveways(l, x.b.s0(), x.b.s1()))
                        add(d[0] - FLARE, d[1] + FLARE, Math.min(vEdge, vEdge + dir * reach), Math.max(vEdge, vEdge + dir * reach), new Drive(d[0], d[1], vEdge, dir));
                }
            // the blocks' ground (the streets round them are in place: their sidewalks are its edges)
            Grid[] gs = new Grid[blocks.size()];
            for (int k = 0; k < gs.length; k++) gs[k] = grid(blocks.get(k));
            for (int k = 0; k < gs.length; k++) blocks.get(k).grid = gs[k];
        }

        private void add(double s0, double s1, double v0, double v1, Object o) {
            for (long i = Math.max(i0, (long) Math.floor(s0 / CELL)); i <= Math.min(i0 + ni - 1, (long) Math.floor(s1 / CELL)); i++)
                for (long j = Math.max(j0, (long) Math.floor(v0 / CELL)); j <= Math.min(j0 + nj - 1, (long) Math.floor(v1 / CELL)); j++) {
                    int k = (int) (i - i0) * nj + (int) (j - j0);
                    if (cells[k] == null) cells[k] = new ArrayList<>(4);
                    cells[k].add(o);
                }
        }

        private List<Object> at(double s, double v) {
            long i = (long) Math.floor(s / CELL) - i0, j = (long) Math.floor(v / CELL) - j0;
            if (i < 0 || j < 0 || i >= ni || j >= nj) return List.of();
            List<Object> l = cells[(int) i * nj + (int) j];
            return l == null ? List.of() : l;
        }

        /** The footprint at town coordinates (s, v), or null. */
        Spot spot(double s, double v) {
            if (!plan.inside(s, v, 1)) return null;
            List<Object> here = at(s, v);
            for (Object o : here)
                if (o instanceof NodeX n && Math.abs(s - n.s()) <= n.extS() && Math.abs(v - n.v()) <= n.extV()) return node(n, s - n.s(), v - n.v());
            for (Object o : here)
                if (o instanceof Seg g) {
                    double a = g.along(s, v), w = g.across(s, v);
                    if (a >= g.a0() + g.p0() && a <= g.a1() - g.p1() && Math.abs(w) <= g.sec().halfRow()) return street(g, a, w, here);
                }
            for (Object o : here)
                if (o instanceof BlockX x && s >= x.b.s0() && s <= x.b.s1() && v >= x.b.v0() && v <= x.b.v1()) {
                    // while the grids are laid out (the streets' spots only) a block has none yet
                    if (x.grid == null) return null;
                    boolean lot = false;
                    for (TownPlan.Lot l : x.lots) if (s >= l.s0() && s <= l.s1() && v >= l.v0() && v <= l.v1()) { lot = true; break; }
                    return new Spot(lot ? Part.LOT : Part.YARD, x.grid.at(s, v));
                }
            return null;
        }

        /** A point of a street's strip: its part by the offset ws from the centre line (signed: the side), a driveway's apron. */
        private Spot street(Seg g, double a, double ws, List<Object> here) {
            double e = g.datum(a), w = Math.abs(ws);
            TownStreets.Section sec = g.sec();
            double ha = sec.halfAsphalt();
            if (w <= ha) return new Spot(Part.ASPHALT, e - TownStreets.CROWN * w / ha);
            double top = e - TownStreets.CROWN + TownStreets.CURB_HEIGHT;
            double band = sec.curb() + sec.utility();
            if (g.par() && w <= ha + band) {
                // a driveway of the lot on this side (the nearest: two neighbours' flares may meet): the apron from the
                // gutter's lip up to the sidewalk, flared at its ends
                double edge = g.line() + Math.signum(ws) * sec.halfRow(), out = Double.POSITIVE_INFINITY;
                for (Object o : here) {
                    if (!(o instanceof Drive d) || d.dir() != -(int) Math.signum(ws) || Math.abs(d.vEdge() - edge) > 0.5) continue;
                    out = Math.min(out, Math.max(0, Math.max(d.s0() - a, a - d.s1())));
                }
                if (out <= FLARE) {
                    double gutter = e - TownStreets.CROWN, apron = gutter + LIP + (top - gutter - LIP) * (w - ha) / band;
                    return new Spot(Part.DRIVEWAY, apron + (top - apron) * out / FLARE);
                }
            }
            if (w <= ha + sec.curb()) return new Spot(Part.CURB, top);
            if (w <= ha + sec.curb() + sec.utility()) return new Spot(Part.UTILITY, top);
            return new Spot(Part.SIDEWALK, top);
        }

        /** A point of an intersection's box (offsets from the node): its part by the distance to the asphalt. */
        private Spot node(NodeX n, double ds, double dv) {
            double dA = asphaltDistance(n, ds, dv);
            if (dA <= 1e-9) {
                // crowned across each arm by that arm's own width (the centre square by the widest)
                double[] ha = n.ha();
                double hp = ha[ds >= 0 ? 0 : 1] > 0 && Math.abs(ds) > n.haX() ? ha[ds >= 0 ? 0 : 1] : n.haP();
                double hx = ha[dv >= 0 ? 2 : 3] > 0 && Math.abs(dv) > n.haP() ? ha[dv >= 0 ? 2 : 3] : n.haX();
                double cf = Math.min(1, Math.min(Math.abs(dv) / hp, Math.abs(ds) / hx));
                return new Spot(Part.ASPHALT, n.h() - TownStreets.CROWN * cf);
            }
            double top = n.h() - TownStreets.CROWN + TownStreets.CURB_HEIGHT;
            if (dA <= n.curb()) return new Spot(Part.CURB, top);
            if (dA <= n.curb() + n.utility()) return new Spot(Part.UTILITY, top);
            return new Spot(Part.SIDEWALK, top);
        }

        /** Distance from a box point to the asphalt: the centre square, the arms there, the curb returns between them. */
        private double asphaltDistance(NodeX n, double ds, double dv) {
            double best = rect(ds, dv, -n.haX(), n.haX(), -n.haP(), n.haP());
            double[] ha = n.ha();
            double far = 1e9;
            if (ha[0] > 0) best = Math.min(best, rect(ds, dv, 0, far, -ha[0], ha[0]));
            if (ha[1] > 0) best = Math.min(best, rect(ds, dv, -far, 0, -ha[1], ha[1]));
            if (ha[2] > 0) best = Math.min(best, rect(ds, dv, -ha[2], ha[2], 0, far));
            if (ha[3] > 0) best = Math.min(best, rect(ds, dv, -ha[3], ha[3], -far, 0));
            if (best <= 0) return 0;
            // a curb return in the quadrant of the point, when both its arms are there
            int qs = ds >= 0 ? 0 : 1, qv = dv >= 0 ? 2 : 3;
            if (ha[qs] > 0 && ha[qv] > 0) {
                double a = Math.abs(ds) - ha[qv], b = Math.abs(dv) - ha[qs];
                if (a > 0 && b > 0 && a < RETURN && b < RETURN) {
                    double dc = Math.hypot(a - RETURN, b - RETURN);
                    if (dc >= RETURN) return 0;
                    best = Math.min(best, RETURN - dc);
                }
            }
            return best;
        }

        private static double rect(double x, double y, double x0, double x1, double y0, double y1) {
            double dx = x < x0 ? x0 - x : x > x1 ? x - x1 : 0, dy = y < y0 ? y0 - y : y > y1 ? y - y1 : 0;
            return Math.sqrt(dx * dx + dy * dy);
        }

        /** A street or intersection spot (not a lot or yard), or null. */
        private Spot streetSpot(double s, double v) {
            Spot sp = spot(s, v);
            return sp == null || sp.part() == Part.LOT || sp.part() == Part.YARD ? null : sp;
        }

        /**
         * A block's graded ground on a GRID (at most GRID metres between its nodes, the block's edges on nodes): its edge
         * nodes at the sidewalk just outside (the streets' heights), each lot's nodes LOT_BLEND or more inside its edges
         * at its pad (a lot too narrow for that: the node nearest its middle), the rest (the lots' margins, the yards)
         * relaxed to a smooth surface between (Laplace, SOR), so neighbouring pads and the streets meet in banks, never
         * in steps.
         */
        private Grid grid(BlockX x) {
            TownPlan.Block b = x.b;
            int ns = Math.max(2, (int) Math.ceil((b.s1() - b.s0()) / GRID - 1e-9)), nv = Math.max(2, (int) Math.ceil((b.v1() - b.v0()) / GRID - 1e-9));
            double ds = (b.s1() - b.s0()) / ns, dv = (b.v1() - b.v0()) / nv;
            int w = ns + 1;
            double[] h = new double[w * (nv + 1)];
            boolean[] fix = new boolean[h.length];
            double sum = 0;
            int nfix = 0;
            for (int j = 0; j <= nv; j++)
                for (int i = 0; i <= ns; i++) {
                    if (i != 0 && i != ns && j != 0 && j != nv) continue;
                    double s = b.s0() + i * ds + (i == 0 ? -0.5 : i == ns ? 0.5 : 0), v = b.v0() + j * dv + (j == 0 ? -0.5 : j == nv ? 0.5 : 0);
                    Spot st = streetSpot(s, v);
                    if (st == null) continue;
                    h[i + w * j] = st.h();
                    fix[i + w * j] = true;
                    sum += st.h();
                    nfix++;
                }
            for (TownPlan.Lot l : x.lots) {
                // a lot as wide as its block (a park, a school, a parking lot) is not one pad: its ground follows the streets
                // round it (a flat pad over 128 m of a 7 % grade leaves banks steeper than 1:1); every other lot, a highway or
                // industry lot too, is levelled at its pad (2026-10-18: buildings stand on it, its driveway enters it level)
                if (l.s0() - b.s0() < 0.5 && b.s1() - l.s1() < 0.5) continue;
                // the margin: LOT_BLEND, wider where the sidewalk along one of the lot's block edges lies further from its
                // pad than BANK allows over it, or a neighbouring lot's pad (each takes half the difference)
                double far = 0;
                for (TownPlan.Lot o : x.lots) {
                    if (o == l || o.s0() - b.s0() < 0.5 && b.s1() - o.s1() < 0.5) continue;
                    boolean touch = o.s0() <= l.s1() + 0.5 && o.s1() >= l.s0() - 0.5 && o.v0() <= l.v1() + 0.5 && o.v1() >= l.v0() - 0.5;
                    if (touch) far = Math.max(far, Math.abs(o.pad() - l.pad()) / 2);
                }
                for (int j = 0; j <= nv; j++)
                    for (int i = 0; i <= ns; i++) {
                        int k = i + w * j;
                        if (!fix[k] || i != 0 && i != ns && j != 0 && j != nv) continue;
                        double s = b.s0() + i * ds, v = b.v0() + j * dv;
                        boolean along = (i == 0 && Math.abs(l.s0() - b.s0()) < 0.01 || i == ns && Math.abs(l.s1() - b.s1()) < 0.01) && v >= l.v0() - 0.01 && v <= l.v1() + 0.01
                                || (j == 0 && Math.abs(l.v0() - b.v0()) < 0.01 || j == nv && Math.abs(l.v1() - b.v1()) < 0.01) && s >= l.s0() - 0.01 && s <= l.s1() + 0.01;
                        if (along) far = Math.max(far, Math.abs(h[k] - l.pad()));
                    }
                double in = Math.max(LOT_BLEND, (l.s1() - l.s0() > WIDE || l.v1() - l.v0() > WIDE ? CORNER : 1) * far / BANK);
                boolean any = false;
                for (int j = 1; j < nv; j++)
                    for (int i = 1; i < ns; i++) {
                        double s = b.s0() + i * ds, v = b.v0() + j * dv;
                        if (s - l.s0() < in - 1e-9 || l.s1() - s < in - 1e-9 || v - l.v0() < in - 1e-9 || l.v1() - v < in - 1e-9) continue;
                        h[i + w * j] = l.pad();
                        fix[i + w * j] = true;
                        any = true;
                        sum += l.pad();
                        nfix++;
                    }
                if (!any) {
                    int i = Math.max(1, Math.min(ns - 1, (int) Math.round(((l.s0() + l.s1()) / 2 - b.s0()) / ds)));
                    int j = Math.max(1, Math.min(nv - 1, (int) Math.round(((l.v0() + l.v1()) / 2 - b.v0()) / dv)));
                    h[i + w * j] = l.pad();
                    fix[i + w * j] = true;
                    sum += l.pad();
                    nfix++;
                }
            }
            double mean = nfix == 0 ? 0 : sum / nfix;
            for (int k = 0; k < h.length; k++) if (!fix[k]) h[k] = mean;
            // an edge node without a street beside it (none: the blocks lie between rights of way) is relaxed with the rest,
            // its missing neighbours its own value
            double omega = 1.9;
            for (int it = 0; it < RELAX; it++) {
                double moved = 0;
                for (int j = 0; j <= nv; j++)
                    for (int i = 0; i <= ns; i++) {
                        int k = i + w * j;
                        if (fix[k]) continue;
                        double a = i > 0 ? h[k - 1] : h[k], c = i < ns ? h[k + 1] : h[k], d = j > 0 ? h[k - w] : h[k], e = j < nv ? h[k + w] : h[k];
                        // the node spacing differs across and along: weighted by 1 / spacing squared
                        double ws = 1 / (ds * ds), wv = 1 / (dv * dv);
                        double target = (ws * (a + c) + wv * (d + e)) / (2 * ws + 2 * wv), step = omega * (target - h[k]);
                        h[k] += step;
                        moved = Math.max(moved, Math.abs(step));
                    }
                if (moved < 1e-3) break;
            }
            return new Grid(b.s0(), b.v0(), ds, dv, ns, nv, h);
        }

        /**
         * The nearest footprint point within REACH of (s, v) off the footprint: {distance, its height}, or null. The
         * footprint is the intersections' boxes, the streets' strips and the blocks.
         */
        double[] edge(double s, double v) {
            if (!plan.inside(s, v, REACH + 1)) return null;
            double best = REACH, bs = 0, bv = 0;
            boolean found = false;
            // the index cells within REACH (an entry in several is measured again: cheaper than a set)
            long ia = Math.max(i0, (long) Math.floor((s - REACH) / CELL)), ib = Math.min(i0 + ni - 1, (long) Math.floor((s + REACH) / CELL));
            long ja = Math.max(j0, (long) Math.floor((v - REACH) / CELL)), jb = Math.min(j0 + nj - 1, (long) Math.floor((v + REACH) / CELL));
            for (long i = ia; i <= ib; i++)
                for (long j = ja; j <= jb; j++) {
                    List<Object> l = cells[(int) (i - i0) * nj + (int) (j - j0)];
                    if (l == null) continue;
                    for (Object o : l) {
                        double r0, r1, r2, r3;
                        if (o instanceof NodeX n) { r0 = n.s() - n.extS(); r1 = n.s() + n.extS(); r2 = n.v() - n.extV(); r3 = n.v() + n.extV(); }
                        else if (o instanceof Seg g) {
                            double hr = g.sec().halfRow();
                            if (g.par()) { r0 = g.a0(); r1 = g.a1(); r2 = g.line() - hr; r3 = g.line() + hr; }
                            else { r0 = g.line() - hr; r1 = g.line() + hr; r2 = g.a0(); r3 = g.a1(); }
                        } else if (o instanceof BlockX x) { r0 = x.b.s0(); r1 = x.b.s1(); r2 = x.b.v0(); r3 = x.b.v1(); }
                        else continue;
                        double cs = Math.max(r0, Math.min(r1, s)), cv = Math.max(r2, Math.min(r3, v)), d = Math.sqrt((s - cs) * (s - cs) + (v - cv) * (v - cv));
                        if (d < best) { best = d; bs = cs; bv = cv; found = true; }
                    }
                }
            if (!found) return null;
            // the height just inside the edge
            double ins = Math.max(0, Math.min(1, best)) * 0.02;
            Spot sp = spot(bs + (bs - s) / Math.max(best, 1e-6) * ins, bv + (bv - v) / Math.max(best, 1e-6) * ins);
            if (sp == null) sp = spot(bs, bv);
            return sp == null ? null : new double[]{best, sp.h()};
        }
    }
}
