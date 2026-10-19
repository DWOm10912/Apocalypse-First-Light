package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson.RouteData;
import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson.Structure;
import com.antaurora.apofirstlight.worldgen.network.route.RoadClassSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The T junctions of the built network (C3.3b group 1; docs/worldgen/federal_junctions_c3_3b.md 3), at build time
 * (RoadNetworkBuild): a state road whose end lies on another state road's centreline (the planner's join) away from that
 * road's ends becomes the minor road of a T junction on it (the host). A county road (G3, RoadCounty) joins a state road
 * the same way, as the minor; it is never a host.
 * <ul>
 *   <li>skipped (the C3.3a trim stays): a host structure within HOST_CLEAR of the mouth, a minor structure within
 *   MINOR_CLEAR of its end (realigned: within the realigned stretch and REALIGN_CLEAR before it);</li>
 *   <li>a crossing angle under SKEW: the minor's last stretch is realigned to meet the host square: from BACK m back on
 *   the minor a cubic Bezier to APPROACH m off the host's centreline, heading straight at it, then straight in; the
 *   radius at least MIN_RADIUS; the mouth may move along the host by up to SHIFT; the profile taken from the old line
 *   where the new one runs on it, linear elsewhere, smoothed;</li>
 *   <li>the minor's landing: its profile over the last Junction.LANDING before the host's paved edge at most
 *   Junction.LANDING_GRADE, ending at the host's edge height, blended into the old profile over BLEND_MIN .. BLEND_MAX
 *   within the class's grade;</li>
 *   <li>its end line: Junction.END_MARGIN beyond both fillets' tangent points, on the 2 m lattice (the trim);</li>
 *   <li>the trims, splits and verify ignore the conflicts of the minor's last stretch with its host, and of the host's
 *   mouth stretch with the minor (the junction joins them).</li>
 * </ul>
 */
final class RoadJunctions {
    private RoadJunctions() {}

    /** A minor end within TOUCH of a host's centreline, at least MID from the host's ends, m. */
    static final double TOUCH = 10, MID = 60;
    static final double HOST_CLEAR = 50, MINOR_CLEAR = 60, REALIGN_CLEAR = 20;
    /** Crossing angles under this are realigned, degrees. */
    static final double SKEW = 70;
    static final double[] BACK = {120, 160, 200, 260, 320};
    static final double APPROACH = 50, SHIFT = 150, SHIFT_STEP = 5, MIN_RADIUS = 60, SCORE_RADIUS = 150, POINT = 10;
    /** Conflicts ignored between a minor and its host: the minor's last ZONE_MINOR (realigned: plus BACK), the host's mouth +- ZONE_HOST, m. */
    static final double ZONE_MINOR = 120, ZONE_HOST = 140;
    static final double BLEND_MIN = 60, BLEND_MAX = 300, BLEND_STEP = 20;
    /** A host piece (after splits) keeps the mouth +- HOST_KEEP, m. */
    static final double HOST_KEEP = 40;

    /**
     * One junction: the minor (its id before splits), which end, the host (its id before splits), the end line (the
     * minor's JSON station), the mouth (the host's JSON station), the ignored conflict zones {s0, s1} on each.
     */
    record Spec(String minor, boolean atEnd, String host, double endLine, double mouth, double[] minorZone, double[] hostZone, double angle,
                boolean realigned) {}

    record Result(List<RouteData> roads, List<Spec> junctions) {}

    private static double profileLength(RouteData r) { return (r.profile().length - 1) * RoadProfile.DS; }

    private static boolean structureNear(List<Structure> ss, double s0, double s1) {
        for (Structure s : ss) if (s.s1() >= s0 && s.s0() <= s1) return true;
        return false;
    }

