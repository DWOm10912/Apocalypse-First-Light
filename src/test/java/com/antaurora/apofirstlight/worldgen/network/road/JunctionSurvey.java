package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson.Node;
import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson.RouteData;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * C3.3b survey (docs/worldgen/federal_junctions_c3_3b.md): where the built network's roads stop short of another road
 * (trimmed ends, split gaps), which road they meet, the classes, the crossing angle, the height difference between the
 * two designs at the meeting point and the plan node there. Pure Java; argument: a cached road network file.
 */
public final class JunctionSurvey {
    private JunctionSurvey() {}

    public static void main(String[] args) throws Exception {
        String plan = Files.readString(Path.of(args[0]));
        RoadNetworkBuild.Result built = RoadNetworkBuild.build(plan);
        List<Node> nodes = RoadNetworkJson.nodes(plan);
        List<RouteData> routes = built.routes();
        System.out.println("built roads " + routes.size());
        for (String line : built.report()) if (line.startsWith("trim") || line.startsWith("split") || line.startsWith("left out") || line.startsWith("town") || line.startsWith("junction")
                || line.startsWith("through") || line.startsWith("CONFLICT") || line.startsWith("corner") || line.startsWith("structure")) System.out.println("  " + line);
        System.out.println("conflicts " + built.conflicts());
        System.out.println("meetings:");
        for (RouteData r : routes) {
            RoadCurve full = new RoadCurve(r.xs(), r.zs());
            RoadProfile pr = RoadProfile.of(r.profile());
            double len = Math.min(full.length, (r.profile().length - 1) * RoadProfile.DS);
            double a = r.trim() == null ? 0 : r.trim()[0], b = r.trim() == null ? len : r.trim()[1];
            if (a > 0) meet(r, full, pr, 0, a, routes, nodes);
            if (b < len - 1) meet(r, full, pr, len, len - b, routes, nodes);
        }
    }

    /** One stopped end of r: its untrimmed end at station s (cut metres), the nearest other road there. */
    private static void meet(RouteData r, RoadCurve full, RoadProfile pr, double s, double cut, List<RouteData> routes, List<Node> nodes) {
        double[] f = new double[5];
        full.frame(s, f);
        RouteData best = null;
        double bd = Double.POSITIVE_INFINITY, bs = 0, bu = 0;
        // the nearest point of another road to r's cut stretch (from the untrimmed end inward by the cut)
        for (double t = 0; t <= cut + 1e-9; t += 2) {
            double st = s == 0 ? t : s - t;
            double[] p = full.point(st, 0);
            for (RouteData q : routes) {
                if (q.id().equals(r.id())) continue;
                RoadCurve qc = new RoadCurve(q.xs(), q.zs());
                double[] l = qc.local(p[0], p[1], 400, 0);
                if (l == null) continue;
                if (Math.abs(l[1]) < bd) { bd = Math.abs(l[1]); best = q; bs = l[0]; bu = st; }
            }
        }
        String node = "-";
        double nd = 600;
        for (Node n : nodes) {
            double d = Math.hypot(n.x() - f[0], n.z() - f[1]);
            if (d < nd && !n.kind().equals("BRIDGE") && !n.kind().equals("TUNNEL") && !n.kind().equals("BORDER_CROSSING")) { nd = d; node = n.kind() + " " + n.id() + String.format(Locale.ROOT, " (%.0f m)", d); }
        }
        if (best == null) {
            System.out.printf(Locale.ROOT, "  %-48s %s end cut %.0f m: no other road within 400 m; node %s%n", r.id(), s == 0 ? "start" : "end", cut, node);
            return;
        }
        RoadCurve qc = new RoadCurve(best.xs(), best.zs());
        double[] g = new double[5], h = new double[5];
        full.frame(bu, g);
        qc.frame(bs, h);
        double ang = Math.toDegrees(Math.acos(Math.min(1, Math.abs(g[2] * h[2] + g[3] * h[3]))));
        double qLen = Math.min(qc.length, (best.profile().length - 1) * RoadProfile.DS);
        double dy = pr.at(bu) - RoadProfile.of(best.profile()).at(bs);
        String where = bs < 50 || bs > qLen - 50 ? "its end" : String.format(Locale.ROOT, "its middle (s %.0f of %.0f)", bs, qLen);
        System.out.printf(Locale.ROOT, "  %-48s %-5s cut %4.0f m -> %-9s %-48s at %s; closest %.0f m, angle %.0f deg, dy %+.1f m; node %s%n",
                r.id(), s == 0 ? "start" : "end", cut, best.roadClass(), best.id(), where, bd, ang, dy, node);
    }
}
