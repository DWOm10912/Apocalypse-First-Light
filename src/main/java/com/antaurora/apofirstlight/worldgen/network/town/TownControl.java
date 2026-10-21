package com.antaurora.apofirstlight.worldgen.network.town;

import java.util.ArrayList;
import java.util.List;

/**
 * The framework towns' intersection control and pavement markings (Town Framework V1, docs/worldgen/federal_towns_v1.md
 * 7.8; MUTCD 2B.04, 2B.05, 3B.01, 3B.16, 3B.18 as a small US town paints and signs its streets). Pure geometry over a
 * town's intersections (TownWorks.NodeX) in its own coordinates; TownMesh paints the marks, TownSigns stands the STOP
 * signs.
 * <ul>
 *   <li>the arms' ranks: Main St and the exit's cross street (C14, and the exit crossroad's mouth, which goes on as it)
 *   2, the other streets 1, a county road's mouth (G3) 0;</li>
 *   <li>control at an intersection of three or more arms: a T's stem stops; of four arms the lower ranked pair stops
 *   (two-way stop), of two ranked alike the cross streets (constant s: the short numbered streets) stop where they are
 *   residential, every arm where both are rank 2 (Main St at the exit street: an all-way stop, ALL WAY plaque); a county
 *   road's mouth stops always;</li>
 *   <li>crosswalks (transverse lines, MUTCD 3B.18): on every arm of an intersection that has a rank 2 arm (Main St and
 *   the exit street's corners, where people walk), in line with the crossing street's sidewalk, at least WALK wide,
 *   two LINE_WALK white lines curb to curb (round the curb returns);</li>
 *   <li>stop bars (white, BAR deep): across the approach half from STOP_IN off the centre line to the curb, STOP_GAP
 *   before the crosswalk or past the curb return's end;</li>
 *   <li>centre lines: rank 2 streets (C14) a double yellow line (two LINE lines, CENTRE_GAP off the centre line) from
 *   the marks at one end to those at the other; the residential streets none (MUTCD 3B.01: local streets).</li>
 * </ul>
 * Mouths: their marks stay on the town's pavement (before the road's end along the arm); a mark that does not fit is
 * left out.
 */
public final class TownControl {
    private TownControl() {}

    /** Stop bar depth (24 in), its gap before a crosswalk or past the curb return (4 ft), its start off the centre line, m. */
    public static final double BAR = 0.6, STOP_GAP = 1.2, STOP_IN = 0.2;
    /** Crosswalk: least width between its lines (8 ft), the lines' width (12 in), m. */
    public static final double WALK = 2.4, LINE_WALK = 0.3;
    /** The double yellow centre line: each line's width and its inner edge off the centre line (as RoadMesh's), m. */
    public static final double LINE = 0.15, CENTRE_GAP = 0.05;
    /** A mark's ends kept this far off the curb's face, m. */
    public static final double CURB_GAP = 0.08;

    /** A painted mark: a rectangle in town coordinates (axis-aligned there), yellow or white. */
    public record Mark(boolean yellow, double s0, double v0, double s1, double v1) {}

    /**
     * A STOP sign's approach: the node, the arm (TownPlan.DIR_*), whether all the node's arms stop, the stop bar's near
     * offset from the node along the arm, the side of the approach lane (+1: the lateral axis' positive side) and the
     * arm's half asphalt there.
     */
    public record Stop(int node, int dir, boolean allWay, double offset, int side, double half) {}

    /** A town's marks and stops. */
    public record Plan(List<Mark> marks, List<Stop> stops) {}

    /** The arm kinds. */
    static final int STREET = 0, EXIT = 1, ENTRY = 2;