    static Result find(List<RouteData> roads, List<String> report) {
        List<RouteData> out = new ArrayList<>(roads);
        List<Spec> specs = new ArrayList<>();
        for (int i = 0; i < out.size(); i++) {
            for (boolean atEnd : new boolean[]{false, true}) {
                RouteData r = out.get(i);
                if (!r.roadClass().equals("state") && !r.roadClass().equals("county")) continue;
                int n = r.xs().length;
                double px = atEnd ? r.xs()[n - 1] : r.xs()[0], pz = atEnd ? r.zs()[n - 1] : r.zs()[0];
                int host = -1;
                double hs = 0, hu = Double.POSITIVE_INFINITY;
                for (int j = 0; j < out.size(); j++) {
                    RouteData q = out.get(j);
                    if (j == i || !q.roadClass().equals("state")) continue;
                    RoadCurve qc = new RoadCurve(q.xs(), q.zs());
                    double[] l = qc.local(px, pz, TOUCH, 0);
                    if (l == null) continue;
                    double qLen = Math.min(qc.length, profileLength(q));
                    if (l[0] < MID || l[0] > qLen - MID || Math.abs(l[1]) >= hu) continue;
                    host = j;
                    hs = l[0];
                    hu = Math.abs(l[1]);
                }
                if (host < 0) continue;
                RouteData q = out.get(host);
                RoadCurve rc = new RoadCurve(r.xs(), r.zs()), qc = new RoadCurve(q.xs(), q.zs());
                double[] f = new double[5], g = new double[5];
                rc.frame(atEnd ? rc.length : 0, f);
                qc.frame(hs, g);
                double angle = Math.toDegrees(Math.acos(Math.min(1, Math.abs(f[2] * g[2] + f[3] * g[3]))));
                boolean realign = angle < SKEW;
                String what = String.format(Locale.ROOT, "%s %s on %s s %.0f (%.0f deg)", r.id(), atEnd ? "end" : "start", q.id(), hs, angle);
                if (structureNear(q.structures(), hs - HOST_CLEAR, hs + HOST_CLEAR)) {
                    report.add("junction skipped (trimmed): " + what + ": a structure of the host near the mouth");
                    continue;
                }
                double clear = realign ? BACK[0] + REALIGN_CLEAR : MINOR_CLEAR, len = Math.min(rc.length, profileLength(r));
                if (atEnd ? structureNear(r.structures(), len - clear, Double.POSITIVE_INFINITY) : structureNear(r.structures(), Double.NEGATIVE_INFINITY, clear)) {
                    report.add("junction skipped (trimmed): " + what + ": a structure of the minor road near its end");
                    continue;
                }
                RouteData m = r;
                double back = 0;
                if (realign) {
                    double[] back1 = new double[1];
                    m = realign(r, atEnd, q, hs, back1, report);
                    if (m == null) {
                        report.add("junction skipped (trimmed): " + what + ": no realignment fits");
                        continue;
                    }
                    back = back1[0];
                }
                double[] jl = new double[3];
                RouteData landed = landing(m, atEnd, q, jl, report);
                if (landed == null) {
                    report.add("junction skipped (trimmed): " + what + ": the landing does not fit the grade");
                    continue;
                }
                out.set(i, landed);
                double endLine = jl[0], mouth = jl[1];
                double z = ZONE_MINOR + back;
                double[] mz = atEnd ? new double[]{endLine - z, Double.POSITIVE_INFINITY} : new double[]{Double.NEGATIVE_INFINITY, endLine + z};
                specs.add(new Spec(r.id(), atEnd, q.id(), endLine, mouth, mz, new double[]{mouth - ZONE_HOST - back, mouth + ZONE_HOST + back}, angle, realign));
            }
        }
        return new Result(out, specs);
    }

    // ------------------------------------------------------------------ realignment

