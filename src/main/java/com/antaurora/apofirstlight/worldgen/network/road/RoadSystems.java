package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson.Node;
import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson.RouteData;
import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson.Structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static com.antaurora.apofirstlight.worldgen.network.road.RoadInterchanges.*;

/**
 * The system interchanges of the built network (C3.3b group 3; docs/worldgen/federal_systems_c3_3b.md), at build time
 * (RoadNetworkBuild, before the diamonds): an interstate Q whose end lies on another interstate H's centreline (the
 * planner's join) becomes a trumpet. In the crossing's frame (X along H, toward where the far carriageway's traffic goes;
 * Y off H toward Q):
 * <ul>
 *   <li>Q ends (an open end, RoadDesign#openEnd) STEMS m off H's centreline; each carriageway runs on as two single-lane
 *   ramps (CrossSection.RAMP) side by side on its two lanes' centres: the inner lane as the left-turn ramp ("left-in",
 *   "left-out"), the outer lane as the direct right-turn ramp ("right-in", "right-out": CSC curves of R_DIRECT), which
 *   splits from / merges into the left-turn ramp (attached to it: RoadWorks' marks on a one-way host);</li>
 *   <li>the left turns cross H on two overpasses along H's normal at the crossing: the loop (R_LOOP through about 270
 *   degrees, its leg LEG off the normal) for the minor one, the semi-direct ramp round the loop's outside (R_OUTER, LOOP_GAP
 *   off it, its leg on the outer circle's tangent) for the major one: Q's traffic toward, and from, the town Q's corridor
 *   runs on to along H (the other join of its corridor on H, else its town). The loop exits before, or enters after, the
 *   semi-direct (no weaving);</li>
 *   <li>on H the ramps leave and join like the diamonds' (RoadInterchanges: the 1:TAPER exit taper to W_LEAD; from W_LEAD
 *   the entrance approach at 1:TAPER, the acceleration lane and the merge taper), lying in H's shoulder plane while attached
 *   (RoadInterchanges#hExt); a left-turn ramp's open end lies in Q's lane plane;</li>
 *   <li>profiles: vertical curves of RAMP_K_DESIGN (RAMP_K where too short) round straight grades between the attached
 *   stretches, the open ends and the overpasses' crests (grade 0 over H's centreline, raised until their soffits keep
 *   CLEARANCE over H's pavement and its shoulder plane carried out to UNDER: the loop's lead runs under both decks); at
 *   most RAMP_GRADE, BRIDGE_GRADE on the decks;</li>
 *   <li>the loop is built in two pieces (~1, ~2) joined by open ends halfway between its nose on H and its deck's lead
 *   (it passes over its own taper on H);
 *   <li>the direct ramps are built in two pieces (~1, ~2) joined by open ends, one attached to H, the other to the
 *   left-turn ramp;</li>
 *   <li>off H: a direct ramp's curve never comes nearer H than its taper's lead (#clearOf: where H bends toward the ramp,
 *   or the turn needs more room than the stem, a curve would run back across H), and every ramp design keeps its paved
 *   band GAP off H's at H's level once clear of it (#offH; on a deck it passes over);</li>
 *   <li>a site is tried at stems STEMS and crossings shifted along H by SHIFT_STEP; it is skipped (Q stays as the planner
 *   left it: trimmed short of H) where H has a structure under its attached stretches or the crossing, Q a bridge or
 *   tunnel in what is cut away, another system interchange lies within SPACING, or other roads come within
 *   RoadInterchanges.OTHERS;</li>
 *   <li>zones: the new roads, Q and H ignore each other's conflicts; the claim on H keeps the diamonds SPACING off
 *   (RoadInterchanges#footprint).</li>
 * </ul>
 */
final class RoadSystems {
    private RoadSystems() {}

    /** Q's end within TOUCH of H's centreline, its crossing END_CLEAR from H's ends; joins flatter than MIN_ANGLE are skipped. */
    static final double TOUCH = 10, END_CLEAR = 1500, MIN_ANGLE = 50;
    /** Q's end (its centreline) this far off H's centreline, tried in turn, m. */
    static final double[] STEMS = {360, 400, 450, 520, 600};
    /** The crossing shifted along H from the join by up to SHIFTS steps of SHIFT_STEP each way, m. */
    static final double SHIFT_STEP = 25;
    static final int SHIFTS = 4;
    /** Radii: the direct ramps (70 km/h), the loop (50 km/h), the semi-direct round it and its curve onto H, m. */
    static final double R_DIRECT = 314, R_LOOP = 85, LOOP_GAP = 40, R_OUTER = R_LOOP + LOOP_GAP, R_SEMI = 320;
    /** The left-turn ramps' curves between Q's end and the overpasses, the largest that fits, m. */
    static final double[] R_STEM = {900, 700, 550, 450};
    /**
     * At Q's end: the left-turn ramp on the inner lane's centre, the direct ramp one lane width outside it; the loop's leg
     * off the crossing's normal, m.
     */
    static final double LANE = CrossSection.R4.crown + CrossSection.R4.lane / 2, SPLIT_W = CrossSection.R4.lane, LEG = 5.5;
    /** The decks' straight lead beyond their spans; the straights at Q's end and at the direct ramps' split; the least straight of a CSC, m. */
    static final double LEAD = 40, STEM_STRAIGHT = 20, DIRECT_STRAIGHT = 20, MIN_STRAIGHT = 10;
    /** The exit taper's end and the entrance approach's start off H's centreline (the loop's lead), m. */
    static final double W_LEAD = W0 + TAPER_RUN / TAPER;
    /** Under a deck: H's pavement and its shoulder plane carried out to this |u|, m. */
    static final double UNDER = 31;
    static final double BRIDGE_GRADE = 0.05;
    /** The least spacing of a system interchange's crossing from another interchange's on its host, m. */
    static final double SPACING = 1600;
    /** The direct ramps' two pieces join this far from either attached stretch at least, m. */
    static final double SPLIT_MIN = 40;
    /**
     * Control point spacing: about R / POINT_PER_R of the path's least radius, POINT_MIN .. POINT_MAX (the stored points are
     * rounded to the cm, which moves a 10 m spline's curvature by up to 0.02 / h^2: 6 % of R 314's); the dense paths' step, m.
     */
    static final double POINT_PER_R = 16, POINT_MIN = 10, POINT_MAX = 20, STEP = 0.5;
    /** Two of a site's roads keep their paved bands GAP apart where their pavements lie within CLEARANCE + RAMP_DECK of each other; Q's end region, m. */
    static final double GAP = 1, END_REGION = 80;
    /** A direct ramp's curve may come this much nearer H than its taper's lead (#clearOf), m. */
    static final double H_TOL = 0.5;
    private static final double DS = RoadProfile.DS, T0 = Math.atan(1 / TAPER);

    /** A system interchange's claim on its host: the crossing and the stations its ramps take. */
    record Claim(String host, double sc, double s0, double s1) {}

    record Result(List<RouteData> roads, List<RoadNetworkBuild.Zone> zones, List<Claim> claims) {}

    private record Interstate(RouteData route, RoadCurve curve, RoadDesign design, double length) {
        static Interstate of(RouteData r) {
            RoadCurve c = new RoadCurve(r.xs(), r.zs());
            return new Interstate(r, c, RoadNetworkJson.design(r), Math.min(c.length, (r.profile().length - 1) * DS));
        }
    }

    /** A join: interstate q's end (atEnd) on interstate h at station rc, q coming from h's side sigma, at angle (deg). */
    private record Site(int q, boolean atEnd, int h, double rc, int sigma, double angle) {}

    /** A built site. */
    private record Built(RouteData q, List<RouteData> ramps, List<RoadNetworkBuild.Zone> zones, Claim claim, String line, List<String> lines) {}

    // ------------------------------------------------------------------ sites