    static Plan of(TownWorks.Town t) {
        TownPlan p = t.plan;
        int n = p.nodes.size();
        int[][] rank = new int[n][4], kind = new int[n][4];
        double[][] end = new double[n][4];
        boolean[] c14 = new boolean[p.streets.size()];
        for (int i = 0; i < n; i++) for (int d = 0; d < 4; d++) end[i][d] = Double.POSITIVE_INFINITY;
        for (int k = 0; k < p.streets.size(); k++) {
            TownPlan.Street st = p.streets.get(k);
            TownPlan.Node a = p.nodes.get(st.a()), b = p.nodes.get(st.b());
            boolean par = Math.abs(a.v() - b.v()) < 1e-6;
            int r = st.cls().equals("C14") ? 2 : 1;
            c14[k] = r == 2;
            rank[st.a()][par ? (b.s() > a.s() ? 0 : 1) : (b.v() > a.v() ? 2 : 3)] = r;
            rank[st.b()][par ? (a.s() > b.s() ? 0 : 1) : (a.v() > b.v() ? 2 : 3)] = r;
        }
        for (double[] e : p.exitEnds) {
            int i = (int) e[0];
            if (i < 0 || i >= n) continue;
            int d = e[1] > p.nodes.get(i).v() ? TownPlan.DIR_V_UP : TownPlan.DIR_V_DOWN;
            rank[i][d] = 2;
            kind[i][d] = EXIT;
            end[i][d] = Math.abs(e[1] - p.nodes.get(i).v());
        }
        for (TownPlan.Entry e : p.entries) {
            if (e.node() < 0 || e.node() >= n) continue;
            TownPlan.Node q = p.nodes.get(e.node());
            rank[e.node()][e.dir()] = 0;
            kind[e.node()][e.dir()] = ENTRY;
            end[e.node()][e.dir()] = Math.abs(e.end() - (e.dir() <= TownPlan.DIR_S_DOWN ? q.s() : q.v()));
        }
        double hand = Math.signum(p.ax * p.bz - p.az * p.bx);
        List<Mark> marks = new ArrayList<>();
        List<Stop> stops = new ArrayList<>();
        // per node and arm: how far out its marks reach (the centre lines start there)
        double[][] clear = new double[n][4];
        for (int i = 0; i < n; i++) {
            TownWorks.NodeX x = t.nodes[i];
            double[] ha = x.ha();
            int legs = 0;
            boolean main = false;
            for (int d = 0; d < 4; d++) if (ha[d] > 0) { legs++; if (rank[i][d] == 2) main = true; }
            boolean[] stop = new boolean[4];
            boolean allWay = false;
            if (legs >= 3) {
                if (legs == 3) {
                    // the stem: the arm whose opposite is missing
                    for (int d = 0; d < 4; d++) if (ha[d] > 0 && ha[d ^ 1] <= 0) stop[d] = true;
                } else {
                    int rs = Math.max(rank[i][0], rank[i][1]), rv = Math.max(rank[i][2], rank[i][3]);
                    if (rs == 2 && rv == 2) { allWay = true; for (int d = 0; d < 4; d++) stop[d] = true; }
                    else if (rs < rv) stop[0] = stop[1] = true;
                    else stop[2] = stop[3] = true;
                }
                for (int d = 0; d < 4; d++) if (ha[d] > 0 && kind[i][d] == ENTRY) stop[d] = true;
            }
            for (int d = 0; d < 4; d++) {
                if (ha[d] <= 0) continue;
                double hx = d <= TownPlan.DIR_S_DOWN ? x.haX() : x.haP(), ext = d <= TownPlan.DIR_S_DOWN ? x.extS() : x.extV();
                double reach = hx + TownWorks.RETURN;
                // the crosswalk in line with the crossing street's sidewalk
                double cw = Double.NaN;
                if (main && legs >= 3) {
                    double c = ext - x.sidewalk() / 2, h = Math.max(WALK, x.sidewalk()) / 2;
                    if (c + h + LINE_WALK <= end[i][d] - 0.05 && c - h - LINE_WALK > hx + 0.3) {
                        cw = c + h + LINE_WALK;
                        for (double[] o : new double[][]{{c - h - LINE_WALK, c - h}, {c + h, c + h + LINE_WALK}})
                            band(marks, t, x, i, d, o[0], o[1], -1, 1, false);
                        reach = Math.max(reach, cw);
                    }
                }
                if (stop[d]) {
                    double o = Math.max(hx + TownWorks.RETURN, Double.isNaN(cw) ? 0 : cw) + STOP_GAP;
                    o = Math.min(o, end[i][d] - BAR - 0.05);
                    if (o > hx + 0.3) {
                        int side = side(hand, d);
                        // the approach half: STOP_IN off the centre line to the curb
                        double w0 = STOP_IN, w1 = Math.min(half(t, x, d, o, side), half(t, x, d, o + BAR, side)) - CURB_GAP;
                        if (w1 > w0 + 0.5) {
                            marks.add(quad(false, x, d, o, o + BAR, side * w0, side * w1));
                            stops.add(new Stop(i, d, allWay, o, side, ha[d]));
                            reach = Math.max(reach, o + BAR);
                        }
                    }
                }
                clear[i][d] = reach;
            }
        }
        // the double yellow centre lines of the C14 streets, between the marks at their ends
        for (int k = 0; k < p.streets.size(); k++) {
            if (!c14[k]) continue;
            TownPlan.Street st = p.streets.get(k);
            int ia = st.a(), ib = st.b();
            TownPlan.Node a = p.nodes.get(ia), b = p.nodes.get(ib);
            boolean par = Math.abs(a.v() - b.v()) < 1e-6;
            double la = par ? a.s() : a.v(), lb = par ? b.s() : b.v();
            if (lb < la) { int q = ia; ia = ib; ib = q; double z = la; la = lb; lb = z; }
            int da = par ? TownPlan.DIR_S_UP : TownPlan.DIR_V_UP, db = da ^ 1;
            double from = la + clear[ia][da], to = lb - clear[ib][db];
            if (to - from < 1) continue;
            double line = par ? a.v() : a.s();
            for (int sg : new int[]{-1, 1}) {
                double w0 = line + sg * CENTRE_GAP, w1 = line + sg * (CENTRE_GAP + LINE);
                marks.add(par ? new Mark(true, from, Math.min(w0, w1), to, Math.max(w0, w1)) : new Mark(true, Math.min(w0, w1), from, Math.max(w0, w1), to));
            }
        }
        return new Plan(List.copyOf(marks), List.copyOf(stops));
    }