    /**
     * The minor realigned to meet the host square (the class comment); null when no Bezier keeps MIN_RADIUS. back[0]:
     * the realigned length from the minor's old line (for the zones).
     */
    private static RouteData realign(RouteData r, boolean atEnd, RouteData q, double hs, double[] back, List<String> report) {
        int n = r.xs().length;
        // work with the junction at the end of the control polyline
        double[] xs = new double[n], zs = new double[n];
        for (int i = 0; i < n; i++) { xs[i] = r.xs()[atEnd ? i : n - 1 - i]; zs[i] = r.zs()[atEnd ? i : n - 1 - i]; }
        RoadCurve rc = new RoadCurve(xs, zs), qc = new RoadCurve(q.xs(), q.zs());
        double len = rc.length, qLen = Math.min(qc.length, profileLength(q));
        double[] p60 = rc.point(len - 60, 0), l60 = qc.local(p60[0], p60[1], 400, 0);
        if (l60 == null) return null;
        int side = l60[1] > 0 ? 1 : -1;
        double bestScore = Double.NEGATIVE_INFINITY, bestB = 0, bestSm = 0, bestR = 0;
        double[][] bestCtrl = null;
        double[] f = new double[5], g = new double[5];
        double full = Math.min(len, profileLength(r));
        for (double b : BACK) {
            // the realigned stretch and REALIGN_CLEAR before it keep clear of the minor's structures
            if (b > len - 60 || (atEnd ? structureNear(r.structures(), full - b - REALIGN_CLEAR, Double.POSITIVE_INFINITY)
                    : structureNear(r.structures(), Double.NEGATIVE_INFINITY, b + REALIGN_CLEAR))) continue;
            double s0 = len - b;
            rc.frame(s0, f);
            double[] p0 = {f[0], f[1]}, t0 = {f[2], f[3]};
            for (double sm = hs - SHIFT; sm <= hs + SHIFT + 1e-9; sm += SHIFT_STEP) {
                if (sm < MID || sm > qLen - MID || structureNear(q.structures(), sm - HOST_CLEAR, sm + HOST_CLEAR)) continue;
                qc.frame(sm, g);
                // the approach point off the host and the heading in (toward the host's centreline)
                double nx = -g[3], nz = g[2];
                double[] qp = {g[0] + side * nx * APPROACH, g[1] + side * nz * APPROACH}, dq = {-side * nx, -side * nz};
                double a = Math.hypot(qp[0] - p0[0], qp[1] - p0[1]) * 0.4;
                double[][] c = {p0, {p0[0] + t0[0] * a, p0[1] + t0[1] * a}, {qp[0] - dq[0] * a, qp[1] - dq[1] * a}, qp};
                double minR = bezierMinRadius(c);
                if (minR < MIN_RADIUS || !clearOfHost(c, qc, side)) continue;
                // a radius beyond SCORE_RADIUS gains nothing: the shortest realignment near the old mouth wins
                double score = Math.min(minR, SCORE_RADIUS) - 0.2 * Math.abs(sm - hs) - 0.1 * b;
                if (score > bestScore) { bestScore = score; bestB = b; bestSm = sm; bestCtrl = c; bestR = minR; }
            }
        }
        if (bestCtrl == null) return null;
        back[0] = bestB;
        // the new control polyline: the old points up to 40 m before the Bezier, the old curve on to it, the Bezier, the
        // straight approach to the host's centreline
        double s0 = len - bestB;
        List<double[]> pts = new ArrayList<>();
        double lastS = 0;
        for (int i = 0; i < n; i++) {
            double[] l = rc.local(xs[i], zs[i], 50, 0);
            double st = l == null ? (i == 0 ? 0 : len) : l[0];
            if (i == 0 || st <= s0 - 40) { pts.add(new double[]{xs[i], zs[i]}); lastS = Math.max(lastS, i == 0 ? 0 : st); }
        }
        for (double s = lastS + POINT; s < s0 - POINT / 2; s += POINT) pts.add(rc.point(s, 0));
        List<double[]> bz = bezierPoints(bestCtrl, POINT);
        pts.addAll(bz);
        qc.frame(bestSm, g);
        double[] qp = bestCtrl[3];
        double[] mouth = {g[0], g[1]};
        double run = Math.hypot(mouth[0] - qp[0], mouth[1] - qp[1]);
        int k = Math.max(1, (int) Math.round(run / POINT));
        for (int i = 1; i <= k; i++) pts.add(new double[]{qp[0] + (mouth[0] - qp[0]) * i / k, qp[1] + (mouth[1] - qp[1]) * i / k});
        int m = pts.size();
        double[] nx = new double[m], nz = new double[m];
        for (int i = 0; i < m; i++) {
            double[] p = pts.get(atEnd ? i : m - 1 - i);
            nx[i] = RoadNetworkJson.round2(p[0]);
            nz[i] = RoadNetworkJson.round2(p[1]);
        }
        // the profile: on the old line its height, off it linear toward the host's height at the mouth, then smoothed
        RoadCurve oc = new RoadCurve(r.xs(), r.zs()), nc = new RoadCurve(nx, nz);
        RoadProfile op = RoadProfile.of(r.profile());
        double hostY = RoadProfile.of(q.profile()).at(bestSm);
        int ms = RoadProfile.samples(nc.length);
        double[] e = new double[ms];
        for (int i = 0; i < ms; i++) {
            double[] p = nc.point(Math.min(nc.length, i * RoadProfile.DS), 0);
            double[] l = oc.local(p[0], p[1], RoadNetworkBuild.ON_LEG, 0);
            e[i] = l == null ? Double.NaN : op.at(l[0]);
        }
        // the far end (the host's side) toward the host's height
        int far = atEnd ? ms - 1 : 0;
        if (Double.isNaN(e[far])) e[far] = hostY;
        for (int i = 0; i < ms; ) {
            if (!Double.isNaN(e[i])) { i++; continue; }
            int j = i;
            while (j < ms && Double.isNaN(e[j])) j++;
            if (i == 0) for (int q2 = 0; q2 < j; q2++) e[q2] = e[j];
            else if (j == ms) for (int q2 = i; q2 < ms; q2++) e[q2] = e[i - 1];
            else for (int q2 = i; q2 < j; q2++) e[q2] = e[i - 1] + (e[j] - e[i - 1]) * (q2 - i + 1) / (j - i + 1);
            i = j;
        }
        double[] sm = RoadProfile.smooth(e, RoadNetworkBuild.spec(r.roadClass()).vSigma() / RoadProfile.DS);
        double from = bestB + 80;
        for (int i = 0; i < ms; i++) {
            double t = atEnd ? nc.length - i * RoadProfile.DS : i * RoadProfile.DS;   // distance from the junction's end
            double w = t <= bestB + 40 ? 1 : t >= from ? 0 : CrossSection.smooth((from - t) / 40);
            e[i] = RoadNetworkJson.round2(e[i] + (sm[i] - e[i]) * w);
        }
        // structures where they were (none in the realigned stretch: REALIGN_CLEAR)
        List<Structure> ss = new ArrayList<>();
        for (Structure s : r.structures()) {
            double[] a0 = oc.point(s.s0(), 0), a1 = oc.point(s.s1(), 0);
            double[] l0 = nc.local(a0[0], a0[1], 5, 0), l1 = nc.local(a1[0], a1[1], 5, 0);
            if (l0 == null || l1 == null) return null;
            ss.add(new Structure(s.kind(), RoadNetworkJson.round2(Math.min(l0[0], l1[0])), RoadNetworkJson.round2(Math.max(l0[0], l1[0])), s.height()));
        }
        report.add(String.format(Locale.ROOT, "junction realign: %s %s: last %.0f m to meet %s square at s %.0f (moved %.0f m), radius >= %.0f m",
                r.id(), atEnd ? "end" : "start", bestB, q.id(), bestSm, bestSm - hs, bestR));
        return new RouteData(r.id(), r.roadClass(), r.corridor(), r.from(), r.to(), nx, nz, e, List.copyOf(ss), null, List.of(), List.of(), r.hosts(), r.corners());
    }

