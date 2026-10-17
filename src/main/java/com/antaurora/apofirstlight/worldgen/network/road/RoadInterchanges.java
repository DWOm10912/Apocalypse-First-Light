package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson.RouteData;
import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson.Structure;
import com.antaurora.apofirstlight.worldgen.network.route.RoadClassSpec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The diamond interchanges of the built network (C3.3b group 2; docs/worldgen/federal_interchanges_c3_3b.md), at build
 * time (RoadNetworkBuild, before the T junctions): a state road whose end lies on an interstate's centreline (the
 * planner's join) away from the interstate's ends becomes a crossroad over the interstate with four one-way ramps.
 * <ul>
 *   <li>sites: such ends within TOUCH; two from opposite sides within PAIR along the interstate are one interchange at
 *   their middle (both roads realigned onto one crossroad); the crossing moves along the interstate by up to SHIFT
 *   (SHIFT_STEP) until the interstate keeps the footprint (FOOT each way) clear of its structures and of other
 *   interchanges; a site whose roads would come within OTHERS of another road is skipped (the C3.3a trim stays);</li>
 *   <li>the crossroad: the state road's last stretch realigned (a cubic Bezier from BACK m back, radius at least
 *   MIN_RADIUS) onto the interstate's normal at the crossing, D + APPROACH off its centreline, then straight across: on to
 *   the other road's realignment, or (one road) STUB beyond where its profile is back on the ground's proxy (the
 *   interstate's datum at the crossing); its profile lifted so the overpass's soffit keeps CLEARANCE over the interstate's
 *   pavement (cones at CROSS_GRADE, vertical curves of at least CROSS_K) with an OVERPASS structure of two SPAN spans;</li>
 *   <li>the ramps (CrossSection.RAMP): in the interstate's (s, u) frame, an exit leaves the outer lane at 1:TAPER, curves
 *   out and back (RAMP_RADIUS reverse curves) to run PARALLEL m at D off the centreline into the crossroad; an entrance
 *   mirrors it, joins at 1:TAPER, runs an ACCEL lane and tapers out over MERGE; while attached (taper to nose) a ramp lies
 *   in the interstate's outer shoulder plane (#hExt), beyond it its own profile (vertical curves of RAMP_K_DESIGN round a
 *   straight grade, at most RAMP_GRADE) lands
 *   at the crossroad as a T junction's minor (Junction, its landing at most Junction.LANDING_GRADE);</li>
 *   <li>zones: the ramps' conflicts with their interstate, crossroad and each other, the crossroad's with the interstate
 *   near the crossing, are ignored by the trims, splits and verify (the interchange joins them).</li>
 * </ul>
 */
final class RoadInterchanges {
    private RoadInterchanges() {}

    /** A state road's end within TOUCH of an interstate's centreline; two from opposite sides within PAIR are one site, m. */
    static final double TOUCH = 10, PAIR = 1300;
    /** The ramps' parallel run off the interstate's centreline (their terminals); the crossroad reaches the normal APPROACH beyond, m. */
    static final double D = 90, APPROACH = 70;
    static final double SHIFT = 300, SHIFT_STEP = 10;
    /** Full designs tried per site (the footprint check before each is cheap). */
    static final int MAX_ATTEMPTS = 8;
    /** One road: its far stub's extent before trimming, the run on beyond its landing, m. */
    static final double FAR = 700, STUB = 30;
    /** The overpass: half its length (two spans on one pier at the crossing), the soffit's clearance over the interstate's pavement, m. */
    static final double SPAN = 44, CLEARANCE = 5.3, CLEAR_MARGIN = 0.1;
    /** The crossroad's grade off the overpass and its crest K; the steepest it may be at a ramp terminal. */
    static final double CROSS_GRADE = 0.03, CROSS_K = 26, TERMINAL_GRADE = 0.035;
    /**
     * Ramp plan: taper 1:TAPER over TAPER_RUN (q), reverse curves to RAMP_ANGLE degrees, parallel run, acceleration lane,
     * merge taper (two SOFT_MERGE arcs over about MERGE), the turn into the acceleration lane on a SOFT_ACCEL arc, m.
     */
    static final double TAPER = 20, TAPER_RUN = 210, RAMP_ANGLE = 25, PARALLEL = 50, ACCEL = 200, MERGE = 90, SOFT_ACCEL = 1000, SOFT_MERGE = 500;
    /**
     * The reverse curves' radii in the interstate's frame, tried in turn: the interstate's own curvature adds to a ramp's
     * where both turn one way (2026-10-16: R 200 became 138 m in the world on a 640 m interstate curve), so the first whose
     * world radius keeps RAMP_MIN_RADIUS is built, m.
     */
    static final double[] RAMP_RADII = {200, 230, 260, 300};
    /**
     * Ramp design (60 km/h): the free stretch's steepest grade (AASHTO's upper value; where the interstate is steeper at the
     * nose, its grade there), smallest crest / sag K, the K its vertical curves are designed with, smallest radius.
     */
    static final double RAMP_GRADE = 0.06, RAMP_K = 18, RAMP_K_DESIGN = 25, RAMP_MIN_RADIUS = 180;
    /** The ramp's lane slope turns from the interstate's shoulder plane to its own over this beyond the nose, m. */
    static final double NOSE_BLEND = 40;
    /** The crossroad's realignment (RoadJunctions#realign's search): Bezier starts back, smallest radius, radius scored up to, point spacing, m. */
    static final double[] BACK = {200, 260, 320, 400, 500, 650, 800};
    static final double MIN_RADIUS = 150, SCORE_RADIUS = 300, POINT = 10, REALIGN_CLEAR = 20;
    /** Other roads keep this clear of an interchange's new roads (their centrelines from its points, beyond their paved edge), m. */
    static final double OTHERS = RoadWorks.REACH + 10;
    /** Fine turtle step of the ramp plan, m. */
    private static final double FINE = 0.5;

    static final CrossSection I_SEC = CrossSection.R4, RAMP_SEC = CrossSection.RAMP;
    /** The ramp's centreline at the taper (its right lane edge on the outer lane line), at the painted nose, at the physical nose. */
    static final double W0 = I_SEC.lanesOut - RAMP_SEC.lanesOut, W_PAINT = I_SEC.lanesOut + RAMP_SEC.lanesOut, W_NOSE = I_SEC.edge + RAMP_SEC.edge;

    /** A ramp's plan in the interstate's frame (traffic toward +q, on the +w side): control points, the painted and the physical nose. */
    record Path(double[] q, double[] w, double[] paint, double[] nose) {
        double first() { return q[0]; }

        double last() { return q[q.length - 1]; }
    }

    static final Path[] EXITS = paths(true), ENTRIES = paths(false);
    /**
     * The interchange's reach along the interstate each way (the farther ramp end at the largest radius plus 40 m); the
     * crossing keeps END_CLEAR from the interstate's ends, m.
     */
    static final double FOOT = Math.max(-EXITS[EXITS.length - 1].first(), ENTRIES[ENTRIES.length - 1].last()) + 40, END_CLEAR = FOOT + 40;

    record Result(List<RouteData> roads, List<RoadJunctions.Spec> terminals, List<RoadNetworkBuild.Zone> zones) {}

    /** A state road's end (its point) on an interstate: the road's index, the interstate's, its station there, the side it comes from. */
    private record Candidate(int road, double x, double z, int interstate, double s, int side) {}

