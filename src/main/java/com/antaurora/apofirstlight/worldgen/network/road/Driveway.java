package com.antaurora.apofirstlight.worldgen.network.road;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The forest home's private gravel driveway (County road G3, 2026-10-18, docs/worldgen/county_road_g3.md; RoadCounty.Home
 * #drive): from the home's node H to the county road's paved edge, joining it from the side (the side join, 2026-10-19,
 * docs/worldgen/county_road_g3_side_join_plan.md 4), a smooth gravel surface drawn as a mesh like the roads' pavement
 * (DriveMesh, user 2026-10-18: "车道改成和县道一样的平滑路面（碎石贴图），边缘不要锯齿"). RoadWorks gives its columns: the
 * collision blocks where a column is all gravel (RoadWorks.Kind.DRIVE), graded turf rounded down under it at its edges, a
 * VERGE wide beyond that blending into the base; the road surface query gives its height. One height function (#height) for
 * all of them. Pure Java.
 * <ul>
 *   <li>the ribbon: HALF each side of the centreline, crowned CROWN, from H to the line across the driveway APRON before
 *   the paved edge (F; the driveway's last DRIVE_STRAIGHT is straight and square to the road, RoadCounty);</li>
 *   <li>the apron (#joined) from F to the paved edge: in the frame of F (a along the straight toward the road, v across
 *   it, left +) the paved edge lies D(v) on (a table every APRON_STEP); the gravel spans |v| up to hw(t), t = APRON (1 - a /
 *   D(v)) the way still to the edge: HALF, widening in a fillet of radius FILLET onto the edge (HALF + FILLET there,
 *   tangent to it); its height blends from the driveway's datum and crown at F into the pavement's edge height (its cross
 *   slope carried on beyond the edge) at the edge, smooth in t, so the gravel meets the asphalt without a step; beyond the
 *   edge the road's (the mesh tucks the gravel UNDER under the asphalt, UNDER_DROP down);</li>
 *   <li>the pads (Main 2026-10-19: the farm gate and the mailbox stand on whole blocks): the datum runs level at the
 *   gate's whole Y over +- RoadCounty.GATE_FLAT (RoadCounty#drive), where the crown fades out (over GATE_BLEND beyond)
 *   and the gravel rides PAD_LIFT over Y (over the pads' turf tops, never coplanar with them); the block columns the
 *   gate's box (GATE_ALONG along, GATE_ACROSS across its centre) overlaps, the mailbox's column and its four neighbours
 *   (not those reaching within MAILBOX_KEEP of the paved edge) are pads: RoadWorks#column levels their turf at Y
 *   (#padY).</li>
 * </ul>
 */
public final class Driveway {
    /** Half width of the gravel, the verge's width beyond it, m; the gravel's crown (cross slope each way). */
    public static final double HALF = RoadCounty.DRIVE_HALF, VERGE = 2.5, CROWN = 0.02;
    public static final Driveway EMPTY = new Driveway(new double[0]);
    /** The apron: its length before the paved edge, its fillets' radius, its D(v) table's step and reach across, m. */
    static final double APRON = RoadCounty.APRON, FILLET = RoadCounty.APRON_R, APRON_STEP = 0.05, APRON_REACH = HALF + FILLET + VERGE + 1.5;
    /** The mesh tucks the gravel this far under the asphalt past the paved edge, this far below it, m. */
    static final double UNDER = 0.3, UNDER_DROP = 0.01;
    /**
     * The apron's height runs on this far past the paved edge (the collision and the surface query: the edge from the
     * table and the road's own test differ by micrometres, a sliver neither would claim), m.
     */
    public static final double JOIN = 0.02;
    /** The gravel rides this far over a pad's whole Y, m. */
    public static final double PAD_LIFT = 0.01;

    /** All datum points {x, z, y} along the centreline to the paved edge, and their distance from it. */
    private final double[] all;
    private final double[] allToEnd;
    /** The ribbon's points (to F when joined: the line APRON before the edge), their distance from the edge. */
    private final double[] p;
    private final int n;
    private final double[] toEnd;
    /** The apron (null: not joined): the county road, its side of the driveway, F, the way in (unit, toward the road), the way across (left of it). */
    private final RoadDesign road;
    private final int side;
    private final double fx, fz, ix, iz, tx, tz;
    /** D(v), the paved edge's height and its outward slope there, v = -APRON_REACH .. APRON_REACH every APRON_STEP. */
    private final double[] reach, edgeY, edgeSlope;
    private final Set<Long> chunks = new HashSet<>();
    /** The gate {x, y, z, way x, way z} and the mailbox {x, y, z, ...} (null: none), the gate's distance from the paved edge (NaN: none). */
    private final double[] gate, mailbox;
    private final double padTo;
    /** The pads' block columns (#key) and their whole Y. */
    private final Map<Long, Integer> pads = new HashMap<>();

    public Driveway(double[] points) { this(points, null, null, null); }

    /**
     * The driveway of a road network JSON's home (RoadCounty#read), EMPTY without one: the server's works and the
     * client's copy (RoadClient#receive: the mesh, the surface query) build it the same way.
     */
    public static Driveway of(RoadCounty.Home home) {
        return home == null || home.drive() == null || home.drive().length < 6 ? EMPTY : new Driveway(home.drive(), null, home.gate(), home.mailbox());
    }

    private Driveway(double[] points, RoadDesign d, double[] gate, double[] mailbox) {
        this.all = points.clone();
        int na = all.length / 3;
        this.allToEnd = new double[na];
        for (int i = na - 2; i >= 0; i--) allToEnd[i] = allToEnd[i + 1] + Math.hypot(all[3 * i + 3] - all[3 * i], all[3 * i + 4] - all[3 * i + 1]);
        // the apron needs the straight's last APRON and a ribbon before it
        boolean apron = d != null && na >= 2 && allToEnd[0] > APRON + 1;
        double[] fp = null;
        int s = 0;
        if (apron) {
            fp = at(APRON);
            double ex = all[3 * na - 3], ez = all[3 * na - 2], l = Math.hypot(ex - fp[0], ez - fp[1]);
            double[] le = d.local(ex, ez, d.section.edge + 10);
            if (l < 1e-6 || le == null) apron = false;
            else s = le[1] >= 0 ? 1 : -1;
        }
        if (!apron) {
            this.p = all;
            this.toEnd = allToEnd;
            this.road = null;
            this.side = 0;
            this.fx = this.fz = this.ix = this.iz = this.tx = this.tz = 0;
            this.reach = this.edgeY = this.edgeSlope = null;
        } else {
            int k = 0;
            while (k < na && allToEnd[k] > APRON + 1e-6) k++;
            this.p = new double[3 * (k + 1)];
            this.toEnd = new double[k + 1];
            System.arraycopy(all, 0, p, 0, 3 * k);
            System.arraycopy(allToEnd, 0, toEnd, 0, k);
            p[3 * k] = fp[0];
            p[3 * k + 1] = fp[1];
            p[3 * k + 2] = fp[2];
            toEnd[k] = APRON;
            this.road = d;
            this.side = s;
            this.fx = fp[0];
            this.fz = fp[1];
            double ex = all[3 * na - 3], ez = all[3 * na - 2], l = Math.hypot(ex - fx, ez - fz);
            this.ix = (ex - fx) / l;
            this.iz = (ez - fz) / l;
            this.tx = -iz;
            this.tz = ix;
            int m = (int) Math.round(APRON_REACH / APRON_STEP);
            reach = new double[2 * m + 1];
            edgeY = new double[2 * m + 1];
            edgeSlope = new double[2 * m + 1];
            double edge = d.section.edge;
            for (int j = -m; j <= m; j++) {
                double v = j * APRON_STEP, ox = fx + tx * v, oz = fz + tz * v;
                // the paved edge along the way in: bisect side * u - edge (falling toward the road)
                double lo = 0, hi = APRON + 6;
                for (int it = 0; it < 40; it++) {
                    double mid = (lo + hi) / 2;
                    double[] q = d.local(ox + ix * mid, oz + iz * mid, edge + APRON + 20);
                    if (q != null && s * q[1] - edge > 0) lo = mid;
                    else hi = mid;
                }
                double dv = (lo + hi) / 2;
                double[] q = d.local(ox + ix * dv, oz + iz * dv, edge + APRON + 20);
                double sg = q == null ? 0 : Math.max(0, Math.min(d.length, q[0]));
                double e0 = d.pavementY(sg, s * edge), e1 = d.pavementY(sg, s * (edge - 0.3));
                reach[j + m] = dv;
                edgeY[j + m] = e0;
                edgeSlope[j + m] = (e0 - e1) / 0.3;
            }
        }
        this.n = p.length / 3;
        double r = HALF + FILLET + VERGE + 1;
        for (int i = 0; i < na; i++) {
            double x = all[3 * i], z = all[3 * i + 1];
            for (int cz = (int) Math.floor((z - r) / 16); cz <= (int) Math.floor((z + r) / 16); cz++)
                for (int cx = (int) Math.floor((x - r) / 16); cx <= (int) Math.floor((x + r) / 16); cx++) chunks.add(key(cx, cz));
        }
        this.gate = gate;
        this.mailbox = mailbox;
        this.padTo = gate == null || na < 2 ? Double.NaN : Math.hypot(gate[0] - all[3 * na - 3], gate[2] - all[3 * na - 2]);
        if (gate != null) {
            int gy = (int) Math.round(gate[1]);
            double ha = RoadCounty.GATE_ALONG, hv = RoadCounty.GATE_ACROSS, ux = gate[3], uz = gate[4], rr = ha + hv + 2;
            for (int cz = (int) Math.floor(gate[2] - rr); cz <= (int) Math.floor(gate[2] + rr); cz++)
                for (int cx = (int) Math.floor(gate[0] - rr); cx <= (int) Math.floor(gate[0] + rr); cx++)
                    if (overlaps(cx, cz, gate[0], gate[2], ux, uz, ha, hv)) pads.put(key(cx, cz), gy);
        }
        // the mailbox's pad beside the road (only joined: its neighbours by the pavement keep the road's turf)
        if (mailbox != null && d != null) {
            int mx = (int) Math.floor(mailbox[0]), mz = (int) Math.floor(mailbox[2]), my = (int) Math.round(mailbox[1]);
            int[][] around = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] o : around) {
                int cx = mx + o[0], cz = mz + o[1];
                boolean byRoad = false;
                for (int k = 0; k < 4 && !byRoad; k++) {
                    double[] q = d.local(cx + (k & 1), cz + (k >> 1), d.section.edge + 10);
                    byRoad = q != null && Math.abs(q[1]) <= d.section.edge + RoadCounty.MAILBOX_KEEP;
                }
                if (!byRoad || o[0] == 0 && o[1] == 0) pads.putIfAbsent(key(cx, cz), my);
            }
        }
    }

    /**
     * Whether block column (cx, cz) overlaps the box centred on (gx, gz), ha along the unit way (ux, uz) and hv across it
     * (separating axes; touching does not count).
     */
    private static boolean overlaps(int cx, int cz, double gx, double gz, double ux, double uz, double ha, double hv) {
        double ex = Math.abs(ux) * ha + Math.abs(uz) * hv, ez = Math.abs(uz) * ha + Math.abs(ux) * hv;
        if (gx + ex <= cx || gx - ex >= cx + 1 || gz + ez <= cz || gz - ez >= cz + 1) return false;
        double dx = cx + 0.5 - gx, dz = cz + 0.5 - gz, hs = 0.5 * (Math.abs(ux) + Math.abs(uz));
        return Math.abs(dx * ux + dz * uz) < ha + hs && Math.abs(-dx * uz + dz * ux) < hv + hs;
    }

    /** The whole Y of a pad block column (cx, cz), or Integer.MIN_VALUE. */
    public int padCell(int cx, int cz) {
        Integer y = pads.get(key(cx, cz));
        return y == null ? Integer.MIN_VALUE : y;
    }

    /** The whole Y of corner (x, z) when one of the four columns sharing it is a pad, else Integer.MIN_VALUE. */
    public int padY(int x, int z) {
        if (pads.isEmpty()) return Integer.MIN_VALUE;
        for (int k = 0; k < 4; k++) {
            int y = padCell(x - 1 + (k & 1), z - 1 + (k >> 1));
            if (y != Integer.MIN_VALUE) return y;
        }
        return Integer.MIN_VALUE;
    }

    /** Whether any column within one of (cx, cz) is a pad (its corners may be pads' corners). */
    public boolean nearPad(int cx, int cz) {
        if (pads.isEmpty()) return false;
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) if (pads.containsKey(key(cx + dx, cz + dz))) return true;
        return false;
    }

    /** How much of the gate's level stretch holds at toEnd from the paved edge: 1 within GATE_FLAT, fading over GATE_BLEND. */
    double padWeight(double toEnd) {
        if (Double.isNaN(padTo)) return 0;
        double e = Math.abs(toEnd - padTo) - RoadCounty.GATE_FLAT;
        return e <= 0 ? 1 : e >= RoadCounty.GATE_BLEND ? 0 : 1 - CrossSection.smooth(e / RoadCounty.GATE_BLEND);
    }

    /** The crown at toEnd (none on the gate's level stretch). */
    double crown(double toEnd) { return CROWN * (1 - padWeight(toEnd)); }

    /** The gravel's lift over the datum at toEnd (PAD_LIFT on the gate's level stretch). */
    double lift(double toEnd) { return PAD_LIFT * padWeight(toEnd); }

    /** The distances from the paved edge where the gate's level stretch and its blends end (none without a gate). */
    double[] padBreaks() {
        if (Double.isNaN(padTo)) return new double[0];
        double f = RoadCounty.GATE_FLAT, b = f + RoadCounty.GATE_BLEND;
        return new double[]{padTo - b, padTo - f, padTo + f, padTo + b};
    }

    /** The point {x, z, datum} this far from the paved edge on the full centreline. */
    private double[] at(double d) {
        int na = all.length / 3;
        for (int i = 0; i + 1 < na; i++) {
            if (allToEnd[i + 1] > d) continue;
            double l = allToEnd[i] - allToEnd[i + 1], f = l < 1e-9 ? 0 : (allToEnd[i] - d) / l;
            f = Math.max(0, Math.min(1, f));
            return new double[]{all[3 * i] + (all[3 * i + 3] - all[3 * i]) * f, all[3 * i + 1] + (all[3 * i + 4] - all[3 * i + 1]) * f,
                    all[3 * i + 2] + (all[3 * i + 5] - all[3 * i + 2]) * f};
        }
        return new double[]{all[0], all[1], all[2]};
    }

    /**
     * This driveway joined to its county road d (its last point on d's paved edge): the apron (the class comment).
     * Unchanged when d is null.
     */
    public Driveway joined(RoadDesign d) {
        if (isEmpty() || d == null) return this;
        return new Driveway(all, d, gate, mailbox);
    }

    /** The way in at F (toward the road, the ribbon's end normal), or null when not joined. */
    double[] endWay() { return road == null ? null : new double[]{ix, iz}; }

    private static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xffffffffL); }

    public boolean isEmpty() { return all.length / 3 < 2; }

    public boolean touches(int cx, int cz) { return chunks.contains(key(cx, cz)); }

    /** Whether the apron is there (#joined). */
    boolean joinedApron() { return road != null; }

    /** The ribbon's number of datum points, their position and datum, their distance from the paved edge (DriveMesh). */
    int size() { return n; }

    double x(int i) { return p[3 * i]; }

    double z(int i) { return p[3 * i + 1]; }

    double y(int i) { return p[3 * i + 2]; }

    double toEnd(int i) { return toEnd[i]; }

    /**
     * {distance across from the centreline, datum, gravel half width there, signed lateral offset (left of the way toward
     * the road: +), distance from the paved edge, the gravel's height at its edge on that side, the gravel's height here
     * (beyond the half width: as if it ran on)} of (x, z): on the ribbon the nearest point of the centreline whose foot lies
     * on it (not beyond either end; when joined not past F's line), on the apron its frame (the class comment); null off
     * both and past the paved edge.
     */
    public double[] near(double x, double z) {
        if (road != null) {
            // the apron's box past F's line (elsewhere the ribbon's: the driveway may wind past that line far off)
            double a = (x - fx) * ix + (z - fz) * iz, v = (x - fx) * tx + (z - fz) * tz;
            if (a > 0 && Math.abs(v) <= APRON_REACH) return apron(a, v);
        }
        double best = Double.POSITIVE_INFINITY, h = 0, along = 0, side = 0;
        for (int i = 0; i + 1 < n; i++) {
            double ax = p[3 * i], az = p[3 * i + 1], bx = p[3 * i + 3], bz = p[3 * i + 4];
            double dx = bx - ax, dz = bz - az, l2 = dx * dx + dz * dz;
            if (l2 < 1e-12) continue;
            double l = Math.sqrt(l2), t = ((x - ax) * dx + (z - az) * dz) / l2;
            // beyond the first segment's free end, beyond the last's (when joined the apron's)
            if (i == 0 && t < 0) continue;
            if (i == n - 2 && t > 1) continue;
            double tc = Math.max(0, Math.min(1, t));
            double d = Math.hypot(ax + dx * tc - x, az + dz * tc - z);
            if (d < best) {
                best = d;
                h = p[3 * i + 2] + (p[3 * i + 5] - p[3 * i + 2]) * tc;
                along = toEnd[i] - l * tc;
                side = (x - ax) * -dz + (z - az) * dx >= 0 ? 1 : -1;
            }
        }
        if (best == Double.POSITIVE_INFINITY) return null;
        double cr = crown(along), lift = lift(along);
        return new double[]{best, h, HALF, side * best, along, h + lift - cr * HALF, h + lift - cr * best};
    }

    /** #near on the apron at a along the way in from F, v across (null past the paved edge). */
    private double[] apron(double a, double v) {
        double dv = reach(v);
        if (a > dv + JOIN) return null;
        double t = APRON * (1 - a / dv), hw = width(t), datum = at(Math.max(0, APRON - a))[2];
        return new double[]{Math.abs(v), datum, hw, v, Math.max(0, APRON - a), apronY(a, Math.copySign(hw, v)), apronY(a, v)};
    }

    /** The gravel's half width t from the paved edge on the apron: HALF, widening in the fillet to HALF + FILLET at the edge. */
    static double width(double t) {
        if (t >= FILLET) return HALF;
        double c = FILLET - Math.max(0, t);
        return HALF + FILLET - Math.sqrt(Math.max(0, FILLET * FILLET - c * c));
    }

    /** The table at v (linear, clamped). */
    private static double table(double[] tab, double v) {
        int m = (tab.length - 1) / 2;
        double f = Math.max(-m, Math.min(m, v / APRON_STEP)) + m;
        int k = Math.min(tab.length - 2, (int) Math.floor(f));
        return tab[k] + (tab[k + 1] - tab[k]) * (f - k);
    }

    /** D(v): the paved edge's distance from F's line along the way in at v across. */
    double reach(double v) { return table(reach, v); }

    /** The apron's height at a along, v across: the driveway's datum and crown at F blended into the pavement's edge (the class comment). */
    double apronY(double a, double v) {
        double dv = reach(v), t = Math.max(0, APRON * (1 - a / dv)), w = CrossSection.smooth(Math.min(1, t / APRON));
        double ry = table(edgeY, v) + table(edgeSlope, v) * (dv - a);
        double dy = at(Math.max(0, APRON - a))[2] - CROWN * Math.abs(v);
        return ry + (dy - ry) * w;
    }

    /** The world point {x, z} at a along the way in from F, v across. */
    double[] apronPoint(double a, double v) { return new double[]{fx + ix * a + tx * v, fz + iz * a + tz * v}; }

    /** The along coordinate of F from H (the apron's texture continues the ribbon's), m. */
    double alongF() { return allToEnd[0] - APRON; }

    /** The pavement's height at (x, z) under which the mesh tucks the gravel (the county road's, less UNDER_DROP). */
    double underY(double x, double z) {
        double[] q = road.local(x, z, road.section.edge + 10);
        double s = q == null ? 0 : Math.max(0, Math.min(road.length, q[0]));
        double u = q == null ? side * road.section.edge : Math.copySign(Math.min(Math.abs(q[1]), road.section.edge), q[1]);
        return road.pavementY(s, u) - UNDER_DROP;
    }

    /** The gravel's height at (x, z), NaN off it. */
    public double height(double x, double z) {
        if (isEmpty()) return Double.NaN;
        double[] q = near(x, z);
        return q == null || q[0] > q[2] ? Double.NaN : q[6];
    }

    /** The gravel's height at its edge on the side of (x, z) (a near() result): where the verge starts. */
    double edge(double[] q) { return q[5]; }

    /** Whether (x, z) lies within the gravel and its verge grown by extra. */
    public boolean clear(double x, double z, double extra) {
        if (isEmpty()) return false;
        double[] q = near(x, z);
        return q != null && q[0] <= q[2] + VERGE + extra;
    }
}