    static double[] bez(double[][] c, double t) {
        double u = 1 - t;
        double a = u * u * u, b = 3 * u * u * t, d = 3 * u * t * t, e = t * t * t;
        return new double[]{a * c[0][0] + b * c[1][0] + d * c[2][0] + e * c[3][0], a * c[0][1] + b * c[1][1] + d * c[2][1] + e * c[3][1]};
    }

    static double bezierMinRadius(double[][] c) {
        double minR = Double.POSITIVE_INFINITY;
        for (int i = 0; i <= 200; i++) {
            double t = i / 200.0, u = 1 - t;
            double dx = 3 * (u * u * (c[1][0] - c[0][0]) + 2 * u * t * (c[2][0] - c[1][0]) + t * t * (c[3][0] - c[2][0]));
            double dz = 3 * (u * u * (c[1][1] - c[0][1]) + 2 * u * t * (c[2][1] - c[1][1]) + t * t * (c[3][1] - c[2][1]));
            double ddx = 6 * (u * (c[2][0] - 2 * c[1][0] + c[0][0]) + t * (c[3][0] - 2 * c[2][0] + c[1][0]));
            double ddz = 6 * (u * (c[2][1] - 2 * c[1][1] + c[0][1]) + t * (c[3][1] - 2 * c[2][1] + c[1][1]));
            double v = Math.hypot(dx, dz), k = Math.abs(dx * ddz - dz * ddx) / (v * v * v);
            if (k > 1e-12) minR = Math.min(minR, 1 / k);
        }
        return minR;
    }