    private record Interstate(RouteData route, RoadCurve curve, RoadDesign design, double length) {}

    private record Ramp(RouteData route, RoadJunctions.Spec terminal, String line) {}

    private record Site(RouteData crossroad, List<Ramp> ramps, List<RoadNetworkBuild.Zone> zones, double s0, double s1, String line) {}

    private static double profileLength(RouteData r) { return (r.profile().length - 1) * RoadProfile.DS; }

    // ------------------------------------------------------------------ sites

    static Result build(List<RouteData> roads, List<String> report) {
        List<RouteData> out = new ArrayList<>(roads);
        // offline baseline (InterchangeCheck -PnoInterchanges): the network as group 1 builds it
        if (Boolean.getBoolean("afl.noInterchanges")) return new Result(out, List.of(), List.of());
        Map<Integer, Interstate> is = new HashMap<>();
        for (int j = 0; j < out.size(); j++) {
            RouteData q = out.get(j);
            if (!q.roadClass().equals("interstate")) continue;
            RoadCurve qc = new RoadCurve(q.xs(), q.zs());
            is.put(j, new Interstate(q, qc, RoadNetworkJson.design(q), Math.min(qc.length, profileLength(q))));
        }
        List<Candidate> cands = new ArrayList<>();
        for (int i = 0; i < out.size(); i++) {
            RouteData r = out.get(i);
            if (!r.roadClass().equals("state")) continue;
            RoadCurve rc = new RoadCurve(r.xs(), r.zs());
            double len = Math.min(rc.length, profileLength(r));
            int n = r.xs().length;
            for (boolean atEnd : new boolean[]{false, true}) {
                double px = atEnd ? r.xs()[n - 1] : r.xs()[0], pz = atEnd ? r.zs()[n - 1] : r.zs()[0];
                for (var e : is.entrySet()) {
                    double[] l = e.getValue().curve.local(px, pz, TOUCH, 0);
                    if (l == null) continue;
                    double[] back = rc.point(atEnd ? len - 60 : 60, 0), lb = e.getValue().curve.local(back[0], back[1], 400, 0);
                    if (lb == null) continue;
                    cands.add(new Candidate(i, px, pz, e.getKey(), l[0], lb[1] > 0 ? 1 : -1));
                }
            }
        }
        // pairs: opposite sides of one interstate, different roads, the nearest within PAIR
        boolean[] paired = new boolean[cands.size()];
        List<Candidate[]> sites = new ArrayList<>();
        for (int a = 0; a < cands.size(); a++) {
            if (paired[a]) continue;
            Candidate ca = cands.get(a);
            int best = -1;
            for (int b = a + 1; b < cands.size(); b++) {
                Candidate cb = cands.get(b);
                if (paired[b] || cb.interstate != ca.interstate || cb.side == ca.side || cb.road == ca.road || Math.abs(cb.s - ca.s) > PAIR) continue;
                if (best < 0 || Math.abs(cb.s - ca.s) < Math.abs(cands.get(best).s - ca.s)) best = b;
            }
            paired[a] = true;
            if (best >= 0) paired[best] = true;
            sites.add(best >= 0 ? new Candidate[]{ca, cands.get(best)} : new Candidate[]{ca});
        }
        List<RoadJunctions.Spec> terminals = new ArrayList<>();
        List<RoadNetworkBuild.Zone> zones = new ArrayList<>();
        List<RouteData> added = new ArrayList<>();
        Map<Integer, List<double[]>> claimed = new HashMap<>();
        // a road merged into another's crossroad (a pair's second road) lives on in that one
        int[] alias = new int[out.size()];
        for (int i = 0; i < alias.length; i++) alias[i] = i;
        int built = 0;
        for (Candidate[] st : sites) {
            Candidate a = st[0], b = st.length > 1 ? st[1] : null;
            Interstate c = is.get(a.interstate);
            String what = String.format(Locale.ROOT, "%s on %s s %.0f%s", roads.get(a.road).id(), c.route.id(), a.s,
                    b == null ? "" : String.format(Locale.ROOT, " + %s s %.0f", roads.get(b.road).id(), b.s));
            int ai = root(alias, a.road), bi = b == null ? -1 : root(alias, b.road);
            int ea = endAt(out.get(ai), a), eb = b == null ? 0 : endAt(out.get(bi), b);
            if (ai == bi || ea < 0 || eb < 0) {
                report.add("interchange skipped (trimmed): " + what + (ai == bi ? ": both ends of one road" : ": the road's end there is gone"));
                continue;
            }
            double sm = b == null ? a.s : (a.s + b.s) / 2;
            String first = null;
            List<String> why = new ArrayList<>();
            Site site = null;
            int attempts = 0;
            for (int k = 0; k <= 2 * (int) Math.round(SHIFT / SHIFT_STEP) && site == null; k++) {
                double sc = sm + (k % 2 == 1 ? 1 : -1) * ((k + 1) / 2) * SHIFT_STEP;
                String f = footprint(c, a.interstate, sc, claimed);
                if (f != null) {
                    if (first == null) first = f;
                    continue;
                }
                if (attempts++ >= MAX_ATTEMPTS) break;
                String[] w = new String[1];
                site = site(out, added, ai, ea == 1, a.side, bi, eb == 1, b == null ? 0 : b.side, sc, c, w);
                if (site == null && !why.contains(w[0])) why.add(String.format(Locale.ROOT, "%+.0f: %s", sc - sm, w[0]));
            }
            if (site == null) {
                report.add("interchange skipped (trimmed): " + what + ": " + (why.isEmpty() ? first : String.join("; ", why)));
                continue;
            }
            // the earlier interchanges on these roads: their stations onto the new crossroad
            remap(out.get(ai), site.crossroad, terminals, zones);
            if (bi >= 0) remap(out.get(bi), site.crossroad, terminals, zones);
            out.set(ai, site.crossroad);
            if (bi >= 0) { out.set(bi, null); alias[bi] = ai; }
            for (Ramp r : site.ramps) { added.add(r.route); terminals.add(r.terminal); }
            zones.addAll(site.zones);
            claimed.computeIfAbsent(a.interstate, k -> new ArrayList<>()).add(new double[]{site.s0, site.s1});
            built++;
            report.add("interchange: " + what + ": " + site.line);
            for (Ramp r : site.ramps) report.add("  " + r.line);
        }
        List<RouteData> roadsOut = new ArrayList<>();
        for (RouteData r : out) if (r != null) roadsOut.add(r);
        roadsOut.addAll(added);
        report.add(String.format(Locale.ROOT, "interchanges: %d built of %d sites (%d candidate ends)", built, sites.size(), cands.size()));
        return new Result(roadsOut, terminals, zones);
    }

    /** Why the interstate cannot take an interchange crossing at sc (its ends, its structures, another interchange), or null. */
    private static String footprint(Interstate c, int ii, double sc, Map<Integer, List<double[]>> claimed) {
        if (sc < END_CLEAR || sc > c.length - END_CLEAR) return "too near the interstate's end";
        for (Structure s : c.route.structures())
            if (s.s1() >= sc - FOOT && s.s0() <= sc + FOOT) return "an interstate " + s.kind().toLowerCase(Locale.ROOT) + " within the footprint";
        for (double[] f : claimed.getOrDefault(ii, List.of())) if (f[0] < sc + FOOT && f[1] > sc - FOOT) return "overlaps another interchange";
        return null;
    }

