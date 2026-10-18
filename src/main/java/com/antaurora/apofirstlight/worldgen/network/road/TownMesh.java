package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.network.town.TownPlan;
import com.antaurora.apofirstlight.worldgen.network.town.TownWorks;

import java.util.ArrayList;
import java.util.List;

/**
 * The pavement of the framework towns (Town Framework V1, docs/worldgen/federal_towns_v1.md 7): the street asphalt, the
 * curbs and the sidewalks of TownWorks#spot, meshed on the town's own grid (its streets are axis-aligned there) in CELL
 * squares, a square holding two parts (a curb return's arc) cut into FINE ones. Every square is split along the parts'
 * true boundary (marching squares, 2026-10-17: the FINE squares had been one part each, read at their corners, which made
 * a saw of sloped curb tops and 0.25 m steps round every curb return): its crossings found on its sides by bisection
 * (the same on both squares of a side), each part's polygon drawn at that part's own heights. Where a paved part meets a
 * lower one (the curb's face over the gutter) a vertical face closes the step, drawn by the higher side only; along the
 * pavement's outer edge a SKIRT hides the turf's 1/8 steps. A square holding three parts, or two in a saddle, is one
 * part (its centre's). Texture in the town's coordinates, a tile every RoadMesh.TEXTURE metres (a square never crosses
 * one). Drawn with the roads' chunk meshes (JunctionMesh#pavement).
 */
public final class TownMesh {
    private TownMesh() {}

    static final double CELL = 1, FINE = 0.25, SKIRT = 0.25;
    /** How far into a part its heights are read beside a boundary, m. */
    private static final double INSET = 1e-3;
    /**
     * A crossing this close to a square's corner snaps to it: a boundary running along a grid line a few mm off cuts no
     * sliver (a wedge thinner than 1 mm flickers), m.
     */
    private static final double SNAP = 0.01;
    /** Polygons smaller than this are not drawn, m2. */
    private static final double TINY = 1e-5;

    /** The towns' pavement triangles inside chunk (cx, cz), relative to (cx * 16, 0, cz * 16). */
    public static List<RoadMesh.Triangle> chunk(TownWorks works, int cx, int cz) {
        List<RoadMesh.Triangle> out = new ArrayList<>();
        if (works.isEmpty() || !works.touches(cx, cz)) return out;
        double x0 = cx * 16, z0 = cz * 16;
        for (TownPlan p : works.plans()) {
            // the chunk's square in town coordinates
            double s0 = Double.POSITIVE_INFINITY, s1 = Double.NEGATIVE_INFINITY, v0 = Double.POSITIVE_INFINITY, v1 = Double.NEGATIVE_INFINITY;
            for (int b = 0; b <= 1; b++) for (int a = 0; a <= 1; a++) {
                double[] l = p.local(x0 + 16 * a, z0 + 16 * b);
                s0 = Math.min(s0, l[0]); s1 = Math.max(s1, l[0]); v0 = Math.min(v0, l[1]); v1 = Math.max(v1, l[1]);
            }
            if (s1 < p.limit[0] || s0 > p.limit[1] || v1 < p.limit[2] || v0 > p.limit[3]) continue;
            s0 = Math.max(s0, p.limit[0]); s1 = Math.min(s1, p.limit[1]); v0 = Math.max(v0, p.limit[2]); v1 = Math.min(v1, p.limit[3]);
            List<RoadMesh.Triangle> world = new ArrayList<>();
            for (double s = Math.floor(s0 / CELL) * CELL; s < s1; s += CELL)
                for (double v = Math.floor(v0 / CELL) * CELL; v < v1; v += CELL) {
                    if (pure(works, p, s, v, CELL)) square(world, works, p, s, v, CELL);
                    else
                        for (double fs = s; fs < s + CELL - 1e-9; fs += FINE)
                            for (double fv = v; fv < v + CELL - 1e-9; fv += FINE) square(world, works, p, fs, fv, FINE);
                }
            for (RoadMesh.Triangle t : world) RoadMesh.clipInto(out, t, x0, z0);
        }
        return out;
    }

    // ------------------------------------------------------------------ parts as drawn

