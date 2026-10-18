package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson.Node;
import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson.RouteData;
import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson.Structure;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * C3.3b group 3 survey (interstate to interstate): on the planned network, the interstate legs of one corridor that
 * meet at a city (the kink, the height step, what a curve of the interstate radius through the corner would need: its
 * tangent length and how far it would pass the city, the structures within reach), and the interstate legs that end
 * on another interstate (the planner's joins: station, angle, side, height step, the host's radius and grade around,
 * the structures of both, the diamonds of group 2 near, the city). Pure Java; arguments: cached road network files.
 */
public final class SystemSurvey {
    private SystemSurvey() {}

    static final double[] RADII = {660, 900};
    static final double HOST_REACH = 1500, LEG_REACH = 2000;

    public static void main(String[] args) throws Exception {
        for (String a : args) survey(a);
    }

    private static double len(RouteData r) {
        return Math.min(new RoadCurve(r.xs(), r.zs()).length, (r.profile().length - 1) * RoadProfile.DS);
    }

    private static void survey(String file) throws Exception {
        String plan = Files.readString(Path.of(file));
        List<RouteData> routes = RoadNetworkJson.routes(plan);
        List<Node> nodes = RoadNetworkJson.nodes(plan);
        RoadNetworkBuild.Result built = RoadNetworkBuild.build(plan);
        // diamond centres (group 2) in the built network: the mean of each site's ramp ends on the interstate
        List<double[]> diamonds = new ArrayList<>();
        List<String> diamondIds = new ArrayList<>();
        for (RouteData r : built.routes()) {
            if (!r.id().startsWith("ramp:") || !r.id().endsWith(":exit+")) continue;
            String site = r.id().substring(0, r.id().lastIndexOf(':'));
            double sx = 0, sz = 0;
            int n = 0;
            for (RouteData q : built.routes())
                if (q.id().startsWith(site + ":")) { sx += q.xs()[0] + q.xs()[q.xs().length - 1]; sz += q.zs()[0] + q.zs()[q.zs().length - 1]; n += 2; }
            diamonds.add(new double[]{sx / n, sz / n});
            diamondIds.add(site.substring(5));
        }
        System.out.println("== " + Path.of(file).getFileName() + " (" + routes.size() + " planned routes, " + diamonds.size() + " diamonds built)");
        for (String line : built.report()) if (line.startsWith("town") && line.contains("interstate")) System.out.println("  built: " + line);

        System.out.println("  corners (one interstate corridor's legs meeting at a city):");
        for (RouteData a : routes) {
            if (!a.roadClass().equals("interstate")) continue;
            for (RouteData b : routes) {
                if (b == a || !b.roadClass().equals("interstate") || !b.corridor().equals(a.corridor()) || !a.to().equals(b.from())) continue;
                corner(a, b, nodes);
            }
        }
        System.out.println("  joins (an interstate leg ending on another interstate):");
        for (Node nd : nodes) {
            if (!nd.id().contains("/join/") || !nd.id().startsWith("interstate")) continue;
            String rid = nd.id().substring(0, nd.id().indexOf("/join/"));
            boolean atEnd = nd.id().endsWith("/end");
            RouteData r = null;
            for (RouteData q : routes) if (q.id().equals(rid)) r = q;
            if (r == null) continue;
            join(r, atEnd, nd, routes, nodes, diamonds, diamondIds);
        }
    }