    private static int root(int[] alias, int i) {
        while (alias[i] != i) i = alias[i];
        return i;
    }

    /** Which end of r is the candidate's (1: its end, 0: its start), -1: neither any more. */
    private static int endAt(RouteData r, Candidate c) {
        int n = r.xs().length;
        if (Math.hypot(r.xs()[n - 1] - c.x, r.zs()[n - 1] - c.z) < 1) return 1;
        if (Math.hypot(r.xs()[0] - c.x, r.zs()[0] - c.z) < 1) return 0;
        return -1;
    }

    /**
     * An earlier interchange's terminals and zones on road old (its crossroad), carried onto neu (old rebuilt with another
     * interchange at its other end): stations by projection (old's other end is unchanged), its id renamed.
     */
    private static void remap(RouteData old, RouteData neu, List<RoadJunctions.Spec> specs, List<RoadNetworkBuild.Zone> zones) {
        String oid = old.id(), nid = neu.id();
        RoadCurve oc = new RoadCurve(old.xs(), old.zs()), nc = new RoadCurve(neu.xs(), neu.zs());
        for (int i = 0; i < specs.size(); i++) {
            RoadJunctions.Spec j = specs.get(i);
            if (!j.host().equals(oid)) continue;
            specs.set(i, new RoadJunctions.Spec(j.minor(), j.atEnd(), nid, j.endLine(), station(oc, nc, j.mouth()), j.minorZone(), range(oc, nc, j.hostZone()),
                    j.angle(), j.realigned()));
        }
        for (int i = 0; i < zones.size(); i++) {
            RoadNetworkBuild.Zone z = zones.get(i);
            if (z.road().equals(oid)) {
                double[] r = range(oc, nc, new double[]{z.s0(), z.s1()});
                zones.set(i, new RoadNetworkBuild.Zone(nid, z.other(), r[0], r[1]));
            } else if (z.other().equals(oid)) zones.set(i, new RoadNetworkBuild.Zone(z.road(), nid, z.s0(), z.s1()));
        }
    }

    /** Station t of curve oc on curve nc (by projection; t itself when off it). */
    private static double station(RoadCurve oc, RoadCurve nc, double t) {
        double c = Math.max(0, Math.min(oc.length, t));
        double[] p = oc.point(c, 0), l = nc.local(p[0], p[1], 3, 0);
        return l == null ? t : l[0] + (t - c);
    }

    /** A station range of oc on nc (infinite ends kept, swapped where nc runs the other way). */
    private static double[] range(RoadCurve oc, RoadCurve nc, double[] r) {
        double ref = Double.isFinite(r[0]) ? r[0] : Double.isFinite(r[1]) ? r[1] : oc.length / 2;
        boolean flip = station(oc, nc, ref + 10) < station(oc, nc, ref);
        double a = Double.isFinite(r[0]) ? station(oc, nc, r[0]) : flip ? -r[0] : r[0];
        double b = Double.isFinite(r[1]) ? station(oc, nc, r[1]) : flip ? -r[1] : r[1];
        return new double[]{Math.min(a, b), Math.max(a, b)};
    }

    private static <T> T fail(String[] why, String w) {
        why[0] = w;
        return null;
    }

    /** r driven so that its end at the interstate is its end (atEnd: as it is), its town corners carried over. */
    private static RouteData toward(RouteData r, boolean atEnd) {
        if (atEnd) return r;
        RouteData v = RoadNetworkBuild.reverse(r);
        double len = new RoadCurve(r.xs(), r.zs()).length;
        double[] cs = new double[r.corners().length];
        for (int i = 0; i < cs.length; i++) cs[i] = RoadNetworkJson.round2(len - r.corners()[r.corners().length - 1 - i]);
        return new RouteData(v.id(), v.roadClass(), v.corridor(), v.from(), v.to(), v.xs(), v.zs(), v.profile(), v.structures(), null, List.of(),
                List.of(), new String[2], cs);
    }

    // ------------------------------------------------------------------ one site

