package com.antaurora.apofirstlight.worldgen.network.route;

import com.antaurora.apofirstlight.worldgen.federal.FederalMap;
import com.antaurora.apofirstlight.worldgen.network.road.RoadCurve;
import com.antaurora.apofirstlight.worldgen.network.road.RoadProfile;
import com.antaurora.apofirstlight.worldgen.network.route.RoadNetworkPlan.Candidate;
import com.antaurora.apofirstlight.worldgen.network.route.RoadNetworkPlan.Join;
import com.antaurora.apofirstlight.worldgen.network.route.RoadNetworkPlan.Route;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * C3.3b group 3 in the planner (docs/worldgen/federal_systems_c3_3b.md, 4.1): interstates pass their towns in one bend,
 * and an interstate joining another comes in at a proper angle (room for the trumpet interchange).
 * <ul>
 *   <li>{@link #corner}: two legs of one interstate corridor meeting at a town become one line there: a circular arc of
 *   {@link #BEND_MARGIN} x 1.1 x the class radius tangent to both legs as planned (#bend), both tangent points within
 *   {@link #WINDOW} m of the town, the rest of both legs unchanged; the legs then meet at the line's point nearest the
 *   town. (A relaxation with the window's ends held, the first try, pulled the bend toward its chord and kinked at the
 *   window's ends; with them free the legs' heading jumped there, 2026-10-17.) Where the arc does not fit or moves the
 *   line more than {@link #MAX_SHIFT} m (the second leg doubling back), the second leg is searched again from the first
 *   {@link #RESEARCH} m before the town, the town and the way back closed, and bent there; if that fails too the legs
 *   stay apart (the network build trims them, as before);</li>
 *   <li>a fold-back (Town Framework V1, 2026-10-17: the line runs into a town up a valley and out again along it, a
 *   corner of {@link #FOLD_KINK} degrees or more that neither bends): the second leg searched again from
 *   {@link #FOLD_BACK} m before where the two legs part (the first point back from the town more than {@link #FOLD_PART}
 *   m off the second), the stretch into the town ({@link #FOLD_CLOSED} m round it) and the town closed, and bent there:
 *   the line passes the town, the legs meet at its point nearest the town (the town's exit goes there, TownExits);</li>
 *   <li>{@link #designChains}: legs meeting end to start are designed as one road on the terrain (RouteDesigner), each
 *   leg's profile taken from it, so the legs meet at one height;</li>
 *   <li>{@link #steepen}: an interstate leg meeting another interstate at less than {@link #MIN_JOIN_ANGLE} degrees is
 *   re-aligned over its last part: from one of its control points {@link #JOIN_BACK_MIN} .. {@link #JOIN_BACK_MAX} m
 *   back (the nearest that fits first), an arc of 1.1 x the class radius onto a straight of at least {@link #JOIN_STEM}
 *   m that meets the other interstate at {@link #JOIN_ANGLE} degrees against its tangent where it is met; on the side of
 *   the other interstate that control point lies on (a leg may cross it on its way in), clear of its structures and of
 *   closed cells.</li>
 * </ul>
 * Pure Java, deterministic (fixed orders, no hash iteration).
 */
final class RouteCorners {
    private RouteCorners() {}

    /** Reach of a town bend each way from the town, m. */
    static final double WINDOW = 2000;
    /** The most a bend may move the line, m; beyond it the second leg is searched again. */
    static final double MAX_SHIFT = 800;
    /** Where the second leg is searched again from: this far before the town on the first, m. */
    static final double RESEARCH = 1000;
    /** Cells closed to that search: within this of the town centre, and behind its start within BEHIND, m. */
    static final double TOWN_CLOSED = 450, BEHIND = 600;
    /** A fold-back: the corner (degrees) from which it is one; where the legs part, m; the search's start before that, m. */
    static final double FOLD_KINK = 120, FOLD_PART = 500, FOLD_BACK = 600;
    /** The cells closed round the fold's stretch into the town, m. */
    static final double FOLD_CLOSED = 300;
    /** Join angles (degrees): below the first a join is re-aligned, to the second. */
    static final double MIN_JOIN_ANGLE = 60, JOIN_ANGLE = 70;
    /** The straight before the other interstate (the trumpet's stem and its ramps), m; how far back a re-alignment may begin. */
    static final double JOIN_STEM = 500, JOIN_BACK_MIN = 600, JOIN_BACK_MAX = 5000;

    // ------------------------------------------------------------------ town corners

    /** The two legs after a town corner: the first (its curve changed, its design replaced by #designChains) and the second. */
    record Corner(Route a, Candidate b) {}

    /** A bend: both legs' new control polylines ({x[], z[]}), the most it moved the line, how close it passes the town. */
    record Bend(double[][] a, double[][] b, double shift, double passes) {}

    /**
     * The corner of leg a (ending at town ca) and the candidate b (starting there): bent, else b searched again and bent,
     * else both unchanged. The note goes into b's trace.
     */
    static Corner corner(RouteGrid g, RouteTerrain t, RoadClassSpec k, Route a, Candidate b, FederalMap.City town, FederalMap.City next,
                         List<Route> onto) {
        double rmin = 1.1 * k.radius;
        double[] ax = a.curve().controlX(), az = a.curve().controlZ();
        double[] bx = b.curve().controlX(), bz = b.curve().controlZ();
        String[] why = new String[1];
        Bend bend = bend(ax, az, bx, bz, town.x(), town.z(), rmin, why);
        if (bend != null) return apply(k, a, b, bend, String.format(Locale.ROOT, "bent at %s (moved <= %.0f m, passes %.0f m off)", town.id(), bend.shift(), bend.passes()));
        // the second leg again, from the first RESEARCH m before the town, the town and the way back closed
        int i = vertexBefore(ax, az, RoutePolyline.length(ax, az) - RESEARCH);
        if (i < 2) return new Corner(a, note(b, "corner at " + town.id() + " kept apart: " + why[0] + "; the first leg is too short to search again"));
        double sx = ax[i], sz = az[i], hx = ax[i + 1] - ax[i - 1], hz = az[i + 1] - az[i - 1], hl = Math.hypot(hx, hz);
        hx /= hl;
        hz /= hl;
        boolean[] avoid = new boolean[g.cells()];
        int open = g.openCell(sx, sz);
        for (int c = 0; c < avoid.length; c++) {
            double x = g.cx(c % g.nx), z = g.cz(c / g.nx);
            if (Math.hypot(x - town.x(), z - town.z()) < TOWN_CLOSED) avoid[c] = true;
            else if (Math.hypot(x - sx, z - sz) < BEHIND && (x - sx) * hx + (z - sz) * hz < -RouteGrid.CELL) avoid[c] = true;
        }
        avoid[open] = false;
        Candidate nb = RoadNetworkPlan.leg(g, t, k, town, new double[]{sx, sz}, next, onto, avoid);
        if (nb == null || nb.curve() == null)
            return new Corner(a, note(b, "corner at " + town.id() + " kept apart: " + why[0] + "; no second search"));
        double[] cx = new double[i + 1], cz = new double[i + 1];
        System.arraycopy(ax, 0, cx, 0, i + 1);
        System.arraycopy(az, 0, cz, 0, i + 1);
        String[] why2 = new String[1];
        Bend again = bend(cx, cz, nb.curve().controlX(), nb.curve().controlZ(), town.x(), town.z(), rmin, why2);
        if (again == null) {
            String[] why3 = new String[1];
            Corner by = bypass(g, t, k, a, b, town, next, onto, rmin, why3);
            if (by != null) return by;
            return new Corner(a, note(b, "corner at " + town.id() + " kept apart: " + why[0] + "; searched again: " + why2[0]
                    + (why3[0] == null ? "" : "; " + why3[0])));
        }
        return apply(k, a, nb, again, String.format(Locale.ROOT, "searched again from %.0f m before %s (%s), bent (moved <= %.0f m, passes %.0f m off)",
                RESEARCH, town.id(), why[0], again.shift(), again.passes()));
    }

    /**
     * The fold-back's bypass (see the class comment), or null (why[0] set when it was one and the bypass failed; null when
     * the corner is no fold-back).
     */
    private static Corner bypass(RouteGrid g, RouteTerrain t, RoadClassSpec k, Route a, Candidate b, FederalMap.City town, FederalMap.City next,
                                 List<Route> onto, double rmin, String[] why) {
        double[] ax = a.curve().controlX(), az = a.curve().controlZ();
        double[] bx = b.curve().controlX(), bz = b.curve().controlZ();
        int na = ax.length, nb0 = bx.length;
        if (na < 3 || nb0 < 3) return null;
        // the corner as planned, over the last / first 200 m
        double[] pa = pointAt(ax, az, RoutePolyline.length(ax, az) - 200), pb = pointAt(bx, bz, 200);
        double ux = ax[na - 1] - pa[0], uz = az[na - 1] - pa[1], vx = pb[0] - bx[0], vz = pb[1] - bz[0];
        double kink = Math.toDegrees(Math.abs(Math.atan2(ux * vz - uz * vx, ux * vx + uz * vz)));
        if (kink < FOLD_KINK) return null;
        // where the legs part: back from the town along a, the first point more than FOLD_PART off b
        double[][] rb = RoutePolyline.resample(bx, bz, RoutePolyline.SPACING);
        double la = RoutePolyline.length(ax, az), part = -1;
        for (double back = 0; back <= la; back += RoutePolyline.SPACING) {
            double[] p = pointAt(ax, az, la - back);
            double d = Double.POSITIVE_INFINITY;
            for (int i = 0; i < rb[0].length; i++) d = Math.min(d, Math.hypot(rb[0][i] - p[0], rb[1][i] - p[1]));
            if (d > FOLD_PART) { part = back; break; }
        }
        if (part < 0) { why[0] = String.format(Locale.ROOT, "a %.0f deg fold-back whose legs never part", kink); return null; }
        int i = vertexBefore(ax, az, la - part - FOLD_BACK);
        if (i < 2) { why[0] = String.format(Locale.ROOT, "a %.0f deg fold-back parting %.0f m before %s: the first leg is too short to bypass", kink, part, town.id()); return null; }
        double sx = ax[i], sz = az[i], hx = ax[i + 1] - ax[i - 1], hz = az[i + 1] - az[i - 1], hl = Math.hypot(hx, hz);
        hx /= hl;
        hz /= hl;
        // closed: the town, the stretch into it (a from where the legs part on), behind the start
        double[][] ra = RoutePolyline.resample(ax, az, RoutePolyline.SPACING);
        double from = la - part;
        boolean[] avoid = new boolean[g.cells()];
        for (int c = 0; c < avoid.length; c++) {
            double x = g.cx(c % g.nx), z = g.cz(c / g.nx);
            if (Math.hypot(x - town.x(), z - town.z()) < TOWN_CLOSED) { avoid[c] = true; continue; }
            if (Math.hypot(x - sx, z - sz) < BEHIND && (x - sx) * hx + (z - sz) * hz < -RouteGrid.CELL) { avoid[c] = true; continue; }
        }
        double run = 0;
        for (int q = 0; q < ra[0].length; q++) {
            if (q > 0) run += Math.hypot(ra[0][q] - ra[0][q - 1], ra[1][q] - ra[1][q - 1]);
            if (run < from) continue;
            int c0 = g.cellOf(ra[0][q], ra[1][q]), cc = c0 % g.nx, cr = c0 / g.nx, rc = (int) Math.ceil(FOLD_CLOSED / RouteGrid.CELL) + 1;
            for (int dr = -rc; dr <= rc; dr++) for (int dc = -rc; dc <= rc; dc++) {
                int col = cc + dc, row = cr + dr;
                if (col < 0 || row < 0 || col >= g.nx || row >= g.nz) continue;
                if (Math.hypot(g.cx(col) - ra[0][q], g.cz(row) - ra[1][q]) <= FOLD_CLOSED) avoid[row * g.nx + col] = true;
            }
        }
        avoid[g.openCell(sx, sz)] = false;
        Candidate nb = RoadNetworkPlan.leg(g, t, k, town, new double[]{sx, sz}, next, onto, avoid);
        if (nb == null || nb.curve() == null) { why[0] = String.format(Locale.ROOT, "a %.0f deg fold-back: no bypass from %.0f m before %s", kink, part + FOLD_BACK, town.id()); return null; }
        double[] cx = new double[i + 1], cz = new double[i + 1];
        System.arraycopy(ax, 0, cx, 0, i + 1);
        System.arraycopy(az, 0, cz, 0, i + 1);
        String[] why2 = new String[1];
        Bend bend = bend(cx, cz, nb.curve().controlX(), nb.curve().controlZ(), town.x(), town.z(), rmin, why2);
        if (bend == null) { why[0] = String.format(Locale.ROOT, "a %.0f deg fold-back: the bypass from %.0f m before %s does not bend (%s)", kink, part + FOLD_BACK, town.id(), why2[0]); return null; }
        return apply(k, a, nb, bend, String.format(Locale.ROOT, "a %.0f deg fold-back at %s (the legs part %.0f m before it): bypassed from %.0f m before, bent (moved <= %.0f m, passes %.0f m off)",
                kink, town.id(), part, part + FOLD_BACK, bend.shift(), bend.passes()));
    }

    /** The point at arc length s along a polyline (clamped). */
    private static double[] pointAt(double[] xs, double[] zs, double s) {
        double run = 0;
        if (s <= 0) return new double[]{xs[0], zs[0]};
        for (int q = 1; q < xs.length; q++) {
            double l = Math.hypot(xs[q] - xs[q - 1], zs[q] - zs[q - 1]);
            if (run + l >= s) {
                double f = (s - run) / l;
                return new double[]{xs[q - 1] + f * (xs[q] - xs[q - 1]), zs[q - 1] + f * (zs[q] - zs[q - 1])};
            }
            run += l;
        }
        return new double[]{xs[xs.length - 1], zs[zs.length - 1]};
    }

    private static Corner apply(RoadClassSpec k, Route a, Candidate b, Bend bend, String note) {
        RoadCurve ca = new RoadCurve(bend.a()[0], bend.a()[1]), cb = new RoadCurve(bend.b()[0], bend.b()[1]);
        // the designs are replaced once the corridor's legs are designed as one road (#designChains)
        Route na = new Route(a.id(), a.corridor(), a.roadClass(), a.from(), a.to(), a.fromJoin(), a.toJoin(), ca, a.design(), a.hard(), a.soft(),
                a.round(), a.score(), a.trace());
        Candidate nb = new Candidate(b.cells(), cb, b.design(), b.score(), b.round(), (b.trace() == null ? "" : b.trace()) + " | " + note,
                b.fromJoin(), b.toJoin());
        return new Corner(na, nb);
    }

    private static Candidate note(Candidate b, String note) {
        return new Candidate(b.cells(), b.curve(), b.design(), b.score(), b.round(), (b.trace() == null ? "" : b.trace()) + " | " + note,
                b.fromJoin(), b.toJoin());
    }

    /** The last control point at or before arc length s (0 when s is before the second). */
    static int vertexBefore(double[] xs, double[] zs, double s) {
        double run = 0;
        int i = 0;
        for (int q = 1; q < xs.length - 1; q++) {
            run += Math.hypot(xs[q] - xs[q - 1], zs[q] - zs[q - 1]);
            if (run > s) break;
            i = q;
        }
        return i;
    }

    /** The bend's radius over the planner's rule (1.1 x the class radius), so its 40 m chords keep that rule. */
    static final double BEND_MARGIN = 1.02;

    /**
     * Legs a and b meeting at a point (a's end, b's start: the town, or where b was searched again from) joined there by a
     * circular arc of BEND_MARGIN x rmin tangent to both (its centre where the lines that far off both legs, on the inside
     * of the turn, meet: the legs as planned, not straightened), both tangent points within WINDOW of the meeting point;
     * the line resampled at the planner's spacing and relaxed by its rule; the legs then meet at its point nearest the
     * town. Null (why[0] set) when the arc does not fit within WINDOW or moves the line more than MAX_SHIFT.
     */
    static Bend bend(double[] ax, double[] az, double[] bx, double[] bz, double tx, double tz, double rmin, String[] why) {
        double[][] ra = RoutePolyline.resample(ax, az, RoutePolyline.SPACING), rb = RoutePolyline.resample(bx, bz, RoutePolyline.SPACING);
        RoutePolyline.relax(ra[0], ra[1], rmin);
        RoutePolyline.relax(rb[0], rb[1], rmin);
        Line la = Line.back(ra[0], ra[1]), lb = new Line(rb[0], rb[1]);
        // the turn at the meeting point, over 200 m each way
        double[] pa = la.point(Math.min(200, la.length)), pb = lb.point(Math.min(200, lb.length)), j = la.point(0);
        double ux = j[0] - pa[0], uz = j[1] - pa[1], vx = pb[0] - j[0], vz = pb[1] - j[1];
        double cross = ux * vz - uz * vx, dot = ux * vx + uz * vz;
        double turn = Math.toDegrees(Math.abs(Math.atan2(cross, dot)));
        List<double[]> pts = new ArrayList<>();
        double r = BEND_MARGIN * rmin, shift = 0;
        if (turn < 1) {
            for (int i = 0; i < ra[0].length; i++) pts.add(new double[]{ra[0][i], ra[1][i]});
            for (int i = 1; i < rb[0].length; i++) pts.add(new double[]{rb[0][i], rb[1][i]});
        } else {
            double sg = Math.signum(cross);
            // gap(t): how far the centre found from a's point t back lies beyond r off b, toward the turn's inside
            double reach = Math.min(WINDOW, Math.min(la.length, lb.length) - RoutePolyline.SPACING);
            double t0 = -1, t1 = -1, prev = Double.NaN;
            for (double t = 5; t <= reach; t += 5) {
                double fv = gap(la, lb, t, sg, r);
                if (!Double.isNaN(prev) && prev < 0 && fv >= 0) { t0 = t - 5; t1 = t; break; }
                prev = fv;
            }
            if (t0 < 0) { why[0] = String.format(Locale.ROOT, "a %.0f deg bend of R %.0f m does not fit within %.0f m", turn, r, WINDOW); return null; }
            for (int it = 0; it < 40; it++) {
                double tm = (t0 + t1) / 2;
                if (gap(la, lb, tm, sg, r) < 0) t0 = tm; else t1 = tm;
            }
            double ta = t1;
            double[] fa = la.frame(ta);
            double[] c = centre(fa, sg, r);
            double[] q = lb.project(c[0], c[1]);
            double tb = q[0];
            if (tb <= 0 || tb > WINDOW || Math.abs(q[1] - r) > 1) {
                why[0] = String.format(Locale.ROOT, "the %.0f deg bend touches the second leg %.0f m on", turn, tb);
                return null;
            }
            double aA = Math.atan2(fa[1] - c[1], fa[0] - c[0]), aB = Math.atan2(q[3] - c[1], q[2] - c[0]);
            double sweep = sg * (aB - aA);
            while (sweep <= 0) sweep += 2 * Math.PI;
            while (sweep > 2 * Math.PI) sweep -= 2 * Math.PI;
            if (sweep > Math.PI) { why[0] = String.format(Locale.ROOT, "the %.0f deg bend turns back (%.0f deg)", turn, Math.toDegrees(sweep)); return null; }
            // a up to the arc, the arc, b on from it
            int na = ra[0].length;
            for (int i = 0; i < na; i++) if (la.at(na - 1 - i) > ta + RoutePolyline.SPACING / 2) pts.add(new double[]{ra[0][i], ra[1][i]});
            int steps = Math.max(1, (int) Math.ceil(sweep * r / RoutePolyline.SPACING));
            for (int i = 0; i <= steps; i++) {
                double ang = aA + sg * sweep * i / steps;
                double[] p = {c[0] + r * Math.cos(ang), c[1] + r * Math.sin(ang)};
                pts.add(p);
                shift = Math.max(shift, Math.min(la.project(p[0], p[1])[1], lb.project(p[0], p[1])[1]));
            }
            for (int i = 0; i < rb[0].length; i++) if (lb.at(i) > tb + RoutePolyline.SPACING / 2) pts.add(new double[]{rb[0][i], rb[1][i]});
        }
        double[] px = new double[pts.size()], pz = new double[pts.size()];
        for (int i = 0; i < px.length; i++) { px[i] = pts.get(i)[0]; pz[i] = pts.get(i)[1]; }
        double[][] w = RoutePolyline.resample(px, pz, RoutePolyline.SPACING);
        double[] wx = w[0], wz = w[1];
        RoutePolyline.relax(wx, wz, rmin);
        int n = wx.length;
        double minR = Double.POSITIVE_INFINITY;
        for (int i = 1; i < n - 1; i++) minR = Math.min(minR, RoutePolyline.radius(wx, wz, i));
        if (minR < 0.99 * rmin) { why[0] = String.format(Locale.ROOT, "the bend does not keep the radius (R %.0f m)", minR); return null; }
        if (shift > MAX_SHIFT) { why[0] = String.format(Locale.ROOT, "the %.0f deg bend moves the line %.0f m", turn, shift); return null; }
        // the legs meet at the line's point nearest the town
        int m = -1;
        double best = Double.POSITIVE_INFINITY;
        for (int i = 3; i < n - 3; i++) {
            double d = Math.hypot(wx[i] - tx, wz[i] - tz);
            if (d < best) { best = d; m = i; }
        }
        if (m < 0) { why[0] = "the legs are too short"; return null; }
        return new Bend(new double[][]{java.util.Arrays.copyOfRange(wx, 0, m + 1), java.util.Arrays.copyOfRange(wz, 0, m + 1)},
                new double[][]{java.util.Arrays.copyOfRange(wx, m, n), java.util.Arrays.copyOfRange(wz, m, n)}, shift, best);
    }

    /**
     * The centre r off a's frame f (run back from the meeting point, so a's way is -f's tangent) toward the turn's inside
     * (sg: the turn's sign, positive turning from +x toward +z).
     */
    private static double[] centre(double[] f, double sg, double r) {
        // a's way of travel (-tx, -tz); its left (toward +z of +x) normal (tz, -tx)
        return new double[]{f[0] + sg * r * f[3], f[1] - sg * r * f[2]};
    }

    /** For a's point t back from the meeting point: how far its centre lies beyond r off b, toward the turn's inside. */
    private static double gap(Line la, Line lb, double t, double sg, double r) {
        double[] c = centre(la.frame(t), sg, r);
        double[] q = lb.project(c[0], c[1]);
        // projecting onto b's very start: the centre is still behind the turn
        if (q[0] <= 1e-6) return -r;
        return sg * q[4] - r;
    }

    /** A polyline with its arc lengths: points, frames and projections (offsets positive toward +z of its direction +x). */
    static final class Line {
        final double[] x, z, s;
        final double length;

        Line(double[] x, double[] z) {
            this.x = x;
            this.z = z;
            s = new double[x.length];
            for (int i = 1; i < x.length; i++) s[i] = s[i - 1] + Math.hypot(x[i] - x[i - 1], z[i] - z[i - 1]);
            length = s[x.length - 1];
        }

        /** The polyline run backwards (from its end). */
        static Line back(double[] x, double[] z) {
            double[] bx = x.clone(), bz = z.clone();
            reverse(bx);
            reverse(bz);
            return new Line(bx, bz);
        }

        double at(int i) { return s[i]; }

        double[] point(double t) {
            double[] f = frame(t);
            return new double[]{f[0], f[1]};
        }

        /** {x, z, tx, tz} at arc length t (clamped). */
        double[] frame(double t) {
            int i = 1;
            while (i < x.length - 1 && s[i] < t) i++;
            double l = s[i] - s[i - 1], f = l < 1e-9 ? 0 : Math.max(0, Math.min(1, (t - s[i - 1]) / l));
            double dx = x[i] - x[i - 1], dz = z[i] - z[i - 1], dl = Math.max(1e-9, Math.hypot(dx, dz));
            return new double[]{x[i - 1] + dx * f, z[i - 1] + dz * f, dx / dl, dz / dl};
        }

        /** {arc length, distance, x, z, offset} of the nearest point (offset positive toward +z of the direction +x). */
        double[] project(double px, double pz) {
            double best = Double.POSITIVE_INFINITY;
            double[] out = null;
            for (int i = 1; i < x.length; i++) {
                double dx = x[i] - x[i - 1], dz = z[i] - z[i - 1], l2 = dx * dx + dz * dz;
                double f = l2 < 1e-12 ? 0 : Math.max(0, Math.min(1, ((px - x[i - 1]) * dx + (pz - z[i - 1]) * dz) / l2));
                double qx = x[i - 1] + dx * f, qz = z[i - 1] + dz * f, d = Math.hypot(px - qx, pz - qz);
                if (d < best) {
                    best = d;
                    double l = Math.sqrt(l2);
                    out = new double[]{s[i - 1] + l * f, d, qx, qz, l < 1e-9 ? 0 : (dx * (pz - z[i - 1]) - dz * (px - x[i - 1])) / l};
                }
            }
            return out;
        }
    }

    /**
     * The corridor's legs from index first on that meet end to start, designed as one road on the terrain; each leg's
     * profile is the road's at its stations (projected), its structures, hard widths and score from that profile.
     */
    static void designChains(RouteTerrain t, RoadClassSpec k, List<Route> routes, int first) {
        int i = first;
        while (i < routes.size()) {
            int j = i;
            while (j + 1 < routes.size() && meets(routes.get(j), routes.get(j + 1))) j++;
            if (j > i) designChain(t, k, routes, i, j);
            i = j + 1;
        }
    }

    static boolean meets(Route a, Route b) {
        if (!a.corridor().equals(b.corridor())) return false;
        double[] ax = a.curve().controlX(), az = a.curve().controlZ(), bx = b.curve().controlX(), bz = b.curve().controlZ();
        return ax[ax.length - 1] == bx[0] && az[az.length - 1] == bz[0];
    }

    private static void designChain(RouteTerrain t, RoadClassSpec k, List<Route> routes, int i, int j) {
        List<double[]> pts = new ArrayList<>();
        for (int q = i; q <= j; q++) {
            double[] xs = routes.get(q).curve().controlX(), zs = routes.get(q).curve().controlZ();
            for (int p = q == i ? 0 : 1; p < xs.length; p++) pts.add(new double[]{xs[p], zs[p]});
        }
        double[] cx = new double[pts.size()], cz = new double[pts.size()];
        for (int p = 0; p < cx.length; p++) { cx[p] = pts.get(p)[0]; cz[p] = pts.get(p)[1]; }
        RoadCurve road = new RoadCurve(cx, cz);
        RouteDesigner.Result d = RouteDesigner.design(k, road, t);
        double before = 0;
        for (int q = i; q <= j; q++) {
            Route r = routes.get(q);
            RoadCurve lc = r.curve();
            int n = RoadProfile.samples(Math.floor(lc.length / RoadProfile.DS) * RoadProfile.DS);
            double[] e = new double[n];
            for (int s = 0; s < n; s++) {
                double[] p = lc.point(s * RoadProfile.DS, 0);
                double[] l = road.local(p[0], p[1], 50, 0);
                e[s] = d.profile().at(l != null ? l[0] : before + s * RoadProfile.DS);
            }
            RouteDesigner.Result ld = RouteDesigner.design(k, lc, t, e);
            double[] hard = RoadNetworkPlan.hardWidths(k, ld);
            routes.set(q, new Route(r.id(), r.corridor(), r.roadClass(), r.from(), r.to(), r.fromJoin(), r.toJoin(), lc, ld, hard, r.soft(), r.round(),
                    RoadNetworkPlan.score(k, ld), r.trace() + String.format(Locale.ROOT, " | designed as one road with %d legs", j - i + 1)));
            before += lc.length;
        }
    }

    // ------------------------------------------------------------------ joins onto another interstate

    /**
     * Candidate c with its join onto an interstate (its end, or its start) re-aligned to JOIN_ANGLE where it meets at
     * less than MIN_JOIN_ANGLE; c unchanged (a note in its trace) when it is steep enough or nothing fits.
     */
    static Candidate steepen(RouteGrid g, RouteTerrain t, RoadClassSpec k, Candidate c, List<Route> onto, boolean end) {
        Join j = end ? c.toJoin() : c.fromJoin();
        if (j == null || c.curve() == null) return c;
        Route host = null;
        for (Route r : onto) if (r.id().equals(j.route())) host = r;
        if (host == null || host.roadClass() != RoadClassSpec.INTERSTATE) return c;
        double[] xs = c.curve().controlX(), zs = c.curve().controlZ();
        if (!end) { reverse(xs); reverse(zs); }
        int n = xs.length;
        double[] f = new double[5];
        host.curve().frame(j.station(), f);
        double tx = f[2], tz = f[3];
        double dx = xs[n - 1] - xs[n - 2], dz = zs[n - 1] - zs[n - 2], dl = Math.hypot(dx, dz);
        double along = (dx * tx + dz * tz) / dl;
        double angle = Math.toDegrees(Math.acos(Math.min(1, Math.abs(along))));
        if (angle >= MIN_JOIN_ANGLE) return c;
        double len = RoutePolyline.length(xs, zs);
        double sa = along >= 0 ? 1 : -1, th = Math.toRadians(JOIN_ANGLE);
        // the host as a polyline every 4 m, within reach of the join
        HostLine hl = new HostLine(host.curve(), j.station() - 6000, j.station() + 6000, 4);
        double rad = 1.1 * k.radius;
        double run = 0;
        double[] arcLen = new double[n];
        for (int i = 1; i < n; i++) { run += Math.hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]); arcLen[i] = run; }
        // why the tried starts failed: too near the host, no meeting point, a short stem, a blocked station, too shallow
        // there, the wrong side or closed
        int[] fails = new int[6];
        for (int i = n - 2; i >= 1; i--) {
            double back = len - arcLen[i];
            if (back < JOIN_BACK_MIN) continue;
            if (back > JOIN_BACK_MAX) break;
            // the side of the host this start lies on (the leg may cross the host on its way in)
            double off = hl.side(xs[i], zs[i]), side = Math.signum(off);
            if (Math.abs(off) < JOIN_STEM) { fails[0]++; continue; }
            double hx = xs[i + 1] - xs[i - 1], hz = zs[i + 1] - zs[i - 1], h = Math.hypot(hx, hz);
            hx /= h;
            hz /= h;
            // the heading to come in on, against the host's tangent where it is met (found again where the host curves):
            // on along the host's way, across it toward the host (right normal (-tz, tx))
            double ref = j.station(), qx = 0, qz = 0, newAngle = 0;
            double[] hit = null;
            List<double[]> path = null;
            for (int it = 0; it < 4; it++) {
                host.curve().frame(ref, f);
                double hx0 = f[2], hz0 = f[3];
                qx = sa * Math.cos(th) * hx0 + side * Math.sin(th) * hz0;
                qz = sa * Math.cos(th) * hz0 - side * Math.sin(th) * hx0;
                double phi = Math.atan2(hx * qz - hz * qx, hx * qx + hz * qz);
                path = new ArrayList<>();
                arc(xs[i], zs[i], hx, hz, rad, phi, path);
                double[] e = path.get(path.size() - 1);
                hit = hl.ray(e[0], e[1], qx, qz);
                if (hit == null) break;
                host.curve().frame(hit[2], f);
                newAngle = Math.toDegrees(Math.acos(Math.min(1, Math.abs(qx * f[2] + qz * f[3]))));
                if (Math.abs(newAngle - JOIN_ANGLE) < 2) break;
                ref = hit[2];
            }
            if (hit == null) { fails[1]++; continue; }
            double ex = path.get(path.size() - 1)[0], ez = path.get(path.size() - 1)[1];
            double stem = Math.hypot(hit[0] - ex, hit[1] - ez);
            if (stem < JOIN_STEM) { fails[2]++; continue; }
            double s1 = hit[2];
            if (s1 < RoadNetworkPlan.JOIN_CLEAR || s1 > host.curve().length - RoadNetworkPlan.JOIN_CLEAR || RoadNetworkPlan.onStructure(host, s1, RoadNetworkPlan.JOIN_CLEAR)) {
                fails[3]++;
                continue;
            }
            if (newAngle < MIN_JOIN_ANGLE) { fails[4]++; continue; }
            // the new part keeps to the start's side of the host and off closed cells
            boolean ok = true;
            for (int q = 0; q < path.size() && ok; q++) ok = hl.side(path.get(q)[0], path.get(q)[1]) * side > 20 && !g.closed[g.cellOf(path.get(q)[0], path.get(q)[1])];
            for (double st = 0; st < stem && ok; st += 16) ok = !g.closed[g.cellOf(ex + qx * st, ez + qz * st)];
            if (!ok) { fails[5]++; continue; }
            path.add(new double[]{hit[0], hit[1]});
            double[] px = new double[path.size() + 1], pz = new double[path.size() + 1];
            px[0] = xs[i];
            pz[0] = zs[i];
            for (int q = 0; q < path.size(); q++) { px[q + 1] = path.get(q)[0]; pz[q + 1] = path.get(q)[1]; }
            double[][] tail = RoutePolyline.resample(px, pz, RoutePolyline.SPACING);
            int m = tail[0].length;
            double[] nx = new double[i + m], nz = new double[i + m];
            System.arraycopy(xs, 0, nx, 0, i);
            System.arraycopy(zs, 0, nz, 0, i);
            System.arraycopy(tail[0], 0, nx, i, m);
            System.arraycopy(tail[1], 0, nz, i, m);
            if (!end) { reverse(nx); reverse(nz); }
            RoadCurve curve = new RoadCurve(nx, nz);
            RouteDesigner.Result d = RouteDesigner.design(k, curve, t);
            Join nj = new Join(host.id(), s1, hit[0], hit[1]);
            String note = String.format(Locale.ROOT, " | join on %s steepened %.0f -> %.0f deg over the last %.0f m (stem %.0f m)", host.id(), angle, newAngle,
                    back, stem);
            return new Candidate(c.cells(), curve, d, RoadNetworkPlan.score(k, d), c.round(), (c.trace() == null ? "" : c.trace()) + note,
                    end ? c.fromJoin() : nj, end ? nj : c.toJoin());
        }
        return new Candidate(c.cells(), c.curve(), c.design(), c.score(), c.round(), (c.trace() == null ? "" : c.trace())
                + String.format(Locale.ROOT, " | join on %s at %.0f deg: no re-alignment fits (starts failing: %d near the host, %d no meeting, %d short stem, %d blocked station, %d shallow, %d side or closed)",
                host.id(), angle, fails[0], fails[1], fails[2], fails[3], fails[4], fails[5]), c.fromJoin(), c.toJoin());
    }

    /** Points of an arc from (x, z) heading (hx, hz), radius r, turning phi (positive: from +x toward +z), into out (every <= 10 m). */
    static void arc(double x, double z, double hx, double hz, double r, double phi, List<double[]> out) {
        double sgn = Math.signum(phi);
        if (sgn == 0) { out.add(new double[]{x, z}); return; }
        double ox = x - sgn * r * hz, oz = z + sgn * r * hx;
        double p0 = Math.atan2(z - oz, x - ox);
        int steps = Math.max(2, (int) Math.ceil(Math.abs(phi) * r / 10));
        for (int q = 1; q <= steps; q++) {
            double a = p0 + phi * q / steps;
            out.add(new double[]{ox + r * Math.cos(a), oz + r * Math.sin(a)});
        }
    }

    static void reverse(double[] a) {
        for (int i = 0, j = a.length - 1; i < j; i++, j--) { double v = a[i]; a[i] = a[j]; a[j] = v; }
    }

    /** A stretch of a road as a polyline: where a ray first meets it, and which side of it a point is on. */
    static final class HostLine {
        final double[] x, z, s;

        HostLine(RoadCurve c, double from, double to, double step) {
            from = Math.max(0, from);
            to = Math.min(c.length, to);
            int n = Math.max(2, (int) Math.ceil((to - from) / step) + 1);
            x = new double[n];
            z = new double[n];
            s = new double[n];
            double[] f = new double[5];
            for (int i = 0; i < n; i++) {
                s[i] = Math.min(to, from + i * step);
                c.frame(s[i], f);
                x[i] = f[0];
                z[i] = f[1];
            }
        }

        /** {x, z, station} where the ray from (px, pz) along (dx, dz) first crosses the line, or null. */
        double[] ray(double px, double pz, double dx, double dz) {
            double bestT = Double.POSITIVE_INFINITY;
            double[] out = null;
            for (int i = 0; i + 1 < x.length; i++) {
                double ex = x[i + 1] - x[i], ez = z[i + 1] - z[i];
                double den = dx * ez - dz * ex;
                if (Math.abs(den) < 1e-12) continue;
                double wx = x[i] - px, wz = z[i] - pz;
                double tr = (wx * ez - wz * ex) / den, u = (wx * dz - wz * dx) / den;
                if (tr <= 0 || u < 0 || u > 1 || tr >= bestT) continue;
                bestT = tr;
                out = new double[]{px + dx * tr, pz + dz * tr, s[i] + (s[i + 1] - s[i]) * u};
            }
            return out;
        }

        /** Signed distance of a point from the nearest point of the line (positive to the right of its direction). */
        double side(double px, double pz) {
            int b = 0;
            double bd = Double.POSITIVE_INFINITY;
            for (int i = 0; i < x.length; i++) {
                double d = (px - x[i]) * (px - x[i]) + (pz - z[i]) * (pz - z[i]);
                if (d < bd) { bd = d; b = i; }
            }
            int a = Math.max(0, Math.min(x.length - 2, b));
            double tx = x[a + 1] - x[a], tz = z[a + 1] - z[a], tl = Math.hypot(tx, tz);
            return (-(px - x[b]) * tz + (pz - z[b]) * tx) / tl;
        }
    }
}