    private static void corner(RouteData a, RouteData b, List<Node> nodes) {
        RoadCurve ac = new RoadCurve(a.xs(), a.zs()), bc = new RoadCurve(b.xs(), b.zs());
        double al = len(a), bl = len(b);
        double[] fa = new double[5], fb = new double[5];
        ac.frame(al, fa);
        bc.frame(0, fb);
        double kink = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, fa[2] * fb[2] + fa[3] * fb[3]))));
        double turn = Math.signum(fa[2] * fb[3] - fa[3] * fb[2]);
        double dy = RoadProfile.of(b.profile()).at(0) - RoadProfile.of(a.profile()).at(al);
        System.out.printf(Locale.ROOT, "    %s @ %s (%.0f, %.0f): %s -> %s, kink %.1f deg (%s), dy %+.1f m, legs %.0f / %.0f m%n", a.corridor(), a.to(),
                fa[0], fa[1], a.id(), b.id(), kink, turn > 0 ? "right" : "left", dy, al, bl);
        double half = Math.toRadians(kink) / 2;
        for (double r : RADII) {
            double t = r * Math.tan(half), e = r * (1 / Math.cos(half) - 1);
            System.out.printf(Locale.ROOT, "      R %.0f: tangent %.0f m, passes the city %.0f m off, curve %.0f m; structures within tangent + 300:%s%n", r, t, e,
                    r * Math.toRadians(kink), structs(a, al - t - 300, al, al, -1) + structs(b, 0, t + 300, 0, 1));
        }
        // the grades either side over the tangent of the larger radius
        double t = RADII[1] * Math.tan(half);
        RoadProfile pa = RoadProfile.of(a.profile()), pb = RoadProfile.of(b.profile());
        double ga = 0, gb = 0;
        for (double s = 0; s <= t; s += 2) { ga = Math.max(ga, Math.abs(pa.grade(Math.max(0, al - s)))); gb = Math.max(gb, Math.abs(pb.grade(Math.min(bl, s)))); }
        System.out.printf(Locale.ROOT, "      grades within %.0f m: in %.1f %%, out %.1f %%; heights at %.0f m back: %.1f / %.1f; nodes within 1500:%s%n", t, ga * 100, gb * 100, t,
                pa.at(Math.max(0, al - t)), pb.at(Math.min(bl, t)), near(nodes, fa[0], fa[1], 1500));
    }

    /** Structures of r with stations in [s0, s1], as distances from the reference station (dir -1: back from it). */
    private static String structs(RouteData r, double s0, double s1, double ref, int dir) {
        StringBuilder sb = new StringBuilder();
        for (Structure s : r.structures()) {
            if (s.s1() < s0 || s.s0() > s1) continue;
            double d0 = dir < 0 ? ref - s.s1() : s.s0() - ref, d1 = dir < 0 ? ref - s.s0() : s.s1() - ref;
            sb.append(String.format(Locale.ROOT, " %s %s %.0f..%.0f", r.id().substring(r.id().indexOf(':') + 1), s.kind(), d0, d1));
        }
        return sb.toString();
    }

    private static String near(List<Node> nodes, double x, double z, double reach) {
        StringBuilder sb = new StringBuilder();
        for (Node n : nodes) {
            if (n.kind().equals("BRIDGE") || n.kind().equals("CULVERT") || n.kind().equals("VIADUCT") || n.kind().equals("TUNNEL")) continue;
            double d = Math.hypot(n.x() - x, n.z() - z);
            if (d <= reach) sb.append(String.format(Locale.ROOT, " %s %s %.0f", n.kind(), n.id(), d));
        }
        return sb.length() == 0 ? " none" : sb.toString();
    }

    private static void join(RouteData r, boolean atEnd, Node nd, List<RouteData> routes, List<Node> nodes, List<double[]> diamonds, List<String> diamondIds) {
        RouteData h = null;
        double[] hl = null;
        for (RouteData q : routes) {
            if (q == r || !q.roadClass().equals("interstate") || q.corridor().equals(r.corridor())) continue;
            double[] l = new RoadCurve(q.xs(), q.zs()).local(nd.x(), nd.z(), 10, 0);
            if (l != null && (hl == null || Math.abs(l[1]) < Math.abs(hl[1]))) { h = q; hl = l; }
        }
        if (h == null) { System.out.println("    " + nd.id() + ": no interstate host within 10 m"); return; }
        RoadCurve rc = new RoadCurve(r.xs(), r.zs()), hc = new RoadCurve(h.xs(), h.zs());
        double rl = len(r), hlen = len(h), hs = hl[0];
        double[] f = new double[5], g = new double[5];
        rc.frame(atEnd ? rl : 0, f);
        hc.frame(hs, g);
        // the joining leg's direction toward the host
        double dx = atEnd ? f[2] : -f[2], dz = atEnd ? f[3] : -f[3];
        double ang = Math.toDegrees(Math.acos(Math.min(1, Math.abs(dx * g[2] + dz * g[3]))));
        // which way along the host the leg points (+: with the host's stations)
        double along = dx * g[2] + dz * g[3];
        double[] back = rc.point(atEnd ? rl - 80 : 80, 0), lb = hc.local(back[0], back[1], 400, 0);
        String side = lb == null ? "?" : lb[1] > 0 ? "+u" : "-u";
        RoadProfile rp = RoadProfile.of(r.profile()), hp = RoadProfile.of(h.profile());
        double dy = rp.at(atEnd ? rl : 0) - hp.at(hs);
        double maxK = 0, maxG = 0;
        for (double s = Math.max(0, hs - HOST_REACH); s <= Math.min(hlen, hs + HOST_REACH); s += 2) {
            hc.frame(s, g);
            maxK = Math.max(maxK, Math.abs(g[4]));
            maxG = Math.max(maxG, Math.abs(hp.grade(s)));
        }
        double rg = 0;
        for (double t = 0; t <= 800; t += 2) rg = Math.max(rg, Math.abs(rp.grade(atEnd ? rl - t : t)));
        System.out.printf(Locale.ROOT, "    %s %s on %s s %.0f (of %.0f), %.0f deg from %s, leg points %s the host's stations; dy %+.1f m%n",
                r.id(), atEnd ? "end" : "start", h.id(), hs, hlen, ang, side, along > 0 ? "with" : "against", dy);
        System.out.printf(Locale.ROOT, "      host within %.0f: radius >= %s, grade <= %.1f %%; leg grade over its last 800 m <= %.1f %%%n", HOST_REACH,
                maxK < 1e-9 ? "inf" : String.format(Locale.ROOT, "%.0f", 1 / maxK), maxG * 100, rg * 100);
        System.out.println("      host structures within " + (int) HOST_REACH + ":" + orNone(structs(h, hs - HOST_REACH, hs + HOST_REACH, hs, 1)));
        System.out.println("      leg structures within " + (int) LEG_REACH + " of its end:"
                + orNone(atEnd ? structs(r, rl - LEG_REACH, rl, rl, -1) : structs(r, 0, LEG_REACH, 0, 1)));
        StringBuilder ds = new StringBuilder();
        for (int i = 0; i < diamonds.size(); i++) {
            double d = Math.hypot(diamonds.get(i)[0] - nd.x(), diamonds.get(i)[1] - nd.z());
            if (d < 4000) ds.append(String.format(Locale.ROOT, " %s %.0f m", diamondIds.get(i), d));
        }
        System.out.println("      diamonds within 4000:" + orNone(ds.toString()));
        System.out.println("      nodes within 3000:" + near(nodes, nd.x(), nd.z(), 3000));
    }

    private static String orNone(String s) { return s.isEmpty() ? " none" : s; }
}
