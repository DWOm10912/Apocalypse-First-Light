package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson.Node;
import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson.RouteData;
import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson.Structure;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * C3.3b group 2 survey (docs/worldgen/federal_interchanges_c3_3b.md): every state road end that lies on an interstate's
 * centreline (the planner's join), with what a diamond interchange there needs to know: the interstate's station and
 * the distance to its ends, the crossing angle, the interstate's smallest radius and steepest grade within REACH, the
 * structures of both roads near, the height difference, other roads and towns near. Pure Java; arguments: cached road
 * network files.
 */
public final class InterchangeSurvey {
    private InterchangeSurvey() {}

    static final double TOUCH = 10, REACH = 700, NEAR = 800;

    public static void main(String[] args) throws Exception {
        for (String a : args) survey(a);
    }

    private static void survey(String file) throws Exception {
        String plan = Files.readString(Path.of(file));
        List<RouteData> routes = RoadNetworkJson.routes(plan);
        List<Node> nodes = RoadNetworkJson.nodes(plan);
        System.out.println("== " + Path.of(file).getFileName() + " (" + routes.size() + " planned routes)");
        for (RouteData r : routes) {
            if (!r.roadClass().equals("state")) continue;
            for (boolean atEnd : new boolean[]{false, true}) {
                int n = r.xs().length;
                double px = atEnd ? r.xs()[n - 1] : r.xs()[0], pz = atEnd ? r.zs()[n - 1] : r.zs()[0];
                for (RouteData q : routes) {
                    if (!q.roadClass().equals("interstate")) continue;
                    RoadCurve qc = new RoadCurve(q.xs(), q.zs());
                    double[] l = qc.local(px, pz, TOUCH, 0);
                    if (l == null) continue;
                    report(r, atEnd, q, qc, l[0], routes, nodes);
                }
            }
        }
    }

    private static void report(RouteData r, boolean atEnd, RouteData q, RoadCurve qc, double hs, List<RouteData> routes, List<Node> nodes) {
        RoadCurve rc = new RoadCurve(r.xs(), r.zs());
        double rLen = Math.min(rc.length, (r.profile().length - 1) * RoadProfile.DS);
        double qLen = Math.min(qc.length, (q.profile().length - 1) * RoadProfile.DS);
        double[] f = new double[5], g = new double[5];
        rc.frame(atEnd ? rLen : 0, f);
        qc.frame(hs, g);
        double ang = Math.toDegrees(Math.acos(Math.min(1, Math.abs(f[2] * g[2] + f[3] * g[3]))));
        // the interstate around the crossing: smallest radius and steepest grade within REACH
        double maxK = 0, maxG = 0;
        RoadProfile qp = RoadProfile.of(q.profile());
        for (double s = Math.max(0, hs - REACH); s <= Math.min(qLen, hs + REACH); s += 2) {
            qc.frame(s, g);
            maxK = Math.max(maxK, Math.abs(g[4]));
            maxG = Math.max(maxG, Math.abs(qp.grade(s)));
        }
        StringBuilder qs = new StringBuilder();
        for (Structure s : q.structures())
            if (s.s1() >= hs - NEAR && s.s0() <= hs + NEAR) qs.append(String.format(Locale.ROOT, " %s %.0f..%.0f", s.kind(), s.s0() - hs, s.s1() - hs));
        StringBuilder rs = new StringBuilder();
        for (Structure s : r.structures()) {
            double d = atEnd ? rLen - s.s1() : s.s0();
            if (d < 500) rs.append(String.format(Locale.ROOT, " %s %.0f m back", s.kind(), d));
        }
        // the state road's own grade over its last 300 m, and the heights at the crossing
        RoadProfile rp = RoadProfile.of(r.profile());
        double rg = 0;
        for (double t = 0; t <= 300; t += 2) rg = Math.max(rg, Math.abs(rp.grade(atEnd ? rLen - t : t)));
        double dy = rp.at(atEnd ? rLen : 0) - qp.at(hs);
        // other roads near the crossing (not these two), and towns
        StringBuilder others = new StringBuilder();
        double[] c = qc.point(hs, 0);
        for (RouteData o : routes) {
            if (o == r || o == q) continue;
            RoadCurve oc = new RoadCurve(o.xs(), o.zs());
            double[] l = oc.local(c[0], c[1], NEAR, 0);
            if (l != null) others.append(String.format(Locale.ROOT, " %s(%s) %.0f m", o.id(), o.roadClass(), Math.abs(l[1])));
        }
        String town = "-";
        double td = 2000;
        for (Node nd : nodes) {
            if (nd.id().contains("/join/") || nd.kind().equals("BRIDGE") || nd.kind().equals("TUNNEL") || nd.kind().equals("PASS") || nd.kind().equals("CROSSING")) continue;
            double d = Math.hypot(nd.x() - c[0], nd.z() - c[1]);
            if (d < td) { td = d; town = nd.kind() + " " + nd.id() + String.format(Locale.ROOT, " %.0f m", d); }
        }
        // which side of the interstate the state road comes from
        double[] back = rc.point(atEnd ? rLen - 60 : 60, 0), lb = qc.local(back[0], back[1], 200, 0);
        String side = lb == null ? "?" : lb[1] > 0 ? "+u" : "-u";
        System.out.printf(Locale.ROOT, "  %s %s on %s s %.0f (of %.0f; ends %.0f / %.0f m), %.0f deg from %s; I radius >= %s, grade <= %.1f %%; state grade %.1f %%, dy %+.1f m%n",
                r.id(), atEnd ? "end" : "start", q.id(), hs, qLen, hs, qLen - hs, ang, side,
                maxK < 1e-9 ? "inf" : String.format(Locale.ROOT, "%.0f", 1 / maxK), maxG * 100, rg * 100, dy);
        System.out.println("      I structures within " + (int) NEAR + ":" + (qs.length() == 0 ? " none" : qs));
        System.out.println("      state structures within 500 of the end:" + (rs.length() == 0 ? " none" : rs));
        System.out.println("      other roads within " + (int) NEAR + ":" + (others.length() == 0 ? " none" : others));
        System.out.println("      nearest town: " + town);
    }
}