    /** Whether the Bezier keeps at least 0.9 APPROACH off the host's centreline, on its side. */
    private static boolean clearOfHost(double[][] c, RoadCurve qc, int side) {
        for (int i = 0; i <= 100; i++) {
            double[] p = bez(c, i / 100.0), l = qc.local(p[0], p[1], 600, 0);
            if (l != null && side * l[1] < 0.9 * APPROACH) return false;
        }
        return true;
    }

    /** Points every step (arc length) along the Bezier, both ends included. */
    static List<double[]> bezierPoints(double[][] c, double step) {
        int k = 600;
        double[][] p = new double[k + 1][];
        double[] cum = new double[k + 1];
        for (int i = 0; i <= k; i++) {
            p[i] = bez(c, (double) i / k);
            if (i > 0) cum[i] = cum[i - 1] + Math.hypot(p[i][0] - p[i - 1][0], p[i][1] - p[i - 1][1]);
        }
        int m = Math.max(1, (int) Math.round(cum[k] / step));
        List<double[]> out = new ArrayList<>();
        int j = 0;
        for (int i = 0; i <= m; i++) {
            double s = cum[k] * i / m;
            while (j < k - 1 && cum[j + 1] < s) j++;
            double t = cum[j + 1] > cum[j] ? (s - cum[j]) / (cum[j + 1] - cum[j]) : 0;
            out.add(new double[]{p[j][0] + (p[j + 1][0] - p[j][0]) * t, p[j][1] + (p[j + 1][1] - p[j][1]) * t});
        }
        return out;
    }

    // ------------------------------------------------------------------ landing

    /**
     * The minor's landing (the class comment) and its end line; null when the blend cannot keep the class's grade or the
     * geometry cannot be found. jl: {end line, the host's station of the mouth, the minor's station of the host's edge}.
     */
    private static RouteData landing(RouteData m, boolean atEnd, RouteData q, double[] jl, List<String> report) {
        RoadDesign hd, md;
        try {
            hd = RoadNetworkJson.design(q);
            md = RoadNetworkJson.design(m);
        } catch (IllegalArgumentException e) {
            return null;
        }
        Junction g = Junction.geometry(hd, md, atEnd);
        if (g == null) return null;
        int dir = atEnd ? 1 : -1;
        double sEdge = g.sEdge;
        double[] pe = md.point(sEdge, 0), le = hd.curve.local(pe[0], pe[1], 50, 40);
        if (le == null) return null;
        double ec = hd.pavementY(Math.max(0, Math.min(hd.length, le[0])), g.side * hd.section.edge);
        RoadProfile op = RoadProfile.of(m.profile());
        double[] e0 = m.profile();
        int ms = e0.length;
        // the old grade over the landing, held to LANDING_GRADE (rise away from the host)
        double gOld = (op.at(sEdge - dir * Junction.LANDING) - op.at(sEdge)) / Junction.LANDING;
        double gl = Math.max(-Junction.LANDING_GRADE, Math.min(Junction.LANDING_GRADE, gOld));
        double delta = op.at(sEdge - dir * Junction.LANDING) - (ec + gl * Junction.LANDING);
        RoadClassSpec k = RoadNetworkBuild.spec(m.roadClass());
        double limit = Math.min(k.gradeMountain, Math.max(k.grade, op.maxGrade()));
        // structures stop the blend
        double free = Double.POSITIVE_INFINITY;
        for (Structure s : m.structures()) {
            double d = atEnd ? sEdge - s.s1() : s.s0() - sEdge;
            if (d > 0) free = Math.min(free, d - RoadNetworkBuild.clearance(s));
        }
        double lb0 = Math.max(BLEND_MIN, 2 * Math.abs(delta) / Junction.LANDING_GRADE);
        for (double lb = Math.min(BLEND_MAX, Math.ceil(lb0 / BLEND_STEP) * BLEND_STEP); lb <= BLEND_MAX + 1e-9; lb += BLEND_STEP) {
            if (Junction.LANDING + lb > free) break;
            double[] e = e0.clone();
            int lo = ms, hi = -1;
            for (int i = 0; i < ms; i++) {
                double s = i * RoadProfile.DS, qd = dir * (sEdge - s);   // distance back from the host's edge
                double lin = ec + gl * qd;
                if (qd <= Junction.LANDING) e[i] = lin;
                else if (qd < Junction.LANDING + lb) e[i] = e0[i] + (lin - e0[i]) * (1 - CrossSection.smooth((qd - Junction.LANDING) / lb));
                else continue;
                e[i] = RoadNetworkJson.round2(e[i]);
                lo = Math.min(lo, i);
                hi = Math.max(hi, i);
            }
            double worst = 0;
            for (int i = Math.max(1, lo - 1); i <= Math.min(ms - 1, hi + 1); i++) {
                // the grade up to the host's edge (beyond it the profile is cut off at the end line)
                double s = i * RoadProfile.DS;
                if (dir * (s - sEdge) > 0) continue;
                worst = Math.max(worst, Math.abs(e[i] - e[i - 1]) / RoadProfile.DS);
            }
            if (worst > limit + 1e-4) continue;
            RouteData out = m.withProfile(e);
            double end = endLine(g);
            jl[0] = end;
            jl[1] = g.sMouth;
            jl[2] = sEdge;
            report.add(String.format(Locale.ROOT, "junction landing: %s %s: %.2f %% over %.0f m to the host's edge at %.2f (was %+.2f m), blended over %.0f m (max grade %.1f %%); end line s %.0f",
                    m.id(), atEnd ? "end" : "start", gl * 100, Junction.LANDING, ec, delta, lb, worst * 100, end));
            return out;
        }
        return null;
    }