    /** The lateral side (+1: the positive lateral axis) on the right of the traffic coming in along arm d. */
    static int side(double hand, int d) {
        double[] a = TownPlan.arm(d);
        // travel -a; its right in town coordinates: (-tv, ts) when the axes keep the world's turn (hand > 0)
        double ts = -a[0], tv = -a[1];
        double rs = hand > 0 ? -tv : tv, rv = hand > 0 ? ts : -ts;
        return (int) Math.signum(d <= TownPlan.DIR_S_DOWN ? rv : rs);
    }

    /**
     * The asphalt's half width on side sg of arm d, offset o out from the node: the arm's own, wider over the curb return
     * when the perpendicular arm on that side is there (the return's arc, TownWorks#asphaltDistance).
     */
    static double half(TownWorks.Town t, TownWorks.NodeX x, int d, double o, int sg) {
        double[] ha = x.ha();
        int perp = d <= TownPlan.DIR_S_DOWN ? (sg > 0 ? TownPlan.DIR_V_UP : TownPlan.DIR_V_DOWN) : (sg > 0 ? TownPlan.DIR_S_UP : TownPlan.DIR_S_DOWN);
        // the return starts at the perpendicular arm's own edge (TownWorks#asphaltDistance), not the node box's
        double a = o - ha[perp], r = TownWorks.RETURN;
        if (ha[perp] <= 0 || a <= 0 || a >= r) return ha[d];
        return ha[d] + r - Math.sqrt(r * r - (r - a) * (r - a));
    }

    /** A band across arm d from offset o0 to o1, laterally from the curb on side lo to the curb on side hi (-1, 1). */
    private static void band(List<Mark> out, TownWorks.Town t, TownWorks.NodeX x, int i, int d, double o0, double o1, int lo, int hi, boolean yellow) {
        double wl = Math.min(half(t, x, d, o0, lo), half(t, x, d, o1, lo)) - CURB_GAP, wh = Math.min(half(t, x, d, o0, hi), half(t, x, d, o1, hi)) - CURB_GAP;
        out.add(quad(yellow, x, d, o0, o1, lo * wl, hi * wh));
    }

    /** The mark from offset o0 to o1 along arm d and lateral w0 to w1 (signed on the lateral axis). */
    static Mark quad(boolean yellow, TownWorks.NodeX x, int d, double o0, double o1, double w0, double w1) {
        double[] a = TownPlan.arm(d);
        boolean alongS = d <= TownPlan.DIR_S_DOWN;
        double sg = alongS ? a[0] : a[1];
        double p0 = (alongS ? x.s() : x.v()) + sg * o0, p1 = (alongS ? x.s() : x.v()) + sg * o1;
        double l0 = (alongS ? x.v() : x.s()) + Math.min(w0, w1), l1 = (alongS ? x.v() : x.s()) + Math.max(w0, w1);
        return alongS ? new Mark(yellow, Math.min(p0, p1), l0, Math.max(p0, p1), l1) : new Mark(yellow, l0, Math.min(p0, p1), l1, Math.max(p0, p1));
    }
}