    /**
     * The interchange crossing the interstate at sc of road ai (its end at the interstate atEnd, coming from side) and,
     * paired, road bi (-1: none); null and why[0] where it cannot be built.
     */
    private static Site site(List<RouteData> out, List<RouteData> added, int ai, boolean aEnd, int aSide, int bi, boolean bEnd, int bSide, double sc,
                             Interstate c, String[] why) {
        RouteData a = toward(out.get(ai), aEnd), b = bi < 0 ? null : toward(out.get(bi), bEnd);
        double[] backA = new double[1], backB = new double[1];
        List<double[]> pa = approach(a, c, sc, aSide, backA, why);
        if (pa == null) return fail(why, "no realignment of " + a.id() + " fits (" + why[0] + ")");
        List<double[]> pb = null;
        if (b != null) {
            pb = approach(b, c, sc, bSide, backB, why);
            if (pb == null) return fail(why, "no realignment of " + b.id() + " fits (" + why[0] + ")");
        }
        // the crossroad's control polyline: a's approach, the normal across, b's approach back (or the far stub)
        List<double[]> pts = new ArrayList<>(pa);
        double uFrom = aSide * (D + APPROACH), uTo = b != null ? bSide * (D + APPROACH) : -aSide * FAR;
        int k = (int) Math.round(Math.abs(uTo - uFrom) / POINT);
        for (int i = 1; i <= k; i++) pts.add(c.curve.point(sc, uFrom + (uTo - uFrom) * i / k));
        if (pb != null) for (int i = pb.size() - 2; i >= 0; i--) pts.add(pb.get(i));
        double[][] xz = polyline(pts);
        RoadCurve nc = new RoadCurve(xz[0], xz[1]);
        double[] c0 = c.curve.point(sc, 0), lc = nc.local(c0[0], c0[1], 5, 0);
        if (lc == null) return fail(why, "the crossroad misses the crossing");
        double xc = lc[0];
        // the profile: the old roads' heights on their lines, linear between, smoothed where new
        RoadCurve oa = new RoadCurve(a.xs(), a.zs()), ob = b == null ? null : new RoadCurve(b.xs(), b.zs());
        RoadProfile fa = RoadProfile.of(a.profile()), fb = b == null ? null : RoadProfile.of(b.profile());
        int ms = RoadProfile.samples(nc.length);
        double[] e = new double[ms];
        boolean[] fresh = new boolean[ms];
        for (int i = 0; i < ms; i++) {
            double[] p = nc.point(Math.min(nc.length, i * RoadProfile.DS), 0);
            double[] l = oa.local(p[0], p[1], RoadNetworkBuild.ON_LEG, 0);
            if (l != null && l[0] <= profileLength(a)) { e[i] = fa.at(l[0]); continue; }
            if (ob != null && (l = ob.local(p[0], p[1], RoadNetworkBuild.ON_LEG, 0)) != null && l[0] <= profileLength(b)) { e[i] = fb.at(l[0]); continue; }
            e[i] = Double.NaN;
            fresh[i] = true;
        }
        if (Double.isNaN(e[ms - 1])) e[ms - 1] = c.design.datum(sc);
        fill(e);
        smoothFresh(e, fresh);
        double[] e0 = e.clone();
        double[] f = overpass(e, nc, xc, c);
        // one road: the stub ends STUB beyond where the profile is back down
        if (b == null) {
            double land = Double.NaN;
            for (int i = 0; i < ms; i++) {
                double t = i * RoadProfile.DS;
                if (t >= xc + D + 60 && Math.abs(f[i] - e0[i]) < 0.3) { land = t; break; }
            }
            if (Double.isNaN(land)) return fail(why, "the far stub does not come back down");
            double end = Math.ceil((land + STUB) / POINT) * POINT;
            List<double[]> kept = new ArrayList<>();
            for (double[] p : pts) {
                double[] l = nc.local(p[0], p[1], 2, 0);
                if (l == null || l[0] <= end + 1e-6) kept.add(p);
                else break;
            }
            xz = polyline(kept);
            nc = new RoadCurve(xz[0], xz[1]);
            ms = RoadProfile.samples(nc.length);
            f = Arrays.copyOf(f, ms);
        }
        for (int i = 0; i < ms; i++) f[i] = RoadNetworkJson.round2(f[i]);
        // structures and corners onto the new line, the overpass
        List<Structure> ss = new ArrayList<>();
        List<Double> corners = new ArrayList<>();
        for (RouteData r : b == null ? List.of(a) : List.of(a, b)) {
            RoadCurve or = new RoadCurve(r.xs(), r.zs());
            for (Structure s : r.structures()) {
                double[] p0 = or.point(s.s0(), 0), p1 = or.point(s.s1(), 0);
                double[] l0 = nc.local(p0[0], p0[1], 5, 0), l1 = nc.local(p1[0], p1[1], 5, 0);
                if (l0 == null || l1 == null) return fail(why, "a structure of " + r.id() + " is off the crossroad");
                ss.add(new Structure(s.kind(), RoadNetworkJson.round2(Math.min(l0[0], l1[0])), RoadNetworkJson.round2(Math.max(l0[0], l1[0])), s.height()));
            }
            for (double cs : r.corners()) {
                double[] p = or.point(cs, 0), l = nc.local(p[0], p[1], 5, 0);
                if (l != null) corners.add(RoadNetworkJson.round2(l[0]));
            }
        }
        double clearance = Double.NaN;
        ss.add(new Structure("OVERPASS", RoadNetworkJson.round2(xc - SPAN), RoadNetworkJson.round2(xc + SPAN), 0));
        ss.sort((p, q) -> Double.compare(p.s0(), q.s0()));
        String id = b == null ? a.id() : a.id() + "+" + b.id();
        RouteData x = new RouteData(id, "state", a.corridor(), a.from(), b == null ? a.to() : b.from(), xz[0], xz[1], f, List.copyOf(ss), null,
                List.of(), List.of(), new String[2], corners.stream().mapToDouble(Double::doubleValue).sorted().toArray());
        RoadDesign xd;
        try {
            xd = RoadNetworkJson.design(x);
        } catch (IllegalArgumentException ex) {
            return fail(why, "the crossroad cannot be designed: " + ex.getMessage());
        }
        clearance = clearance(xd, xc, c);
        if (clearance < CLEARANCE - 0.01) return fail(why, String.format(Locale.ROOT, "overpass clearance %.2f m", clearance));
        for (int side : new int[]{-1, 1})
            for (double t = xc + side * (D - 20); Math.abs(t - xc) <= D + 20; t += side * RoadProfile.DS)
                if (Math.abs(xd.profile.grade(t)) > TERMINAL_GRADE)
                    return fail(why, String.format(Locale.ROOT, "the crossroad is %.1f %% steep at a ramp terminal", Math.abs(xd.profile.grade(t)) * 100));
        // the four ramps
        List<Ramp> ramps = new ArrayList<>();
        for (int tau : new int[]{1, -1})
            for (boolean exit : new boolean[]{true, false}) {
                Ramp r = ramp(c, sc, tau, exit, x, xd, xc, why);
                if (r == null) return null;
                ramps.add(r);
            }
        // other roads keep clear of everything new
        List<double[]> fresh2 = new ArrayList<>();
        for (int i = 0; i < ms; i++) {
            double t = i * RoadProfile.DS;
            double[] p = nc.point(Math.min(nc.length, t), 0);
            double[] l = oa.local(p[0], p[1], RoadNetworkBuild.ON_LEG, 0);
            if (l == null && (ob == null || ob.local(p[0], p[1], RoadNetworkBuild.ON_LEG, 0) == null) && i % 5 == 0) fresh2.add(p);
        }
        for (Ramp r : ramps) for (int i = 0; i < r.route.xs().length; i++) fresh2.add(new double[]{r.route.xs()[i], r.route.zs()[i]});
        String near = others(out, added, fresh2, c.route, out.get(ai), bi < 0 ? null : out.get(bi));
        if (near != null) return fail(why, "comes near " + near);
        // zones
        List<RoadNetworkBuild.Zone> zs = new ArrayList<>();
        String ib = RoadJunctions.base(c.route.id());
        double inf = Double.POSITIVE_INFINITY;
        for (Ramp r : ramps) {
            String rid = r.route.id();
            zs.add(new RoadNetworkBuild.Zone(rid, ib, -inf, inf));
            zs.add(new RoadNetworkBuild.Zone(ib, rid, sc - FOOT, sc + FOOT));
            zs.add(new RoadNetworkBuild.Zone(rid, id, -inf, inf));
            zs.add(new RoadNetworkBuild.Zone(id, rid, xc - (D + 150), xc + (D + 150)));
            for (Ramp o : ramps) if (o != r) zs.add(new RoadNetworkBuild.Zone(rid, o.route.id(), -inf, inf));
        }
        zs.add(new RoadNetworkBuild.Zone(id, ib, xc - (D + 200), xc + (D + 200)));
        zs.add(new RoadNetworkBuild.Zone(ib, id, sc - 300, sc + 300));
        String line = String.format(Locale.ROOT, "crossing s %.0f, crossroad %s (%.0f m%s; realigned %.0f%s m), overpass s %.0f..%.0f deck %.2f, clearance %.2f m, crest K %.0f",
                sc, id, xd.length, b == null ? ", far stub" : "", backA[0],
                b == null ? "" : String.format(Locale.ROOT, " + %.0f", backB[0]), xc - SPAN, xc + SPAN, xd.datum(xc), clearance, crestK(xd, xc));
        return new Site(x, ramps, zs, sc - FOOT, sc + FOOT, line);
    }

    /** Points to rounded control arrays, repeated points dropped. */
    private static double[][] polyline(List<double[]> pts) {
        List<double[]> q = new ArrayList<>();
        for (double[] p : pts) {
            double[] r = {RoadNetworkJson.round2(p[0]), RoadNetworkJson.round2(p[1])};
            if (!q.isEmpty() && Math.hypot(r[0] - q.get(q.size() - 1)[0], r[1] - q.get(q.size() - 1)[1]) < 1) continue;
            q.add(r);
        }
        double[] xs = new double[q.size()], zs = new double[q.size()];
        for (int i = 0; i < xs.length; i++) { xs[i] = q.get(i)[0]; zs[i] = q.get(i)[1]; }
        return new double[][]{xs, zs};
    }

