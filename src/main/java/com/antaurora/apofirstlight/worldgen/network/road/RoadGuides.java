package com.antaurora.apofirstlight.worldgen.network.road;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The interchange guide signs (C3.3b group 2 package B, 2026-10-17; docs/worldgen/federal_interchanges_c3_3b.md 10):
 * green ground-mounted signs on two posts (fixture road_guide_sign, GuideSigns: the legend in the bone), placed from the
 * built designs like RoadSigns (pure geometry: every chunk pass places the same signs). Per interchange (its ramps share
 * the id up to their last ':'; the crossing: the middle of the crossroad's overpass, RoadDesign.Span#over):
 * <ul>
 *   <li>its exit number: the interstate corridor's milepost at the crossing (its designs chained west to east or south
 *   to north, a stretch run on another interstate counted along that one), rounded, at least 1; two in one mile A, B;</li>
 *   <li>per exit ramp (the interstate's traffic travelling k, the ramp on its right): the advance sign (EXIT tab, the
 *   crossroad's shield, its direction where it names one town, the towns, 1 MILE) a mile before the taper, else half a
 *   mile (shifted up to SHIFTS while the place is not clear; none where another interchange lies within FOOT + 100 m or
 *   between it and the exit); the exit direction sign (the same, an up-right arrow) DIR_BEFORE before the taper; the
 *   gore sign (EXIT n, the arrow) in the gore past the physical nose where the gap holds it;</li>
 *   <li>per entrance ramp, on the crossroad before its mouth for both directions of the crossroad's traffic: the
 *   interstate's shield, its direction, its control city (the corridor's end town that way), an arrow up-right (the ramp
 *   on the traffic's right) or up-left.</li>
 * </ul>
 * A system interchange (C3.3b group 3, docs/worldgen/federal_systems_c3_3b.md; its ramps' ids begin system:): its crossing
 * the middle of its decks on H, numbered with the diamonds on H's corridor; per exit ramp off H (the loop, the direct
 * ramp) the advance signs a mile AND half a mile before the taper (each where its place is clear), the exit direction
 * sign and the gore sign, their legend the ending interstate Q's shield, its direction away from H and its control
 * city; at Q's end the two split signs (GuideSigns SPLIT) SPLIT_BEFORE before it, one per lane of Q's carriageway
 * toward H: the left lane's in the median (arrow up-left), the right lane's on the right (up-right), each naming H's
 * shield and direction that lane leads onto (by H's corridor: E or N toward its east or north end), Q's own where Q's
 * corridor runs on along H that way (another of its system interchanges on H that way), and H's control city.
 * A crossroad direction names its town (RoadDesign#from at its start, #to at its end) where it runs on DESTINATION m or
 * more from the crossing. Signs stand on the right of their traffic (the left split sign: in the median), the panel's
 * near end SIDE_I (interstate) or SIDE_X (crossroad) beyond the paved edge; every post POST_CLEAR off any paved band and off every pad, never on a
 * bridge or in a tunnel's reach; each post's foot on the turf as drawn, else the ground's surface (the density's), the
 * design's roadside where no ground is given. Signs read English in every language (RoadNames).
 */
final class RoadGuides {
    private RoadGuides() {}

    static final double MILE = 1609.344;
    /** Designs of one corridor chain where their ends lie this close (else the stretch between is counted, CONCURRENT). */
    static final double CHAIN = 60;
    /** A crossroad direction names its town where it runs on this far from the crossing, m. */
    static final double DESTINATION = 1000;
    /** The panel's near end beyond the paved edge: on the interstate, on the crossroad, m. */
    static final double SIDE_I = 3, SIDE_X = 2;
    /** A post kept this far off every paved band, m. */
    static final double POST_CLEAR = 0.6;
    /** Posts' feet differing more than this: no sign there (too steep), m. */
    static final double FEET_SPREAD = 2.5;
    /** The advance sign's shifts from its mile (or half mile) while its place is not clear, m. */
    static final double[] SHIFTS = {0, 20, -20, 40, -40, 60, -60, 80, -80};
    /** The exit direction sign before the taper, and further upstream while its place is not clear, m. */
    static final double DIR_BEFORE = 25;
    static final double[] DIR_SHIFTS = {0, 15, 30, 45};
    /** The gore sign down the exit ramp from the physical nose, its steps; the room it needs beside its panel, m. */
    static final double GORE_FROM = 15, GORE_TO = 120, GORE_STEP = 5, GORE_ROOM = 1.2;
    /** The entrance guide before the ramp's mouth on the crossroad, its shortest, the steps, m. */
    static final double ENTRANCE = 60, ENTRANCE_MIN = 25, ENTRANCE_STEP = 5;
    /** Another interchange this close to an advance sign's place (or between it and its exit): no advance sign, m. */
    static final double OTHER = RoadInterchanges.FOOT + 100;
    /** A stretch run on another interstate between two designs of a corridor: both ends within this of it, m. */
    static final double CONCURRENT = 60;
    /** A system interchange's ending interstate: its open end this close to its ramps' open ends, m. */
    static final double Q_END = 25;
    /** The split signs before Q's end, further back while not clear (up to SPLIT_BACK more, in SPLIT_STEP), m. */
    static final double SPLIT_BEFORE = 60, SPLIT_BACK = 80, SPLIT_STEP = 10;
    /** The median split sign's near end off the inner paved edge, m. */
    static final double SIDE_M = 3;

    /** An interchange: its ramps, interstate, crossing station on it, crossroad and crossing station on that, exit number. */
    private static final class Site {
        final String key;
        final List<RoadDesign> ramps = new ArrayList<>();
        RoadDesign host, cross;
        double sc, xc, cs;
        String exit = "";
        /** A system interchange: its ending interstate q, its open end at the site q's end (qAtEnd) or start. */
        final boolean system;
        RoadDesign q;
        boolean qAtEnd;

        Site(String key) {
            this.key = key;
            system = key.startsWith("system:");
        }
    }

    /** An interstate corridor's designs west to east (south to north): each one's milepost offset and direction. */
    private static final class Corridor {
        final boolean eastWest;
        final List<RoadDesign> designs = new ArrayList<>();
        final Map<RoadDesign, double[]> at = new HashMap<>();
        String startTown, endTown;

        Corridor(boolean eastWest) { this.eastWest = eastWest; }

        /** Whether design d's stations grow toward the corridor's east (north) end. */
        boolean forward(RoadDesign d) { return at.get(d)[1] > 0; }

        /** The corridor's milepost (m) of design d's station s. */
        double station(RoadDesign d, double s) {
            double[] a = at.get(d);
            return a[0] + (a[1] > 0 ? s : d.length - s);
        }
    }

    static List<TunnelMesh.Placement> all(RoadWorks works, RoadWorks.Ground ground) {
        List<TunnelMesh.Placement> out = new ArrayList<>();
        Map<String, RoadDesign> byId = new HashMap<>();
        for (RoadDesign d : works.roads()) byId.put(d.id, d);
        Map<String, Corridor> corridors = corridors(works.roads());
        Map<String, Site> sites = new LinkedHashMap<>();
        for (RoadDesign d : works.roads()) {
            if (d.attach == null) continue;
            int c = d.id.lastIndexOf(':');
            if (c < 0) continue;
            Site st = sites.computeIfAbsent(d.id.substring(0, c), Site::new);
            st.ramps.add(d);
            if (st.system) {
                // H: the interstate its ramps leave and join (the direct ramps' other ends are on its left-turn ramps)
                RoadDesign h = works.attachedHost(d);
                if (h != null && h.section.divided) st.host = h;
                continue;
            }
            if (st.host == null) st.host = works.attachedHost(d);
            String x = d.attach.atEnd() ? d.hostStart : d.hostEnd;
            if (st.cross == null && x != null) st.cross = byId.get(x);
        }
        // the crossings (the crossroad's overpass nearest the ramps: its middle; the id's station is the plan's, before the
        // interstate's ends were trimmed) and the exit numbers, per corridor
        Map<Corridor, List<Site>> numbered = new HashMap<>();
        for (Site st : sites.values()) {
            if (st.system) {
                Corridor co = st.host == null ? null : corridors.get(RoadNames.corridor(st.host.id));
                if (co == null || !co.at.containsKey(st.host) || !systemSite(works, st)) { st.host = null; continue; }
                st.cs = co.station(st.host, st.sc);
                numbered.computeIfAbsent(co, k -> new ArrayList<>()).add(st);
                continue;
            }
            if (st.host == null || st.cross == null) continue;
            double[] r0 = st.ramps.get(0).point(0, 0);
            double best = Double.POSITIVE_INFINITY;
            for (RoadDesign.Span sp : st.cross.spans) {
                if (!sp.over()) continue;
                double m = (sp.s0() + sp.s1()) / 2;
                double[] p = st.cross.point(m, 0);
                double dist = Math.hypot(p[0] - r0[0], p[1] - r0[1]);
                if (dist < best) { best = dist; st.xc = m; }
            }
            double[] c0 = st.cross.point(st.xc, 0), l = st.host.local(c0[0], c0[1], CHAIN);
            Corridor co = corridors.get(RoadNames.corridor(st.host.id));
            if (best == Double.POSITIVE_INFINITY || l == null || co == null || !co.at.containsKey(st.host)) { st.host = null; continue; }
            st.sc = Math.max(0, Math.min(st.host.length, l[0]));
            st.cs = co.station(st.host, st.sc);
            numbered.computeIfAbsent(co, k -> new ArrayList<>()).add(st);
        }
        for (List<Site> ss : numbered.values()) {
            ss.sort(Comparator.comparingDouble(s -> s.cs));
            for (int i = 0; i < ss.size(); ) {
                long n = Math.max(1, Math.round(ss.get(i).cs / MILE));
                int j = i;
                while (j < ss.size() && Math.max(1, Math.round(ss.get(j).cs / MILE)) == n) j++;
                for (int k = i; k < j; k++) ss.get(k).exit = n + (j - i > 1 ? String.valueOf((char) ('A' + Math.min(25, k - i))) : "");
                i = j;
            }
        }
        for (Site st : sites.values()) {
            if (st.host == null) continue;
            List<Double> others = new ArrayList<>();
            for (Site o : sites.values()) if (o != st && o.host == st.host) others.add(o.sc);
            if (st.system) {
                // the exits off H (not the direct ramps' splits off the left-turn ramps), Q's split signs
                for (RoadDesign r : st.ramps)
                    if (!r.attach.atEnd() && works.attachedHost(r) == st.host)
                        exitSigns(works, ground, st, r, others, (t, e) -> systemExitLegend(st, corridors, t, e), true, out);
                splitSigns(works, ground, st, corridors, sites.values(), out);
                continue;
            }
            if (st.cross == null) continue;
            Corridor co = corridors.get(RoadNames.corridor(st.host.id));
            for (RoadDesign r : st.ramps) {
                if (r.attach.atEnd()) continue;
                exitSigns(works, ground, st, r, others, (t, e) -> exitLegend(st, t, e), false, out);
            }
            for (RoadDesign r : st.ramps) {
                if (!r.attach.atEnd()) continue;
                entranceSigns(works, ground, st, co, r, out);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- corridors

    /** The interstates' corridors (RoadNames#corridor of their ids): designs chained west to east, south to north. */
    private static Map<String, Corridor> corridors(List<RoadDesign> roads) {
        Map<String, List<RoadDesign>> by = new LinkedHashMap<>();
        for (RoadDesign d : roads) if (d.section.divided && d.attach == null) by.computeIfAbsent(RoadNames.corridor(d.id), k -> new ArrayList<>()).add(d);
        Map<String, Corridor> out = new HashMap<>();
        for (Map.Entry<String, List<RoadDesign>> e : by.entrySet()) {
            if (e.getKey() == null) continue;
            List<RoadDesign> ds = e.getValue();
            RoadNames.Route route = RoadNames.ROUTES.get(e.getKey());
            boolean ew;
            if (route != null) ew = route.eastWest();
            else {
                double dx = 0, dz = 0;
                for (RoadDesign d : ds) {
                    double[] a = d.point(0, 0), b = d.point(d.length, 0);
                    dx += Math.abs(b[0] - a[0]);
                    dz += Math.abs(b[1] - a[1]);
                }
                ew = dx >= dz;
            }
            Corridor co = new Corridor(ew);
            // the east (north) end's key: larger x, smaller z
            Map<RoadDesign, Double> key = new HashMap<>();
            for (RoadDesign d : ds) {
                double[] a = d.point(0, 0), b = d.point(d.length, 0);
                key.put(d, ew ? Math.min(a[0], b[0]) : -Math.max(a[1], b[1]));
            }
            ds.sort(Comparator.comparingDouble(key::get));
            double off = 0;
            double[] prevEnd = null;
            for (RoadDesign d : ds) {
                double[] a = d.point(0, 0), b = d.point(d.length, 0);
                boolean fwd = ew ? a[0] <= b[0] : a[1] >= b[1];
                double[] near = fwd ? a : b, far = fwd ? b : a;
                if (prevEnd != null) off += gap(roads, prevEnd, near);
                co.at.put(d, new double[]{off, fwd ? 1 : -1});
                co.designs.add(d);
                off += d.length;
                prevEnd = far;
            }
            RoadDesign first = ds.get(0), last = ds.get(ds.size() - 1);
            co.startTown = RoadNames.town(co.forward(first) ? first.from : first.to);
            co.endTown = RoadNames.town(co.forward(last) ? last.to : last.from);
            out.put(e.getKey(), co);
        }
        return out;
    }

    /** The corridor's length between two of its designs' ends: along another interstate both lie on, else straight. */
    private static double gap(List<RoadDesign> roads, double[] a, double[] b) {
        double straight = Math.hypot(b[0] - a[0], b[1] - a[1]);
        if (straight < CHAIN) return straight;
        for (RoadDesign q : roads) {
            if (!q.section.divided || q.attach != null) continue;
            double[] la = q.local(a[0], a[1], CONCURRENT), lb = q.local(b[0], b[1], CONCURRENT);
            if (la != null && lb != null) return Math.abs(lb[0] - la[0]);
        }
        return straight;
    }

    // ---------------------------------------------------------------- legends

    /** A direction word's letter for a horizontal direction (dx, dz) on an east-west or north-south route. */
    private static String cardinal(boolean eastWest, double dx, double dz) {
        return eastWest ? (dx >= 0 ? "E" : "W") : (dz >= 0 ? "S" : "N");
    }

    /** The exit signs' legend: the crossroad's shield, its towns (a direction where it names one). */
    private static GuideSigns.Spec exitLegend(Site st, GuideSigns.Type type, String extra) {
        RoadDesign x = st.cross;
        RoadNames.Route route = RoadNames.route(x.id);
        List<String> dests = new ArrayList<>();
        String card = "";
        String a = x.from == null ? null : RoadNames.town(x.from), b = x.to == null ? null : RoadNames.town(x.to);
        boolean toStart = a != null && st.xc >= DESTINATION, toEnd = b != null && x.length - st.xc >= DESTINATION && !b.equals(toStart ? a : null);
        if (toStart) dests.add(a);
        if (toEnd) dests.add(b);
        if (dests.size() == 1) {
            double[] t = BridgeMesh.tangent(x, st.xc);
            int k = toStart ? -1 : 1;
            boolean ew = route != null ? route.eastWest() : Math.abs(t[0]) >= Math.abs(t[1]);
            card = cardinal(ew, k * t[0], k * t[1]);
        }
        return new GuideSigns.Spec(type, st.exit, route == null ? "" : route.tile(), card, dests, extra, new double[]{0, 0});
    }

    // ---------------------------------------------------------------- exit signs

    /**
     * An exit ramp's signs on its interstate: the advance sign a mile before the taper, else half a mile (both: each, a
     * system interchange's), the exit direction sign, the gore sign; legend: the sign's legend by type and extra.
     */
    private static void exitSigns(RoadWorks works, RoadWorks.Ground ground, Site st, RoadDesign ramp, List<Double> others,
                                  java.util.function.BiFunction<GuideSigns.Type, String, GuideSigns.Spec> legend, boolean both, List<TunnelMesh.Placement> out) {
        RoadDesign h = st.host;
        int k = ramp.attach.side();
        double[] r0 = ramp.point(0, 0), lt = h.local(r0[0], r0[1], h.section.edge + 10);
        if (lt == null) return;
        double taper = lt[0];
        // the advance sign: a mile before the taper, else (or also: both) half a mile
        for (double dist : new double[]{MILE, MILE / 2}) {
            boolean placed = false;
            for (double shift : SHIFTS) {
                double s = taper - k * (dist + shift);
                if (s < 0 || s > h.length) continue;
                boolean blocked = false;
                for (double o : others)
                    if (Math.abs(o - s) < OTHER || (o - s) * (o - taper) < 0) { blocked = true; break; }
                if (blocked) continue;
                TunnelMesh.Placement p = beside(works, ground, h, s, k, SIDE_I, legend.apply(GuideSigns.Type.ADVANCE, dist == MILE ? "1" : "h"));
                if (p != null) {
                    out.add(p);
                    placed = true;
                    break;
                }
            }
            if (placed && !both) break;
        }
        for (double shift : DIR_SHIFTS) {
            TunnelMesh.Placement p = beside(works, ground, h, taper - k * (DIR_BEFORE + shift), k, SIDE_I, legend.apply(GuideSigns.Type.DIRECTION, "ur"));
            if (p != null) {
                out.add(p);
                break;
            }
        }
        TunnelMesh.Placement g = gore(works, ground, st, ramp);
        if (g != null) out.add(g);
    }

    /**
     * The gore sign: down the exit ramp from its physical nose, where the gap between the interstate's paved edge and the
     * ramp's left paved edge holds the panel with GORE_ROOM; centred in the gap, facing up the ramp.
     */
    private static TunnelMesh.Placement gore(RoadWorks works, RoadWorks.Ground ground, Site st, RoadDesign ramp) {
        RoadDesign h = st.host;
        int k = ramp.attach.side();
        GuideSigns.Spec spec = new GuideSigns.Spec(GuideSigns.Type.GORE, st.exit, "", "", List.of(), "ur", new double[]{0, 0});
        GuideSigns.Layout l = GuideSigns.layout(spec);
        double w = l.panel()[2] - l.panel()[0], e = h.section.edge;
        for (double q = ramp.attach.nose() + GORE_FROM; q <= Math.min(ramp.length, ramp.attach.nose() + GORE_TO) + 1e-9; q += GORE_STEP) {
            if (ramp.span(q) != null) continue;
            double[] p = ramp.point(q, -ramp.section.edge), lh = h.local(p[0], p[1], e + 40);
            boolean debug = Boolean.getBoolean("afl.debugGuides");
            if (lh == null || lh[0] < 0 || lh[0] > h.length || h.span(lh[0]) != null || h.tunnel(lh[0], RoadSigns.STRUCTURE_CLEAR) != null) {
                if (debug) System.out.printf(java.util.Locale.ROOT, "    gore %s %s q=%.0f: off H or structure (%s)%n", st.exit, ramp.id, q, lh == null ? "null" : String.format(java.util.Locale.ROOT, "%.1f %.1f", lh[0], lh[1]));
                continue;
            }
            double gap = Math.abs(lh[1]) - e;
            if (gap < w + GORE_ROOM) {
                if (debug) System.out.printf(java.util.Locale.ROOT, "    gore %s %s q=%.0f: gap %.2f%n", st.exit, ramp.id, q, gap);
                continue;
            }
            double v = e + gap / 2;
            double[] c = h.point(lh[0], k * v), t = BridgeMesh.tangent(ramp, q);
            double design = h.formationY(lh[0], k * e) + h.section.roadside(gap / 2, -1e9);
            TunnelMesh.Placement pl = stand(works, ground, c, -t[0], -t[1], spec, l, design, design);
            if (pl != null) return pl;
            if (debug) System.out.printf(java.util.Locale.ROOT, "    gore %s %s q=%.0f: not clear at %.1f %.1f%n", st.exit, ramp.id, q, c[0], c[1]);
        }
        return null;
    }

    // ---------------------------------------------------------------- system interchanges

    /**
     * A system interchange's crossing station on H (the mean of its decks' middles) and its ending interstate (the divided
     * road whose open end lies within Q_END of one of its roads' open ends); false where either is missing.
     */
    private static boolean systemSite(RoadWorks works, Site st) {
        double sum = 0;
        int n = 0;
        List<double[]> ends = new ArrayList<>();
        for (RoadDesign d : works.roads()) {
            if (!d.id.startsWith(st.key + ":")) continue;
            for (RoadDesign.Span sp : d.spans) {
                if (!sp.over()) continue;
                double[] m = d.point((sp.s0() + sp.s1()) / 2, 0), l = st.host.local(m[0], m[1], CHAIN);
                if (l != null) { sum += l[0]; n++; }
            }
            if (d.openStart) ends.add(d.point(0, 0));
            if (d.openEnd) ends.add(d.point(d.length, 0));
        }
        if (n == 0) return false;
        st.sc = Math.max(0, Math.min(st.host.length, sum / n));
        String hc = RoadNames.corridor(st.host.id);
        for (RoadDesign q : works.roads()) {
            if (!q.section.divided || q.attach != null || q == st.host || hc != null && hc.equals(RoadNames.corridor(q.id))) continue;
            for (int e = 0; e < 2; e++) {
                if (!(e == 0 ? q.openStart : q.openEnd)) continue;
                double[] p = q.point(e == 0 ? 0 : q.length, 0);
                for (double[] o : ends)
                    if (Math.hypot(o[0] - p[0], o[1] - p[1]) < Q_END) {
                        st.q = q;
                        st.qAtEnd = e == 1;
                        return true;
                    }
            }
        }
        return false;
    }

    /** A corridor's cardinal toward its east or north end (toEnd), else its west or south end. */
    private static String toward(Corridor co, boolean toEnd) { return toEnd ? (co.eastWest ? "E" : "N") : (co.eastWest ? "W" : "S"); }

    /** A system interchange's exit legend: Q's shield, its direction away from H and its control city that way. */
    private static GuideSigns.Spec systemExitLegend(Site st, Map<String, Corridor> corridors, GuideSigns.Type type, String extra) {
        RoadNames.Route route = RoadNames.route(st.q.id);
        Corridor co = corridors.get(RoadNames.corridor(st.q.id));
        String card = "", town = null;
        if (co != null && co.at.containsKey(st.q)) {
            // away from H: up Q's stations where it starts at the site
            boolean toEnd = co.forward(st.q) == !st.qAtEnd;
            card = toward(co, toEnd);
            town = toEnd ? co.endTown : co.startTown;
        }
        return new GuideSigns.Spec(type, st.exit, route == null ? "" : route.tile(), card, town == null ? List.of() : List.of(town), extra, new double[]{0, 0});
    }

    /**
     * Q's corridor's cardinal along H from site st for H's traffic heading toward H's corridor end (toEnd) or start, where
     * another of Q's corridor's system interchanges on H's corridor lies that way (Q's corridor runs on along H to it);
     * null where none.
     */
    private static String concurrent(Site st, java.util.Collection<Site> sites, Corridor qc, boolean toEnd) {
        if (qc == null || !qc.at.containsKey(st.q)) return null;
        String hc = RoadNames.corridor(st.host.id);
        Site best = null;
        for (Site o : sites) {
            if (o == st || !o.system || o.host == null || o.q == null || o.q == st.q || !qc.at.containsKey(o.q)) continue;
            if (hc == null || !hc.equals(RoadNames.corridor(o.host.id)) || (o.cs - st.cs) * (toEnd ? 1 : -1) <= 0) continue;
            if (best == null || Math.abs(o.cs - st.cs) < Math.abs(best.cs - st.cs)) best = o;
        }
        if (best == null) return null;
        // toward the other's interstate along Q's corridor: toward its end where that one comes later in it
        return toward(qc, qc.designs.indexOf(best.q) > qc.designs.indexOf(st.q));
    }

    /** The split signs at Q's end (the class comment): the left lane's in the median, the right lane's on the right. */
    private static void splitSigns(RoadWorks works, RoadWorks.Ground ground, Site st, Map<String, Corridor> corridors, java.util.Collection<Site> sites,
                                   List<TunnelMesh.Placement> out) {
        RoadDesign q = st.q, h = st.host;
        Corridor hc = corridors.get(RoadNames.corridor(h.id)), qc = corridors.get(RoadNames.corridor(q.id));
        if (hc == null || !hc.at.containsKey(h)) return;
        RoadNames.Route hr = RoadNames.route(h.id), qr = RoadNames.route(q.id);
        // Q's traffic toward H travels k; the H traffic each lane joins: the side of H its ramp lies on
        int k = st.qAtEnd ? 1 : -1;
        double end = st.qAtEnd ? q.length : 0;
        int left = 0, right = 0;
        for (RoadDesign r : st.ramps) {
            if (!r.attach.atEnd() || works.attachedHost(r) != h) continue;
            String n = r.id.substring(r.id.lastIndexOf(':') + 1);
            if (n.startsWith("left-in")) left = r.attach.side();
            else if (n.startsWith("right-in")) right = r.attach.side();
        }
        if (left == 0 || right == 0) return;
        for (int lane = 0; lane < 2; lane++) {
            int travel = lane == 0 ? left : right;
            boolean toEnd = hc.forward(h) == travel > 0;
            String shields = hr == null ? "" : hr.tile(), cards = toward(hc, toEnd);
            String qCard = qr == null ? null : concurrent(st, sites, qc, toEnd);
            if (qCard != null) {
                shields += "+" + qr.tile();
                cards += "+" + qCard;
            }
            String town = toEnd ? hc.endTown : hc.startTown;
            GuideSigns.Spec spec = new GuideSigns.Spec(GuideSigns.Type.SPLIT, "", shields, cards, town == null ? List.of() : List.of(town),
                    lane == 0 ? "ul" : "ur", new double[]{0, 0});
            for (double back = SPLIT_BEFORE; back <= SPLIT_BEFORE + SPLIT_BACK + 1e-9; back += SPLIT_STEP) {
                double s = end - k * back;
                TunnelMesh.Placement p = lane == 0 ? median(works, ground, q, s, k, spec) : beside(works, ground, q, s, k, SIDE_I, spec);
                if (p != null) {
                    out.add(p);
                    break;
                }
            }
        }
    }

    /**
     * A guide sign in divided road d's median, on the left of its traffic travelling k at station s: its panel's near end
     * SIDE_M off the inner paved edge, facing that traffic; null where the median cannot hold it, on or near a bridge or a
     * tunnel, or where a post is not clear.
     */
    private static TunnelMesh.Placement median(RoadWorks works, RoadWorks.Ground ground, RoadDesign d, double s, int k, GuideSigns.Spec spec) {
        if (!d.section.divided || s < 0 || s > d.length) return null;
        if (d.span(s) != null || d.tunnel(s, RoadSigns.STRUCTURE_CLEAR) != null) return null;
        for (double q : new double[]{-RoadSigns.STRUCTURE_CLEAR, RoadSigns.STRUCTURE_CLEAR})
            if (d.span(Math.max(0, Math.min(d.length, s + q))) != null) return null;
        GuideSigns.Layout l = GuideSigns.layout(spec);
        double w = l.panel()[2] - l.panel()[0], m = d.section.halfMedian, v = m - SIDE_M - w / 2;
        if (v - w / 2 < -m + SIDE_M) return null;
        double[] c = d.point(s, k * v), t = BridgeMesh.tangent(d, s);
        // the posts' design heights: the median's turf at their offsets (the viewer's right toward the traffic's lanes)
        double d0 = d.formationY(s, k * (v + l.posts()[0])), d1 = d.formationY(s, k * (v + l.posts()[1]));
        return stand(works, ground, c, -k * t[0], -k * t[1], spec, l, d0, d1);
    }

    // ---------------------------------------------------------------- entrance guides

    /** The entrance guides of an entrance ramp: on its crossroad, before its mouth, for both directions of the crossroad's traffic. */
    private static void entranceSigns(RoadWorks works, RoadWorks.Ground ground, Site st, Corridor co, RoadDesign ramp, List<TunnelMesh.Placement> out) {
        Junction j = null;
        for (Junction q : works.junctions()) if (q.minor == ramp && !q.atEnd) j = q;
        if (j == null || co == null) return;
        RoadDesign h = st.host;
        int travel = ramp.attach.side();
        double[] t = BridgeMesh.tangent(h, st.sc);
        boolean towardEnd = co.forward(h) == travel > 0;
        String town = towardEnd ? co.endTown : co.startTown;
        RoadNames.Route route = RoadNames.route(h.id);
        for (int k : new int[]{-1, 1}) {
            GuideSigns.Spec spec = new GuideSigns.Spec(GuideSigns.Type.ENTRANCE, "", route == null ? "" : route.tile(),
                    cardinal(co.eastWest, travel * t[0], travel * t[1]), town == null ? List.of() : List.of(town), j.side == k ? "ur" : "ul", new double[]{0, 0});
            for (double dist = ENTRANCE; dist >= ENTRANCE_MIN - 1e-9; dist -= ENTRANCE_STEP) {
                TunnelMesh.Placement p = beside(works, ground, j.host, j.sMouth - k * dist, k, SIDE_X, spec);
                if (p != null) {
                    out.add(p);
                    break;
                }
            }
        }
    }

    // ---------------------------------------------------------------- placing

    /**
     * A guide sign on the right of road d's traffic travelling k at station s, its panel's near end side m beyond the paved
     * edge, facing that traffic; null off the road, on or near a bridge or a tunnel, or where a post is not clear.
     */
    static TunnelMesh.Placement beside(RoadWorks works, RoadWorks.Ground ground, RoadDesign d, double s, int k, double side, GuideSigns.Spec spec) {
        if (s < 0 || s > d.length) return null;
        if (d.span(s) != null || d.tunnel(s, RoadSigns.STRUCTURE_CLEAR) != null) return null;
        for (double q : new double[]{-RoadSigns.STRUCTURE_CLEAR, RoadSigns.STRUCTURE_CLEAR})
            if (d.span(Math.max(0, Math.min(d.length, s + q))) != null) return null;
        GuideSigns.Layout l = GuideSigns.layout(spec);
        double w = l.panel()[2] - l.panel()[0], e = d.section.edge;
        double v = e + side + w / 2;
        double[] c = d.point(s, k * v), t = BridgeMesh.tangent(d, s);
        double base = d.formationY(s, k * e);
        // the posts' design heights: the roadside at their offsets (post 0 the nearer: on the traffic's left)
        double d0 = base + d.section.roadside(v + l.posts()[0] - e, -1e9), d1 = base + d.section.roadside(v + l.posts()[1] - e, -1e9);
        return stand(works, ground, c, -k * t[0], -k * t[1], spec, l, d0, d1);
    }

    /**
     * The sign with its panel's centre over (c), facing (fx, fz): its posts clear, their feet found (design heights where no
     * ground), the origin on the left post's foot; null where a post or the panel's end is not clear or the feet differ
     * too much.
     */
    private static TunnelMesh.Placement stand(RoadWorks works, RoadWorks.Ground ground, double[] c, double fx, double fz, GuideSigns.Spec spec,
                                              GuideSigns.Layout l, double design0, double design1) {
        double n = Math.hypot(fx, fz);
        double ezx = fx / n, ezz = fz / n, exx = ezz, exz = -ezx;
        double[] ys = new double[2];
        for (int i = 0; i < 2; i++) {
            double px = l.posts()[i], x = c[0] + exx * px, z = c[1] + exz * px;
            if (!open(works, x, z)) return null;
            ys[i] = foot(works, ground, x, z, i == 0 ? design0 : design1);
        }
        for (double px : new double[]{l.panel()[0], l.panel()[2]})
            if (!open(works, c[0] + exx * px, c[1] + exz * px)) return null;
        if (Math.abs(ys[1] - ys[0]) > FEET_SPREAD) return null;
        GuideSigns.Spec placed = new GuideSigns.Spec(spec.type(), spec.exit(), spec.shield(), spec.cardinal(), spec.dests(), spec.extra(),
                new double[]{0, ys[1] - ys[0]});
        return RoadSigns.part("guide_sign", placed.bone(), c[0], ys[0], c[1], fx, fz);
    }

    /** Whether (x, z) is POST_CLEAR off every road's paved band (its stations; a divided road's median turf is clear) and off every pad. */
    private static boolean open(RoadWorks works, double x, double z) {
        if (works.pad(x, z) != null) return false;
        RoadDesign[] near = works.roadsNear(x, z);
        if (near == null) return true;
        for (RoadDesign q : near) {
            double[] l = q.local(x, z, q.section.edge + 3);
            if (l == null || l[0] < -POST_CLEAR || l[0] > q.length + POST_CLEAR || Math.abs(l[1]) > q.section.edge + POST_CLEAR) continue;
            if (q.section.divided && Math.abs(l[1]) <= q.section.halfMedian - POST_CLEAR) continue;
            return false;
        }
        return true;
    }

    /** A post's foot: on the turf as drawn, else the ground's surface with the works (the density's), else (no ground) the design's. */
    private static double foot(RoadWorks works, RoadWorks.Ground ground, double x, double z, double design) {
        if (ground == null) return design;
        double t = works.turfTop(x, z, ground);
        return Double.isNaN(t) ? works.densityHeight((int) Math.floor(x), (int) Math.floor(z), ground) : t;
    }
}