    static Result build(List<RouteData> roads, List<Node> nodes, List<String> report) {
        List<RouteData> out = new ArrayList<>(roads);
        if (Boolean.getBoolean("afl.noSystems")) return new Result(out, List.of(), List.of());
        List<Site> sites = new ArrayList<>();
        for (int qi = 0; qi < out.size(); qi++) {
            RouteData q = out.get(qi);
            if (!q.roadClass().equals("interstate")) continue;
            Interstate iq = Interstate.of(q);
            int n = q.xs().length;
            for (boolean atEnd : new boolean[]{false, true}) {
                double px = atEnd ? q.xs()[n - 1] : q.xs()[0], pz = atEnd ? q.zs()[n - 1] : q.zs()[0];
                for (int hi = 0; hi < out.size(); hi++) {
                    RouteData h = out.get(hi);
                    if (hi == qi || !h.roadClass().equals("interstate") || same(RoadNames.corridor(h.id()), RoadNames.corridor(q.id()))) continue;
                    RoadCurve hc = new RoadCurve(h.xs(), h.zs());
                    double[] l = hc.local(px, pz, TOUCH, 0);
                    if (l == null) continue;
                    double[] back = iq.curve.point(atEnd ? iq.length - 200 : 200, 0), lb = hc.local(back[0], back[1], 600, 0);
                    if (lb == null) continue;
                    double[] fq = new double[5], fh = new double[5];
                    iq.curve.frame(atEnd ? iq.length : 0, fq);
                    hc.frame(l[0], fh);
                    double angle = Math.toDegrees(Math.acos(Math.min(1, Math.abs(fq[2] * fh[2] + fq[3] * fh[3]))));
                    sites.add(new Site(qi, atEnd, hi, l[0], lb[1] > 0 ? 1 : -1, angle));
                }
            }
        }
        sites.sort(Comparator.comparing((Site s) -> out.get(s.h).id()).thenComparingDouble(Site::rc));
        List<RoadNetworkBuild.Zone> zones = new ArrayList<>();
        List<Claim> claims = new ArrayList<>();
        List<RouteData> added = new ArrayList<>();
        int built = 0;
        for (Site st : sites) {
            RouteData q = out.get(st.q), h = out.get(st.h);
            String what = String.format(Locale.ROOT, "%s (its %s) on %s s %.0f, %.0f deg", q.id(), st.atEnd ? "end" : "start", h.id(), st.rc, st.angle);
            String skip = null;
            Interstate ih = Interstate.of(h);
            if (st.angle < MIN_ANGLE) skip = String.format(Locale.ROOT, "the join is flatter than %.0f deg", MIN_ANGLE);
            else if (st.rc < END_CLEAR || st.rc > ih.length - END_CLEAR) skip = "too near the host's end";
            for (Claim c : claims) if (skip == null && c.host.equals(h.id()) && Math.abs(c.sc - st.rc) < SPACING)
                skip = String.format(Locale.ROOT, "within %.0f m of the system interchange at s %.0f", SPACING, c.sc);
            int tau = skip == null ? direction(out, sites, st, ih, nodes) : 0;
            if (skip == null && tau == 0) skip = "no direction along the host (no other join of its corridor, its town not found)";
            if (skip != null) {
                report.add("system interchange skipped: " + what + ": " + skip);
                continue;
            }
            Built b = null;
            List<String> why = new ArrayList<>();
            // each stem's crossing: under Q's centreline where it is halfway between its end and the decks' leads (a skewed
            // join's left-turn ramps bend once to cross square), shifted from there
            Interstate iq = Interstate.of(q);
            double[] natural = new double[STEMS.length];
            for (int j = 0; j < STEMS.length; j++) natural[j] = natural(iq, st.atEnd, ih, st.sigma, (STEMS[j] + SPAN + LEAD) / 2, st.rc);
            for (int k = 0; k <= 2 * SHIFTS && b == null; k++) {
                double shift = (k % 2 == 1 ? 1 : -1) * ((k + 1) / 2) * SHIFT_STEP;
                for (int j = 0; j < STEMS.length; j++) {
                    double stem = STEMS[j], sc = Math.round(natural[j] + shift);
                    String[] w = new String[1];
                    b = site(out, added, st, q, ih, tau, sc, stem, w);
                    if (b != null) break;
                    String reason = w[0] == null ? "?" : w[0];
                    if (Boolean.getBoolean("afl.debugSystems")) System.out.printf(Locale.ROOT, "[systems] %s s %.0f stem %.0f: %s%n", h.id(), sc, stem, reason);
                    if (why.stream().noneMatch(x -> x.endsWith(reason))) why.add(String.format(Locale.ROOT, "%+.0f / stem %.0f: %s", sc - st.rc, stem, reason));
                }
            }
            if (b == null) {
                report.add("system interchange skipped: " + what + ": " + String.join("; ", why));
                continue;
            }
            out.set(st.q, b.q);
            added.addAll(b.ramps);
            zones.addAll(b.zones);
            claims.add(b.claim);
            built++;
            report.add("system interchange: " + what + ": " + b.line);
            for (String line : b.lines) report.add("  " + line);
        }
        List<RouteData> result = new ArrayList<>(out);
        result.addAll(added);
        report.add(String.format(Locale.ROOT, "system interchanges: %d built of %d sites", built, sites.size()));
        return new Result(result, zones, claims);
    }

    private static boolean same(String a, String b) { return a != null && a.equals(b); }

    /** The host station under q's centreline where it lies y off h's (on q's side sigma), searched back from its end on h; rc where it never does. */
    private static double natural(Interstate q, boolean atEnd, Interstate h, int sigma, double y, double rc) {
        for (double d = 0; d < q.length; d += DS) {
            double[] p = q.curve.point(atEnd ? q.length - d : d, 0), l = h.curve.local(p[0], p[1], y + 200, 0);
            if (l != null && sigma * l[1] >= y) return l[0];
        }
        return rc;
    }

    /**
     * The main movement's direction along h (tau: +1 toward rising stations): toward the nearest other join of q's
     * corridor on h, else toward q's town at that end (its node projected onto h); 0: neither.
     */
    private static int direction(List<RouteData> out, List<Site> sites, Site st, Interstate h, List<Node> nodes) {
        String corridor = RoadNames.corridor(out.get(st.q).id());
        double best = Double.POSITIVE_INFINITY;
        int tau = 0;
        for (Site o : sites)
            if (o != st && o.h == st.h && same(RoadNames.corridor(out.get(o.q).id()), corridor) && Math.abs(o.rc - st.rc) < best) {
                best = Math.abs(o.rc - st.rc);
                tau = o.rc > st.rc ? 1 : -1;
            }
        if (tau != 0) return tau;
        RouteData q = out.get(st.q);
        String town = st.atEnd ? q.to() : q.from();
        for (Node n : nodes) {
            if (!n.id().equals(town)) continue;
            double[] l = h.curve.local(n.x(), n.z(), 4000, 0);
            if (l != null && Math.abs(l[0] - st.rc) > 1) return l[0] > st.rc ? 1 : -1;
        }
        return 0;
    }

    // ------------------------------------------------------------------ geometry helpers

    /** A pose in the world plane: heading th = atan2(dz, dx) (a right turn increases it). */
    private record Pose(double x, double z, double th) {
        double[] p() { return new double[]{x, z}; }

        Pose ahead(double d) { return new Pose(x + Math.cos(th) * d, z + Math.sin(th) * d, th); }
    }

    private static double norm(double a) {
        double t = a % (2 * Math.PI);
        if (t < 0) t += 2 * Math.PI;
        return t > 2 * Math.PI - 1e-9 ? 0 : t;
    }

    private static double wrap(double a) { return Math.atan2(Math.sin(a), Math.cos(a)); }

    /** A dense polyline being built (every STEP m) and its end pose. */
    private static final class Path {
        final List<double[]> pts = new ArrayList<>();
        /** The index ranges [from, to) of pts that follow a host's frame (#add: a taper, an entrance). */
        final List<int[]> follow = new ArrayList<>();
        double x, z, th, minR = Double.POSITIVE_INFINITY;

        Path(Pose p) {
            x = p.x;
            z = p.z;
            th = p.th;
            pts.add(new double[]{x, z});
        }

        Pose pose() { return new Pose(x, z, th); }

        void straight(double len) {
            if (len <= 1e-9) return;
            int n = Math.max(1, (int) Math.ceil(len / STEP));
            double x0 = x, z0 = z;
            for (int i = 1; i <= n; i++) pts.add(new double[]{x0 + Math.cos(th) * len * i / n, z0 + Math.sin(th) * len * i / n});
            x = x0 + Math.cos(th) * len;
            z = z0 + Math.sin(th) * len;
        }

        /** An arc of radius r turning d (1 right, -1 left) by a >= 0. */
        void arc(double r, int d, double a) {
            if (a <= 1e-12) return;
            minR = Math.min(minR, r);
            double cx = x - r * d * Math.sin(th), cz = z + r * d * Math.cos(th);
            int n = Math.max(1, (int) Math.ceil(r * a / STEP));
            double h0 = th;
            for (int i = 1; i <= n; i++) {
                double h = h0 + d * a * i / n;
                pts.add(new double[]{cx + r * d * Math.sin(h), cz - r * d * Math.cos(h)});
            }
            th = h0 + d * a;
            x = cx + r * d * Math.sin(th);
            z = cz - r * d * Math.cos(th);
        }

        /** Points continuing the path (the first dropped where it repeats the end); the pose then as given. */
        void add(List<double[]> more, Pose end) {
            int i0 = pts.size();
            for (int i = 0; i < more.size(); i++) {
                double[] p = more.get(i);
                if (i == 0 && Math.hypot(p[0] - x, p[1] - z) < 1e-6) continue;
                pts.add(p);
            }
            follow.add(new int[]{i0, pts.size()});
            x = end.x;
            z = end.z;
            th = end.th;
        }
    }

    /** A CSC path from pose a: an arc of r1 turning d1 by a1, a straight l, an arc of r2 turning d2 by a2. */
    private record Csc(double r1, int d1, double a1, double l, double r2, int d2, double a2) {
        double length() { return r1 * a1 + l + r2 * a2; }

        void draw(Path p) {
            p.arc(r1, d1, a1);
            p.straight(l);
            p.arc(r2, d2, a2);
        }
    }

    /** The CSC from a to b with turns d1, d2 (1 right, -1 left) and radii r1, r2; null where the circles leave none. */
    private static Csc csc(Pose a, Pose b, double r1, int d1, double r2, int d2) {
        double c1x = a.x - r1 * d1 * Math.sin(a.th), c1z = a.z + r1 * d1 * Math.cos(a.th);
        double c2x = b.x - r2 * d2 * Math.sin(b.th), c2z = b.z + r2 * d2 * Math.cos(b.th);
        double dx = c2x - c1x, dz = c2z - c1z, dl = Math.hypot(dx, dz), c = r2 * d2 - r1 * d1;
        if (dl < 1e-9 || Math.abs(c) > dl) return null;
        double phi = Math.atan2(dz, dx), ts = phi - Math.asin(c / dl);
        return new Csc(r1, d1, norm(d1 * (ts - a.th)), dl * Math.cos(phi - ts), r2, d2, norm(d2 * (b.th - ts)));
    }