    /** NaN runs filled: linear between known samples, constant beyond the ends. */
    private static void fill(double[] e) {
        int n = e.length;
        for (int i = 0; i < n; ) {
            if (!Double.isNaN(e[i])) { i++; continue; }
            int j = i;
            while (j < n && Double.isNaN(e[j])) j++;
            if (i == 0) for (int q = 0; q < j; q++) e[q] = e[j];
            else if (j == n) for (int q = i; q < n; q++) e[q] = e[i - 1];
            else for (int q = i; q < j; q++) e[q] = e[i - 1] + (e[j] - e[i - 1]) * (q - i + 1) / (j - i + 1);
            i = j;
        }
    }

    /** The state class's smoothing over the new samples (within 40 m of one fully, fading out over 40 m more). */
    private static void smoothFresh(double[] e, boolean[] fresh) {
        int n = e.length;
        double[] sm = RoadProfile.smooth(e, RoadClassSpec.STATE.vSigma() / RoadProfile.DS);
        double[] dist = distances(fresh);
        for (int i = 0; i < n; i++) {
            double d = dist[i] * RoadProfile.DS, w = d <= 40 ? 1 : d >= 80 ? 0 : CrossSection.smooth((80 - d) / 40);
            e[i] += (sm[i] - e[i]) * w;
        }
    }

    /** Distance (samples) to the nearest marked sample. */
    private static double[] distances(boolean[] mark) {
        int n = mark.length;
        double[] d = new double[n];
        for (int i = 0; i < n; i++) d[i] = mark[i] ? 0 : (i > 0 ? d[i - 1] + 1 : 1e9);
        for (int i = n - 2; i >= 0; i--) d[i] = Math.min(d[i], d[i + 1] + 1);
        return d;
    }

    // ------------------------------------------------------------------ crossroad

    /**
     * The approach of road r (driven toward the interstate, on side) onto the interstate's normal at sc: its points up to
     * the Bezier, the Bezier to D + APPROACH off the centreline heading in; null when none keeps MIN_RADIUS. back[0]: the
     * realigned length.
     */
    private static List<double[]> approach(RouteData r, Interstate c, double sc, int side, double[] back, String[] why) {
        RoadCurve rc = new RoadCurve(r.xs(), r.zs());
        double len = rc.length, full = Math.min(len, profileLength(r)), off = D + APPROACH;
        double[] g = new double[5], f = new double[5];
        c.curve.frame(sc, g);
        double nx = -g[3], nz = g[2];
        double[] qp = {g[0] + side * nx * off, g[1] + side * nz * off}, dq = {-side * nx, -side * nz};
        double bestScore = Double.NEGATIVE_INFINITY, bestB = 0, bestR = 0;
        double[][] bestC = null;
        why[0] = "too short";
        for (double b : BACK) {
            if (b > len - 60) continue;
            boolean blocked = false;
            for (Structure s : r.structures()) if (s.s1() >= full - b - REALIGN_CLEAR) blocked = true;
            if (blocked) { why[0] = "a structure"; continue; }
            double s0 = len - b;
            rc.frame(s0, f);
            double[] p0 = {f[0], f[1]}, t0 = {f[2], f[3]};
            double[] l0 = c.curve.local(p0[0], p0[1], 1200, 0);
            if (l0 == null || side * l0[1] < 0.9 * off) { why[0] = "too close to the interstate"; continue; }
            double a = Math.hypot(qp[0] - p0[0], qp[1] - p0[1]) * 0.4;
            double[][] cc = {p0, {p0[0] + t0[0] * a, p0[1] + t0[1] * a}, {qp[0] - dq[0] * a, qp[1] - dq[1] * a}, qp};
            double minR = RoadJunctions.bezierMinRadius(cc);
            if (minR < MIN_RADIUS) { why[0] = String.format(Locale.ROOT, "radius %.0f m", Math.max(minR, bestR)); bestR = Math.max(bestR, minR); continue; }
            if (!clear(cc, c.curve, side, 0.9 * off)) { why[0] = "swings toward the interstate"; continue; }
            double score = Math.min(minR, SCORE_RADIUS) - 0.1 * b;
            if (score > bestScore) { bestScore = score; bestB = b; bestC = cc; }
        }
        if (bestC == null) return null;
        back[0] = bestB;
        double s0 = len - bestB;
        List<double[]> pts = new ArrayList<>();
        double lastS = 0;
        for (int i = 0; i < r.xs().length; i++) {
            double[] l = rc.local(r.xs()[i], r.zs()[i], 50, 0);
            double st = l == null ? (i == 0 ? 0 : len) : l[0];
            if (i == 0 || st <= s0 - 40) { pts.add(new double[]{r.xs()[i], r.zs()[i]}); lastS = Math.max(lastS, i == 0 ? 0 : st); }
        }
        for (double s = lastS + POINT; s < s0 - POINT / 2; s += POINT) pts.add(rc.point(s, 0));
        pts.addAll(RoadJunctions.bezierPoints(bestC, POINT));
        return pts;
    }

    /** Whether the Bezier keeps at least min off the interstate's centreline, on its side. */
    private static boolean clear(double[][] cc, RoadCurve ic, int side, double min) {
        for (int i = 0; i <= 100; i++) {
            double[] p = RoadJunctions.bez(cc, i / 100.0), l = ic.local(p[0], p[1], 1200, 0);
            if (l != null && side * l[1] < min) return false;
        }
        return true;
    }

    /**
     * The crossroad's profile lifted over the overpass (the class comment): the soffit's lower bound per station over the
     * interstate's paved band, cones at the grade limit (the old grade where steeper), vertical curves of CROSS_K around
     * the lift (blended into the old profile 150 .. 250 m beyond it), repeated until the curves keep the bound.
     */
    private static double[] overpass(double[] e0, RoadCurve nc, double xc, Interstate c) {
        int n = e0.length;
        double deck = RoadClassSpec.STATE.deckDepth(), half = CrossSection.R2.edge + BridgeMesh.PARAPET_WIDTH;
        double[] lower = new double[n];
        Arrays.fill(lower, Double.NaN);
        for (int i = 0; i < n; i++) {
            double t = i * RoadProfile.DS;
            if (Math.abs(t - xc) > SPAN) continue;
            double need = Double.NaN;
            for (double u = -half; u <= half + 1e-9; u += 1) {
                double[] p = nc.point(t, u), l = c.curve.local(p[0], p[1], I_SEC.edge + 2, 0);
                if (l == null || !I_SEC.paved(Math.abs(l[1]))) continue;
                double y = c.design.pavementY(Math.max(0, Math.min(c.design.length, l[0])), l[1]) + CLEARANCE + deck + CLEAR_MARGIN;
                if (Double.isNaN(need) || y > need) need = y;
            }
            lower[i] = need;
        }
        double[] grade = new double[n];
        for (int i = 1; i < n; i++) grade[i] = Math.max(CROSS_GRADE, Math.abs(e0[i] - e0[i - 1]) / RoadProfile.DS + 1e-6);
        double[] extra = new double[n], bound = new double[n], f = e0;
        for (int it = 0; it < 8; it++) {
            for (int i = 0; i < n; i++) bound[i] = Double.isNaN(lower[i]) ? Double.NaN : lower[i] + extra[i];
            double[] l = e0.clone();
            RoadProfile.lift(l, grade, bound);
            boolean[] lifted = new boolean[n];
            for (int i = 0; i < n; i++) lifted[i] = l[i] > e0[i] + 1e-6;
            double[] v = RoadProfile.vertical(l, grade, RoadClassSpec.STATE.vSigma(), CROSS_K), d = distances(lifted);
            f = new double[n];
            for (int i = 0; i < n; i++) {
                double m = d[i] * RoadProfile.DS, w = m <= 150 ? 1 : m >= 250 ? 0 : CrossSection.smooth((250 - m) / 100);
                f[i] = e0[i] + (v[i] - e0[i]) * w;
            }
            double worst = 0;
            for (int i = 0; i < n; i++) {
                if (Double.isNaN(lower[i])) continue;
                double dd = lower[i] - f[i];
                if (dd > 0) { extra[i] += dd + 0.01; worst = Math.max(worst, dd); }
            }
            if (worst <= 0.005) break;
        }
        RoadProfile.lift(f, grade, lower);
        return f;
    }

