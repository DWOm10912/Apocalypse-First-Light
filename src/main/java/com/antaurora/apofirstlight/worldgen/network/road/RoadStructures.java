package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson.RouteData;
import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson.Structure;
import com.antaurora.apofirstlight.worldgen.network.route.RouteTerrain;
import com.antaurora.apofirstlight.worldgen.terrain.v2.RiverNetwork;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The structures of the built network that need the terrain (C3.3a group D, 2026-10-15; docs/worldgen/federal_highways_c3.md
 * 11.13): the box culverts and the bridges' wing wall reach. The server decides them once from the planned ground and the
 * river network and writes them into the built network JSON (RoadNetworkJson.RouteData culverts / wings), so the
 * client, which has no terrain, builds the same structures.
 * <ul>
 *   <li>a CULVERT structure becomes a box culvert (Culvert) laid along the stream: the stream is led straight through
 *   it (as a highway's culvert channelises it), the trough's axis from where the river crosses one tip line to where it
 *   crosses the other, its old meanders under the embankment filled (Culvert#fills); the clear width the widest river
 *   between the tips and a margin, the bed at the river's centre depth, the soffit a little over the water; the
 *   roof runs between the two portals, where the embankment over the corridor falls to two blocks over the slab; the
 *   headwalls stand to the embankment there plus a coping; the mouth walls run on to the tips, where the embankment meets
 *   the natural ground, their copings falling straight and never under the earth behind them. A culvert near another
 *   structure, near a route's end, too skewed, under the pavement's base, or under another road stays a slab culvert
 *   span (the C3.3a fallback);</li>
 *   <li>a bridge's wing walls run out, per span end and side, while the embankment's design stands over the natural
 *   ground and is dry: they stop where the embankment meets the ground (no 44 m walls in the open, E3).</li>
 * </ul>
 */
public final class RoadStructures {
    private RoadStructures() {}

    /** A culvert this close (m) to another structure, or to a route's end, stays a slab culvert span. */
    public static final double NEAR = 40;
    /** The smallest cosine between the stream's axis and the road's normal (60 degrees of skew). */
    public static final double MIN_COS = 0.5;
    /** Widest trough, m; a stream that needs more stays a slab culvert span. */
    public static final double MAX_WIDTH = 8;
    /** Mouth walls: at least TIP_MIN, at most TIP_MAX beyond the portal; the tip where the embankment rises TIP_RISE or less. */
    public static final double TIP_MIN = 1, TIP_MAX = 20, TIP_RISE = 0.3;
    /** The furthest a portal may lie from the road, m. */
    public static final double PORTAL_MAX = 80;
    /** The river's centre line followed this far both ways from the crossing, m; the axis meets it at both tips within AXIS_TOLERANCE. */
    public static final double LINE_REACH = 160, AXIS_TOLERANCE = 0.25;
    /** Sampling step of the designer, m. */
    private static final double STEP = 0.25;

    public record Result(String json, List<String> report) {}

    /** The built network JSON with its box culverts and wing reaches (the rest unchanged), and a report line per culvert. */
    @SuppressWarnings("unchecked")
    public static Result attach(String builtJson, RouteTerrain terrain) {
        Map<String, Object> root = (Map<String, Object>) new RoadNetworkJson.Reader(builtJson).value();
        String version = (String) root.get("version"), stage = (String) root.get("stage");
        List<RouteData> routes = new ArrayList<>(RoadNetworkJson.routes(builtJson));
        RoadWorks.Ground ground = RoadWorks.Ground.of(terrain.plan);
        List<String> report = new ArrayList<>();
        // the embankment over every stream: no culvert spans, no water rule
        List<RoadDesign> bare = new ArrayList<>();
        for (RouteData r : routes) bare.add(RoadNetworkJson.design(r, false));
        RoadWorks works0 = new RoadWorks(bare, RoadWorks.Water.NONE);
        int boxed = 0, slab = 0;
        for (int i = 0; i < routes.size(); i++) {
            RouteData r = routes.get(i);
            List<Culvert> cs = new ArrayList<>();
            for (Structure s : r.structures()) {
                if (!"CULVERT".equals(s.kind())) continue;
                String[] why = new String[1];
                Culvert c = culvert(r, bare.get(i), s, works0, terrain, ground, why);
                if (c != null) {
                    cs.add(c);
                    boxed++;
                    report.add(String.format(Locale.ROOT, "box culvert %s s=%.0f: W %.1f m, water %d (bed %d), soffit %.2f, roof u -%.1f..%.1f, heads %.2f / %.2f, tips %.1f..%.1f",
                            r.id(), c.s, c.width, c.level, c.bed, c.soffit, c.u0, c.u1, c.head0, c.head1, c.tip0, c.tip1));
                } else {
                    slab++;
                    report.add(String.format(Locale.ROOT, "culvert %s s=%.0f..%.0f stays a slab span: %s", r.id(), s.s0(), s.s1(), why[0]));
                }
            }
            routes.set(i, r.withStructures(cs, List.of()));
        }
        // the wing reach of every span, on the designs as built (box culverts out of the spans)
        for (int i = 0; i < routes.size(); i++) {
            RouteData r = routes.get(i);
            RoadDesign d = RoadNetworkJson.design(r);
            double a = r.trim() == null ? 0 : r.trim()[0];
            List<double[]> wings = new ArrayList<>();
            for (RoadDesign.Span sp : d.spans) {
                if (sp.s1() - sp.s0() < 2) continue;
                wings.add(new double[]{RoadNetworkJson.round2(sp.s0() + a), RoadNetworkJson.round2(sp.s1() + a),
                        reach(d, sp, sp.s0(), -1, terrain, ground), reach(d, sp, sp.s0(), 1, terrain, ground),
                        reach(d, sp, sp.s1(), -1, terrain, ground), reach(d, sp, sp.s1(), 1, terrain, ground)});
            }
            routes.set(i, r.withStructures(r.culverts(), wings));
        }
        report.add(0, String.format(Locale.ROOT, "structures: %d box culverts, %d slab culvert spans", boxed, slab));
        return new Result(RoadNetworkJson.write(version, stage, routes), report);
    }

    // ------------------------------------------------------------------ wing reach

    /**
     * How far out (|u|, on the walls' 0.5 m breaks) the wing walls of span end sOut run on one side: strip by strip from
     * the parapet's outer face while the embankment's design (wingTop less its coping) stands more than 0.1 m over the
     * planned ground over the wall's whole length (TAIL behind the end to 1 m into the span) and none of it is water.
     */
    static double reach(RoadDesign d, RoadDesign.Span sp, double sOut, int side, RouteTerrain terrain, RoadWorks.Ground ground) {
        CrossSection sec = d.section;
        double v0 = sec.edge + BridgeMesh.PARAPET_WIDTH, sgn = sOut == sp.s0() ? 1 : -1;
        double[] breaks = BridgeMesh.breaks(sec);
        double reach = v0;
        // the strips |u| = lo .. hi outward from the parapet's face (the breaks are symmetric)
        for (int k = 0; k + 1 < breaks.length; k++) {
            double lo = breaks[k], hi = breaks[k + 1];
            if (lo < v0 - 1e-9) continue;
            if (hi > BridgeMesh.WALL_REACH + 1e-9) break;
            if (!stands(d, sOut, sgn, side * (lo + hi) * 0.5, terrain, ground)) break;
            reach = hi;
        }
        return RoadNetworkJson.round2(reach);
    }

    /** Whether a wing wall strip at lateral u retains earth: the design over the ground and dry along the whole wall. */
    private static boolean stands(RoadDesign d, double sOut, double sgn, double u, RouteTerrain terrain, RoadWorks.Ground ground) {
        double top = BridgeMesh.wingTop(d, sOut, u) - BridgeMesh.COPING;
        if (Double.isNaN(top)) return false;
        for (double t = -BridgeMesh.TAIL; t <= 1 + 1e-9; t += 0.25) {
            double[] p = d.point(sOut + sgn * t, u);
            int w = terrain.water(p[0], p[1]);
            if (w == RouteTerrain.RIVER || w == RouteTerrain.LAKE || w == RouteTerrain.SEA) return false;
            if (top <= ground.at(p[0], p[1]) + 0.1) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ box culverts

    /** The box culvert for CULVERT structure st of route r (bare: its design without culvert spans), or null (why[0]). */
    static Culvert culvert(RouteData r, RoadDesign d, Structure st, RoadWorks works0, RouteTerrain terrain, RoadWorks.Ground ground, String[] why) {
        double trim = r.trim() == null ? 0 : r.trim()[0];
        double s0 = st.s0() - trim, s1 = st.s1() - trim;
        if (s0 < NEAR || s1 > d.length - NEAR) return fail(why, "near the route's end");
        for (Structure o : r.structures())
            if (o != st && !"DEEP_CUT".equals(o.kind()) && o.s0() < st.s1() + NEAR && o.s1() > st.s0() - NEAR)
                return fail(why, "within " + (int) NEAR + " m of a " + o.kind().toLowerCase(Locale.ROOT));
        RiverNetwork rv = terrain.plan.rivers;
        RiverNetwork.Column col = new RiverNetwork.Column();
        // the wet stretch on the centreline
        double w0 = Double.NaN, w1 = Double.NaN;
        for (double s = s0 - 20; s <= s1 + 20; s += STEP) {
            double[] p = d.point(s, 0);
            rv.column((int) Math.floor(p[0]), (int) Math.floor(p[1]), col);
            if (col.kind == RiverNetwork.Column.RIVER) { if (Double.isNaN(w0)) w0 = s; w1 = s; }
        }
        if (Double.isNaN(w0)) return fail(why, "no river under the road");
        double sc = (w0 + w1) * 0.5;
        double[] c = d.point(sc, 0);
        // the river line nearest the crossing, and the river's centre line within 30 m of it (1 m samples)
        int best = -1;
        double bd = Double.POSITIVE_INFINITY, bt = 0;
        for (int i = 0; i + 1 < rv.vertices(); i++) {
            if (rv.lineOfVertex(i) != rv.lineOfVertex(i + 1)) continue;
            double ax = rv.vertexX(i), az = rv.vertexZ(i), dx = rv.vertexX(i + 1) - ax, dz = rv.vertexZ(i + 1) - az;
            double l2 = dx * dx + dz * dz;
            double t = l2 > 1e-12 ? Math.max(0, Math.min(1, ((c[0] - ax) * dx + (c[1] - az) * dz) / l2)) : 0;
            double dist = Math.hypot(ax + dx * t - c[0], az + dz * t - c[1]);
            if (dist < bd) { bd = dist; best = i; bt = t; }
        }
        if (best < 0 || bd > 20) return fail(why, "no river line at the crossing");
        List<double[]> line = riverLine(rv, best, bt, LINE_REACH);
        if (line.size() < 8) return fail(why, "river line too short");
        // the first axis: the principal direction of the centre line within 30 m of the crossing
        double mx = 0, mz = 0;
        int n = 0;
        for (double[] p : line) if (Math.abs(p[3]) <= 30) { mx += p[0]; mz += p[1]; n++; }
        mx /= n;
        mz /= n;
        double sxx = 0, sxz = 0, szz = 0;
        for (double[] p : line)
            if (Math.abs(p[3]) <= 30) { double ex = p[0] - mx, ez = p[1] - mz; sxx += ex * ex; sxz += ex * ez; szz += ez * ez; }
        double ang = 0.5 * Math.atan2(2 * sxz, sxx - szz);
        double ax = Math.cos(ang), az = Math.sin(ang), px = mx, pz = mz;
        double width = rv.vertexWidth(best) + (rv.vertexWidth(best + 1) - rv.vertexWidth(best)) * bt;
        double w = Math.max(2.5, Math.ceil((width + 1) * 2) / 2);
        int level = Integer.MIN_VALUE, depth = 0, bed = 0, slabBlock = 0;
        double soffit = 0, sJson = 0;
        // the stream is led straight through the culvert (its channel under the embankment filled): the axis runs from
        // where the river crosses one tip line to where it crosses the other, refined until both lie on it
        Culvert cv;
        for (int pass = 0; ; pass++) {
            double[] nrm = BridgeMesh.lateral(d, sc);
            if (ax * nrm[0] + az * nrm[1] < 0) { ax = -ax; az = -az; }
            double cos = ax * nrm[0] + az * nrm[1];
            if (cos < MIN_COS) return fail(why, String.format(Locale.ROOT, "skew %.0f deg", Math.toDegrees(Math.acos(cos))));
            // the origin: where the axis crosses the centreline (the road's tangent line at sc, then onto the curve)
            double[] tan = BridgeMesh.tangent(d, sc);
            double det = ax * -tan[1] - az * -tan[0];
            double k = ((c[0] - px) * -tan[1] - (c[1] - pz) * -tan[0]) / det;
            double[] l = d.local(px + ax * k, pz + az * k, 10);
            if (l == null || l[0] < s0 - 20 || l[0] > s1 + 20) return fail(why, "axis misses the road");
            double sO = l[0];
            double[] o = d.point(sO, 0);
            nrm = BridgeMesh.lateral(d, sO);
            if (pass == 0) {
                // the water: level and depth at the deepest river column across the channel at the origin
                double bx = -az, bz = ax;
                for (double b = -width; b <= width; b += 0.5) {
                    rv.column((int) Math.floor(o[0] + bx * b), (int) Math.floor(o[1] + bz * b), col);
                    if (col.kind == RiverNetwork.Column.RIVER && col.depth > depth) { depth = col.depth; level = col.level; }
                }
                if (level == Integer.MIN_VALUE) return fail(why, "no river water at the axis");
                bed = level - depth + 1;
                double fill = d.datum(sO) - (level + 1);
                soffit = level + 1 + (fill >= 6 ? 1.5 : 0.5);
                slabBlock = (int) Math.ceil(soffit + Culvert.SLAB - 1e-9) - 1;
                sJson = RoadNetworkJson.round2(Math.max(st.s0(), Math.min(st.s1(), sO + trim)));
            }
            cv = shape(d, works0, ground, sJson, o, ax, az, nrm, w, bed, level, soffit, slabBlock, why);
            if (cv == null) return null;
            double[] e0 = crossing(line, cv, cv.tip0), e1 = crossing(line, cv, cv.tip1);
            if (e0 == null || e1 == null) return fail(why, "the river does not reach both ends of the culvert");
            // the clear width: the widest river between the tips and a margin
            double wide = width;
            for (double[] p : line) {
                double pa = cv.a(p[0], p[1]);
                if (pa >= cv.tip0 && pa <= cv.tip1) wide = Math.max(wide, p[2]);
            }
            double need = Math.max(2.5, Math.ceil((wide + 1) * 2) / 2);
            if (need > MAX_WIDTH) return fail(why, String.format(Locale.ROOT, "the stream needs a %.1f m trough", need));
            if (Math.abs(e0[1]) < AXIS_TOLERANCE && Math.abs(e1[1]) < AXIS_TOLERANCE && need <= w + 1e-9) break;
            if (pass == 5) return fail(why, "the culvert's axis does not settle");
            w = Math.max(w, need);
            double dx = e1[2] - e0[2], dz = e1[3] - e0[3], len = Math.hypot(dx, dz);
            ax = dx / len;
            az = dz / len;
            px = e0[2];
            pz = e0[3];
        }
        // the slab under the pavement's base: two blocks of base over it (asphalt, gravel), and no other road over it
        double[] bb = cv.bounds(1);
        for (int z = (int) Math.floor(bb[1]); z <= (int) Math.floor(bb[3]); z++)
            for (int x = (int) Math.floor(bb[0]); x <= (int) Math.floor(bb[2]); x++) {
                Culvert.Cell cell = cv.cell(x, z);
                if (cell == null) continue;
                RoadWorks.Hit h = works0.hit(x + 0.5, z + 0.5, RoadWorks.REACH);
                if (h != null && h.road() != d && RoadWorks.onFormation(h)) return fail(why, "under another road " + h.road().id);
                double[] pave = RoadWorks.pavedRange(d, x, z);
                if (pave == null) continue;
                if (cell.own()) return fail(why, "the headwall reaches the pavement");
                if ((int) Math.floor(pave[0] - 0.02) - 2 < slabBlock) return fail(why, "the slab reaches the pavement's base");
            }
        return cv;
    }

    /** The culvert of a given clear width: portals, headwalls, tips, copings (rounded as the JSON carries it). */
    private static Culvert shape(RoadDesign d, RoadWorks works0, RoadWorks.Ground ground, double sJson, double[] o, double ax, double az,
                                 double[] nrm, double w, int bed, int level, double soffit, int slabBlock, String[] why) {
        double half = w / 2, out = half + Culvert.WALL;
        // a frame with the final directions (the culvert's own a / b / u) to sample in
        Culvert f = new Culvert(sJson, o[0], o[1], ax, az, nrm[0], nrm[1], w, bed, level, soffit, 1, 1, 0, 0, -1, 1, new double[4]);
        double[] portal = new double[2];
        for (int e = 0; e < 2; e++) {
            int sg = e == 1 ? 1 : -1;
            portal[e] = Double.NaN;
            for (double u = 0; u <= PORTAL_MAX; u += STEP) {
                double min = Double.POSITIVE_INFINITY;
                for (double b = -out; b <= out + 1e-9; b += STEP) min = Math.min(min, earth(works0, ground, f, f.aAt(sg * u, b), b, false));
                if (min < slabBlock + 2) { portal[e] = u; break; }
            }
            if (Double.isNaN(portal[e])) return fail(why, "no portal within " + (int) PORTAL_MAX + " m");
            if (portal[e] < Culvert.WALL + 1) return fail(why, "the embankment is too low over the slab");
        }
        double[] head = new double[2];
        for (int e = 0; e < 2; e++) {
            int sg = e == 1 ? 1 : -1;
            double h = soffit + Culvert.SLAB + 0.5;
            for (double q = 0; q <= Culvert.WALL + 1e-9; q += STEP)
                for (double b = -out - 0.75; b <= out + 0.75 + 1e-9; b += STEP)
                    h = Math.max(h, earth(works0, ground, f, f.aAt(sg * (portal[e] - q), b), b, false) + Culvert.COPING);
            head[e] = h;
        }
        Culvert g = new Culvert(sJson, o[0], o[1], ax, az, nrm[0], nrm[1], w, bed, level, soffit, portal[0], portal[1], head[0], head[1], -1, 1, new double[4]);
        // tips: where the embankment's rise over the ground across the trough falls to TIP_RISE
        double[] tip = new double[2];
        for (int e = 0; e < 2; e++) {
            int sg = e == 1 ? 1 : -1;
            double outer = e == 1 ? Math.max(g.aPortal(1, -out), g.aPortal(1, out)) : Math.min(g.aPortal(0, -out), g.aPortal(0, out));
            tip[e] = outer + sg * TIP_MAX;
            for (double t = TIP_MIN; t <= TIP_MAX + 1e-9; t += STEP) {
                double a = outer + sg * t, rise = 0;
                for (double b = -out - 0.75; b <= out + 0.75 + 1e-9; b += STEP) {
                    double x = g.wx(a, b), z = g.wz(a, b), gr = ground.at(x, z), s = works0.surface(x, z, gr);
                    if (!Double.isNaN(s)) rise = Math.max(rise, s - gr);
                }
                if (rise <= TIP_RISE) { tip[e] = a; break; }
            }
        }
        Culvert h = new Culvert(sJson, o[0], o[1], ax, az, nrm[0], nrm[1], w, bed, level, soffit, portal[0], portal[1], head[0], head[1],
                RoadNetworkJson.round2(tip[0]), RoadNetworkJson.round2(tip[1]), new double[4]);
        // copings: the lowest straight fall from the head that stays COPING over the earth behind the wall, never rising
        double[] tops = new double[4];
        for (int e = 0; e < 2; e++)
            for (int side = -1; side <= 1; side += 2) {
                double ap = h.aStart(e, side), tp = h.tip(e), hd = h.head(e);
                double top = level + 1.3;
                int m = Math.max(1, (int) Math.ceil(Math.abs(tp - ap) / STEP));
                for (int j = 1; j <= m; j++) {
                    double t = (double) j / m, a = ap + (tp - ap) * t, e2 = Double.NEGATIVE_INFINITY;
                    for (double b = half; b <= out + 0.75 + 1e-9; b += STEP) e2 = Math.max(e2, earth(works0, ground, h, a, side * b, true));
                    top = Math.max(top, hd + (e2 + Culvert.COPING - hd) / t);
                }
                tops[e * 2 + (side > 0 ? 1 : 0)] = Math.min(hd, top);
            }
        return new Culvert(sJson, o[0], o[1], ax, az, nrm[0], nrm[1], w, bed, level, soffit, RoadNetworkJson.round2(portal[0]), RoadNetworkJson.round2(portal[1]),
                RoadNetworkJson.round2(head[0]), RoadNetworkJson.round2(head[1]), h.tip0, h.tip1, tops).rounded();
    }

    /**
     * The earth at frame point (a, b): the embankment's design (no water rule), or the natural ground; natural ground as
     * the noise draws it (its block top, ceil) when blockTop.
     */
    private static double earth(RoadWorks works0, RoadWorks.Ground ground, Culvert f, double a, double b, boolean blockTop) {
        double x = f.wx(a, b), z = f.wz(a, b), g = ground.at(x, z), s = works0.surface(x, z, g);
        return Double.isNaN(s) ? blockTop ? Math.ceil(g - 1e-9) : g : s;
    }

    /**
     * Where the river's centre line crosses the culvert's line a = at: {a, b, x, z} of the crossing nearest the axis, or
     * null when the line does not cross it.
     */
    private static double[] crossing(List<double[]> line, Culvert cv, double at) {
        double[] best = null;
        for (int i = 0; i + 1 < line.size(); i++) {
            double[] p = line.get(i), q = line.get(i + 1);
            double pa = cv.a(p[0], p[1]) - at, qa = cv.a(q[0], q[1]) - at;
            if (pa * qa > 0 || pa == qa) continue;
            double t = pa / (pa - qa), x = p[0] + (q[0] - p[0]) * t, z = p[1] + (q[1] - p[1]) * t, b = cv.b(x, z);
            if (best == null || Math.abs(b) < Math.abs(best[1])) best = new double[]{at, b, x, z};
        }
        return best;
    }

    /** The river's centre line from the point at t on segment i, both ways along its line: {x, z, width, arc}, 1 m steps. */
    private static List<double[]> riverLine(RiverNetwork rv, int i, double t, double reach) {
        int line = rv.lineOfVertex(i), first = rv.lineStart(line), last = rv.lineEnd(line) - 1;
        List<double[]> fwd = new ArrayList<>(), back = new ArrayList<>();
        walk(rv, i, t, first, last, 1, reach, fwd);
        walk(rv, i, t, first, last, -1, reach, back);
        List<double[]> out = new ArrayList<>(back.size() + fwd.size());
        for (int k = back.size() - 1; k >= 1; k--) out.add(back.get(k));
        out.addAll(fwd);
        return out;
    }

    private static void walk(RiverNetwork rv, int i, double t, int first, int last, int dir, double reach, List<double[]> out) {
        double px = rv.vertexX(i) + (rv.vertexX(i + 1) - rv.vertexX(i)) * t, pz = rv.vertexZ(i) + (rv.vertexZ(i + 1) - rv.vertexZ(i)) * t;
        double pw = rv.vertexWidth(i) + (rv.vertexWidth(i + 1) - rv.vertexWidth(i)) * t;
        out.add(new double[]{px, pz, pw, 0});
        double arc = 0, next = 1;
        int v = dir > 0 ? i + 1 : i;
        while (v >= first && v <= last && arc < reach) {
            double qx = rv.vertexX(v), qz = rv.vertexZ(v), qw = rv.vertexWidth(v), len = Math.hypot(qx - px, qz - pz);
            while (len > 1e-9 && arc + len >= next && next <= reach) {
                double f = (next - arc) / len;
                out.add(new double[]{px + (qx - px) * f, pz + (qz - pz) * f, pw + (qw - pw) * f, dir * next});
                next += 1;
            }
            arc += len;
            px = qx;
            pz = qz;
            pw = qw;
            v += dir;
        }
    }

    private static Culvert fail(String[] why, String reason) {
        why[0] = reason;
        return null;
    }
}