    /** The shortest CSC of radius r from a to b with both arcs under maxArc (any of the four words), or null. */
    private static Csc bestCsc(Pose a, Pose b, double r, double maxArc) {
        Csc best = null;
        for (int d1 : new int[]{1, -1})
            for (int d2 : new int[]{1, -1}) {
                Csc c = csc(a, b, r, d1, r, d2);
                if (c == null || c.a1 > maxArc || c.a2 > maxArc || c.l < 0) continue;
                if (best == null || c.length() < best.length()) best = c;
            }
        return best;
    }

    /** The crossing's frame: J on H's centreline at the crossing, X and Y (the class comment). */
    private record Cross(double jx, double jz, double xx, double xz, double yx, double yz) {
        double[] at(double x, double y) { return new double[]{jx + x * xx + y * yx, jz + x * xz + y * yz}; }

        double x(double[] p) { return (p[0] - jx) * xx + (p[1] - jz) * xz; }

        double y(double[] p) { return (p[0] - jx) * yx + (p[1] - jz) * yz; }

        double thX() { return Math.atan2(xz, xx); }

        double thY() { return Math.atan2(yz, yx); }

        Pose pose(double x, double y, double th) {
            double[] p = at(x, y);
            return new Pose(p[0], p[1], th);
        }
    }

    /**
     * H's canonical frame for one carriageway (RoadInterchanges#path): traffic tau along H's stations on its tau side, q
     * along the traffic from the crossing sc, w outward from the centreline, headings relative to the traffic (positive
     * outward).
     */
    private record Lane(Interstate h, double sc, int tau) {
        double[] world(double q, double w) { return h.curve.point(sc + tau * q, tau * w); }

        /** The world pose at (q, w) heading rel (the offset line's scale on the along component). */
        Pose pose(double q, double w, double rel) {
            double s = sc + tau * q;
            double[] f = new double[5];
            h.curve.frame(s, f);
            double th = Math.atan2(tau * f[3], tau * f[2]), scale = 1 - f[4] * tau * w;
            double[] p = h.curve.point(s, tau * w);
            return new Pose(p[0], p[1], th + Math.atan2(Math.sin(rel), Math.cos(rel) * scale));
        }

        /** {q, w} of a world point, or null off H. */
        double[] local(double[] p) {
            double[] l = h.curve.local(p[0], p[1], 600, 600);
            return l == null ? null : new double[]{tau * (l[0] - sc), tau * l[1]};
        }

        /** A canonical turtle's points in the world. */
        List<double[]> world(Turtle t) {
            List<double[]> out = new ArrayList<>(t.pts.size());
            for (double[] p : t.pts) out.add(world(p[0], p[1]));
            return out;
        }

        Pose pose(Turtle t) { return pose(t.q, t.w, t.h); }
    }

    /** The exit taper from q0 (canonical): W0 out at 1:TAPER to W_LEAD. */
    private static Turtle taper(double q0) {
        Turtle t = new Turtle(q0, W0, T0);
        t.straight(TAPER_RUN / Math.cos(T0));
        return t;
    }

    /** The entrance from (q0, w0) heading 1:TAPER in: the approach to W_PAINT, the acceleration lane, the merge taper. */
    private static Turtle entrance(double q0, double w0) {
        Turtle t = new Turtle(q0, w0, -T0);
        double soft = SOFT_ACCEL * (1 - Math.cos(T0));
        t.straight((w0 - W_PAINT - soft) / Math.sin(T0));
        t.arc(SOFT_ACCEL, 0);
        t.straight(ACCEL);
        double tt = Math.acos(1 - (W_PAINT - W0) / (2 * SOFT_MERGE));
        t.arc(SOFT_MERGE, -tt);
        t.arc(SOFT_MERGE, 0);
        return t;
    }

    /** The canonical {paint, nose} points of an exit taper or an entrance (RoadInterchanges#path). */
    private static double[][] noses(Turtle t, boolean exit) {
        double[] paint = null, nose = null;
        for (double[] p : t.pts) {
            if (paint == null && (exit ? p[1] >= W_PAINT - 1e-3 : p[1] <= W_PAINT + 5e-3)) paint = p;
            if (nose == null && (exit ? p[1] >= W_NOSE : p[1] <= W_NOSE)) nose = p;
        }
        return new double[][]{paint, nose};
    }

    /** Bisection of a function rising through 0 over [lo, hi] (NaN: no sign change). */
    private interface Fn { double at(double x); }

    private static double root(Fn f, double lo, double hi) {
        double flo = f.at(lo), fhi = f.at(hi);
        if (Double.isNaN(flo) || Double.isNaN(fhi) || flo > 0 == fhi > 0) return Double.NaN;
        boolean rising = fhi > flo;
        for (int it = 0; it < 60; it++) {
            double mid = (lo + hi) / 2, fm = f.at(mid);
            if (Double.isNaN(fm)) return Double.NaN;
            if (fm > 0 == rising) hi = mid; else lo = mid;
        }
        return (lo + hi) / 2;
    }

    // ------------------------------------------------------------------ ramps (plan, profile)

    /** A ramp being built: its plan (control points, curve), its profile's fixed samples, anchors and slopes. */
    private static final class Ramp {
        final String id, name, from, to, corridor;
        final double[] xs, zs;
        final RoadCurve rc;
        final double len;
        final int m;
        final double[] natural, fixed, k;
        final List<double[]> anchors = new ArrayList<>();
        final List<Structure> structures = new ArrayList<>();
        double[] e;
        double tc = Double.NaN, limit = RAMP_GRADE;

        Ramp(String id, String name, Path path, String from, String to, String corridor) {
            this.id = id;
            this.name = name;
            this.from = from;
            this.to = to;
            this.corridor = corridor;
            double[][] c = resample(path, Math.max(POINT_MIN, Math.min(POINT_MAX, path.minR / POINT_PER_R)));
            xs = c[0];
            zs = c[1];
            rc = new RoadCurve(xs, zs);
            len = rc.length;
            m = (int) Math.ceil(len / DS - 1e-9) + 1;
            RoadDesign pd = RoadNetworkJson.design(new RouteData(id, "ramp", corridor, from, to, xs, zs, new double[m], List.of(), null));
            natural = pd.slopes();
            fixed = new double[m];
            Arrays.fill(fixed, Double.NaN);
            k = natural.clone();
        }

        /** Station of a world point on the ramp (within 3 m of its centreline), NaN off it. */
        double station(double[] p) {
            double[] l = rc.local(p[0], p[1], 3, 0);
            return l == null ? Double.NaN : l[0];
        }
    }

    /**
     * A dense path's control points every about point m (both ends kept), rounded to cm. The B-spline (RoadCurve) runs
     * inside its control polygon on a curve, by about point^2 / 6R (9 cm at 19 m on R 660): where the path follows its
     * host's frame (Path#follow: a taper, an entrance beside H's lanes) the points are moved out by a sixth of their second
     * difference so the curve passes through them, its lane edges on the host's lines. Not on the free stretches: across
     * a curvature step that would overshoot the step's radius.
     */
    private static double[][] resample(Path path, double point) {
        List<double[]> p = path.pts;
        double[] cum = new double[p.size()];
        for (int i = 1; i < cum.length; i++) cum[i] = cum[i - 1] + Math.hypot(p.get(i)[0] - p.get(i - 1)[0], p.get(i)[1] - p.get(i - 1)[1]);
        int m = Math.max(1, (int) Math.round(cum[cum.length - 1] / point));
        double[] xs = new double[m + 1], zs = new double[m + 1];
        boolean[] follows = new boolean[m + 1];
        int j = 0;
        for (int i = 0; i <= m; i++) {
            double s = cum[cum.length - 1] * i / m;
            while (j < cum.length - 2 && cum[j + 1] < s) j++;
            double f = cum[j + 1] > cum[j] ? (s - cum[j]) / (cum[j + 1] - cum[j]) : 0;
            xs[i] = p.get(j)[0] + (p.get(j + 1)[0] - p.get(j)[0]) * f;
            zs[i] = p.get(j)[1] + (p.get(j + 1)[1] - p.get(j)[1]) * f;
            for (int[] r : path.follow) if (j >= r[0] && j + 1 < r[1]) follows[i] = true;
        }
        double[] cx = xs.clone(), cz = zs.clone();
        for (int i = 1; i < m; i++)
            if (follows[i - 1] && follows[i] && follows[i + 1]) {
                cx[i] = xs[i] - (xs[i - 1] - 2 * xs[i] + xs[i + 1]) / 6;
                cz[i] = zs[i] - (zs[i - 1] - 2 * zs[i] + zs[i + 1]) / 6;
            }
        for (int i = 0; i <= m; i++) {
            cx[i] = RoadNetworkJson.round2(cx[i]);
            cz[i] = RoadNetworkJson.round2(cz[i]);
        }
        return new double[][]{cx, cz};
    }

    /**
     * Ramp r lies in its host's shoulder plane (RoadInterchanges#hExt; side: the host's side it lies on) from its start to
     * its nose (start) or from its nose to its end: those samples fixed, its slope blended into its own over NOSE_BLEND
     * beyond the nose, an anchor at the nose sample (the host's grade over K_CHORD). Null, or why not.
     */
    private static String attached(Ramp r, RoadDesign hd, RoadCurve hc, int side, double nose, boolean start) {
        int ch = (int) Math.round(RoadProfile.K_CHORD / DS);
        int ni = start ? (int) Math.floor(nose / DS) : (int) Math.ceil(nose / DS);
        if (ni - ch < 0 || ni + ch >= r.m) return r.name + ": the nose is off the ramp";
        for (int i = 0; i < r.m; i++) {
            double t = Math.min(i * DS, r.len), into = start ? t - nose : nose - t;
            boolean run = start ? i <= ni : i >= ni;
            if (!run && into > NOSE_BLEND + 4) continue;
            double h0 = hExt(hd, hc, side, r.rc.point(t, 0), 2), hl = hExt(hd, hc, side, r.rc.point(t, -1), 2), hr = hExt(hd, hc, side, r.rc.point(t, 1), 2);
            if (Double.isNaN(h0) || Double.isNaN(hl) || Double.isNaN(hr)) return r.name + ": the attached stretch is off its host";
            double ka = (hl - hr) / 2;
            r.k[i] = into <= 0 ? ka : ka + (r.natural[i] - ka) * CrossSection.smooth(Math.min(1, into / NOSE_BLEND));
            if (run) r.fixed[i] = h0;
        }
        double gn = start ? (r.fixed[ni] - r.fixed[ni - ch]) / RoadProfile.K_CHORD : (r.fixed[ni + ch] - r.fixed[ni]) / RoadProfile.K_CHORD;
        r.anchors.add(new double[]{ni * DS, r.fixed[ni], gn});
        r.limit = Math.max(r.limit, Math.abs(gn) + 0.002);
        return null;
    }