    /** The overpass's least clearance: its soffit over the interstate's pavement under its deck. */
    private static double clearance(RoadDesign xd, double xc, Interstate c) {
        RoadDesign.Span sp = xd.span(xc);
        if (sp == null) return Double.NaN;
        double half = xd.section.edge + BridgeMesh.PARAPET_WIDTH, worst = Double.POSITIVE_INFINITY;
        for (double t = sp.s0(); t <= sp.s1() + 1e-9; t += 1)
            for (double u = -half; u <= half + 1e-9; u += 1) {
                double[] p = xd.point(t, u), l = c.curve.local(p[0], p[1], I_SEC.edge + 2, 0);
                if (l == null || !I_SEC.paved(Math.abs(l[1]))) continue;
                double soffit = BridgeMesh.soffit(xd, sp, t, u < 0 ? -1 : 1);
                worst = Math.min(worst, soffit - c.design.pavementY(Math.max(0, Math.min(c.design.length, l[0])), l[1]));
            }
        return worst;
    }

    /** The crossroad's smallest K over its overpass and 120 m each way. */
    private static double crestK(RoadDesign xd, double xc) {
        int ch = (int) Math.round(RoadProfile.K_CHORD / RoadProfile.DS);
        double k = Double.POSITIVE_INFINITY;
        for (double t = xc - SPAN - 120; t <= xc + SPAN + 120; t += RoadProfile.DS) {
            double a = xd.profile.at(t - ch * RoadProfile.DS), m = xd.profile.at(t), b = xd.profile.at(t + ch * RoadProfile.DS);
            double second = Math.abs(a - 2 * m + b) / (RoadProfile.K_CHORD * RoadProfile.K_CHORD);
            if (second > 1e-12) k = Math.min(k, 1 / (100 * second));
        }
        return k;
    }

    // ------------------------------------------------------------------ ramps

    /** A turtle over the (q, w) plane: heading h from +q toward +w. */
    private static final class Turtle {
        final List<double[]> pts = new ArrayList<>();
        double q, w, h;

        Turtle(double q, double w, double h) {
            this.q = q;
            this.w = w;
            this.h = h;
            pts.add(new double[]{q, w});
        }

        void straight(double len) {
            int n = Math.max(1, (int) Math.ceil(len / FINE));
            for (int i = 0; i < n; i++) {
                q += Math.cos(h) * len / n;
                w += Math.sin(h) * len / n;
                pts.add(new double[]{q, w});
            }
        }

        /** An arc of radius r turning to heading to. */
        void arc(double r, double to) {
            double dh = to - h;
            int n = Math.max(1, (int) Math.ceil(r * Math.abs(dh) / FINE));
            double chord = 2 * r * Math.sin(Math.abs(dh) / n / 2);
            for (int i = 0; i < n; i++) {
                double hm = h + dh / n / 2;
                q += Math.cos(hm) * chord;
                w += Math.sin(hm) * chord;
                h += dh / n;
                pts.add(new double[]{q, w});
            }
        }

        void lineTo(double q1, double w1) {
            h = Math.atan2(w1 - w, q1 - q);
            straight(Math.hypot(q1 - q, w1 - w));
        }
    }

    private static Path[] paths(boolean exit) {
        Path[] out = new Path[RAMP_RADII.length];
        for (int i = 0; i < out.length; i++) out[i] = path(exit, RAMP_RADII[i]);
        return out;
    }

    /** The canonical exit or entrance plan (the class comment) with reverse curves of radius r, its points every about POINT. */
    static Path path(boolean exit, double r) {
        double t0 = Math.atan(1 / TAPER), tm = Math.toRadians(RAMP_ANGLE);
        // the soft turn into the acceleration lane drops w by this
        double soft = exit ? 0 : SOFT_ACCEL * (1 - Math.cos(t0));
        double tangent = (D - W0 - TAPER_RUN / TAPER - r * (Math.cos(t0) - Math.cos(tm)) - r * (1 - Math.cos(tm))) / Math.sin(tm);
        Turtle t;
        if (exit) {
            t = new Turtle(0, W0, t0);
            t.straight(TAPER_RUN / Math.cos(t0));
            t.arc(r, tm);
            t.straight(tangent);
            t.arc(r, 0);
            t.straight(PARALLEL);
            double qt = -t.q;
            for (double[] p : t.pts) p[0] += qt;
        } else {
            t = new Turtle(0, D, 0);
            t.straight(PARALLEL);
            t.arc(r, -tm);
            t.straight(tangent);
            t.arc(r, -t0);
            t.straight((t.w - W_PAINT - soft) / Math.sin(t0));
            t.arc(SOFT_ACCEL, 0);
            t.straight(ACCEL);
            // the merge taper: two arcs back onto the outer lane line
            double tt = Math.acos(1 - (W_PAINT - W0) / (2 * SOFT_MERGE));
            t.arc(SOFT_MERGE, -tt);
            t.arc(SOFT_MERGE, 0);
        }
        // the painted nose (the ramp's left lane edge on the outer lane line) and the physical nose (its paved edge on the interstate's)
        double[] paint = null, nose = null;
        for (double[] p : t.pts) {
            boolean past = exit ? p[1] >= W_PAINT - 1e-3 : p[1] <= W_PAINT + 5e-3;
            if (paint == null && past && (exit || p[0] > PARALLEL)) paint = p.clone();
            if (nose == null && (exit ? p[1] >= W_NOSE : p[1] <= W_NOSE && p[0] > PARALLEL)) nose = p.clone();
        }
        // resample every about POINT along the polyline, both ends kept
        List<double[]> p = t.pts;
        double[] cum = new double[p.size()];
        for (int i = 1; i < cum.length; i++) cum[i] = cum[i - 1] + Math.hypot(p.get(i)[0] - p.get(i - 1)[0], p.get(i)[1] - p.get(i - 1)[1]);
        int m = Math.max(1, (int) Math.round(cum[cum.length - 1] / POINT));
        double[] qs = new double[m + 1], ws = new double[m + 1];
        int j = 0;
        for (int i = 0; i <= m; i++) {
            double s = cum[cum.length - 1] * i / m;
            while (j < cum.length - 2 && cum[j + 1] < s) j++;
            double f = cum[j + 1] > cum[j] ? (s - cum[j]) / (cum[j + 1] - cum[j]) : 0;
            qs[i] = p.get(j)[0] + (p.get(j + 1)[0] - p.get(j)[0]) * f;
            ws[i] = p.get(j)[1] + (p.get(j + 1)[1] - p.get(j)[1]) * f;
        }
        return new Path(qs, ws, paint, nose);
    }

