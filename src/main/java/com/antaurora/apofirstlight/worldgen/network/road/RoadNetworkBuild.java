package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson.Node;
import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson.RouteData;
import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson.Structure;
import com.antaurora.apofirstlight.worldgen.network.route.RoadClassSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The national rollout's built network (C3.3a; docs/worldgen/federal_highways_c3.md, 11): every planned route of the C3.1
 * plan, turned into the routes the world builds, as one JSON (RoadNetworkJson.write) the server's works and every
 * client read. Pure Java, deterministic: a function of the plan JSON only.
 * <ul>
 *   <li>through towns: legs of one corridor that meet end to start at a town node (not a join) with a kink of at most
 *   MAX_THROUGH_KINK are one road; around each such node the headings of the control polyline are smoothed (a Gaussian
 *   of sigma points, blended in over 4 sigma, the small position error spread over the window), sigma growing until the
 *   curve there keeps the class's minimum radius; the profile is taken from the legs (each sample on a leg takes its
 *   height, across the corner linear) and smoothed over the node with the class's vertical sigma;</li>
 *   <li>trims: until C3.3b builds junctions and interchanges, a road ending at or near another (a join, a town two roads
 *   share, a sharper kink) stops short: where the conflicts with another road (its works reaching the other's formation,
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

    public static final String STAGE = "c3.3a";
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
    /** The shortest piece of a road kept after trims and splits, m; the most splits of one network. */
    public static final double MIN_PIECE = 200;
    public static final int MAX_SPLITS = 32;

    /** The built network: its JSON, the trims (metres cut at each end) and the report lines. */
    public record Result(String json, List<RouteData> routes, List<String> report, int conflicts) {}

    public static Result build(String planJson) {
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
        List<RouteData> trimmed = splits(trims(roads, report), report);
        int conflicts = verify(trimmed, report);
        return new Result(RoadNetworkJson.write(version, STAGE, trimmed), trimmed, report, conflicts);
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

    /** The successor of each leg in its corridor through a town node, or -1. */
    private static List<RouteData> through(List<RouteData> legs, List<Node> nodes, List<String> report) {
        int n = legs.size();
        int[] next = new int[n], prev = new int[n];
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
                if (k > MAX_THROUGH_KINK) {
                    report.add(String.format(Locale.ROOT, "town %s: %s and %s kink %.1f deg, kept apart (trimmed)", a.to(), a.id(), b.id(), k));
                    continue;
                }
                next[i] = j;
                prev[j] = i;
                break;
            }
        }
        List<RouteData> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (prev[i] >= 0) continue;
            List<RouteData> chain = new ArrayList<>();
            for (int k = i; k >= 0; k = next[k]) chain.add(legs.get(k));
            out.add(chain.size() == 1 ? chain.get(0) : merge(chain, report));
        }
        return out;
    }

    private static RoadClassSpec spec(String roadClass) {
        for (RoadClassSpec k : RoadClassSpec.values()) if (k.id.equals(roadClass)) return k;
        throw new IllegalArgumentException("unknown road class " + roadClass);
    }

    /** One road from a chain of legs: corners smoothed at the shared nodes, profile from the legs, structures mapped. */
    private static RouteData merge(List<RouteData> chain, List<String> report) {
        RouteData first = chain.get(0), last = chain.get(chain.size() - 1);
        RoadClassSpec k = spec(first.roadClass());
        List<double[]> pts = new ArrayList<>();
        List<double[]> nodePts = new ArrayList<>();
        for (int c = 0; c < chain.size(); c++) {
            RouteData r = chain.get(c);
            for (int q = c == 0 ? 0 : 1; q < r.xs().length; q++) pts.add(new double[]{r.xs()[q], r.zs()[q]});
            if (c + 1 < chain.size()) nodePts.add(new double[]{r.xs()[r.xs().length - 1], r.zs()[r.zs().length - 1]});
        }
        double[] corner = new double[nodePts.size()];
        // from the last node back, so the earlier nodes' indices stay valid
        for (int q = nodePts.size() - 1; q >= 0; q--) corner[q] = smoothCorner(pts, nodePts.get(q), k.radius, report, first.corridor());
        double[] xs = new double[pts.size()], zs = new double[pts.size()];
        for (int q = 0; q < xs.length; q++) { xs[q] = RoadNetworkJson.round2(pts.get(q)[0]); zs[q] = RoadNetworkJson.round2(pts.get(q)[1]); }
        RoadCurve merged = new RoadCurve(xs, zs);
        // the legs' own curves and profiles
        List<RoadCurve> lc = new ArrayList<>();
        List<RoadProfile> lp = new ArrayList<>();
        for (RouteData r : chain) { lc.add(new RoadCurve(r.xs(), r.zs())); lp.add(RoadProfile.of(r.profile())); }
        // each sample on a leg (within ON_LEG of it) takes the leg's height; across the smoothed corners, where the
        // merged road leaves the legs, the height runs linearly between the last matched samples
        int m = RoadProfile.samples(merged.length);
        double[] e = new double[m];
        for (int i = 0; i < m; i++) {
            double[] p = merged.point(Math.min(merged.length, i * RoadProfile.DS), 0);
            double best = Double.POSITIVE_INFINITY, h = Double.NaN;
            for (int c = 0; c < chain.size(); c++) {
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
        for (int q = 0; q < nodePts.size(); q++) {
            double[] l = merged.local(nodePts.get(q)[0], nodePts.get(q)[1], MAX_CORNER, 0);
            if (l == null) throw new IllegalStateException("node off the merged road: " + first.corridor());
            double reach = 4 * corner[q] * POINT_SPACING + 2 * k.vSigma(), fade = 2 * k.vSigma();
            for (int i = 0; i < m; i++) {
                double d = Math.abs(i * RoadProfile.DS - l[0]);
                w[i] = Math.max(w[i], d <= reach ? 1 : d >= reach + fade ? 0 : CrossSection.smooth(1 - (d - reach) / fade));
            }
        }
        for (int i = 0; i < m; i++) e[i] = RoadNetworkJson.round2(e[i] + (sm[i] - e[i]) * w[i]);
        // structures at their places on the merged road
        List<Structure> ss = new ArrayList<>();
        for (int c = 0; c < chain.size(); c++) {
            for (Structure s : chain.get(c).structures()) {
                double[] p0 = lc.get(c).point(s.s0(), 0), p1 = lc.get(c).point(s.s1(), 0);
                double[] l0 = merged.local(p0[0], p0[1], 20, 0), l1 = merged.local(p1[0], p1[1], 20, 0);
                if (l0 == null || l1 == null) {
                    report.add("structure " + s.kind() + " " + s.s0() + ".." + s.s1() + " of " + chain.get(c).id() + " lost in the merge (inside a smoothed corner)");
                    continue;
                }
                double a = RoadNetworkJson.round2(Math.min(l0[0], l1[0])), b = RoadNetworkJson.round2(Math.max(l0[0], l1[0]));
                for (int i = 0; i < m; i++) if (w[i] > 0 && i * RoadProfile.DS >= a - 20 && i * RoadProfile.DS <= b + 20) {
                    report.add("structure " + s.kind() + " of " + chain.get(c).id() + " near a smoothed node: check the merged profile");
                    break;
                }
                ss.add(new Structure(s.kind(), a, b, s.height()));
            }
        }
        String id = first.corridor() + ":" + first.from() + "-" + last.to();
        StringBuilder joined = new StringBuilder();
        for (int c = 0; c < chain.size(); c++) joined.append(c > 0 ? " + " : "").append(chain.get(c).id());
        StringBuilder corners = new StringBuilder();
        for (int q = 0; q < corner.length; q++) corners.append(q > 0 ? ", " : "").append(String.format(Locale.ROOT, "%s sigma %.0f points", chain.get(q).to(), corner[q]));
        report.add(String.format(Locale.ROOT, "through: %s = %s (%.0f m; corners %s)", id, joined, merged.length, corners));
        return new RouteData(id, first.roadClass(), first.corridor(), first.from(), last.to(), xs, zs, e, List.copyOf(ss), null);
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

    /** Polyline length from point i0 to point i1. */
    private static double arcTo(List<double[]> p, int i0, int i1) {
        double s = 0;
        for (int i = i0; i < i1; i++) s += Math.hypot(p.get(i + 1)[0] - p.get(i)[0], p.get(i + 1)[1] - p.get(i)[1]);
        return s;
    }

    // ------------------------------------------------------------------ trims

    private static int rank(String roadClass) { return roadClass.equals("interstate") ? 2 : roadClass.equals("state") ? 1 : 0; }

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

    /** Each road's trim window against the untrimmed roads of the same or a higher class. */
    private static List<RouteData> trims(List<RouteData> roads, List<String> report) {
        List<RoadCurve> cs = new ArrayList<>();
        for (RouteData r : roads) cs.add(curve(r));
        double margin = 2 * RoadWorks.REACH + 2 * RoadDesign.END;
        List<RouteData> out = new ArrayList<>();
        for (int i = 0; i < roads.size(); i++) {
            RouteData r = roads.get(i);
            RoadCurve rc = cs.get(i);
            CrossSection rs = CrossSection.of(r.roadClass());
            double profileLen = (r.profile().length - 1) * RoadProfile.DS, len = Math.min(rc.length, profileLen);
            double a = 0, b = len;
            for (int j = 0; j < roads.size(); j++) {
                RouteData q = roads.get(j);
                if (i == j || rank(q.roadClass()) < rank(r.roadClass()) || !near(r, q, margin)) continue;
                List<Double> bad = conflicts(rc, rs, cs.get(j), CrossSection.of(q.roadClass()));
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
                if (lastStart > Double.NEGATIVE_INFINITY) a = Math.max(a, lastStart + RoadDesign.END + TRIM_MARGIN);
                if (firstEnd < Double.POSITIVE_INFINITY) b = Math.min(b, firstEnd - RoadDesign.END - TRIM_MARGIN);
                if (mid > 0) report.add(String.format(Locale.ROOT, "mid-route: %s meets %s on %d stations (the other end trims)", r.id(), q.id(), mid));
            }
            a = Math.ceil(a / RoadProfile.DS - 1e-9) * RoadProfile.DS;
            b = Math.floor(b / RoadProfile.DS + 1e-9) * RoadProfile.DS;
            // never inside a structure: move the end past it
            for (Structure s : r.structures()) {
                if (a > 0 && a >= s.s0() - STRUCTURE_CLEAR && a <= s.s1() + STRUCTURE_CLEAR) a = Math.ceil((s.s1() + STRUCTURE_CLEAR) / RoadProfile.DS) * RoadProfile.DS;
                if (b < len && b >= s.s0() - STRUCTURE_CLEAR && b <= s.s1() + STRUCTURE_CLEAR) b = Math.floor((s.s0() - STRUCTURE_CLEAR) / RoadProfile.DS) * RoadProfile.DS;
            }
            if (b - a < MIN_PIECE) {
                report.add(String.format(Locale.ROOT, "left out: %s (trimmed to %.0f .. %.0f)", r.id(), a, b));
                continue;
            }
            if (a > 0 || b < len) {
                report.add(String.format(Locale.ROOT, "trim: %s keeps %.0f .. %.0f of %.0f m (start -%.0f, end -%.0f)", r.id(), a + 0.0, b, len, a + 0.0, len - b));
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
    private static List<RouteData> splits(List<RouteData> roads, List<String> report) {
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
                List<Double> bad = conflicts(cs.get(i), CrossSection.of(r.roadClass()), cs.get(j), CrossSection.of(q.roadClass()));
                if (bad.isEmpty()) continue;
                int k = 0;
                while (k + 1 < bad.size() && bad.get(k + 1) - bad.get(k) <= RUN_GAP) k++;
                double off = r.trim() == null ? 0 : r.trim()[0];
                double full = r.trim() != null ? r.trim()[1] : Math.min(cs.get(i).length, (r.profile().length - 1) * RoadProfile.DS);
                double c0 = off + bad.get(0), c1 = off + bad.get(k);
                double b0 = Math.floor((c0 - RoadDesign.END - TRIM_MARGIN) / RoadProfile.DS) * RoadProfile.DS;
                double a1 = Math.ceil((c1 + RoadDesign.END + TRIM_MARGIN) / RoadProfile.DS) * RoadProfile.DS;
                for (Structure st : r.structures()) {
                    if (b0 >= st.s0() - STRUCTURE_CLEAR && b0 <= st.s1() + STRUCTURE_CLEAR) b0 = Math.floor((st.s0() - STRUCTURE_CLEAR) / RoadProfile.DS) * RoadProfile.DS;
                    if (a1 >= st.s0() - STRUCTURE_CLEAR && a1 <= st.s1() + STRUCTURE_CLEAR) a1 = Math.ceil((st.s1() + STRUCTURE_CLEAR) / RoadProfile.DS) * RoadProfile.DS;
                }
                pieces = new ArrayList<>();
                double[][] w = {{off, b0}, {a1, full}};
                StringBuilder kept = new StringBuilder();
                for (int p = 0; p < 2; p++) {
                    if (w[p][1] - w[p][0] < MIN_PIECE) continue;
                    pieces.add(new RouteData(r.id() + "~" + (p + 1), r.roadClass(), r.corridor(), r.from(), r.to(), r.xs(), r.zs(), r.profile(),
                            r.structures(), w[p]));
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
    private static int verify(List<RouteData> roads, List<String> report) {
        List<RoadCurve> cs = new ArrayList<>();
        for (RouteData r : roads) cs.add(curve(r));
        double margin = 2 * RoadWorks.REACH + 2 * RoadDesign.END;
        int total = 0;
        for (int i = 0; i < roads.size(); i++) for (int j = 0; j < roads.size(); j++) {
            if (i == j || !near(roads.get(i), roads.get(j), margin)) continue;
            List<Double> bad = conflicts(cs.get(i), CrossSection.of(roads.get(i).roadClass()), cs.get(j), CrossSection.of(roads.get(j).roadClass()));
            if (bad.isEmpty()) continue;
            total += bad.size();
            report.add(String.format(Locale.ROOT, "CONFLICT: %s vs %s on %d stations (%.0f .. %.0f)", roads.get(i).id(), roads.get(j).id(), bad.size(),
                    bad.get(0), bad.get(bad.size() - 1)));
        }
        return total;
    }
}