    /** The drawing class: 0 unpaved or off the footprint, 1 asphalt, 2 concrete (curb and sidewalk: one height, one material). */
    private static int cls(TownWorks.Spot sp) {
        if (sp == null || !sp.part().paved()) return 0;
        return sp.part() == TownWorks.Part.ASPHALT ? 1 : 2;
    }

    private static TownWorks.Spot at(TownWorks w, TownPlan p, double s, double v) {
        double[] x = p.world(s, v);
        return w.spot(x[0], x[1]);
    }

    private static int clsAt(TownWorks w, TownPlan p, double s, double v) { return cls(at(w, p, s, v)); }

    private static RoadMesh.Material material(int c) { return c == 1 ? RoadMesh.Material.ASPHALT : RoadMesh.Material.CONCRETE; }

    /** Whether a square holds one class (its corners, inset, and its centre agree). */
    private static boolean pure(TownWorks w, TownPlan p, double s, double v, double c) {
        int m = clsAt(w, p, s + c / 2, v + c / 2);
        double i = c * 0.02;
        return m == clsAt(w, p, s + i, v + i) && m == clsAt(w, p, s + c - i, v + i) && m == clsAt(w, p, s + i, v + c - i) && m == clsAt(w, p, s + c - i, v + c - i);
    }

    /**
     * Class c's height at (s, v): the spot there when it is c, else the spot just inside c on the way to (ts, tv), a point
     * of c.
     */
    private static double height(TownWorks w, TownPlan p, int c, double s, double v, double ts, double tv) {
        TownWorks.Spot sp = at(w, p, s, v);
        if (sp != null && cls(sp) == c) return sp.h();
        double a = 0, b = 1;
        for (int i = 0; i < 24; i++) {
            double m = (a + b) / 2;
            if (clsAt(w, p, s + (ts - s) * m, v + (tv - v) * m) == c) b = m; else a = m;
        }
        double len = Math.hypot(ts - s, tv - v), t = Math.min(1, b + (len < 1e-12 ? 0 : INSET / len));
        TownWorks.Spot q = at(w, p, s + (ts - s) * t, v + (tv - v) * t);
        if (q == null || cls(q) != c) q = at(w, p, ts, tv);
        return q == null ? Double.NaN : q.h();
    }

    /**
     * Where the class changes on the side from a to b (their classes differ): bisected from the side's lower end (s, then
     * v), so both squares of a side find the same point; snapped to an end within SNAP.
     */
    private static double[] crossing(TownWorks w, TownPlan p, double[] a, double[] b) {
        boolean flip = b[0] < a[0] || b[0] == a[0] && b[1] < a[1];
        double[] f = flip ? b : a, g = flip ? a : b;
        int cf = clsAt(w, p, f[0], f[1]);
        double lo = 0, hi = 1;
        for (int i = 0; i < 24; i++) {
            double m = (lo + hi) / 2;
            if (clsAt(w, p, f[0] + (g[0] - f[0]) * m, f[1] + (g[1] - f[1]) * m) == cf) lo = m; else hi = m;
        }
        double t = (lo + hi) / 2, len = Math.hypot(g[0] - f[0], g[1] - f[1]);
        if (t * len < SNAP) t = 0;
        else if ((1 - t) * len < SNAP) t = 1;
        return new double[]{f[0] + (g[0] - f[0]) * t, f[1] + (g[1] - f[1]) * t};
    }

    // ------------------------------------------------------------------ one square

    /**
     * A polygon vertex: its point, its class's height there, and the edge from it to the next vertex: on the square's side
     * (0 .. 3: v = v0, s = s1, v = v1, s = s0) or inside it (-1); at a crossing the other class there (other), read toward
     * (ots, otv), its corner on that side.
     */
    private record Vx(double s, double v, double y, int side, int other, double ots, double otv) {}