    /** The interstate's outer shoulder plane carried out to the world point p (side: the ramp's side), or NaN off it. */
    static double hExt(RoadDesign id, RoadCurve ic, int side, double[] p) {
        double[] l = ic.local(p[0], p[1], 120, 0);
        if (l == null) return Double.NaN;
        double s = Math.max(0, Math.min(id.length, l[0])), e = id.section.edge;
        double y22 = id.pavementY(s, side * e), y20 = id.pavementY(s, side * (e - 2));
        return y22 + (side * l[1] - e) * (y22 - y20) / 2;
    }

    private static double hermite(double r, double ra, double ea, double ga, double rb, double eb, double gb) {
        double len = rb - ra, t = (r - ra) / len, t2 = t * t, t3 = t2 * t;
        return (2 * t3 - 3 * t2 + 1) * ea + (t3 - 2 * t2 + t) * len * ga + (-2 * t3 + 3 * t2) * eb + (t3 - t2) * len * gb;
    }

    /**
     * A free stretch from (ra, ea, grade ga) to (rb, eb, gb) as a vertical curve of K, a straight grade G and a vertical
     * curve of K: {G, L1, L2}, or null where the stretch is too short for K.
     */
    static double[] curves(double ra, double ea, double ga, double rb, double eb, double gb, double k) {
        double len = rb - ra, dh = eb - ea, c = (ga + gb) / 2, h = len / (200 * k);
        if (h < Math.abs(gb - ga) / 2) return null;
        double lo = c - h, hi = c + h;
        if (rise(lo, ga, gb, len, k) > dh || rise(hi, ga, gb, len, k) < dh) return null;
        for (int it = 0; it < 60; it++) {
            double mid = (lo + hi) / 2;
            if (rise(mid, ga, gb, len, k) < dh) lo = mid; else hi = mid;
        }
        double g = (lo + hi) / 2;
        return new double[]{g, 100 * k * Math.abs(g - ga), 100 * k * Math.abs(gb - g)};
    }

    /** The height gained over len by the straight grade g between vertical curves of K from ga and to gb. */
    private static double rise(double g, double ga, double gb, double len, double k) {
        double l1 = 100 * k * Math.abs(g - ga), l2 = 100 * k * Math.abs(gb - g);
        return (ga + g) / 2 * l1 + g * (len - l1 - l2) + (g + gb) / 2 * l2;
    }

    /** The height at r of a #curves stretch. */
    static double curvesAt(double r, double ra, double ea, double ga, double rb, double gb, double[] v) {
        double x = r - ra, len = rb - ra, g = v[0], l1 = v[1], l2 = v[2];
        double e1 = ea + (ga + g) / 2 * l1;
        if (x <= l1) return ea + ga * x + (l1 > 1e-9 ? (g - ga) * x * x / (2 * l1) : 0);
        if (x <= len - l2) return e1 + g * (x - l1);
        double y = x - (len - l2);
        return e1 + g * (len - l1 - l2) + g * y + (l2 > 1e-9 ? (gb - g) * y * y / (2 * l2) : 0);
    }