    /** The minor's end line of a junction geometry: END_MARGIN beyond both fillets' tangent points, on the 2 m lattice. */
    static double endLine(Junction g) {
        return g.atEnd ? Math.floor((Math.min(g.t2(0), g.t2(1)) - Junction.END_MARGIN) / RoadProfile.DS) * RoadProfile.DS
                : Math.ceil((Math.max(g.t2(0), g.t2(1)) + Junction.END_MARGIN) / RoadProfile.DS) * RoadProfile.DS;
    }

    // ------------------------------------------------------------------ after the trims and splits

    /** The id a road had before the splits (pieces are id~1, id~2). */
    static String base(String id) {
        int i = id.indexOf('~');
        return i < 0 ? id : id.substring(0, i);
    }

    /**
     * Each junction on the pieces after the trims and splits: the minor piece whose end is the end line, the host piece
     * holding the mouth +- HOST_KEEP; the minor's hosts set; the junction built as the runtime builds it (Junction#of)
     * or dropped (reported). kept: the junctions that stand.
     */
    static List<RouteData> resolve(List<RouteData> roads, List<Spec> specs, List<Spec> kept, List<String> report) {
        List<RouteData> out = new ArrayList<>(roads);
        for (Spec j : specs) {
            int mi = -1, hi = -1;
            for (int i = 0; i < out.size(); i++) {
                RouteData r = out.get(i);
                double[] w = r.trim() == null ? new double[]{0, Math.min(new RoadCurve(r.xs(), r.zs()).length, profileLength(r))} : r.trim();
                if (base(r.id()).equals(j.minor()) && Math.abs((j.atEnd() ? w[1] : w[0]) - j.endLine()) < 1e-6) mi = i;
                if (base(r.id()).equals(j.host()) && w[0] <= j.mouth() - HOST_KEEP && w[1] >= j.mouth() + HOST_KEEP) hi = i;
            }
            String what = String.format(Locale.ROOT, "%s %s on %s s %.0f", j.minor(), j.atEnd() ? "end" : "start", j.host(), j.mouth());
            if (mi < 0 || hi < 0) {
                report.add("junction dropped: " + what + (mi < 0 ? ": the minor road's end moved off its end line" : ": the host does not hold the mouth"));
                continue;
            }
            RouteData m = out.get(mi), h = out.get(hi);
            String[] hosts = m.hosts().clone();
            hosts[j.atEnd() ? 1 : 0] = h.id();
            RouteData mm = m.withHosts(hosts);
            if (Junction.of(RoadNetworkJson.design(h), RoadNetworkJson.design(mm), j.atEnd()) == null) {
                report.add("junction dropped: " + what + ": the pad cannot be built");
                continue;
            }
            out.set(mi, mm);
            kept.add(j);
            report.add(String.format(Locale.ROOT, "junction: %s joins %s at s %.0f (%.0f deg%s)", mm.id(), h.id(), j.mouth(), j.angle(), j.realigned() ? ", realigned" : ""));
        }
        return out;
    }
}