    /** An open end of r at height e, grade g (along r) and lane slope k (blended into its own over NOSE_BLEND). */
    private static void open(Ramp r, double e, double g, double k, boolean start) {
        double t = start ? 0 : r.len;
        r.anchors.add(new double[]{t, e, g});
        for (int i = 0; i < r.m; i++) {
            double s = i * DS;
            if (start ? i == 0 : s >= r.len - 1e-9) r.fixed[i] = e + g * (s - t);
            double into = start ? s : r.len - s;
            if (into < NOSE_BLEND) r.k[i] = k + (r.natural[i] - k) * CrossSection.smooth(Math.max(0, into) / NOSE_BLEND);
        }
    }

    /** The free samples between the anchors: vertical curves round a straight grade (RoadInterchanges#curves), else Hermite. */
    private static String fill(Ramp r) {
        r.anchors.sort(Comparator.comparingDouble(a -> a[0]));
        double[] e = r.fixed.clone();
        for (int j = 0; j + 1 < r.anchors.size(); j++) {
            double[] a = r.anchors.get(j), b = r.anchors.get(j + 1);
            double[] v = curves(a[0], a[1], a[2], b[0], b[1], b[2], RAMP_K_DESIGN);
            if (v == null) v = curves(a[0], a[1], a[2], b[0], b[1], b[2], RAMP_K);
            for (int i = 0; i < r.m; i++) {
                double t = i * DS;
                if (!Double.isNaN(e[i]) || t < a[0] - 1e-9 || t > b[0] + 1e-9) continue;
                e[i] = v != null ? curvesAt(t, a[0], a[1], a[2], b[0], b[2], v) : hermite(t, a[0], a[1], a[2], b[0], b[1], b[2]);
            }
        }
        for (double x : e) if (Double.isNaN(x)) return r.name + ": a profile sample outside the anchors";
        r.e = e;
        return null;
    }

    /**
     * The lowest datum of ramp r at sample i for its deck to keep CLEARANCE over H's pavement and its shoulder plane carried
     * out to UNDER (NaN: nothing under the deck there).
     */
    private static double need(Ramp r, int i, Interstate h) {
        double t = i * DS, half = CrossSection.RAMP.edge + BridgeMesh.PARAPET_WIDTH, need = Double.NaN;
        for (double u = -half; u <= half + 1e-9; u += 0.5) {
            double[] p = r.rc.point(t, u);
            double y = under(h, p);
            if (!Double.isNaN(y)) need = Double.isNaN(need) ? y : Math.max(need, y);
        }
        return Double.isNaN(need) ? need : need + CLEARANCE + RoadNetworkJson.RAMP_DECK + CLEAR_MARGIN;
    }

    /** H's pavement at a point, or its shoulder plane carried out (to UNDER); NaN beyond. */
    private static double under(Interstate h, double[] p) {
        double[] l = h.curve.local(p[0], p[1], UNDER + 2, 0);
        if (l == null || Math.abs(l[1]) > UNDER) return Double.NaN;
        double s = Math.max(0, Math.min(h.design.length, l[0])), hm = h.design.section.halfMedian, v = Math.abs(l[1]);
        // the median (turf below its inner shoulders): the higher inner shoulder edge
        if (v < hm) return Math.max(h.design.pavementY(s, -hm), h.design.pavementY(s, hm));
        return v <= h.design.section.edge ? h.design.pavementY(s, l[1]) : hExt(h.design, h.curve, l[1] < 0 ? -1 : 1, p);
    }