    /** One ramp (traffic tau along the interstate's stations, on its tau side), with its terminal on the crossroad. */
    private static Ramp ramp(Interstate c, double sc, int tau, boolean exit, RouteData x, RoadDesign xd, double xc, String[] why) {
        String id = "ramp:" + c.route.id() + "@" + Math.round(sc) + ":" + (exit ? "exit" : "entry") + (tau > 0 ? "+" : "-");
        // the first plan radius whose world radius keeps RAMP_MIN_RADIUS
        Path p = null;
        double[] xs = null, zs = null;
        RoadCurve rc = null;
        double minR = 0;
        for (Path cand : exit ? EXITS : ENTRIES) {
            int n = cand.q().length;
            xs = new double[n];
            zs = new double[n];
            for (int i = 0; i < n; i++) {
                double[] w = c.curve.point(sc + tau * cand.q()[i], tau * cand.w()[i]);
                xs[i] = RoadNetworkJson.round2(w[0]);
                zs[i] = RoadNetworkJson.round2(w[1]);
            }
            rc = new RoadCurve(xs, zs);
            minR = rc.minRadius();
            p = cand;
            if (minR >= RAMP_MIN_RADIUS) break;
        }
        if (minR < RAMP_MIN_RADIUS) return fail(why, String.format(Locale.ROOT, "%s radius %.0f m", id, minR));
        double[] pw = c.curve.point(sc + tau * p.paint()[0], tau * p.paint()[1]), nw = c.curve.point(sc + tau * p.nose()[0], tau * p.nose()[1]);
        double[] lp = rc.local(pw[0], pw[1], 3, 0), ln = rc.local(nw[0], nw[1], 3, 0);
        if (lp == null || ln == null) return fail(why, id + ": the noses are off the ramp");
        double paint = lp[0], nose = ln[0];
        int m = RoadProfile.samples(rc.length);
        double len = (m - 1) * RoadProfile.DS;
        RouteData prov = new RouteData(id, "ramp", c.route.corridor(), c.route.id(), x.id(), xs, zs, new double[m], List.of(), null);
        RoadDesign pd = RoadNetworkJson.design(prov);
        double[] natural = pd.slopes();
        Junction g = Junction.geometry(xd, pd, exit);
        if (g == null) return fail(why, id + ": no terminal geometry");
        double sEdge = g.sEdge;
        double[] pe = pd.point(sEdge, 0), le = xd.curve.local(pe[0], pe[1], 50, 40);
        if (le == null) return fail(why, id + ": the terminal is off the crossroad");
        double ec = xd.pavementY(Math.max(0, Math.min(xd.length, le[0])), g.side * xd.section.edge);
        // attached: the shoulder plane; its slope across the ramp (falling to the ramp's right, positive)
        double[] hc = new double[m], k = new double[m];
        for (int i = 0; i < m; i++) {
            double r = Math.min(i * RoadProfile.DS, rc.length), into = exit ? r - nose : nose - r;
            if (into > NOSE_BLEND + 4) { hc[i] = Double.NaN; k[i] = natural[i]; continue; }
            double h0 = hExt(c.design, c.curve, tau, rc.point(r, 0)), hl = hExt(c.design, c.curve, tau, rc.point(r, -1)),
                    hr = hExt(c.design, c.curve, tau, rc.point(r, 1));
            if (Double.isNaN(h0) || Double.isNaN(hl) || Double.isNaN(hr)) return fail(why, id + ": the attached stretch is off the interstate");
            hc[i] = h0;
            double ka = (hl - hr) / 2;
            k[i] = into <= 0 ? ka : ka + (natural[i] - ka) * CrossSection.smooth(into / NOSE_BLEND);
        }
        for (int i = 0; i < m; i++) k[i] = Math.round(k[i] * 1e4) / 1e4;
        // the free stretch starts at the last attached sample, at the attached stretch's grade over a K_CHORD chord (the
        // shoulder plane carried out wobbles by a few mm between samples)
        int ch = (int) Math.round(RoadProfile.K_CHORD / RoadProfile.DS);
        int ni = exit ? (int) Math.floor(nose / RoadProfile.DS) : (int) Math.ceil(nose / RoadProfile.DS);
        if (ni - ch < 0 || ni + ch >= m) return fail(why, id + ": the nose is off the ramp");
        double rn = ni * RoadProfile.DS, en = hc[ni];
        double gn = exit ? (hc[ni] - hc[ni - ch]) / RoadProfile.K_CHORD : (hc[ni + ch] - hc[ni]) / RoadProfile.K_CHORD;
        double rl = exit ? sEdge - Junction.LANDING : sEdge + Junction.LANDING;
        if ((exit ? rl - nose : nose - rl) < 60) return fail(why, id + ": no room between the nose and the terminal");
        // the landing grade (rise away from the crossroad) giving the gentlest free stretch: vertical curves of RAMP_K_DESIGN
        // (RAMP_K where too short) around a straight grade, a Hermite curve where neither fits
        double[] best = null;
        double bestGrade = Double.POSITIVE_INFINITY, bestGl = 0;
        double ra = exit ? rn : rl, rb = exit ? rl : rn;
        for (double gl = -Junction.LANDING_GRADE; gl <= Junction.LANDING_GRADE + 1e-9; gl += 0.0025) {
            double el = ec + gl * Junction.LANDING;
            double ea = exit ? en : el, ga = exit ? gn : gl, eb = exit ? el : en, gb = exit ? -gl : gn;
            double[] v = curves(ra, ea, ga, rb, eb, gb, RAMP_K_DESIGN);
            if (v == null) v = curves(ra, ea, ga, rb, eb, gb, RAMP_K);
            double[] e = new double[m];
            double worst = 0;
            for (int i = 0; i < m; i++) {
                double r = i * RoadProfile.DS, dEdge = exit ? sEdge - r : r - sEdge;
                boolean attached = exit ? i <= ni : i >= ni;
                if (attached) e[i] = hc[i];
                else if (dEdge <= Junction.LANDING) e[i] = ec + gl * dEdge;
                else e[i] = v != null ? curvesAt(r, ra, ea, ga, rb, gb, v) : hermite(r, ra, ea, ga, rb, eb, gb);
                e[i] = RoadNetworkJson.round3(e[i]);
                // the free stretch's grade (the attached stretch is the interstate's own)
                boolean free = exit ? i > ni + 1 : i < ni - 1;
                if (i > 0 && free) worst = Math.max(worst, Math.abs(e[i] - e[i - 1]) / RoadProfile.DS);
            }
            if (worst < bestGrade - 1e-9) { bestGrade = worst; best = e; bestGl = gl; }
        }
        // where the interstate itself is steeper at the nose, the ramp may leave it at its grade
        double limit = Math.max(RAMP_GRADE, Math.abs(gn) + 0.002);
        // the free stretch's smallest K (the attached stretch is the interstate's shoulder plane, its runoff breaks included)
        double freeK = Double.POSITIVE_INFINITY;
        int kAt = -1;
        double f0 = exit ? rn + RoadProfile.K_CHORD : sEdge, f1 = exit ? sEdge : rn - RoadProfile.K_CHORD;
        for (int i = ch; i + ch < m; i++) {
            double r = i * RoadProfile.DS;
            if (r < f0 || r > f1) continue;
            double second = Math.abs(best[i + ch] - 2 * best[i] + best[i - ch]) / (RoadProfile.K_CHORD * RoadProfile.K_CHORD);
            if (second > 1e-12 && 1 / (100 * second) < freeK) { freeK = 1 / (100 * second); kAt = i; }
        }
        if (Boolean.getBoolean("afl.debugInterchanges")) {
            StringBuilder b = new StringBuilder();
            for (int i = Math.max(0, kAt - 20); i < Math.min(m, kAt + 20); i++) b.append(String.format(Locale.ROOT, " %.0f:%.3f", i * RoadProfile.DS, best[i]));
            System.out.printf(Locale.ROOT, "DEBUG %s: grade %.2f %%, free K %.1f at s %.0f; nose %.0f en %.3f gn %.4f, rl %.0f sEdge %.1f ec %.3f gl %.4f%n   %s%n", id,
                    bestGrade * 100, freeK, kAt * RoadProfile.DS, nose, en, gn, rl, sEdge, ec, bestGl, b);
        }
        if (bestGrade > limit + 1e-4) return fail(why, String.format(Locale.ROOT, "%s grade %.1f %%", id, bestGrade * 100));
        // the profile is kept to mm, which moves the K measured over K_CHORD by up to about a tenth
        if (freeK < 0.9 * RAMP_K) return fail(why, String.format(Locale.ROOT, "%s K %.0f", id, freeK));
        RoadDesign.Attach at = new RoadDesign.Attach(c.route.id(), tau, !exit, RoadNetworkJson.round2(paint), RoadNetworkJson.round2(nose));
        RouteData rd = new RouteData(id, "ramp", c.route.corridor(), c.route.id(), x.id(), xs, zs, best, List.of(), null, List.of(), List.of(),
                new String[2], new double[0], k, at);
        double end = RoadJunctions.endLine(g);
        double[] window = exit ? new double[]{0, end} : new double[]{end, Math.floor(Math.min(rc.length, len) / RoadProfile.DS) * RoadProfile.DS};
        RoadDesign trimmed;
        try {
            trimmed = RoadNetworkJson.design(rd.withTrim(window));
        } catch (IllegalArgumentException ex) {
            return fail(why, id + ": " + ex.getMessage());
        }
        if (Junction.of(xd, trimmed, exit) == null) return fail(why, id + ": the terminal pad cannot be built");
        RoadJunctions.Spec spec = new RoadJunctions.Spec(id, exit, x.id(), end, g.sMouth, new double[]{Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY},
                new double[]{xc - (D + 150), xc + (D + 150)}, 90, false);
        String line = String.format(Locale.ROOT, "%s: %.0f m, radius >= %.0f, nose s %.0f (paint %.0f), grade <= %.1f %%, free K >= %.0f, landing %+.1f %% to %.2f, end line s %.0f",
                id, rc.length, minR, nose, paint, bestGrade * 100, freeK, bestGl * 100, ec, end);
        return new Ramp(rd, spec, line);
    }

    // ------------------------------------------------------------------ other roads

    /** The first other road (not the interstate, not the site's own roads) within OTHERS of any of the points, or null. */
    private static String others(List<RouteData> out, List<RouteData> added, List<double[]> pts, RouteData... own) {
        double x0 = Double.POSITIVE_INFINITY, z0 = Double.POSITIVE_INFINITY, x1 = Double.NEGATIVE_INFINITY, z1 = Double.NEGATIVE_INFINITY;
        for (double[] p : pts) { x0 = Math.min(x0, p[0]); x1 = Math.max(x1, p[0]); z0 = Math.min(z0, p[1]); z1 = Math.max(z1, p[1]); }
        List<RouteData> all = new ArrayList<>(out);
        all.addAll(added);
        for (RouteData o : all) {
            if (o == null) continue;
            boolean mine = false;
            for (RouteData r : own) if (r == o) mine = true;
            if (mine) continue;
            double reach = OTHERS + CrossSection.of(o.roadClass()).edge;
            boolean box = false;
            for (int i = 0; i < o.xs().length && !box; i++)
                box = o.xs()[i] >= x0 - reach - 50 && o.xs()[i] <= x1 + reach + 50 && o.zs()[i] >= z0 - reach - 50 && o.zs()[i] <= z1 + reach + 50;
            if (!box) continue;
            RoadCurve oc = new RoadCurve(o.xs(), o.zs());
            for (double[] p : pts) if (oc.local(p[0], p[1], reach, RoadDesign.END) != null) return o.id();
        }
        return null;
    }
}