    /** One square (s, v) .. (s + c, v + c): each paved class's polygon, its top and its faces down to lower neighbours. */
    private static void square(List<RoadMesh.Triangle> out, TownWorks w, TownPlan p, double s, double v, double c) {
        double[][] k = {{s, v}, {s + c, v}, {s + c, v + c}, {s, v + c}};
        double cs = s + c / 2, cv = v + c / 2;
        int[] kc = new int[4];
        for (int i = 0; i < 4; i++) kc[i] = clsAt(w, p, k[i][0], k[i][1]);
        int mid = clsAt(w, p, cs, cv);
        int a0 = kc[0], b0 = -1;
        boolean three = false;
        for (int q : kc) if (q != a0) { if (b0 < 0) b0 = q; else if (q != b0) three = true; }
        boolean saddle = kc[0] == kc[2] && kc[1] == kc[3] && kc[0] != kc[1];
        double ts = Math.floor(cs / RoadMesh.TEXTURE) * RoadMesh.TEXTURE, tv = Math.floor(cv / RoadMesh.TEXTURE) * RoadMesh.TEXTURE;
        if (b0 < 0 || three || saddle) {
            // one class: the corners', or the centre's where three meet or two cross
            int m = b0 < 0 ? a0 : mid;
            if (m == 0) return;
            List<Vx> poly = new ArrayList<>();
            for (int i = 0; i < 4; i++) poly.add(new Vx(k[i][0], k[i][1], height(w, p, m, k[i][0], k[i][1], cs, cv), i, -1, 0, 0));
            draw(out, w, p, poly, m, s, v, c, ts, tv);
            return;
        }
        double[][] x = new double[4][];
        for (int i = 0; i < 4; i++) if (kc[i] != kc[(i + 1) % 4]) x[i] = crossing(w, p, k[i], k[(i + 1) % 4]);
        for (int m : new int[]{a0, b0}) {
            if (m == 0) continue;
            List<Vx> poly = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) % 4;
                if (kc[i] == m) poly.add(new Vx(k[i][0], k[i][1], height(w, p, m, k[i][0], k[i][1], cs, cv), i, -1, 0, 0));
                if (x[i] == null) continue;
                if (kc[i] == m)        // leaving m: inside the square to where it comes back, against the other class
                    poly.add(new Vx(x[i][0], x[i][1], height(w, p, m, x[i][0], x[i][1], k[i][0], k[i][1]), -1, kc[j], k[j][0], k[j][1]));
                else                   // coming back into m: along side i to its corner (the other class behind it at k[i])
                    poly.add(new Vx(x[i][0], x[i][1], height(w, p, m, x[i][0], x[i][1], k[j][0], k[j][1]), i, kc[i], k[i][0], k[i][1]));
            }
            draw(out, w, p, poly, m, s, v, c, ts, tv);
        }
    }

    /** A class's polygon in a square: its top (a fan: the polygons are convex) and its faces. */
    private static void draw(List<RoadMesh.Triangle> out, TownWorks w, TownPlan p, List<Vx> poly, int m, double s, double v, double c, double ts, double tv) {
        int n = poly.size();
        double area = 0;
        for (int i = 0; i < n; i++) {
            Vx a = poly.get(i), b = poly.get((i + 1) % n);
            area += a.s * b.v - b.s * a.v;
            if (Double.isNaN(a.y)) return;
        }
        if (Math.abs(area) / 2 < TINY) return;
        RoadMesh.Material mat = material(m);
        RoadMesh.Vertex[] q = new RoadMesh.Vertex[n];
        for (int i = 0; i < n; i++) {
            Vx a = poly.get(i);
            double[] xz = p.world(a.s, a.v);
            q[i] = new RoadMesh.Vertex(xz[0], a.y, xz[1], (a.s - ts) / RoadMesh.TEXTURE, (a.v - tv) / RoadMesh.TEXTURE);
        }
        for (int i = 1; i + 1 < n; i++) top(out, mat, q[0], q[i], q[i + 1]);
        // the faces: each edge against what lies across it (the polygons run counter-clockwise in s, v)
        for (int i = 0; i < n; i++) {
            Vx a = poly.get(i), b = poly.get((i + 1) % n);
            double es = b.s - a.s, ev = b.v - a.v, el = Math.hypot(es, ev);
            if (el < 1e-6) continue;
            double ns = ev / el, nv = -es / el;   // outward
            double ms = (a.s + b.s) / 2, mv = (a.v + b.v) / 2;
            int across;
            double ya, yb;
            if (a.side >= 0) {
                // on the square's side: the neighbouring square's class just across its middle
                double os = ms + ns * INSET, ov = mv + nv * INSET;
                across = clsAt(w, p, os, ov);
                if (across != 0) {
                    ya = height(w, p, across, a.s + ns * INSET, a.v + nv * INSET, os, ov);
                    yb = height(w, p, across, b.s + ns * INSET, b.v + nv * INSET, os, ov);
                } else ya = yb = 0;
            } else {
                across = a.other;
                if (across != 0) {
                    ya = height(w, p, across, a.s, a.v, a.ots, a.otv);
                    yb = height(w, p, across, b.s, b.v, b.ots, b.otv);
                } else ya = yb = 0;
            }
            if (across == 0) {
                ya = a.y - SKIRT;
                yb = b.y - SKIRT;
            } else {
                if (Double.isNaN(ya) || Double.isNaN(yb) || ya > a.y - 0.005 && yb > b.y - 0.005) continue;
                ya = Math.min(ya, a.y);
                yb = Math.min(yb, b.y);
            }
            double[] xa = p.world(a.s, a.v), xb = p.world(b.s, b.v), dn = p.world(a.s + ns, a.v + nv);
            double nx = dn[0] - xa[0], nz = dn[1] - xa[1], nl = Math.hypot(nx, nz);
            nx /= nl;
            nz /= nl;
            boolean alongS = Math.abs(es) >= Math.abs(ev);
            double ua = (alongS ? a.s - ts : a.v - tv) / RoadMesh.TEXTURE, ub = (alongS ? b.s - ts : b.v - tv) / RoadMesh.TEXTURE;
            RoadMesh.Vertex ta = new RoadMesh.Vertex(xa[0], a.y, xa[1], ua, 0), tb = new RoadMesh.Vertex(xb[0], b.y, xb[1], ub, 0);
            RoadMesh.Vertex ba = new RoadMesh.Vertex(xa[0], ya, xa[1], ua, (a.y - ya) / RoadMesh.TEXTURE), bb = new RoadMesh.Vertex(xb[0], yb, xb[1], ub, (b.y - yb) / RoadMesh.TEXTURE);
            side(out, mat, ta, tb, bb, nx, nz);
            side(out, mat, ta, bb, ba, nx, nz);
        }
    }

    /** A top triangle wound counter-clockwise from above, its normal up. */
    private static void top(List<RoadMesh.Triangle> out, RoadMesh.Material m, RoadMesh.Vertex a, RoadMesh.Vertex b, RoadMesh.Vertex c) {
        double ux = b.x() - a.x(), uy = b.y() - a.y(), uz = b.z() - a.z(), vx = c.x() - a.x(), vy = c.y() - a.y(), vz = c.z() - a.z();
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        double n = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (n < 1e-12) return;
        if (ny < 0) { RoadMesh.Vertex t = b; b = c; c = t; nx = -nx; ny = -ny; nz = -nz; }
        out.add(new RoadMesh.Triangle(m, a, b, c, nx / n, ny / n, nz / n));
    }

    /** A side triangle wound counter-clockwise seen from where its normal (nx, 0, nz) points. */
    private static void side(List<RoadMesh.Triangle> out, RoadMesh.Material m, RoadMesh.Vertex a, RoadMesh.Vertex b, RoadMesh.Vertex c, double nx, double nz) {
        double ux = b.x() - a.x(), uy = b.y() - a.y(), uz = b.z() - a.z(), vx = c.x() - a.x(), vy = c.y() - a.y(), vz = c.z() - a.z();
        double cx = uy * vz - uz * vy, cz = ux * vy - uy * vx;
        if (Math.abs(cx) + Math.abs(cz) < 1e-12) return;
        if (cx * nx + cz * nz < 0) { RoadMesh.Vertex t = b; b = c; c = t; }
        out.add(new RoadMesh.Triangle(m, a, b, c, nx, 0, nz));
    }
}