    /** The crest of ramp r over H at its crossing station tc: raised until the deck keeps the clearance; null or why not. */
    private static String crest(Ramp r, Interstate h) {
        double[] need = new double[r.m];
        double top = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < r.m; i++) {
            need[i] = Math.abs(i * DS - r.tc) <= SPAN + 2 ? need(r, i, h) : Double.NaN;
            if (!Double.isNaN(need[i])) top = Math.max(top, need[i]);
        }
        if (top == Double.NEGATIVE_INFINITY) return r.name + ": nothing under its deck";
        double[] a = {r.tc, top + 0.3, 0};
        r.anchors.add(a);
        for (int it = 0; it < 12; it++) {
            String w = fill(r);
            if (w != null) return w;
            double worst = 0;
            for (int i = 0; i < r.m; i++) if (!Double.isNaN(need[i])) worst = Math.max(worst, need[i] - r.e[i]);
            if (worst <= 0.005) return null;
            a[1] += worst + 0.01;
        }
        return r.name + ": the crest does not clear H";
    }

    /** The profile's checks: free grades (RAMP_GRADE or the host's at a nose; BRIDGE_GRADE on the deck), free K; null or why not. */
    private static String checks(Ramp r, double[] stats) {
        int ch = (int) Math.round(RoadProfile.K_CHORD / DS);
        double worst = 0, deck = 0, kMin = Double.POSITIVE_INFINITY;
        for (int i = 1; i < r.m; i++) {
            if (!Double.isNaN(r.fixed[i]) && !Double.isNaN(r.fixed[i - 1])) continue;
            double g = Math.abs(r.e[i] - r.e[i - 1]) / DS, t = (i - 0.5) * DS;
            if (!Double.isNaN(r.tc) && Math.abs(t - r.tc) <= SPAN) deck = Math.max(deck, g);
            else worst = Math.max(worst, g);
        }
        for (int i = ch; i + ch < r.m; i++) {
            if (!Double.isNaN(r.fixed[i - ch]) || !Double.isNaN(r.fixed[i + ch])) continue;
            double second = Math.abs(r.e[i + ch] - 2 * r.e[i] + r.e[i - ch]) / (RoadProfile.K_CHORD * RoadProfile.K_CHORD);
            if (second > 1e-12) kMin = Math.min(kMin, 1 / (100 * second));
        }
        stats[0] = Math.max(worst, deck);
        stats[1] = kMin;
        if (worst > r.limit + 1e-4) return String.format(Locale.ROOT, "%s grade %.1f %%", r.name, worst * 100);
        if (deck > BRIDGE_GRADE + 1e-4) return String.format(Locale.ROOT, "%s deck grade %.1f %%", r.name, deck * 100);
        if (kMin < 0.9 * RAMP_K) return String.format(Locale.ROOT, "%s K %.0f", r.name, kMin);
        return null;
    }

    /** The least clearance under ramp design d's overpass (its soffit over H's pavement and shoulder plane out to UNDER). */
    private static double clearance(RoadDesign d, Interstate h) {
        double worst = Double.POSITIVE_INFINITY;
        for (RoadDesign.Span sp : d.spans) {
            if (!sp.over()) continue;
            double half = d.section.edge + BridgeMesh.PARAPET_WIDTH;
            for (double t = sp.s0(); t <= sp.s1() + 1e-9; t += 1)
                for (double u = -half; u <= half + 1e-9; u += 1) {
                    double y = under(h, d.point(t, u));
                    if (!Double.isNaN(y)) worst = Math.min(worst, BridgeMesh.soffit(d, sp, t, u < 0 ? -1 : 1) - y);
                }
        }
        return worst;
    }

    // ------------------------------------------------------------------ one site

    /** Offline only (SystemCheck): each attempt's designs (its ramps, Q, H) under a label, after the ramps' crossings check. */
    static volatile java.util.function.BiConsumer<String, List<RoadDesign>> debugSink;

    private static <T> T fail(String[] why, String w) {
        why[0] = w;
        return null;
    }

    /**
     * Q (route q, its end on h at st) cut STEM off h with the trumpet crossing h at sc (tau: the main movement's direction
     * along h's stations); null and why[0] where it does not fit.
     */
    private static Built site(List<RouteData> out, List<RouteData> added, Site st, RouteData q, Interstate h, int tau, double sc, double stem,
                              String[] why) {
        int sigma = st.sigma, lambda = sigma * tau;
        if (sc < END_CLEAR || sc > h.length - END_CLEAR) return fail(why, "too near the host's end");
        // the crossing's frame
        double[] f = new double[5];
        h.curve.frame(sc, f);
        double tx = f[2], tz = f[3], nx = -tz, nz = tx;
        Cross x = new Cross(f[0], f[1], -sigma * tx, -sigma * tz, sigma * nx, sigma * nz);
        Lane far = new Lane(h, sc, -sigma), near = new Lane(h, sc, sigma);
        // Q cut where its centreline is stem off H's
        Interstate iq = Interstate.of(q);
        double cut = Double.NaN;
        for (int i = 0; i * DS < iq.length - 300; i++) {
            double s = st.atEnd ? iq.length - i * DS : i * DS;
            double[] p = iq.curve.point(s, 0), l = h.curve.local(p[0], p[1], stem + 300, 0);
            if (l != null && sigma * l[1] >= stem) { cut = st.atEnd ? Math.floor(s / DS) * DS : Math.ceil(s / DS) * DS; break; }
        }
        if (Double.isNaN(cut)) return fail(why, "Q never gets the stem off H");
        for (Structure s : q.structures()) {
            boolean gone = st.atEnd ? s.s1() > cut - 60 : s.s0() < cut + 60;
            boolean across = s.s0() < cut + 60 && s.s1() > cut - 60;
            if (across || gone && !s.kind().equals("CULVERT")) return fail(why, "Q has a " + s.kind().toLowerCase(Locale.ROOT) + " near or past the cut");
        }
        RouteData qc = cutQ(q, iq, cut, st.atEnd);
        RoadDesign qd = RoadNetworkJson.design(qc);
        double sj = st.atEnd ? qd.length : 0;
        int toward = st.atEnd ? 1 : -1;
        double[] fq = new double[5];
        qd.curve.frame(sj, fq);
        double thQ = Math.atan2(toward * fq[3], toward * fq[2]);
        double uIn = toward * LANE, uOut = -toward * LANE;
        double[] s0 = qd.point(sj, uIn), n1 = qd.point(sj, uOut);
        Pose sStart = new Pose(s0[0], s0[1], thQ), nEnd = new Pose(n1[0], n1[1], thQ + Math.PI);
        double gToward = toward * qd.profile.grade(sj);
        double eIn = qd.pavementY(sj, uIn), eOut = qd.pavementY(sj, uOut);
        double kIn = qd.cross(sj).lane(uIn > 0 ? 1 : -1), kOut = qd.cross(sj).lane(uOut > 0 ? 1 : -1);
        // the legs: the loop's, its centre, the semi-direct's (on the outer circle's tangent)
        double xL = -LEG * lambda, xC = -lambda * (LEG + R_LOOP), xD = xC + lambda * R_OUTER;
        double xS = lambda < 0 ? xD : xL, xN = lambda < 0 ? xL : xD, yB = SPAN + LEAD;
        double thY = x.thY(), thMY = thY + Math.PI;
        String base = "system:" + h.route.id() + "@" + Math.round(sc);
        String sId = base + ":left-in", nId = base + ":left-out", aId = base + ":right-in", bId = base + ":right-out";
        // ---- the left-turn ramps' plans: the loop first (its centre places the semi-direct)
        Path sPath = new Path(sStart), nPath;
        sPath.straight(STEM_STRAIGHT);
        Csc sStem = stem(sPath.pose(), x.pose(xS, yB, thMY));
        if (sStem == null) return fail(why, "no curve from Q's end onto the left-in overpass");
        sStem.draw(sPath);
        double[] centre;
        double[][] sNoses, nNoses;
        double nQ0;
        if (lambda < 0) {
            // the loop exits: the taper on the far carriageway, R_LOOP right onto the leg
            double qT = root(qt -> {
                Pose p = far.pose(taper(qt));
                double cx = p.x - R_LOOP * Math.sin(p.th), cz = p.z + R_LOOP * Math.cos(p.th);
                return x.x(new double[]{cx, cz}) - xC;
            }, xC - TAPER_RUN - 600, xC - TAPER_RUN + 300);
            if (Double.isNaN(qT)) return fail(why, "the loop does not reach its leg");
            Turtle tp = taper(qT);
            Pose pT = far.pose(tp);
            nPath = new Path(far.pose(qT, W0, T0));
            nPath.add(far.world(tp), pT);
            double turn = norm(thY - pT.th);
            if (turn < Math.PI) return fail(why, "the loop turns only " + Math.round(Math.toDegrees(turn)) + " deg");
            centre = new double[]{pT.x - R_LOOP * Math.sin(pT.th), pT.z + R_LOOP * Math.cos(pT.th)};
            nPath.arc(R_LOOP, 1, turn);
            double y0 = x.y(nPath.pose().p());
            if (y0 > -yB - 10) return fail(why, "the loop ends under the overpass");
            nPath.straight(yB - y0);
            nNoses = noses(tp, true);
            nQ0 = qT;
            // the semi-direct enters: down its leg onto the outer circle, round it, R_SEMI onto the entrance
            double yC = x.y(centre);
            sPath.straight(yB - yC);
            Pose pw = sPath.pose();
            Csc best = null;
            double bestQ = 0;
            for (double qe = xC; qe <= xC + 2000; qe += 2) {
                Csc c = csc(pw, far.pose(qe, W_LEAD, -T0), R_OUTER, -1, R_SEMI, 1);
                if (c == null || c.a1 < Math.PI / 2 || c.l < MIN_STRAIGHT || c.a2 > Math.PI / 2) continue;
                if (best == null || c.length() < best.length()) { best = c; bestQ = qe; }
            }
            if (best == null) return fail(why, "the semi-direct finds no way round the loop onto H");
            best.draw(sPath);
            Turtle te = entrance(bestQ, W_LEAD);
            sPath.add(far.world(te), far.pose(te));
            sNoses = noses(te, false);
        } else {
            // the loop enters: down its leg, R_LOOP right onto the entrance
            double ys = root(y -> loopEnd(x, far, xL, y)[1] - W_LEAD, -yB - 400, -yB - 10);
            if (Double.isNaN(ys)) return fail(why, "the loop does not reach the entrance");
            double[] le = loopEnd(x, far, xL, ys);
            sPath.straight(yB - ys);
            if (Math.hypot(sPath.x - x.at(xL, ys)[0], sPath.z - x.at(xL, ys)[1]) > 0.05) return fail(why, "the loop's leg is off");
            centre = new double[]{sPath.x - R_LOOP * Math.sin(sPath.th), sPath.z + R_LOOP * Math.cos(sPath.th)};
            sPath.arc(R_LOOP, 1, le[2]);
            Turtle te = entrance(le[0], le[1]);
            sPath.add(far.world(te), far.pose(te));
            sNoses = noses(te, false);
            // the semi-direct exits: the taper upstream, R_SEMI right, round the outer circle onto its leg
            double yC = x.y(centre);
            Pose pe = x.pose(xD, yC, thY);
            Csc best = null;
            double bestQ = 0;
            for (double q0 = xC - 2500; q0 <= xC; q0 += 2) {
                Csc c = csc(far.pose(taper(q0)), pe, R_SEMI, 1, R_OUTER, -1);
                if (c == null || c.a2 < Math.PI / 2 || c.l < MIN_STRAIGHT || c.a1 > Math.PI / 2) continue;
                if (best == null || c.length() < best.length()) { best = c; bestQ = q0; }
            }
            if (best == null) return fail(why, "the semi-direct finds no way round the loop off H");
            Turtle tp = taper(bestQ);
            nPath = new Path(far.pose(bestQ, W0, T0));
            nPath.add(far.world(tp), far.pose(tp));
            best.draw(nPath);
            nPath.straight(yB - yC);
            nNoses = noses(tp, true);
            nQ0 = bestQ;
        }
        Csc nStem = stem(nPath.pose(), nEnd.ahead(-STEM_STRAIGHT));
        if (nStem == null) return fail(why, "no curve from the left-out overpass onto Q");
        nStem.draw(nPath);
        nPath.straight(STEM_STRAIGHT);
        if (Math.hypot(nPath.x - nEnd.x, nPath.z - nEnd.z) > 0.05) return fail(why, "left-out misses Q");
        Ramp s = new Ramp(sId, "left-in", sPath, q.id(), h.route.id(), q.corridor());
        Ramp n = new Ramp(nId, "left-out", nPath, h.route.id(), q.id(), q.corridor());
        s.tc = s.station(x.at(xS, 0));
        n.tc = n.station(x.at(xN, 0));
        if (Double.isNaN(s.tc) || Double.isNaN(n.tc)) return fail(why, "an overpass misses the crossing");
        // ---- the direct ramps: split from left-in, merge into left-out (side by side, one lane apart); of their curves the
        // shortest that keeps to the right of its own left-turn ramp and clear of the other (#clearOf)
        double[] qEnd = qd.point(sj, 0);
        double dClear = 2 * CrossSection.RAMP.edge + GAP + 2;
        Guard sGuard = guard(sPath.pts, x, qEnd), nGuard = guard(nPath.pts, x, qEnd);
        Pose a0 = start(s.rc, SPLIT_W);
        Path aPath = new Path(a0);
        aPath.straight(DIRECT_STRAIGHT);
        List<double[]> aCands = new ArrayList<>();
        for (double qe = 0; qe <= 2500; qe += 2) {
            Csc c = csc(aPath.pose(), near.pose(qe, W_LEAD, -T0), R_DIRECT, 1, R_DIRECT, 1);
            if (c != null && c.l >= MIN_STRAIGHT && c.a1 <= Math.PI && c.a2 <= Math.PI) aCands.add(new double[]{c.length(), qe});
        }
        aCands.sort(Comparator.comparingDouble(c -> c[0]));
        Csc aCsc = null;
        double aQ = 0;
        for (double[] cand : aCands) {
            Csc c = csc(aPath.pose(), near.pose(cand[1], W_LEAD, -T0), R_DIRECT, 1, R_DIRECT, 1);
            Path t = new Path(aPath.pose());
            c.draw(t);
            if (clearOf(t.pts, false, s, nGuard, dClear, near)) { aCsc = c; aQ = cand[1]; break; }
        }
        if (aCsc == null) return fail(why, aCands.isEmpty() ? "right-in finds no curve onto H" : "right-in finds no curve clear of H and the left-turn ramps");
        aCsc.draw(aPath);
        Turtle ae = entrance(aQ, W_LEAD);
        aPath.add(near.world(ae), near.pose(ae));
        Pose b1 = end(n.rc, SPLIT_W);
        List<double[]> bCands = new ArrayList<>();
        for (double q0 = -2500; q0 <= 0; q0 += 2) {
            Csc c = csc(near.pose(taper(q0)), b1.ahead(-DIRECT_STRAIGHT), R_DIRECT, 1, R_DIRECT, 1);
            if (c != null && c.l >= MIN_STRAIGHT && c.a1 <= Math.PI && c.a2 <= Math.PI) bCands.add(new double[]{c.length(), q0});
        }
        bCands.sort(Comparator.comparingDouble(c -> c[0]));
        Csc bCsc = null;
        double bQ = 0;
        for (double[] cand : bCands) {
            Pose from = near.pose(taper(cand[1]));
            Csc c = csc(from, b1.ahead(-DIRECT_STRAIGHT), R_DIRECT, 1, R_DIRECT, 1);
            Path t = new Path(from);
            c.draw(t);
            t.straight(DIRECT_STRAIGHT);
            if (clearOf(t.pts, true, n, sGuard, dClear, near)) { bCsc = c; bQ = cand[1]; break; }
        }
        if (bCsc == null) return fail(why, bCands.isEmpty() ? "right-out finds no curve off H" : "right-out finds no curve clear of H and the left-turn ramps");
        Turtle bt = taper(bQ);
        Path bPath = new Path(near.pose(bQ, W0, T0));
        bPath.add(near.world(bt), near.pose(bt));
        bCsc.draw(bPath);
        bPath.straight(DIRECT_STRAIGHT);
        Ramp a = new Ramp(aId, "right-in", aPath, q.id(), h.route.id(), q.corridor());
        Ramp b = new Ramp(bId, "right-out", bPath, h.route.id(), q.id(), q.corridor());
        // ---- the noses
        double sPaint = s.station(far.world(sNoses[0][0], sNoses[0][1])), sNose = s.station(far.world(sNoses[1][0], sNoses[1][1]));
        double nPaint = n.station(far.world(nNoses[0][0], nNoses[0][1])), nNose = n.station(far.world(nNoses[1][0], nNoses[1][1]));
        double[][] an = noses(ae, false), bn = noses(bt, true);
        double aPaint2 = a.station(near.world(an[0][0], an[0][1])), aNose2 = a.station(near.world(an[1][0], an[1][1]));
        double bPaint1 = b.station(near.world(bn[0][0], bn[0][1])), bNose1 = b.station(near.world(bn[1][0], bn[1][1]));
        double[] aSplit = splitNoses(a, s, true), bSplit = splitNoses(b, n, false);
        for (double v : new double[]{sPaint, sNose, nPaint, nNose, aPaint2, aNose2, bPaint1, bNose1})
            if (Double.isNaN(v)) return fail(why, "a nose is off its ramp");
        if (aSplit == null || bSplit == null) return fail(why, "a direct ramp does not leave its left-turn ramp");
        // ---- the profiles: the left-turn ramps (open ends, crests, attached to H), then the direct ramps on them
        String w;
        s.tc = s.station(x.at(xS, 0));
        n.tc = n.station(x.at(xN, 0));
        if (Double.isNaN(s.tc) || Double.isNaN(n.tc)) return fail(why, "an overpass misses the crossing");
        open(s, eIn, gToward, kIn, true);
        if ((w = attached(s, h.design, h.curve, -sigma, sNose, false)) != null) return fail(why, w);
        if ((w = crest(s, h)) != null) return fail(why, w);
        open(n, eOut, -gToward, kOut, false);
        if ((w = attached(n, h.design, h.curve, -sigma, nNose, true)) != null) return fail(why, w);
        if ((w = crest(n, h)) != null) return fail(why, w);
        s.structures.add(new Structure("OVERPASS", RoadNetworkJson.round2(s.tc - SPAN), RoadNetworkJson.round2(s.tc + SPAN), 0));
        n.structures.add(new Structure("OVERPASS", RoadNetworkJson.round2(n.tc - SPAN), RoadNetworkJson.round2(n.tc + SPAN), 0));
        RouteData sr = route(sId, s, new RoadDesign.Attach(h.route.id(), -sigma, true, RoadNetworkJson.round2(sPaint), RoadNetworkJson.round2(sNose)),
                null, new boolean[]{true, false});
        RouteData nr = route(nId, n, new RoadDesign.Attach(h.route.id(), -sigma, false, RoadNetworkJson.round2(nPaint), RoadNetworkJson.round2(nNose)),
                null, new boolean[]{false, true});
        RoadDesign sd, nd;
        try {
            sd = RoadNetworkJson.design(sr);
            nd = RoadNetworkJson.design(nr);
        } catch (IllegalArgumentException ex) {
            return fail(why, "a left-turn ramp cannot be designed: " + ex.getMessage());
        }
        if ((w = attached(a, sd, sd.curve, 1, aSplit[1], true)) != null) return fail(why, w);
        if ((w = attached(a, h.design, h.curve, sigma, aNose2, false)) != null) return fail(why, w);
        if ((w = fill(a)) != null) return fail(why, w);
        if ((w = attached(b, h.design, h.curve, sigma, bNose1, true)) != null) return fail(why, w);
        if ((w = attached(b, nd, nd.curve, 1, bSplit[1], false)) != null) return fail(why, w);
        if ((w = fill(b)) != null) return fail(why, w);
        double[][] stats = new double[4][2];
        Ramp[] all = {s, n, a, b};
        for (int i = 0; i < 4; i++) if ((w = checks(all[i], stats[i])) != null) return fail(why, w);
        // the direct ramps in two pieces, joined where their free stretch is middle
        double aCut = Math.round((aSplit[1] + aNose2) / 2 / DS) * DS, bCut = Math.round((bNose1 + bSplit[1]) / 2 / DS) * DS;
        if (aCut < aSplit[1] + SPLIT_MIN || aCut > aNose2 - SPLIT_MIN) return fail(why, "right-in is too short for its two pieces");
        if (bCut < bNose1 + SPLIT_MIN || bCut > bSplit[1] - SPLIT_MIN) return fail(why, "right-out is too short for its two pieces");
        // their ends at Q's end open too (they go on as Q's lanes: never rounded off the cut by the trims)
        RouteData a1 = route(aId + "~1", a, new RoadDesign.Attach(sId, 1, false, RoadNetworkJson.round2(aSplit[0]), RoadNetworkJson.round2(aSplit[1])),
                new double[]{0, aCut}, new boolean[]{true, true});
        RouteData a2 = route(aId + "~2", a, new RoadDesign.Attach(h.route.id(), sigma, true, RoadNetworkJson.round2(aPaint2), RoadNetworkJson.round2(aNose2)),
                new double[]{aCut, a.len}, new boolean[]{true, false});
        RouteData b1r = route(bId + "~1", b, new RoadDesign.Attach(h.route.id(), sigma, false, RoadNetworkJson.round2(bPaint1), RoadNetworkJson.round2(bNose1)),
                new double[]{0, bCut}, new boolean[]{false, true});
        RouteData b2r = route(bId + "~2", b, new RoadDesign.Attach(nId, 1, true, RoadNetworkJson.round2(bSplit[0]), RoadNetworkJson.round2(bSplit[1])),
                new double[]{bCut, b.len}, new boolean[]{true, true});
        // the loop in two pieces too, joined halfway between its nose on H and its deck's lead: it passes over its own
        // taper on H (a road never crosses itself in the works and meshes)
        RouteData l1, l2;
        String loopId = lambda < 0 ? nId : sId;
        if (lambda < 0) {
            double lLo = nNose + SPLIT_MIN, lHi = n.tc - SPAN - LEAD - SPLIT_MIN, lCut = Math.round((nNose + n.tc - SPAN - LEAD) / 2 / DS) * DS;
            if (lCut < lLo || lCut > lHi) return fail(why, "the loop is too short for its two pieces");
            l1 = route(nId + "~1", n, new RoadDesign.Attach(h.route.id(), -sigma, false, RoadNetworkJson.round2(nPaint), RoadNetworkJson.round2(nNose)),
                    new double[]{0, lCut}, new boolean[]{false, true});
            l2 = route(nId + "~2", n, null, new double[]{lCut, n.len}, new boolean[]{true, true});
        } else {
            double lLo = s.tc + SPAN + LEAD + SPLIT_MIN, lHi = sNose - SPLIT_MIN, lCut = Math.round((s.tc + SPAN + LEAD + sNose) / 2 / DS) * DS;
            if (lCut < lLo || lCut > lHi) return fail(why, "the loop is too short for its two pieces");
            l1 = route(sId + "~1", s, null, new double[]{0, lCut}, new boolean[]{true, true});
            l2 = route(sId + "~2", s, new RoadDesign.Attach(h.route.id(), -sigma, true, RoadNetworkJson.round2(sPaint), RoadNetworkJson.round2(sNose)),
                    new double[]{lCut, s.len}, new boolean[]{true, false});
        }
        List<RouteData> ramps = lambda < 0 ? List.of(sr, l1, l2, a1, a2, b1r, b2r) : List.of(l1, l2, nr, a1, a2, b1r, b2r);
        double[] minR = new double[4];
        List<RoadDesign> ds = new ArrayList<>();
        try {
            for (RouteData r : ramps) ds.add(RoadNetworkJson.design(r));
        } catch (IllegalArgumentException ex) {
            return fail(why, "a ramp cannot be designed: " + ex.getMessage());
        }
        ds.add(qd);
        w = crossings(ds, qd.point(sj, 0));
        if (w == null) w = offH(ds, h);
        if (debugSink != null) {
            List<RoadDesign> all2 = new ArrayList<>(ds);
            all2.add(h.design);
            debugSink.accept(String.format(Locale.ROOT, "%s_stem%.0f_%s", base.substring(base.lastIndexOf('@') + 1), stem, w == null ? "ok" : "fail"), all2);
        }
        if (w != null) return fail(why, w);
        // the free stretches (between Q's end or the split and the noses on H): an attached stretch follows its host's curve
        double[][] free = {{0, sNose}, {nNose, n.len}, {aSplit[1], aNose2}, {bNose1, bSplit[1]}};
        for (int i = 0; i < 4; i++) minR[i] = minRadius(all[i].rc, free[i][0], free[i][1]);
        double loopMin = lambda < 0 ? minR[1] : minR[0], semiMin = lambda < 0 ? minR[0] : minR[1];
        if (loopMin < 0.95 * R_LOOP) return fail(why, String.format(Locale.ROOT, "the loop's radius %.0f m", loopMin));
        if (semiMin < 0.95 * R_OUTER) return fail(why, String.format(Locale.ROOT, "the semi-direct's radius %.0f m", semiMin));
        if (Math.min(minR[2], minR[3]) < 0.95 * R_DIRECT) {
            if (Boolean.getBoolean("afl.debugSystems"))
                for (Ramp r : new Ramp[]{a, b}) {
                    double[] fr = new double[5];
                    double k = 0, at = 0;
                    for (double t = 0; t <= r.len; t += 1) { r.rc.frame(t, fr); if (Math.abs(fr[4]) > k) { k = Math.abs(fr[4]); at = t; } }
                    System.out.printf(Locale.ROOT, "[systems] %s: R %.0f at s %.0f of %.0f; csc %s%n", r.name, 1 / k, at, r.len, r == a ? aCsc : bCsc);
                }
            return fail(why, String.format(Locale.ROOT, "a direct ramp's radius %.0f m", Math.min(minR[2], minR[3])));
        }
        double cs = clearance(sd, h), cn = clearance(nd, h);
        if (Math.min(cs, cn) < CLEARANCE - 0.01) return fail(why, String.format(Locale.ROOT, "overpass clearance %.2f m", Math.min(cs, cn)));
        // H's structures under the attached stretches and the crossing
        double[][] onH = {{sc - SPAN - 20, sc + SPAN + 20}, range(far, nQ0, qOn(far, n, nNose)), range(far, qOn(far, s, sNose), qOn(far, s, s.len)),
                range(near, bQ, qOn(near, b, bNose1)), range(near, qOn(near, a, aNose2), qOn(near, a, a.len))};
        double c0 = Double.POSITIVE_INFINITY, c1 = Double.NEGATIVE_INFINITY;
        for (double[] r : onH) {
            if (Double.isNaN(r[0]) || Double.isNaN(r[1])) return fail(why, "an attached stretch is off H");
            c0 = Math.min(c0, r[0]);
            c1 = Math.max(c1, r[1]);
            for (Structure t : h.route.structures())
                if (t.s1() >= r[0] - 40 && t.s0() <= r[1] + 40)
                    return fail(why, "an interstate " + t.kind().toLowerCase(Locale.ROOT) + String.format(Locale.ROOT, " at s %.0f under the ramps", t.s0()));
        }
        // other roads keep clear of everything new
        List<double[]> pts = new ArrayList<>();
        for (Ramp r : all) for (int i = 0; i < r.xs.length; i++) pts.add(new double[]{r.xs[i], r.zs[i]});
        String near2 = others(out, added, pts, h.route, q);
        if (near2 != null) return fail(why, "comes near " + near2);
        // zones
        List<RoadNetworkBuild.Zone> zs = new ArrayList<>();
        double inf = Double.POSITIVE_INFINITY;
        String hb = RoadJunctions.base(h.route.id());
        List<String> mine = List.of(RoadJunctions.base(q.id()), sId, nId, aId, bId);
        for (String r : mine) {
            for (String o : mine) if (!r.equals(o) || r.equals(aId) || r.equals(bId) || r.equals(loopId)) zs.add(new RoadNetworkBuild.Zone(r, o, -inf, inf));
            if (r.equals(mine.get(0))) continue;
            zs.add(new RoadNetworkBuild.Zone(r, hb, -inf, inf));
            zs.add(new RoadNetworkBuild.Zone(hb, r, c0 - 300, c1 + 300));
        }
        String line = String.format(Locale.ROOT, "crossing s %.0f, stem %.0f m, main movement toward %s stations: %s, %s; overpasses: left-in deck %.2f clearance %.2f, left-out deck %.2f clearance %.2f; on H s %.0f..%.0f",
                sc, stem, tau > 0 ? "rising" : "falling", lambda < 0 ? "left-in semi-direct" : "left-in loop", lambda < 0 ? "left-out loop" : "left-out semi-direct",
                sd.datum(s.tc), cs, nd.datum(n.tc), cn, c0, c1);
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            lines.add(String.format(Locale.ROOT, "%s: %.0f m, radius >= %.0f, grade <= %.1f %%, free K >= %.0f%s", all[i].id, all[i].len, minR[i],
                    stats[i][0] * 100, stats[i][1], i < 2 ? String.format(Locale.ROOT, ", overpass s %.0f..%.0f", all[i].tc - SPAN, all[i].tc + SPAN) : ""));
        return new Built(qc, ramps, zs, new Claim(h.route.id(), sc, c0, c1), line, lines);
    }

    /**
     * Where two of a site's roads (not pieces of one ramp, nor a ramp and the ramp it is attached to) come within GAP of
     * each other's paved band at grade (their pavements less than CLEARANCE + RAMP_DECK apart), away from Q's end (within
     * END_REGION of qEnd they lie side by side on Q's lanes), or null.
     */
    private static String crossings(List<RoadDesign> ds, double[] qEnd) {
        for (RoadDesign r : ds)
            for (RoadDesign o : ds) {
                if (r == o || RoadJunctions.base(r.id).equals(RoadJunctions.base(o.id))) continue;
                if (r.attach != null && r.attach.host().equals(RoadJunctions.base(o.id)) || o.attach != null && o.attach.host().equals(RoadJunctions.base(r.id))) continue;
                for (double t = 0; t <= r.length; t += 4)
                    for (double u = -r.section.edge; u <= r.section.edge + 1e-9; u += r.section.edge) {
                        double[] p = r.point(t, u);
                        if (Math.hypot(p[0] - qEnd[0], p[1] - qEnd[1]) < END_REGION) continue;
                        double[] l = o.curve.local(p[0], p[1], o.section.edge + GAP, 0);
                        if (l == null || l[0] < 0 || l[0] > o.length || o.section.divided && Math.abs(l[1]) < o.section.halfMedian - GAP) continue;
                        if (Math.abs(r.datum(t) - o.datum(l[0])) < CLEARANCE + RoadNetworkJson.RAMP_DECK)
                            return String.format(Locale.ROOT, "%s meets %s at s %.0f", name(r.id), name(o.id), t);
                    }
            }
        return null;
    }

    private static String name(String id) { return id.startsWith("system:") ? id.substring(id.lastIndexOf(':') + 1) : "Q"; }

    /**
     * Whether every ramp design keeps its paved band GAP off H's, at H's level (within CLEARANCE of its pavement: not on a
     * deck over it): a ramp attached to H from where its band first gets GAP off H's (walked away from its attached end),
     * any other everywhere; null, else why not.
     */
    private static String offH(List<RoadDesign> ds, Interstate h) {
        RoadDesign hd = h.design;
        for (RoadDesign r : ds) {
            if (!r.id.startsWith("system:")) continue;
            boolean onH = r.attach != null && r.attach.host().equals(h.route.id()), back = onH && r.attach.atEnd();
            boolean apart = !onH;
            double reach = hd.section.edge + r.section.edge + GAP;
            for (double i = 0; i <= r.length + 1e-9; i += 2) {
                double t = back ? r.length - i : i;
                double[] p = r.point(t, 0), l = hd.curve.local(p[0], p[1], reach + 10, 0);
                double gap = l == null || l[0] < 0 || l[0] > hd.length ? Double.POSITIVE_INFINITY : Math.abs(l[1]) - hd.section.edge - r.section.edge;
                if (gap >= GAP) { apart = true; continue; }
                if (!apart) continue;
                double hu = Math.copySign(Math.max(hd.section.halfMedian, Math.min(Math.abs(l[1]), hd.section.edge)), l[1]);
                if (r.pavementY(t, 0) - hd.pavementY(l[0], hu) < CLEARANCE)
                    return String.format(Locale.ROOT, "%s runs onto H at s %.0f", name(r.id), t);
            }
        }
        return null;
    }

    /** Every 10th point of a left-turn ramp's path at grade: off its deck and its leads (|y| over SPAN + 4), END_REGION off Q's end. */
    private static Guard guard(List<double[]> pts, Cross x, double[] qEnd) {
        Guard g = new Guard();
        for (int i = 0; i < pts.size(); i += 10) {
            double[] p = pts.get(i);
            if (Math.abs(x.y(p)) > SPAN + 4 && Math.hypot(p[0] - qEnd[0], p[1] - qEnd[1]) > END_REGION) g.add(p);
        }
        return g;
    }

    /**
     * Whether a direct ramp's path (pts; walked from its split, or from its merge: rev) keeps to the right of its own
     * left-turn ramp own (its lateral offset at least SPLIT_W - 0.75 while within 50 m of it) and never comes back within d
     * once d off it (its deck and leads aside: the direct ramp's end runs under them), keeps d off the other's guard, and
     * never comes nearer H than its taper's lead (W_LEAD - H_TOL off H's centreline on its side, lane: where H bends toward
     * the ramp a straight would run back onto it).
     */
    private static boolean clearOf(List<double[]> pts, boolean rev, Ramp own, Guard other, double d, Lane lane) {
        boolean apart = false;
        int n = pts.size(), offH = -1;
        for (int k = 0; k < n; k += 5) {
            double[] p = pts.get(rev ? n - 1 - k : k), lh = lane.local(p);
            if (offH < 0 && lh != null && lh[1] < W_LEAD - H_TOL) offH = rev ? n - 1 - k : k;
            double[] l = own.rc.local(p[0], p[1], 50, 0);
            if (l == null) apart = true;
            else if (Math.abs(l[0] - own.tc) > SPAN + LEAD) {
                if (l[1] < SPLIT_W - 0.75) return false;
                if (l[1] >= d) apart = true;
                else if (apart) return false;
            }
        }
        if (!other.clear(pts, d)) return false;
        if (offH >= 0 && Boolean.getBoolean("afl.debugSystems")) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < n; j += Math.max(1, n / 40)) {
                double[] o = lane.local(pts.get(j));
                sb.append(o == null ? " -" : String.format(Locale.ROOT, " %.1f", o[1]));
            }
            System.out.printf(Locale.ROOT, "[systems] %s's direct ramp runs back onto H at point %d of %d; w along:%s%n", own.name, offH, n, sb);
        }
        return offH < 0;
    }

    /** Points to keep clear of (a direct ramp's curve of the left-turn ramps), bucketed by CELL m. */
    private static final class Guard {
        static final double CELL = 16;
        final java.util.Map<Long, List<double[]>> cells = new java.util.HashMap<>();

        void add(double[] p) { cells.computeIfAbsent(key(p[0], p[1]), k -> new ArrayList<>()).add(p); }

        static long key(double x, double z) { return ((long) Math.floor(x / CELL) << 32) ^ ((long) Math.floor(z / CELL) & 0xffffffffL); }

        /** Whether every 10th point of the path keeps d off every point (d at most CELL). */
        boolean clear(List<double[]> pts, double d) {
            for (int i = 0; i < pts.size(); i += 10) {
                double[] p = pts.get(i);
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++) {
                        List<double[]> l = cells.get(key(p[0] + dx * CELL, p[1] + dz * CELL));
                        if (l != null) for (double[] o : l) if (Math.hypot(o[0] - p[0], o[1] - p[1]) < d) return false;
                    }
            }
            return true;
        }
    }

    /** The shortest stem curve (the largest R_STEM that fits, arcs under 100 deg) from a to b, or null. */
    private static Csc stem(Pose a, Pose b) {
        for (double r : R_STEM) {
            Csc c = bestCsc(a, b, r, Math.toRadians(100));
            if (c != null) return c;
        }
        return null;
    }

    /**
     * The loop of an entering left-turn ramp down its leg (frame x xL) to y, turning right R_LOOP until it heads in at
     * 1:TAPER: {q, w, turn} of its end on the far carriageway.
     */
    private static double[] loopEnd(Cross x, Lane far, double xL, double y) {
        double th0 = x.thY() + Math.PI;
        double[] p0 = x.at(xL, y);
        double cx = p0[0] - R_LOOP * Math.sin(th0), cz = p0[1] + R_LOOP * Math.cos(th0);
        double a = norm(x.thX() - T0 - th0);
        double[] l = null;
        for (int it = 0; it < 6; it++) {
            double h = th0 + a;
            double[] p = {cx + R_LOOP * Math.sin(h), cz - R_LOOP * Math.cos(h)};
            l = far.local(p);
            if (l == null) return new double[]{Double.NaN, Double.NaN, Double.NaN};
            a = norm(far.pose(l[0], l[1], -T0).th - th0);
        }
        double h = th0 + a;
        double[] p = {cx + R_LOOP * Math.sin(h), cz - R_LOOP * Math.cos(h)};
        l = far.local(p);
        return new double[]{l[0], l[1], a};
    }

    /** The least radius of curve c over t0 .. t1 (every 2 m). */
    private static double minRadius(RoadCurve c, double t0, double t1) {
        double[] f = new double[5];
        double k = 0;
        for (double t = Math.max(0, t0); t <= Math.min(c.length, t1) + 1e-9; t += 2) {
            c.frame(t, f);
            k = Math.max(k, Math.abs(f[4]));
        }
        return k < 1e-12 ? Double.POSITIVE_INFINITY : 1 / k;
    }

    /** The pose at the start of curve c, off its centreline by u. */
    private static Pose start(RoadCurve c, double u) {
        double[] f = new double[5];
        c.frame(0, f);
        double[] p = c.point(0, u);
        return new Pose(p[0], p[1], Math.atan2(f[3], f[2]));
    }

    /** The pose at the end of curve c, off its centreline by u. */
    private static Pose end(RoadCurve c, double u) {
        double[] f = new double[5];
        c.frame(c.length, f);
        double[] p = c.point(c.length, u);
        return new Pose(p[0], p[1], Math.atan2(f[3], f[2]));
    }

    /**
     * A direct ramp's {paint, nose} on its left-turn ramp (host): where its lane edge leaves the host's (its centreline
     * the two half lanes off) and where its paved edge leaves the host's; split (start: right-in) or merge (end:
     * right-out). Null where it never does.
     */
    private static double[] splitNoses(Ramp r, Ramp host, boolean start) {
        double lanes = 2 * CrossSection.RAMP.lanesOut, edges = 2 * CrossSection.RAMP.edge, paint = Double.NaN, nose = Double.NaN;
        for (int i = 0; i * 0.5 <= r.len; i++) {
            double t = start ? i * 0.5 : r.len - i * 0.5;
            double[] p = r.rc.point(t, 0), l = host.rc.local(p[0], p[1], 40, 0);
            if (l == null) break;
            if (Double.isNaN(paint) && Math.abs(l[1]) >= lanes) paint = t;
            if (Math.abs(l[1]) >= edges) { nose = t; break; }
        }
        return Double.isNaN(paint) || Double.isNaN(nose) ? null : new double[]{start ? paint : r.len - 0.01, nose};
    }

    /** The H stations of canonical q0 .. q1 on a carriageway. */
    private static double[] range(Lane l, double q0, double q1) {
        double a = l.sc + l.tau * q0, b = l.sc + l.tau * q1;
        return new double[]{Math.min(a, b), Math.max(a, b)};
    }

    /** The canonical q of ramp r's station t on a carriageway, NaN off it. */
    private static double qOn(Lane l, Ramp r, double t) {
        double[] q = l.local(r.rc.point(t, 0));
        return q == null ? Double.NaN : q[0];
    }

    /** Ramp r as route id: its plan, profile (to mm) and slopes, its overpass (within the trim window), attachment, trim window and open ends. */
    private static RouteData route(String id, Ramp r, RoadDesign.Attach at, double[] trim, boolean[] open) {
        double[] e = new double[r.m], k = new double[r.m];
        for (int i = 0; i < r.m; i++) {
            e[i] = RoadNetworkJson.round3(r.e[i]);
            k[i] = Math.round(r.k[i] * 1e4) / 1e4;
        }
        // a piece keeps the structures within its trim window (the loop's deck is on one of its pieces)
        List<Structure> st = new ArrayList<>();
        for (Structure t : r.structures) if (trim == null || t.s1() > trim[0] && t.s0() < trim[1]) st.add(t);
        return new RouteData(id, "ramp", r.corridor, r.from, r.to, r.xs, r.zs, e, List.copyOf(st), trim, List.of(), List.of(),
                new String[2], new double[0], k, at, open);
    }

    /**
     * Q cut at station cut (its end on H atEnd: kept up to it, else from it on, renumbered): the control points up to 15
     * m short of the cut and the cut's point, the profile on the 2 m lattice, its structures and corners within; the cut
     * end open.
     */
    private static RouteData cutQ(RouteData q, Interstate iq, double cut, boolean atEnd) {
        List<double[]> pts = new ArrayList<>();
        double[] pc = iq.curve.point(cut, 0);
        if (!atEnd) pts.add(pc);
        for (int i = 0; i < q.xs().length; i++) {
            double[] l = iq.curve.local(q.xs()[i], q.zs()[i], 5, 0);
            double t = l == null ? (i == 0 ? 0 : iq.length) : l[0];
            if (atEnd ? t < cut - 15 : t > cut + 15) pts.add(new double[]{q.xs()[i], q.zs()[i]});
        }
        if (atEnd) pts.add(pc);
        double[] xs = new double[pts.size()], zs = new double[pts.size()];
        for (int i = 0; i < xs.length; i++) { xs[i] = RoadNetworkJson.round2(pts.get(i)[0]); zs[i] = RoadNetworkJson.round2(pts.get(i)[1]); }
        double len = new RoadCurve(xs, zs).length, off = atEnd ? 0 : cut;
        int m = (int) Math.ceil(len / DS - 1e-9) + 1, i0 = (int) Math.round(off / DS);
        double[] e = new double[m];
        for (int i = 0; i < m; i++) e[i] = q.profile()[Math.min(q.profile().length - 1, i0 + i)];
        List<Structure> ss = new ArrayList<>();
        for (Structure s : q.structures()) {
            double a = s.s0() - off, b = s.s1() - off;
            if (a >= 0 && b <= len) ss.add(new Structure(s.kind(), a, b, s.height()));
        }
        double[] corners = Arrays.stream(q.corners()).map(c -> c - off).filter(c -> c >= 0 && c <= len).toArray();
        return new RouteData(q.id(), q.roadClass(), q.corridor(), q.from(), q.to(), xs, zs, e, List.copyOf(ss), null, List.of(), List.of(),
                new String[2], corners, null, null, atEnd ? new boolean[]{false, true} : new boolean[]{true, false});
    }
}
