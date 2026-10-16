package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.network.road.RoadMesh.Material;
import com.antaurora.apofirstlight.worldgen.network.road.RoadMesh.Triangle;
import com.antaurora.apofirstlight.worldgen.network.road.RoadMesh.Vertex;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * The drawn tunnels of a chunk (Tunnel V1; docs/worldgen/federal_tunnels_v1.md): what the client draws over the invisible
 * collision of TunnelWorks, and where its equipment parts stand (the Vextris parts under meshes/road/tunnel/, placed by
 * the client: client/road/TunnelParts). Pure Java, world triangles cut to the chunk like RoadMesh and BridgeMesh.
 * <ul>
 *   <li>lining: the wall panels (TUNNEL_PANEL, 2.4 x 1.2 m panels, three rows over the walkways) and the dark arch
 *   (TUNNEL_ARCH, 2.4 x 2 m tiles) in FACET-degree facets with flat normals, on to the portal rings' faces; the inner wall left open for
 *   the cross passages;</li>
 *   <li>walkways and barriers (CONCRETE), on LEAD beyond the portals with sloped ends; handrails (STEEL) on the walls;
 *   the slot drains along the barrier toes (DRAIN, drawn with the markings);</li>
 *   <li>portals: the headwall's face round the rings, its coping, the rings, the retaining walls and their caps over the
 *   wall columns behind them (#caps); cross passages lined with concrete.</li>
 * </ul>
 * No light anywhere: the world has no power (user, 2026-10-15); luminaires, signals and signs are unlit models.
 */
public final class TunnelMesh {
    private TunnelMesh() {}

    /**
     * Texture tiles: panels 2.4 x 1.2 m, the arch 2.4 m along by 2 m round, concrete 1 m; the drain strip 2.4 m along;
     * steel uniform (stretched over each 2.4 m piece). The lining, rails and drains share the 2.4 m lattice (SPAN).
     */
    static final double PANEL_U = 2.4, PANEL_V = 1.2, ARCH_U = 2.4, ARCH_V = 2.0, TILE = 1.0;
    /** The lattice the lining, the drains and the handrail segments share, m. */
    public static final double SPAN = 2.4;
    /**
     * Slot drain width; handrail height over the walkway, its distance from the wall, its half section; its brackets'
     * distance from a segment's ends, m.
     */
    public static final double DRAIN_W = 0.13, RAIL_H = 1.0, RAIL_X = 0.10, RAIL = 0.05, BRACKET_IN = 0.3;
    /** Panel rows' arc length over the walkway (TunnelSection.PANEL_TOP - WALK_H). */
    static final double PANELS = TunnelSection.PANEL_TOP - TunnelSection.WALK_H;

    // ------------------------------------------------------------------ chunk

    /** The tunnels' triangles in chunk (cx, cz), chunk-relative; empty when no tunnel reaches it. */
    public static List<Triangle> chunk(RoadWorks works, int cx, int cz) {
        List<Triangle> world = new ArrayList<>();
        boolean near = false;
        for (RoadDesign d : works.roads()) {
            if (d.tunnels.isEmpty()) continue;
            double[] r = BridgeMesh.stations(d, cx, cz);
            if (r == null) continue;
            for (RoadDesign.Tunnel tn : d.tunnels) {
                if (tn.s0() - TunnelWorks.APPROACH > r[1] || tn.s1() + TunnelWorks.APPROACH < r[0]) continue;
                tunnel(world, d, tn, r[0], r[1]);
                near = true;
            }
        }
        if (near) caps(world, works, cx, cz);
        List<Triangle> out = new ArrayList<>();
        for (Triangle t : world) RoadMesh.clipInto(out, t, cx * 16, cz * 16);
        return out;
    }

    private static void tunnel(List<Triangle> out, RoadDesign d, RoadDesign.Tunnel tn, double lo, double hi) {
        TunnelSection ts = TunnelSection.of(d.section);
        double[] passages = TunnelWorks.passages(ts, tn);
        for (int side : ts.sides()) {
            double a = Math.max(tn.s0() - TunnelWorks.RING_OUT, lo), b = Math.min(tn.s1() + TunnelWorks.RING_OUT, hi);
            if (a < b) lining(out, d, ts, side, tn, grid(a, b, passages, SPAN), passages);
            a = Math.max(tn.s0() - TunnelWorks.LEAD, lo);
            b = Math.min(tn.s1() + TunnelWorks.LEAD, hi);
            if (a < b) for (boolean left : new boolean[]{true, false}) walk(out, d, ts, side, tn, left, grid(a, b, passages, 2, TILE));
            a = Math.max(tn.s0(), lo);
            b = Math.min(tn.s1(), hi);
            if (a < b) {
                // the handrails are fixtures (TunnelMesh#handrails, V1.1)
                for (boolean left : new boolean[]{true, false}) drain(out, d, ts, side, left, grid(a, b, new double[0], SPAN));
            }
        }
        if (ts.twin) for (double p : passages) if (p + TunnelWorks.PASSAGE_W >= lo && p - TunnelWorks.PASSAGE_W <= hi) passage(out, d, ts, p);
        for (int sgn : new int[]{1, -1}) {
            double sp = sgn > 0 ? tn.s0() : tn.s1();
            if (sp + TunnelWorks.APPROACH < lo || sp - TunnelWorks.APPROACH > hi) continue;
            portal(out, d, ts, sp, sgn, lo, hi);
        }
    }

    /**
     * Stations a .. b on the global lattices (multiples of each step), the passages' opening edges, a and b; a and b are
     * either a tunnel's own ends or lie outside the chunk (BridgeMesh.stations' margin), so neighbouring chunks share
     * every triangle that reaches them.
     */
    static double[] grid(double a, double b, double[] passages, double... steps) {
        TreeSet<Double> set = new TreeSet<>();
        set.add(a);
        set.add(b);
        for (double st : steps) for (double s = Math.ceil(a / st - 1e-9) * st; s < b; s += st) set.add(s);
        for (double p : passages)
            for (double e : new double[]{p - TunnelWorks.PASSAGE_W / 2, p + TunnelWorks.PASSAGE_W / 2}) if (e > a && e < b) set.add(e);
        // drop slivers left by two lattices meeting
        List<Double> out = new ArrayList<>();
        for (double s : set) if (out.isEmpty() || s - out.get(out.size() - 1) > 1e-4) out.add(s);
        if (b - out.get(out.size() - 1) > 1e-9) out.add(b);
        return out.stream().mapToDouble(Double::doubleValue).toArray();
    }

    // ------------------------------------------------------------------ lining

    /**
     * The nominal inner profile of a bore (walkways at WALK_H over the base): the wall from the left walkway, the arch,
     * the wall down to the right walkway, split at the panel rows and the arch's tiles. Entry k: the polyline vertex i it
     * starts from, the fraction t to vertex i + 1, its arc length a, and the material and tile v of the piece from it.
     */
    record Profile(int[] i, double[] t, double[] a, Material[] mat, double[] v0, double[] v1, double total) {}

    private static final java.util.Map<TunnelSection, Profile> PROFILES = new java.util.concurrent.ConcurrentHashMap<>();

    /** The bore's inner polyline at a station (bore x, absolute y): left walkway top, the arch, right walkway top. */
    static double[][] innerPoints(TunnelSection ts, double base, double walkL, double walkR) {
        double[][] arch = ts.archPoints(0);
        double[][] p = new double[arch.length + 2][];
        p[0] = new double[]{0, walkL};
        for (int k = 0; k < arch.length; k++) p[k + 1] = new double[]{arch[k][0], base + arch[k][1]};
        p[arch.length + 1] = new double[]{ts.width, walkR};
        return p;
    }

    static Profile profile(TunnelSection ts) {
        return PROFILES.computeIfAbsent(ts, k -> {
            double[][] p = innerPoints(ts, 0, TunnelSection.WALK_H, TunnelSection.WALK_H);
            double[] cum = new double[p.length];
            for (int i = 1; i < p.length; i++) cum[i] = cum[i - 1] + Math.hypot(p[i][0] - p[i - 1][0], p[i][1] - p[i - 1][1]);
            double total = cum[p.length - 1];
            TreeSet<Double> cuts = new TreeSet<>();
            for (double c : cum) cuts.add(c);
            for (double a = PANEL_V; a < PANELS - 1e-9; a += PANEL_V) { cuts.add(a); cuts.add(total - a); }
            cuts.add(PANELS);
            cuts.add(total - PANELS);
            // the cross passages' openings in the inner wall end at PASSAGE_H over the walkway
            cuts.add(TunnelWorks.PASSAGE_H);
            for (double a = PANELS + ARCH_V; a < total - PANELS - 1e-9; a += ARCH_V) cuts.add(a);
            List<Double> cs = new ArrayList<>();
            for (double c : cuts) if (cs.isEmpty() || c - cs.get(cs.size() - 1) > 1e-6) cs.add(c);
            int n = cs.size();
            int[] ii = new int[n];
            double[] tt = new double[n], aa = new double[n], v0 = new double[n], v1 = new double[n];
            Material[] mm = new Material[n];
            int seg = 0;
            for (int q = 0; q < n; q++) {
                double a = cs.get(q);
                while (seg + 2 < p.length && cum[seg + 1] <= a + 1e-9 && q < n - 1) seg++;
                double len = cum[seg + 1] - cum[seg];
                ii[q] = seg;
                tt[q] = len < 1e-12 ? 0 : Math.max(0, Math.min(1, (a - cum[seg]) / len));
                aa[q] = a;
            }
            for (int q = 0; q + 1 < n; q++) {
                double a = aa[q], b = aa[q + 1], m = (a + b) / 2;
                if (m < PANELS) { mm[q] = Material.TUNNEL_PANEL; double c = Math.floor(a / PANEL_V + 1e-9) * PANEL_V; v0[q] = (a - c) / PANEL_V; v1[q] = (b - c) / PANEL_V; }
                else if (m > total - PANELS) {
                    mm[q] = Material.TUNNEL_PANEL;
                    double ra = total - a, rb = total - b, c = Math.floor(rb / PANEL_V + 1e-9) * PANEL_V;
                    v0[q] = (ra - c) / PANEL_V;
                    v1[q] = (rb - c) / PANEL_V;
                } else { mm[q] = Material.TUNNEL_ARCH; double c = PANELS + Math.floor((a - PANELS) / ARCH_V + 1e-9) * ARCH_V; v0[q] = (a - c) / ARCH_V; v1[q] = (b - c) / ARCH_V; }
            }
            return new Profile(ii, tt, aa, mm, v0, v1, total);
        });
    }

    /** World vertex of bore point (x, y) at station s. */
    static Vertex at(RoadDesign d, TunnelSection ts, int side, double s, double x, double y, double tu, double tv) {
        double[] w = d.point(s, ts.u(side, x));
        return new Vertex(w[0], y, w[1], tu, tv);
    }

    /** The profile's vertices at station s (bore x, y): the nominal splits on the station's own polyline. */
    static double[][] profileAt(RoadDesign d, TunnelSection ts, Profile pf, int side, double s) {
        double pl = TunnelWorks.toe(d, ts, side, s, true), pr = TunnelWorks.toe(d, ts, side, s, false);
        double[][] p = innerPoints(ts, Math.max(pl, pr), pl + TunnelSection.WALK_H, pr + TunnelSection.WALK_H);
        double[][] out = new double[pf.i().length][];
        for (int q = 0; q < out.length; q++) {
            double[] a = p[pf.i()[q]], b = p[Math.min(p.length - 1, pf.i()[q] + 1)];
            double t = pf.t()[q];
            out[q] = new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t};
        }
        return out;
    }

    private static void lining(List<Triangle> out, RoadDesign d, TunnelSection ts, int side, RoadDesign.Tunnel tn, double[] st, double[] passages) {
        Profile pf = profile(ts);
        double[][] prev = profileAt(d, ts, pf, side, st[0]);
        for (int k = 0; k + 1 < st.length; k++) {
            double s0 = st[k], s1 = st[k + 1], sm = (s0 + s1) / 2;
            double[][] next = profileAt(d, ts, pf, side, s1);
            boolean hole = false;
            for (double p : passages) hole |= Math.abs(sm - p) < TunnelWorks.PASSAGE_W / 2;
            double base = TunnelWorks.base(d, ts, side, sm);
            Vertex ref = at(d, ts, side, sm, ts.xa, base + ts.hc, 0, 0);
            for (int q = 0; q + 1 < prev.length; q++) {
                if (hole && pf.a()[q + 1] <= TunnelWorks.PASSAGE_H + 1e-6) continue;
                Material m = pf.mat()[q];
                double tile = m == Material.TUNNEL_PANEL ? PANEL_U : ARCH_U, cell = Math.floor(s0 / tile + 1e-9) * tile;
                double u0 = (s0 - cell) / tile, u1 = (s1 - cell) / tile, v0 = pf.v0()[q], v1 = pf.v1()[q];
                quad(out, m, at(d, ts, side, s0, prev[q][0], prev[q][1], u0, v0), at(d, ts, side, s0, prev[q + 1][0], prev[q + 1][1], u0, v1),
                        at(d, ts, side, s1, next[q + 1][0], next[q + 1][1], u1, v1), at(d, ts, side, s1, next[q][0], next[q][1], u1, v0), ref);
            }
            prev = next;
        }
    }

    // ------------------------------------------------------------------ walkways, rails, drains

    /**
     * One walkway with its barrier: toe, the F-shape's two slopes, the walkway top to the wall; on a lead-in (outside the
     * portals) scaled down by TunnelWorks.leadScale and closed by its side face over the ground.
     */
    private static void walk(List<Triangle> out, RoadDesign d, TunnelSection ts, int side, RoadDesign.Tunnel tn, boolean left, double[] st) {
        for (int k = 0; k + 1 < st.length; k++) {
            double s0 = st[k], s1 = st[k + 1], sm = (s0 + s1) / 2;
            double[][] a = walkPoints(d, ts, side, tn, left, s0), b = walkPoints(d, ts, side, tn, left, s1);
            double base = TunnelWorks.base(d, ts, side, sm);
            Vertex ref = at(d, ts, side, sm, ts.xa, base + ts.hc, 0, 0);
            double cell = Math.floor(s0 + 1e-9), u0 = s0 - cell, u1 = s1 - cell, arc = 0;
            for (int q = 0; q + 1 < a.length; q++) {
                double len = Math.hypot(a[q + 1][0] - a[q][0], a[q + 1][1] - a[q][1]);
                double v0 = arc - Math.floor(arc + 1e-9), v1 = Math.min(1, v0 + len);
                arc += len;
                quad(out, Material.CONCRETE, at(d, ts, side, s0, a[q][0], a[q][1], u0, v0), at(d, ts, side, s0, a[q + 1][0], a[q + 1][1], u0, v1),
                        at(d, ts, side, s1, b[q + 1][0], b[q + 1][1], u1, v1), at(d, ts, side, s1, b[q][0], b[q][1], u1, v0), ref);
            }
            // outside the portals the walkway's back stands free: its face down into the ground
            if (!tn.contains(sm)) {
                double x = left ? 0 : ts.width, n = left ? -1 : 1;
                double ga = ground(d, ts.u(side, x), s0) - 0.3, gb = ground(d, ts.u(side, x), s1) - 0.3;
                double[] pa = d.point(s0, ts.u(side, x)), pb = d.point(s1, ts.u(side, x)), lat = BridgeMesh.lateral(d, sm);
                double sx = (ts.twin && side < 0 ? -n : n) * lat[0], sz = (ts.twin && side < 0 ? -n : n) * lat[1];
                wall(out, Material.CONCRETE, pa[0], pa[1], pb[0], pb[1], ga, gb, a[a.length - 1][1], b[b.length - 1][1], s0, s1, sx, 0, sz);
            }
        }
    }

    /** The finished ground at lateral u and station s beside a lead-in (the pavement, else the formation). */
    private static double ground(RoadDesign d, double u, double s) {
        CrossSection sec = d.section;
        double v = Math.abs(u), sc = TunnelWorks.clamp(d, s);
        if (sec.paved(v)) return d.pavementY(sc, u);
        return d.formationY(sc, Math.copySign(Math.min(v, sec.edge), u));
    }

    /** A walkway's profile at station s from its toe to the wall (bore x, y), scaled on a lead-in. */
    private static double[][] walkPoints(RoadDesign d, TunnelSection ts, int side, RoadDesign.Tunnel tn, boolean left, double s) {
        double[] pt = TunnelWorks.portal(tn, s);
        double scale = pt[1] >= 0 ? 1 : TunnelWorks.leadScale(-pt[1]);
        double p = TunnelWorks.toe(d, ts, side, s, left), h = TunnelSection.WALK_H * scale;
        double xt = left ? ts.toeL : ts.toeR, xe = left ? ts.walkL : ts.width - ts.walkR, xm = left ? xe + 0.08 : xe - 0.08, xw = left ? 0 : ts.width;
        return new double[][]{{xt, p}, {xm, p + 0.25 * scale}, {xe, p + h}, {xw, p + h}};
    }

    /** The slot drain along a barrier's toe: a DRAIN_W strip on the pavement (drawn with the markings). */
    private static void drain(List<Triangle> out, RoadDesign d, TunnelSection ts, int side, boolean left, double[] st) {
        double xa = left ? ts.toeL : ts.toeR, xb = left ? ts.toeL + DRAIN_W : ts.toeR - DRAIN_W;
        for (int k = 0; k + 1 < st.length; k++) {
            double s0 = st[k], s1 = st[k + 1];
            double cell = Math.floor(s0 / SPAN + 1e-9) * SPAN, u0 = (s0 - cell) / SPAN, u1 = (s1 - cell) / SPAN;
            Vertex a = flat(d, ts, side, s0, xa, u0, 0), b = flat(d, ts, side, s0, xb, u0, 1), c = flat(d, ts, side, s1, xb, u1, 1), e = flat(d, ts, side, s1, xa, u1, 0);
            Vertex up = new Vertex(a.x(), a.y() + 10, a.z(), 0, 0);
            quad(out, Material.DRAIN, a, b, c, e, up);
        }
    }

    private static Vertex flat(RoadDesign d, TunnelSection ts, int side, double s, double x, double tu, double tv) {
        double u = ts.u(side, x);
        double[] w = d.point(s, u);
        return new Vertex(w[0], d.pavementY(TunnelWorks.clamp(d, s), u), w[1], tu, tv);
    }

    // ------------------------------------------------------------------ cross passages

    /** A cross passage's lining: floor, walls and roof from one bore's egress walkway to the other's. */
    private static void passage(List<Triangle> out, RoadDesign d, TunnelSection ts, double p) {
        double h = TunnelWorks.PASSAGE_H, w = TunnelWorks.PASSAGE_W / 2, ui = ts.inner;
        double fa = TunnelWorks.toe(d, ts, -1, p, true) + TunnelSection.WALK_H, fb = TunnelWorks.toe(d, ts, 1, p, true) + TunnelSection.WALK_H;
        double[] t = BridgeMesh.tangent(d, p);
        double[][] lat = {d.point(p - w, -ui), d.point(p - w, ui), d.point(p + w, -ui), d.point(p + w, ui)};
        // side walls (facing into the passage)
        wall(out, Material.CONCRETE, lat[0][0], lat[0][1], lat[1][0], lat[1][1], fa, fb, fa + h, fb + h, 0, 2 * ui, t[0], 0, t[1]);
        wall(out, Material.CONCRETE, lat[2][0], lat[2][1], lat[3][0], lat[3][1], fa, fb, fa + h, fb + h, 0, 2 * ui, -t[0], 0, -t[1]);
        // floor and roof, tiled across
        for (double u = -ui; u < ui - 1e-9; u += TILE) {
            double ub = Math.min(ui, u + TILE), ya = fa + (fb - fa) * (u + ui) / (2 * ui), yb = fa + (fb - fa) * (ub + ui) / (2 * ui);
            double[] a = d.point(p - w, u), b = d.point(p - w, ub), c = d.point(p + w, ub), e = d.point(p + w, u);
            double tv = 2 * w;
            for (int roof = 0; roof <= 1; roof++) {
                double dy = roof * h;
                Vertex va = new Vertex(a[0], ya + dy, a[1], 0, 0), vb = new Vertex(b[0], yb + dy, b[1], ub - u, 0),
                        vc = new Vertex(c[0], yb + dy, c[1], ub - u, Math.min(1, tv)), ve = new Vertex(e[0], ya + dy, e[1], 0, Math.min(1, tv));
                Vertex ref = new Vertex(va.x(), va.y() + (roof == 0 ? 10 : -10), va.z(), 0, 0);
                quad(out, Material.CONCRETE, va, vb, vc, ve, ref);
            }
        }
    }

    // ------------------------------------------------------------------ portals

    /** A portal (sp the portal station, sgn +1 when the mountain lies toward +s): headwall, coping, rings, retaining walls. */
    private static void portal(List<Triangle> out, RoadDesign d, TunnelSection ts, double sp, int sgn, double lo, double hi) {
        double hw = TunnelWorks.headHalf(ts), top = d.datum(sp) + TunnelWorks.headTop(ts), yP = d.datum(sp);
        double[] t = BridgeMesh.tangent(d, sp);
        double nx = -sgn * t[0], nz = -sgn * t[1];
        // headwall face round the rings: lateral strips, each from the ground (or a ring's outline) up to the coping
        List<double[]> breaks = new ArrayList<>();
        breaks.add(new double[]{-hw, Double.NaN});
        for (int side : ts.sides()) {
            double[][] ring = ringOuter(d, ts, side, sp);
            for (double[] q : ring) breaks.add(new double[]{ts.u(side, q[0]), q[1]});
        }
        breaks.add(new double[]{hw, Double.NaN});
        breaks.sort((p, q) -> Double.compare(p[0], q[0]));
        for (int k = 0; k + 1 < breaks.size(); k++) {
            double ua = breaks.get(k)[0], ub = breaks.get(k + 1)[0];
            if (ub - ua < 1e-6) continue;
            double ba = breaks.get(k)[1], bb = breaks.get(k + 1)[1];
            // a strip between two ring outline points stands on the ring; elsewhere on the ground in front of the wall
            boolean onRing = !Double.isNaN(ba) && !Double.isNaN(bb) && sameRing(ts, ua, ub);
            if (!onRing) { ba = ground(d, ua, sp - sgn * 0.5) - 0.5; bb = ground(d, ub, sp - sgn * 0.5) - 0.5; }
            double[] pa = d.point(sp, ua), pb = d.point(sp, ub);
            wall(out, Material.CONCRETE, pa[0], pa[1], pb[0], pb[1], ba, bb, top - TunnelWorks.COPING_H, top - TunnelWorks.COPING_H, ua, ub, nx, 0, nz);
        }
        // coping: front, top, the overhang's underside, the ends
        double cf = sp - sgn * TunnelWorks.COPING_OUT, cb = sp + sgn * (TunnelWorks.HEAD + TunnelWorks.COPING_OUT), cw = hw + TunnelWorks.COPING_OUT;
        double[] fa = d.point(cf, -cw), fb = d.point(cf, cw);
        wall(out, Material.CONCRETE, fa[0], fa[1], fb[0], fb[1], top - TunnelWorks.COPING_H, top - TunnelWorks.COPING_H, top, top, -cw, cw, nx, 0, nz);
        horizontal(out, d, cf, cb, -cw, cw, top, 1);
        horizontal(out, d, cf, sp, -cw, cw, top - TunnelWorks.COPING_H, -1);
        for (int e : new int[]{-1, 1}) {
            double[] a = d.point(cf, e * cw), b = d.point(cb, e * cw), lat = BridgeMesh.lateral(d, sp);
            wall(out, Material.CONCRETE, a[0], a[1], b[0], b[1], top - TunnelWorks.COPING_H, top - TunnelWorks.COPING_H, top, top, cf, cb, e * lat[0], 0, e * lat[1]);
        }
        // rings: front annulus, outer side
        double sf = sp - sgn * TunnelWorks.RING_OUT;
        for (int side : ts.sides()) {
            double b = TunnelWorks.base(d, ts, side, sp);
            double[][] in = innerPoints(ts, b, TunnelWorks.toe(d, ts, side, sp, true) + TunnelSection.WALK_H, TunnelWorks.toe(d, ts, side, sp, false) + TunnelSection.WALK_H);
            double[][] ou = ringOuter(d, ts, side, sp);
            for (int q = 0; q + 1 < in.length; q++) {
                List<Vertex> poly = List.of(at(d, ts, side, sf, in[q][0], in[q][1], 0, 0), at(d, ts, side, sf, in[q + 1][0], in[q + 1][1], 0, 0),
                        at(d, ts, side, sf, ou[q + 1][0], ou[q + 1][1], 0, 0), at(d, ts, side, sf, ou[q][0], ou[q][1], 0, 0));
                planar(out, Material.CONCRETE, poly, nx, 0, nz, d, sf);
            }
            Vertex ref = at(d, ts, side, sp, ts.xa, b + ts.hc, 0, 0);
            for (int q = 0; q + 1 < ou.length; q++) {
                Vertex a0 = at(d, ts, side, sf, ou[q][0], ou[q][1], 0, 0), a1 = at(d, ts, side, sf, ou[q + 1][0], ou[q + 1][1], 0, 0),
                        b1 = at(d, ts, side, sp, ou[q + 1][0], ou[q + 1][1], 0, 0), b0 = at(d, ts, side, sp, ou[q][0], ou[q][1], 0, 0);
                double len = Math.min(1, Math.hypot(ou[q + 1][0] - ou[q][0], ou[q + 1][1] - ou[q][1]));
                // facing away from the bore (the reference point is inside it)
                quadAway(out, Material.CONCRETE, new Vertex(a0.x(), a0.y(), a0.z(), 0, 0), new Vertex(a1.x(), a1.y(), a1.z(), 0, len),
                        new Vertex(b1.x(), b1.y(), b1.z(), TunnelWorks.RING_OUT, len), new Vertex(b0.x(), b0.y(), b0.z(), TunnelWorks.RING_OUT, 0), ref);
            }
        }
        // retaining walls: road face, top, end
        CrossSection sec = d.section;
        double[] st = grid(Math.max(lo, sgn > 0 ? sp - TunnelWorks.WALL_LEN : sp), Math.min(hi, sgn > 0 ? sp : sp + TunnelWorks.WALL_LEN), new double[0], 2, TILE);
        if (st.length < 2 || st[0] >= st[st.length - 1]) return;
        for (int e : new int[]{-1, 1}) {
            for (int k = 0; k + 1 < st.length; k++) {
                double s0 = st[k], s1 = st[k + 1], sm = (s0 + s1) / 2;
                double t0 = sgn * (sp - s0), t1 = sgn * (sp - s1);
                double y0 = d.datum(s0) + TunnelWorks.wallHeight(ts, t0), y1 = d.datum(s1) + TunnelWorks.wallHeight(ts, t1);
                double g0 = d.formationY(s0, e * sec.edge) - 0.5, g1 = d.formationY(s1, e * sec.edge) - 0.5;
                double[] a = d.point(s0, e * hw), b = d.point(s1, e * hw), lat = BridgeMesh.lateral(d, sm);
                wall(out, Material.CONCRETE, a[0], a[1], b[0], b[1], g0, g1, y0, y1, s0, s1, -e * lat[0], 0, -e * lat[1]);
                double[] a2 = d.point(s0, e * (hw + TunnelWorks.WALL_T)), b2 = d.point(s1, e * (hw + TunnelWorks.WALL_T));
                Vertex va = new Vertex(a[0], y0, a[1], 0, 0), vb = new Vertex(a2[0], y0, a2[1], TunnelWorks.WALL_T, 0),
                        vc = new Vertex(b2[0], y1, b2[1], TunnelWorks.WALL_T, s1 - s0), ve = new Vertex(b[0], y1, b[1], 0, s1 - s0);
                quad(out, Material.CONCRETE, va, vb, vc, ve, new Vertex(va.x(), va.y() + 10, va.z(), 0, 0));
            }
            double se = sp - sgn * TunnelWorks.WALL_LEN;
            if (se >= lo && se <= hi) {
                double ye = d.datum(se) + TunnelWorks.WALL_END, ge = d.formationY(se, e * sec.edge) - 0.5;
                double[] a = d.point(se, e * hw), b = d.point(se, e * (hw + TunnelWorks.WALL_T));
                wall(out, Material.CONCRETE, a[0], a[1], b[0], b[1], ge, ge, ye, ye, hw, hw + TunnelWorks.WALL_T, -sgn * t[0], 0, -sgn * t[1]);
            }
        }
    }

    /**
     * The retaining walls' caps in chunk (cx, cz), world coordinates: over each wall column of an approach
     * (TunnelWorks#wallColumn) the part of its square behind the wall's back face, and past the wall's end between its
     * faces, level with the wall's top (TunnelWorks#wallTop), concrete. The column's road_structure holds the backfill's
     * collision under it, and the bench's turf beyond meets it at the same height: no slit opens behind a wall down to the
     * ground the column keeps (2026-10-16).
     */
    private static void caps(List<Triangle> out, RoadWorks works, int cx, int cz) {
        for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
            int x = cx * 16 + lx, z = cz * 16 + lz;
            RoadWorks.Hit h = works.hit(x + 0.5, z + 0.5, RoadWorks.REACH);
            RoadDesign.Tunnel tn = TunnelWorks.near(h);
            if (tn == null || TunnelWorks.portal(tn, h.s())[1] >= 0) continue;
            RoadDesign d = h.road();
            if (!TunnelWorks.wallColumn(d, tn, x, z)) continue;
            TunnelSection ts = TunnelSection.of(d.section);
            double hw = TunnelWorks.headHalf(ts);
            // the square's corners: {dx, dz, t, |u|}
            List<double[]> sq = new ArrayList<>();
            for (int[] c : new int[][]{{0, 0}, {1, 0}, {1, 1}, {0, 1}}) {
                double[] l = d.local(x + c[0], z + c[1], RoadWorks.REACH);
                if (l == null) break;
                sq.add(new double[]{c[0], c[1], -TunnelWorks.portal(tn, l[0])[1], Math.abs(l[1])});
            }
            if (sq.size() < 4) continue;
            List<double[]> behind = cut(cut(sq, 3, hw + TunnelWorks.WALL_T, true), 2, 0, true);
            List<double[]> past = cut(cut(cut(sq, 3, hw, true), 3, hw + TunnelWorks.WALL_T, false), 2, TunnelWorks.WALL_LEN, true);
            for (List<double[]> poly : List.of(behind, past)) {
                if (poly.size() < 3) continue;
                List<Vertex> vs = new ArrayList<>();
                for (double[] q : poly) {
                    double px = x + q[0], pz = z + q[1];
                    double[] l = d.local(px, pz, RoadWorks.REACH);
                    double y = l == null ? Double.NaN : TunnelWorks.wallTop(d, ts, l[0], -TunnelWorks.portal(tn, l[0])[1]);
                    if (Double.isNaN(y)) { vs = null; break; }
                    vs.add(new Vertex(px, y, pz, q[0], q[1]));
                }
                if (vs == null) continue;
                Vertex v0 = vs.get(0), up = new Vertex(v0.x(), v0.y() + 10, v0.z(), 0, 0);
                for (int i = 1; i + 1 < vs.size(); i++) tri(out, Material.CONCRETE, v0, vs.get(i), vs.get(i + 1), up, 1);
            }
        }
    }

    /** A convex polygon of {dx, dz, t, |u|} points cut to value[k] >= at (above) or <= at, interpolated along its edges. */
    private static List<double[]> cut(List<double[]> poly, int k, double at, boolean above) {
        List<double[]> out = new ArrayList<>();
        int n = poly.size();
        for (int i = 0; i < n; i++) {
            double[] a = poly.get(i), b = poly.get((i + 1) % n);
            double fa = above ? a[k] - at : at - a[k], fb = above ? b[k] - at : at - b[k];
            if (fa >= 0) out.add(a);
            if (fa >= 0 != fb >= 0) {
                double w = fa / (fa - fb);
                double[] m = new double[a.length];
                for (int j = 0; j < a.length; j++) m[j] = a[j] + (b[j] - a[j]) * w;
                out.add(m);
            }
        }
        return out;
    }

    /** Whether two lateral offsets lie on the same bore's ring outline (not across the gap between twin bores). */
    private static boolean sameRing(TunnelSection ts, double ua, double ub) {
        if (!ts.twin) return true;
        return Math.signum(ua) == Math.signum(ub) && Math.min(Math.abs(ua), Math.abs(ub)) >= ts.inner - TunnelWorks.RING - 1e-6;
    }

    /** A ring's outer outline at the portal (bore x, y): from the floor at -RING over the offset arch to width + RING. */
    private static double[][] ringOuter(RoadDesign d, TunnelSection ts, int side, double sp) {
        double b = TunnelWorks.base(d, ts, side, sp);
        double[][] arch = ts.archPoints(TunnelWorks.RING);
        double[][] p = new double[arch.length + 2][];
        p[0] = new double[]{-TunnelWorks.RING, Math.min(TunnelWorks.toe(d, ts, side, sp, true), b) - 0.3};
        for (int k = 0; k < arch.length; k++) p[k + 1] = new double[]{arch[k][0], b + arch[k][1]};
        p[arch.length + 1] = new double[]{ts.width + TunnelWorks.RING, Math.min(TunnelWorks.toe(d, ts, side, sp, false), b) - 0.3};
        return p;
    }

    /** A horizontal face from station a to b, lateral u0 .. u1, at height y, facing up (dir 1) or down (-1), tiled 1 m. */
    private static void horizontal(List<Triangle> out, RoadDesign d, double a, double b, double u0, double u1, double y, int dir) {
        double sa = Math.min(a, b), sb = Math.max(a, b);
        for (double u = u0; u < u1 - 1e-9; u = Math.min(u1, Math.floor(u / TILE + 1e-9) * TILE + TILE)) {
            double ub = Math.min(u1, Math.floor(u / TILE + 1e-9) * TILE + TILE), cu = Math.floor(u / TILE + 1e-9) * TILE;
            double[] p = d.point(sa, u), q = d.point(sa, ub), r = d.point(sb, ub), s = d.point(sb, u);
            Vertex va = new Vertex(p[0], y, p[1], u - cu, 0), vb = new Vertex(q[0], y, q[1], ub - cu, 0),
                    vc = new Vertex(r[0], y, r[1], ub - cu, Math.min(1, sb - sa)), ve = new Vertex(s[0], y, s[1], u - cu, Math.min(1, sb - sa));
            quad(out, Material.CONCRETE, va, vb, vc, ve, new Vertex(va.x(), y + dir * 10, va.z(), 0, 0));
        }
    }

    // ------------------------------------------------------------------ faces

    /** A quad a b c e (two triangles), each wound and given its own normal facing the reference point. */
    static void quad(List<Triangle> out, Material m, Vertex a, Vertex b, Vertex c, Vertex e, Vertex ref) {
        tri(out, m, a, b, c, ref, 1);
        tri(out, m, a, c, e, ref, 1);
    }

    /** A quad facing away from the reference point. */
    static void quadAway(List<Triangle> out, Material m, Vertex a, Vertex b, Vertex c, Vertex e, Vertex ref) {
        tri(out, m, a, b, c, ref, -1);
        tri(out, m, a, c, e, ref, -1);
    }

    private static void tri(List<Triangle> out, Material m, Vertex a, Vertex b, Vertex c, Vertex ref, int dir) {
        double ux = b.x() - a.x(), uy = b.y() - a.y(), uz = b.z() - a.z(), vx = c.x() - a.x(), vy = c.y() - a.y(), vz = c.z() - a.z();
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx, n = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (n < 1e-9) return;
        double cx = (a.x() + b.x() + c.x()) / 3, cy = (a.y() + b.y() + c.y()) / 3, cz = (a.z() + b.z() + c.z()) / 3;
        if (dir * (nx * (ref.x() - cx) + ny * (ref.y() - cy) + nz * (ref.z() - cz)) < 0) { Vertex t = b; b = c; c = t; nx = -nx; ny = -ny; nz = -nz; }
        out.add(new Triangle(m, a, b, c, nx / n, ny / n, nz / n));
    }

    /**
     * A vertical face over the horizontal line a -> b (bottom and top at both ends), tiled TILE along tu (in metres) and
     * in height, facing (nx, ny, nz).
     */
    static void wall(List<Triangle> out, Material m, double ax, double az, double bx, double bz, double botA, double botB, double topA, double topB,
                     double tuA, double tuB, double nx, double ny, double nz) {
        double lo = Math.min(tuA, tuB), fl = Math.floor(lo / TILE + 1e-9) * TILE;
        if (Math.max(tuA, tuB) > fl + TILE + 1e-9) {
            double cut = fl + TILE, q = (cut - tuA) / (tuB - tuA);
            double mx = ax + (bx - ax) * q, mz = az + (bz - az) * q, mb = botA + (botB - botA) * q, mt = topA + (topB - topA) * q;
            wall(out, m, ax, az, mx, mz, botA, mb, topA, mt, tuA, cut, nx, ny, nz);
            wall(out, m, mx, mz, bx, bz, mb, botB, mt, topB, cut, tuB, nx, ny, nz);
            return;
        }
        double ta = (tuA - fl) / TILE, tb = (tuB - fl) / TILE;
        double lowY = Math.min(botA, botB), hiY = Math.max(topA, topB);
        if (!(hiY > lowY)) return;
        for (int k = (int) Math.floor(lowY); k < hiY; k++) {
            List<double[]> p = List.of(new double[]{0, botA}, new double[]{1, botB}, new double[]{1, topB}, new double[]{0, topA});
            p = clipY(p, k, true);
            p = clipY(p, k + 1, false);
            if (p.size() < 3) continue;
            List<Vertex> v = new ArrayList<>(p.size());
            for (double[] q : p)
                v.add(new Vertex(ax + (bx - ax) * q[0], q[1], az + (bz - az) * q[0], Math.max(0, Math.min(1, ta + (tb - ta) * q[0])), Math.max(0, Math.min(1, k + 1 - q[1]))));
            fan(out, m, v, nx, ny, nz);
        }
    }

    /**
     * A planar polygon in the vertical plane at station s (a portal ring's face): tiled 1 m in (lateral, height) by
     * clipping, facing (nx, ny, nz).
     */
    private static void planar(List<Triangle> out, Material m, List<Vertex> poly, double nx, double ny, double nz, RoadDesign d, double s) {
        double[] o = d.point(s, 0), lat = BridgeMesh.lateral(d, s);
        List<double[]> p = new ArrayList<>();
        double h0 = Double.POSITIVE_INFINITY, h1 = Double.NEGATIVE_INFINITY, y0 = Double.POSITIVE_INFINITY, y1 = Double.NEGATIVE_INFINITY;
        for (Vertex v : poly) {
            double h = (v.x() - o[0]) * lat[0] + (v.z() - o[1]) * lat[1];
            p.add(new double[]{h, v.y()});
            h0 = Math.min(h0, h); h1 = Math.max(h1, h); y0 = Math.min(y0, v.y()); y1 = Math.max(y1, v.y());
        }
        for (int i = (int) Math.floor(h0); i < h1; i++)
            for (int j = (int) Math.floor(y0); j < y1; j++) {
                List<double[]> c = clip(p, 0, i, true);
                c = clip(c, 0, i + 1, false);
                c = clip(c, 1, j, true);
                c = clip(c, 1, j + 1, false);
                if (c.size() < 3) continue;
                List<Vertex> v = new ArrayList<>();
                for (double[] q : c) v.add(new Vertex(o[0] + lat[0] * q[0], q[1], o[1] + lat[1] * q[0], q[0] - i, j + 1 - q[1]));
                fan(out, m, v, nx, ny, nz);
            }
    }

    private static List<double[]> clip(List<double[]> in, int axis, double edge, boolean lower) {
        List<double[]> out = new ArrayList<>(8);
        if (in.isEmpty()) return out;
        double[] a = in.get(in.size() - 1);
        double da = a[axis] - edge;
        boolean ia = lower ? da >= 0 : da <= 0;
        for (double[] b : in) {
            double db = b[axis] - edge;
            boolean ib = lower ? db >= 0 : db <= 0;
            if (ia != ib) {
                double t = da / (da - db);
                out.add(new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t});
            }
            if (ib) out.add(b);
            a = b;
            da = db;
            ia = ib;
        }
        return out;
    }

    private static List<double[]> clipY(List<double[]> in, double edge, boolean lower) { return clip(in, 1, edge, lower); }

    /** A convex polygon fan-triangulated, wound to face (nx, ny, nz), with that normal. */
    private static void fan(List<Triangle> out, Material m, List<Vertex> p, double nx, double ny, double nz) {
        double n = Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= n;
        ny /= n;
        nz /= n;
        for (int i = 1; i + 1 < p.size(); i++) {
            Vertex a = p.get(0), b = p.get(i), c = p.get(i + 1);
            double ux = b.x() - a.x(), uy = b.y() - a.y(), uz = b.z() - a.z(), vx = c.x() - a.x(), vy = c.y() - a.y(), vz = c.z() - a.z();
            double gx = uy * vz - uz * vy, gy = uz * vx - ux * vz, gz = ux * vy - uy * vx;
            if (Math.sqrt(gx * gx + gy * gy + gz * gz) < 1e-9) continue;
            if (gx * nx + gy * ny + gz * nz < 0) { Vertex t = b; b = c; c = t; }
            out.add(new Triangle(m, a, b, c, nx, ny, nz));
        }
    }

    // ------------------------------------------------------------------ equipment

    /**
     * One equipment part: the Vextris mesh (apocalypse_firstlight:road/tunnel/&lt;mesh&gt;), its bone (a variant; null: all),
     * the world origin and the part's axes in the world: ez the side it shows (out of its wall, toward the traffic it
     * faces), ey up, ex = ey x ez (so text reads left to right seen from its front).
     */
    public record Placement(String mesh, String bone, double x, double y, double z, double[] ex, double[] ey, double[] ez) {}

    /** Tunnel names (plates on both portals), picked per tunnel from its road and station. */
    public static final String[] NAMES = {"cedar_pass", "hollow_ridge", "iron_gap", "pine_summit", "black_canyon", "eagle_crest", "stone_notch", "red_bluff"};
    /** Length thresholds (NFPA 502 as in the concept): portal traffic control, in-tunnel systems, passages and fans, m. */
    public static final double PORTAL_CONTROL = 90, SYSTEMS = 240, VENTILATED = 300;
    /** Luminaire spacing: entrance zone (two per side), transition zone, interior, exit zone (the last EXIT_ZONE), m. */
    public static final double ENTRANCE = 80, TRANSITION = 230, EXIT_ZONE = 60, LIGHT_ENTRANCE = 2.2, LIGHT_TRANSITION = 4, LIGHT_BASIC = 8;
    /** Luminaire height over the base; fan pairs' spacing and offset off the axis; equipment spacings, m. */
    public static final double LIGHT_Y = 5.75, FAN_SPACING = 150, FAN_OFFSET = 1.6, NICHE = 83, EGRESS = 25, CCTV = 125, LCS = 250;
    /**
     * Roadside parts beyond the formation's edge; the CMS gantry's distance before the portal, the range and the steps it
     * moves in off a bridge (a long viaduct often reaches the approach), and how far it keeps from a span's ends
     * (abutments, wing walls), m.
     */
    public static final double ROADSIDE = 0.9, GANTRY = 200, GANTRY_MIN = 100, GANTRY_MAX = 800, GANTRY_STEP = 10, BRIDGE_CLEAR = 20;

    /** Which tunnel name a tunnel takes. */
    public static String name(RoadDesign d, RoadDesign.Tunnel tn) {
        return NAMES[Math.floorMod(d.id.hashCode() * 31 + (int) Math.round(tn.s0()), NAMES.length)];
    }

    /** The parts whose origins lie in chunk (cx, cz). */
    public static List<Placement> parts(RoadWorks works, int cx, int cz) {
        List<Placement> out = new ArrayList<>();
        for (RoadDesign d : works.roads()) {
            if (d.tunnels.isEmpty()) continue;
            double[] r = BridgeMesh.stations(d, cx, cz);
            if (r == null) continue;
            for (RoadDesign.Tunnel tn : d.tunnels) {
                double reach = GANTRY_MAX + 16;
                if (tn.s0() - reach > r[1] || tn.s1() + reach < r[0]) continue;
                for (Placement p : all(d, tn))
                    if (Math.floorDiv((int) Math.floor(p.x()), 16) == cx && Math.floorDiv((int) Math.floor(p.z()), 16) == cz) out.add(p);
            }
        }
        return out;
    }

    /** Every part of a tunnel by the concept's equipment rules (docs 5). */
    public static List<Placement> all(RoadDesign d, RoadDesign.Tunnel tn) {
        TunnelSection ts = TunnelSection.of(d.section);
        List<Placement> out = new ArrayList<>();
        double len = tn.length();
        double[] passages = TunnelWorks.passages(ts, tn);
        for (int side : ts.sides()) {
            // the travel direction through this bore: R4 right bore +s, left bore -s; R2 both (entrance zones at both ends)
            int travel = ts.twin ? side : 0;
            // luminaires on both upper walls
            for (double s : lights(tn, travel)) for (boolean left : new boolean[]{true, false}) out.add(onArch(d, ts, side, s, left, LIGHT_Y, "luminaire", null));
            if (len >= SYSTEMS) {
                // emergency cabinets on the egress side's wall, egress signs every EGRESS, cameras opposite every CCTV
                int n = (int) Math.ceil(len / NICHE - 1e-9);
                for (int i = 0; i < n; i++) out.add(onWall(d, ts, side, tn.s0() + len * (i + 0.5) / n, true, 0, "emergency_cabinet", null));
                for (double s = tn.s0() + EGRESS / 2; s < tn.s1(); s += EGRESS) {
                    if (near(passages, s, TunnelWorks.PASSAGE_W)) continue;
                    double best = Double.POSITIVE_INFINITY, to = 0;
                    for (double e : exits(tn, passages)) if (Math.abs(e - s) < best) { best = Math.abs(e - s); to = e; }
                    Placement p = onWall(d, ts, side, s, true, 0.5, "egress_sign", null);
                    // which way the exit lies on the sign's face (+ex is the reader's right)
                    double[] tg = BridgeMesh.tangent(d, s);
                    boolean right = (p.ex()[0] * tg[0] + p.ex()[2] * tg[1]) * (to - s) > 0;
                    int dist = (int) Math.max(25, Math.min(150, Math.round(best / 25) * 25));
                    out.add(new Placement(p.mesh(), (right ? "r" : "l") + dist, p.x(), p.y(), p.z(), p.ex(), p.ey(), p.ez()));
                }
                for (double s = tn.s0() + CCTV / 2; s < tn.s1(); s += CCTV) out.add(onArch(d, ts, side, s, false, 5.0, "cctv", null));
                // lane control signals over each lane: inside each entrance and every LCS
                for (double s : lcs(tn)) out.addAll(laneSignals(d, ts, side, s, travel));
            }
            if (len >= VENTILATED && ts.twin) {
                int n = Math.max(1, (int) Math.floor(len / FAN_SPACING));
                for (int i = 0; i < n; i++) {
                    double s = tn.s0() + len * (i + 0.5) / n;
                    for (int f : new int[]{-1, 1}) out.add(fan(d, ts, side, s, ts.xa + f * FAN_OFFSET));
                }
            }
        }
        if (len >= VENTILATED && !ts.twin) {
            int n = Math.max(1, (int) Math.floor(len / FAN_SPACING));
            for (int i = 0; i < n; i++) for (int f : new int[]{-1, 1}) out.add(fan(d, ts, 0, tn.s0() + len * (i + 0.5) / n, ts.xa + f * FAN_OFFSET));
        }
        // fire doors at the cross passages (both bores)
        for (double p : passages) for (int side : ts.sides()) out.add(onWall(d, ts, side, p, true, 0, "fire_door", null));
        // handrails on both walls of each bore, a segment per SPAN, open at the passages (the egress side)
        for (int side : ts.sides()) for (boolean left : new boolean[]{true, false}) out.addAll(handrails(d, ts, tn, side, left, left ? passages : new double[0]));
        // portals: name plate and clearance plates on the headwalls; traffic control on the approaches by length
        for (int sgn : new int[]{1, -1}) {
            double sp = sgn > 0 ? tn.s0() : tn.s1();
            double[] tg = BridgeMesh.tangent(d, sp);
            double[] ez = {-sgn * tg[0], 0, -sgn * tg[1]};
            double top = d.datum(sp) + TunnelWorks.headTop(ts);
            double[] o = d.point(sp - sgn * 0.02, ts.twin ? 0 : ts.u(0, ts.xa));
            // R4 between the rings; R2 over its ring and the clearance plate
            double plateY = ts.twin ? top - TunnelWorks.COPING_H - 2.2 : top - TunnelWorks.COPING_H - 0.6;
            out.add(part("name_plate", name(d, tn), o[0], plateY, o[1], ez));
            for (int side : ts.sides()) {
                double b = TunnelWorks.base(d, ts, side, sp);
                double[] c = d.point(sp - sgn * (TunnelWorks.RING_OUT + 0.02), ts.u(side, ts.xa));
                out.add(part("clearance_plate", null, c[0], b + ts.crown + TunnelWorks.RING + 0.2, c[1], ez));
            }
            if (len < PORTAL_CONTROL) continue;
            // traffic entering at this portal (R4 the right carriageway at s0, the left one at s1; R2 both) keeps right
            String cls = ts.twin ? "r4" : "r2";
            out.add(roadside(d, sp, sgn, 40, "portal_signal", cls));
            out.add(roadside(d, sp, sgn, 47, "headlight_sign", null));
            out.add(roadside(d, sp, sgn, 55, "closure_gate", null));
            if (len >= SYSTEMS) {
                double t = gantry(d, tn, sp, sgn);
                if (!Double.isNaN(t)) out.add(roadside(d, sp, sgn, t, "cms_gantry", cls));
            }
        }
        return out;
    }

    /**
     * Where the CMS gantry stands before the portal at sp (sgn +1: before s0), its distance from the portal: GANTRY, or
     * the distance nearest to it within GANTRY_MIN .. GANTRY_MAX on the route and clear of bridges (their spans and ends,
     * BRIDGE_CLEAR) and of other tunnels' approaches; NaN: none. At a fixed 200 m one stood beside a deck, its pole 21 m
     * over the valley (2026-10-16; on seed 4 both long tunnels have a viaduct in front of one portal).
     */
    public static double gantry(RoadDesign d, RoadDesign.Tunnel tn, double sp, int sgn) {
        for (int k = 0; GANTRY + k * GANTRY_STEP <= GANTRY_MAX || GANTRY - k * GANTRY_STEP >= GANTRY_MIN; k++)
            for (int dir : k == 0 ? new int[]{1} : new int[]{-1, 1}) {
                double t = GANTRY + dir * k * GANTRY_STEP, s = sp - sgn * t;
                if (t < GANTRY_MIN || t > GANTRY_MAX || s < BRIDGE_CLEAR || s > d.length - BRIDGE_CLEAR) continue;
                boolean clear = true;
                for (RoadDesign.Span span : d.spans) if (s > span.s0() - BRIDGE_CLEAR && s < span.s1() + BRIDGE_CLEAR) clear = false;
                for (RoadDesign.Tunnel o : d.tunnels) if (o != tn && o.near(s, TunnelWorks.APPROACH)) clear = false;
                if (clear) return t;
            }
        return Double.NaN;
    }

    private static boolean near(double[] ps, double s, double r) {
        for (double p : ps) if (Math.abs(p - s) < r) return true;
        return false;
    }

    /** The exits of a bore: both portals and the cross passages. */
    private static double[] exits(RoadDesign.Tunnel tn, double[] passages) {
        double[] out = new double[passages.length + 2];
        out[0] = tn.s0();
        out[1] = tn.s1();
        System.arraycopy(passages, 0, out, 2, passages.length);
        return out;
    }

    /**
     * Luminaire stations of a bore: entrance zone (two per side, LIGHT_ENTRANCE), transition, interior, exit zone (from the
     * entrance portal in the travel direction; travel 0: both directions, each end an entrance).
     */
    static List<Double> lights(RoadDesign.Tunnel tn, int travel) {
        List<Double> out = new ArrayList<>();
        double len = tn.length();
        for (double t = 1.0; t < len - 0.5; ) {
            double from = travel >= 0 ? t : len - t;   // distance from the entrance portal
            double rest = len - from;
            double fromEnd = travel == 0 ? Math.min(from, rest) : from;
            double toExit = travel == 0 ? Double.POSITIVE_INFINITY : rest;
            double step = fromEnd < ENTRANCE ? LIGHT_ENTRANCE : fromEnd < TRANSITION ? LIGHT_TRANSITION : toExit < EXIT_ZONE ? LIGHT_TRANSITION : LIGHT_BASIC;
            out.add(tn.s0() + t);
            t += step;
        }
        return out;
    }

    /** Lane control signal stations: 5 m inside each portal and every LCS between. */
    private static List<Double> lcs(RoadDesign.Tunnel tn) {
        List<Double> out = new ArrayList<>();
        out.add(tn.s0() + 5);
        for (double s = tn.s0() + LCS; s < tn.s1() - 30; s += LCS) out.add(s);
        out.add(tn.s1() - 5);
        return out;
    }

    /**
     * The handrail segments on a bore's wall over a walkway (Tunnel parts removable V1.1, 2026-10-16; before that one
     * continuous rail in the lining): one per SPAN of the lattice between the portals, none where a passage opens. Each a
     * fixture "handrail" whose bone is its length (m): origin at the wall's foot over the walkway at the segment's middle,
     * ex along the chord between its ends (following the grade), ez out of the wall, ey up.
     */
    static List<Placement> handrails(RoadDesign d, TunnelSection ts, RoadDesign.Tunnel tn, int side, boolean left, double[] passages) {
        List<Placement> out = new ArrayList<>();
        double[] st = grid(tn.s0(), tn.s1(), TunnelWorks.passages(ts, tn), SPAN);
        double xw = left ? 0 : ts.width;
        double sx = ts.twin && side < 0 ? -1 : 1, nx = left ? 1 : -1;
        for (int k = 0; k + 1 < st.length; k++) {
            double s0 = st[k], s1 = st[k + 1];
            if (s1 - s0 < 0.2 || near(passages, (s0 + s1) / 2, TunnelWorks.PASSAGE_W / 2)) continue;
            double[] p0 = d.point(s0, ts.u(side, xw)), p1 = d.point(s1, ts.u(side, xw));
            double y0 = TunnelWorks.toe(d, ts, side, s0, left) + TunnelSection.WALK_H, y1 = TunnelWorks.toe(d, ts, side, s1, left) + TunnelSection.WALK_H;
            double[] ex = {p1[0] - p0[0], y1 - y0, p1[1] - p0[1]};
            double len = Math.sqrt(ex[0] * ex[0] + ex[1] * ex[1] + ex[2] * ex[2]);
            for (int i = 0; i < 3; i++) ex[i] /= len;
            double[] lat = BridgeMesh.lateral(d, (s0 + s1) / 2);
            double[] ez = {sx * nx * lat[0], 0, sx * nx * lat[1]};
            double dot = ez[0] * ex[0] + ez[2] * ex[2];
            for (int i = 0; i < 3; i++) ez[i] -= dot * ex[i];
            double el = Math.sqrt(ez[0] * ez[0] + ez[1] * ez[1] + ez[2] * ez[2]);
            for (int i = 0; i < 3; i++) ez[i] /= el;
            double[] ey = cross(ez, ex);
            if (ey[1] < 0) {
                // ex the other way round (the segment is symmetric about its middle)
                for (int i = 0; i < 3; i++) ex[i] = -ex[i];
                ey = cross(ez, ex);
            }
            out.add(new Placement("handrail", String.format(java.util.Locale.ROOT, "%.4f", len), (p0[0] + p1[0]) / 2, (y0 + y1) / 2, (p0[1] + p1[1]) / 2, ex, ey, ez));
        }
        return out;
    }

    /** A part on the arch at height y over the base (left or right side of a bore), facing into the bore. */
    private static Placement onArch(RoadDesign d, TunnelSection ts, int side, double s, boolean left, double y, String mesh, String bone) {
        double b = TunnelWorks.base(d, ts, side, s);
        // the side arc's x at height y: x = xa -+ (dx + sqrt(rs^2 - (y - hc)^2))
        double e = ts.dx + Math.sqrt(Math.max(0, ts.rs * ts.rs - (y - ts.hc) * (y - ts.hc)));
        double x = left ? ts.xa - e : ts.xa + e;
        // inward normal of the side arc there (toward its centre), in bore (x, y)
        double cxa = left ? ts.xa - ts.dx : ts.xa + ts.dx, nx = cxa - x, ny = ts.hc - y, nl = Math.hypot(nx, ny);
        nx /= nl;
        ny /= nl;
        return framed(d, ts, side, s, x, b + y, nx, ny, mesh, bone);
    }

    /** A part on a bore's wall over a walkway (left: the egress side), its origin at the wall's foot over the walkway plus y. */
    private static Placement onWall(RoadDesign d, TunnelSection ts, int side, double s, boolean left, double y, String mesh, String bone) {
        double walk = TunnelWorks.toe(d, ts, side, s, left) + TunnelSection.WALK_H;
        return framed(d, ts, side, s, left ? 0 : ts.width, walk + y, left ? 1 : -1, 0, mesh, bone);
    }

    /**
     * A part at bore point (x, y) whose front faces bore direction (nx, ny) (its ez); ey the section's upward direction
     * perpendicular to ez, ex = ey x ez.
     */
    private static Placement framed(RoadDesign d, TunnelSection ts, int side, double s, double x, double y, double nx, double ny, String mesh, String bone) {
        double[] w = d.point(s, ts.u(side, x)), lat = BridgeMesh.lateral(d, s);
        // bore +x in the world: R4 left bore runs outward to -u
        double sx = ts.twin && side < 0 ? -1 : 1;
        double[] ez = {sx * nx * lat[0], ny, sx * nx * lat[1]};
        double[] ey = perpUp(ez, lat);
        return new Placement(mesh, bone, w[0], y, w[1], cross(ey, ez), ey, ez);
    }

    /** The unit vector in the section plane (spanned by the lateral and up) perpendicular to ez with a positive y. */
    private static double[] perpUp(double[] ez, double[] lat) {
        double h = ez[0] * lat[0] + ez[2] * lat[1];   // ez's lateral component
        double ux = -ez[1] * lat[0], uy = h, uz = -ez[1] * lat[1];
        if (uy < 0) { ux = -ux; uy = -uy; uz = -uz; }
        double l = Math.sqrt(ux * ux + uy * uy + uz * uz);
        return new double[]{ux / l, uy / l, uz / l};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    /** A part standing upright at a world point, its front facing horizontal ez. */
    private static Placement part(String mesh, String bone, double x, double y, double z, double[] ez) {
        double[] ey = {0, 1, 0};
        return new Placement(mesh, bone, x, y, z, cross(ey, ez), ey, ez);
    }

    /**
     * A roadside part t before the portal at sp (sgn +1: before s0), ROADSIDE beyond the formation's edge on the side
     * of the traffic entering there, facing it (the road's direction at its own station), its foot on the finished ground:
     * the open road's 1:4 foreslope there (CrossSection#roadside, the same in cut and fill), the approach's level shelf
     * within BLEND_FROM, blended between like TunnelWorks#surface. At the shoulder hinge's height the feet stood up to
     * 0.22 m over the ground (2026-10-16).
     */
    private static Placement roadside(RoadDesign d, double sp, int sgn, double t, String mesh, String bone) {
        double s = sp - sgn * t, u = sgn * (d.section.edge + ROADSIDE);
        double[] p = d.point(s, u), tg = BridgeMesh.tangent(d, s);
        double w = t >= TunnelWorks.APPROACH ? 1 : t > TunnelWorks.BLEND_FROM
                ? CrossSection.smooth((t - TunnelWorks.BLEND_FROM) / (TunnelWorks.APPROACH - TunnelWorks.BLEND_FROM)) : 0;
        double y = d.formationY(TunnelWorks.clamp(d, s), Math.copySign(d.section.edge, u)) + w * d.section.roadside(ROADSIDE, -1e9);
        return part(mesh, bone, p[0], y, p[1], new double[]{-sgn * tg[0], 0, -sgn * tg[1]});
    }

    /** A jet fan hung under the arch at bore x, its axis along the road. */
    private static Placement fan(RoadDesign d, TunnelSection ts, int side, double s, double x) {
        double b = TunnelWorks.base(d, ts, side, s), y = b + ts.arch(x, 0) - 0.05;
        double[] w = d.point(s, ts.u(side, x)), tg = BridgeMesh.tangent(d, s);
        return part("jet_fan", null, w[0], y, w[1], new double[]{tg[0], 0, tg[1]});
    }

    /** Lane control signals over the lanes of a bore at station s, facing the traffic that comes toward them. */
    private static List<Placement> laneSignals(RoadDesign d, TunnelSection ts, int side, double s, int travel) {
        List<Placement> out = new ArrayList<>();
        double b = TunnelWorks.base(d, ts, side, s);
        double[] tg = BridgeMesh.tangent(d, s);
        CrossSection sec = d.section;
        for (int k = 0; k < sec.lanes; k++) {
            double c = sec.laneCentre(k);
            for (int dirSide : ts.twin ? new int[]{side} : new int[]{-1, 1}) {
                double u = dirSide * c;
                // traffic on the right of the centreline drives +s (faces -s toward it), on the left -s
                int dir = ts.twin ? travel : dirSide;
                double[] w = d.point(s, u);
                out.add(part("lane_signal", null, w[0], b + TunnelSection.CLEARANCE + 0.25, w[1], new double[]{-dir * tg[0], 0, -dir * tg[1]}));
            }
        }
        return out;
    }
}
