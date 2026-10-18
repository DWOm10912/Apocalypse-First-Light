package com.antaurora.apofirstlight.worldgen.network.road;

import java.util.ArrayList;
import java.util.List;

/**
 * A T junction of two state roads (C3.3b; docs/worldgen/federal_junctions_c3_3b.md 3): the minor road's end line (its
 * design end, RoadNetworkBuild sets it) joined to the host's paved edge by one paved pad, the two corners rounded with
 * RADIUS fillets (curb returns). Pure Java over the two designs, so the server's works, the chunk pass, the client's mesh
 * and the ground query (RoadSurfaceQuery) build the same pad.
 * <ul>
 *   <li>the host keeps its own design; the pad meets its paved edge exactly (its height there is the host's pavement);</li>
 *   <li>the pad meets the minor's end line exactly (the minor's pavement there, crown and cross slope);</li>
 *   <li>in between one height function (#height): the host edge's height carried out at the landing grade along the
 *   minor's axis, and the minor's end carried on at the same grade, blended by the distances to the two (each one exact
 *   on its own line, and the two equal on the centreline: it keeps the landing's grade up to the host's edge however
 *   skewed the minor meets a host on a grade); the build gives the minor a landing of at most LANDING_GRADE over the last
 *   LANDING before the host's edge;</li>
 *   <li>outline: the end line, the minor's paved edges on from the end line to the fillets' tangent points T2, the two
 *   arcs to their tangent points T1 on the host's edge, the host's edge between them; the arcs and the minor's edges are
 *   free edges (bevels and the roadside slopes beyond them, RoadWorks).</li>
 * </ul>
 * The minor's centreline and edges beyond its end are its straight extension (RoadCurve).
 */
public final class Junction {
    /** Fillet (curb return) radius, m: a large truck turns right without leaving the pavement. */
    public static final double RADIUS = 15;
    /** The end line lies at least this beyond both T2 (then on the 2 m lattice), m. */
    public static final double END_MARGIN = 1;
    /** Stop bar: from the host's paved edge, across the approach lane from STOP_IN off the centreline, m. */
    public static final double STOP_NEAR = 1.2, STOP_FAR = 1.8, STOP_IN = 0.2;
    /** The minor's landing before the host's edge: length and the steepest grade, m and m/m. */
    public static final double LANDING = 30, LANDING_GRADE = 0.02;
    /**
     * Sight triangles (AASHTO case B1, left turn from a stop at 90 km/h: 7.5 s, about 190 m): the driver's eye this far
     * behind the stop bar, on the approach lane's centre; the host's oncoming lanes this far each way, m. No plants grow
     * in them (RoadWorks#bare).
     */
    public static final double SIGHT_BACK = 4.4, SIGHT_ALONG = 190;
    /** Outline sampling along the arcs and the minor's edges, m. */
    static final double STEP = 1;
    /** How far along the minor (both ways from its end) the geometry is searched; projection reach onto the host, m. */
    static final double SCAN = 80, REACH = 120, EXT = 40;
    /** The least sine of the minor's angle to the host #height divides by (the build realigns under 70 degrees). */
    static final double SKEW_MIN = 0.3;

    public final RoadDesign host, minor;
    /** Which end of the minor joins the host (its end, or its start). */
    public final boolean atEnd;
    /** +1: the minor's stations grow toward the host (atEnd); -1: they fall. */
    public final int dir;
    /** The host's side (sign of its lateral offset) the minor joins on. */
    public final int side;
    /** The minor's station of its end line (its design end), of its centreline on the host's edge, the host's station there. */
    public final double sEnd, sEdge, sMouth;
    /** Pad height rise per metre away from the host along the minor (#height). */
    private double gAway;
    /** The minor's axis toward the host (its straight extension's direction), unit (x, z). */
    private double ax, az;
    private final double[][] centre = new double[2][];
    /** Per minor side (0: u < 0, 1: u > 0): the host's station of T1, the minor's station of T2. */
    private final double[] t1 = new double[2], t2 = new double[2];
    private double[] px, pz;
    private boolean[] free;
    private int[] tris;
    private double bx0, bz0, bx1, bz1;
    private double[][] sight;

    private Junction(RoadDesign host, RoadDesign minor, boolean atEnd, int side, double sEdge, double sMouth) {
        this.host = host;
        this.minor = minor;
        this.atEnd = atEnd;
        this.dir = atEnd ? 1 : -1;
        this.sEnd = atEnd ? minor.length : 0;
        this.side = side;
        this.sEdge = sEdge;
        this.sMouth = sMouth;
    }

    /**
     * The geometry only (the build: an untrimmed minor whose end lies on the host's centreline): the mouth, the host's
     * edge crossing, T1 and T2; null where it cannot be found.
     */
    static Junction geometry(RoadDesign host, RoadDesign minor, boolean atEnd) {
        Junction j = new Junction(host, minor, atEnd, 0, 0, 0);
        double u0 = j.hostU(-40, 0);
        if (Double.isNaN(u0) || Math.abs(u0) < host.section.edge + 1) return null;
        int side = u0 > 0 ? 1 : -1;
        Junction g = new Junction(host, minor, atEnd, side, 0, 0);
        double eh = host.section.edge, em = minor.section.edge;
        double te = g.cross(0, eh), tm = g.cross(0, 0);
        if (Double.isNaN(te) || Double.isNaN(tm)) return null;
        double[] pm = minor.point(g.sEnd + g.dir * tm, 0);
        double[] lm = host.curve.local(pm[0], pm[1], REACH, EXT);
        if (lm == null) return null;
        Junction out = new Junction(host, minor, atEnd, side, g.sEnd + g.dir * te, lm[0]);
        for (int k = 0; k < 2; k++) {
            int sm = k == 0 ? -1 : 1;
            double tc = out.cross(sm * (em + RADIUS), eh + RADIUS);
            if (Double.isNaN(tc)) return null;
            double s2 = out.sEnd + out.dir * tc;
            double[] c = minor.point(s2, sm * (em + RADIUS));
            double[] l = host.curve.local(c[0], c[1], REACH, EXT);
            if (l == null || l[0] < 0 || l[0] > host.length) return null;
            // T2 before the host's edge, toward the minor
            if (out.dir * (out.sEdge - s2) < 0.5) return null;
            out.centre[k] = c;
            out.t1[k] = l[0];
            out.t2[k] = s2;
        }
        return out;
    }

    /** The junction at an end of a built minor (its end line at its design end), or null where it cannot be built. */
    public static Junction of(RoadDesign host, RoadDesign minor, boolean atEnd) {
        Junction j = geometry(host, minor, atEnd);
        if (j == null) return null;
        // the end line lies beyond both T2 (the build puts it there; the straight extension may move T2 a little)
        for (int k = 0; k < 2; k++) if (j.dir * (j.t2[k] - j.sEnd) < -0.5) return null;
        j.t2[0] = j.dir * (j.t2[0] - j.sEnd) < 0 ? j.sEnd : j.t2[0];
        j.t2[1] = j.dir * (j.t2[1] - j.sEnd) < 0 ? j.sEnd : j.t2[1];
        double qe = j.dir * (j.sEdge - j.sEnd);
        if (qe < 1) return null;
        double[] pe = minor.point(j.sEdge, 0);
        double[] le = host.curve.local(pe[0], pe[1], REACH, EXT);
        if (le == null) return null;
        double ec = host.pavementY(clampHost(host, le[0]), j.side * host.section.edge);
        j.gAway = (minor.pavementY(j.sEnd, 0) - ec) / qe;
        double[] ta = BridgeMesh.tangent(minor, j.sEnd);
        j.ax = j.dir * ta[0];
        j.az = j.dir * ta[1];
        j.outline();
        j.sightTriangles();
        return j;
    }

    private static double clampHost(RoadDesign host, double s) { return Math.max(0, Math.min(host.length, s)); }

    /** The host's lateral offset of the minor's point t from its end toward the host at lateral u; NaN off the host's reach. */
    private double hostU(double t, double u) {
        double[] p = minor.point(sEnd + dir * t, u);
        double[] l = host.curve.local(p[0], p[1], REACH, EXT);
        return l == null ? Double.NaN : l[1];
    }

    /**
     * The smallest t (from the end toward the host, -SCAN .. SCAN) where the minor's line at lateral u comes within target of
     * the host's centreline on this side (side * hostU falls to target), coming from the far side; NaN when never.
     */
    double cross(double u, double target) {
        double prev = Double.NaN, tp = 0;
        for (double t = -SCAN; t <= SCAN + 1e-9; t += 1) {
            double v = side * hostU(t, u) - target;
            if (Double.isNaN(v)) { prev = Double.NaN; continue; }
            if (v <= 0) {
                if (Double.isNaN(prev)) return Double.NaN;
                double a = tp, b = t;
                for (int it = 0; it < 40; it++) {
                    double m = (a + b) / 2, vm = side * hostU(m, u) - target;
                    if (Double.isNaN(vm) || vm > 0) a = m; else b = m;
                }
                return (a + b) / 2;
            }
            prev = v;
            tp = t;
        }
        return Double.NaN;
    }

    /** The minor's station where its line at lateral u lies d beyond the host's paved edge, or NaN. */
    public double stationAt(double u, double d) {
        double t = cross(u, host.section.edge + d);
        return Double.isNaN(t) ? Double.NaN : sEnd + dir * t;
    }

    /** T1 (host station) and T2 (minor station) of the fillet on the minor's side k (0: u < 0, 1: u > 0). */
    public double t1(int k) { return t1[k]; }

    public double t2(int k) { return t2[k]; }

    /** The host's station range of the mouth (between the two T1). */
    public double mouthFrom() { return Math.min(t1[0], t1[1]); }

    public double mouthTo() { return Math.max(t1[0], t1[1]); }

    // ------------------------------------------------------------------ outline

    private void outline() {
        List<double[]> v = new ArrayList<>();
        List<Boolean> f = new ArrayList<>();
        double em = minor.section.edge, eh = host.section.edge;
        // the minor's edge (u < 0) from the end line to T2
        double len0 = dir * (t2[0] - sEnd);
        for (double q = 0; q < len0 - 1e-6; q += STEP) { v.add(minor.point(sEnd + dir * q, -em)); f.add(true); }
        double[] a2 = minor.point(t2[0], -em), a1 = host.point(t1[0], side * eh);
        v.add(a2);
        f.add(true);
        arc(v, f, centre[0], a2, a1, true);
        // the host's edge from T1 (u < 0 side) to T1 (u > 0 side), on the host mesh's stations
        v.add(a1);
        f.add(false);
        double lo = Math.min(t1[0], t1[1]), hi = Math.max(t1[0], t1[1]);
        List<Double> hs = new ArrayList<>();
        for (Double s : RoadMesh.stations(host.length, lo, hi)) if (s > lo + 1e-6 && s < hi - 1e-6) hs.add(s);
        if (t1[0] > t1[1]) java.util.Collections.reverse(hs);
        for (double s : hs) { v.add(host.point(s, side * eh)); f.add(false); }
        double[] b1 = host.point(t1[1], side * eh), b2 = minor.point(t2[1], em);
        v.add(b1);
        f.add(true);
        arc(v, f, centre[1], b1, b2, true);
        // the minor's edge (u > 0) back to the end line
        double len1 = dir * (t2[1] - sEnd);
        v.add(b2);
        f.add(true);
        for (double q = Math.ceil(len1 / STEP - 1) * STEP; q > 1e-6; q -= STEP) if (q < len1 - 1e-6) { v.add(minor.point(sEnd + dir * q, em)); f.add(true); }
        // the end line, on the minor mesh's lateral breaks (RoadMesh across)
        for (double u : new double[]{em, 4.0, minor.section.lanesOut, 0, -minor.section.lanesOut, -4.0}) {
            if (Math.abs(u) > em + 1e-9) continue;
            v.add(minor.point(sEnd, u));
            f.add(false);
        }
        // drop repeated points
        List<double[]> pv = new ArrayList<>();
        List<Boolean> pf = new ArrayList<>();
        for (int i = 0; i < v.size(); i++) {
            double[] p = v.get(i), q = pv.isEmpty() ? null : pv.get(pv.size() - 1);
            if (q != null && Math.hypot(p[0] - q[0], p[1] - q[1]) < 0.02) { pf.set(pf.size() - 1, pf.get(pf.size() - 1) && f.get(i)); continue; }
            pv.add(p);
            pf.add(f.get(i));
        }
        int n = pv.size();
        px = new double[n];
        pz = new double[n];
        free = new boolean[n];
        bx0 = bz0 = Double.POSITIVE_INFINITY;
        bx1 = bz1 = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            px[i] = pv.get(i)[0];
            pz[i] = pv.get(i)[1];
            free[i] = pf.get(i);
            bx0 = Math.min(bx0, px[i]); bx1 = Math.max(bx1, px[i]); bz0 = Math.min(bz0, pz[i]); bz1 = Math.max(bz1, pz[i]);
        }
        tris = triangulate(px, pz);
    }

    /** The arc around c from a to b (the short way), its inner points; their edges free. */
    private static void arc(List<double[]> v, List<Boolean> f, double[] c, double[] a, double[] b, boolean isFree) {
        double aa = Math.atan2(a[1] - c[1], a[0] - c[0]), ab = Math.atan2(b[1] - c[1], b[0] - c[0]);
        double sweep = ab - aa;
        while (sweep > Math.PI) sweep -= 2 * Math.PI;
        while (sweep <= -Math.PI) sweep += 2 * Math.PI;
        double ra = Math.hypot(a[0] - c[0], a[1] - c[1]), rb = Math.hypot(b[0] - c[0], b[1] - c[1]);
        int n = Math.max(2, (int) Math.ceil(Math.max(ra, rb) * Math.abs(sweep) / STEP));
        for (int i = 1; i < n; i++) {
            double t = (double) i / n, ang = aa + sweep * t, r = ra + (rb - ra) * t;
            v.add(new double[]{c[0] + r * Math.cos(ang), c[1] + r * Math.sin(ang)});
            f.add(isFree);
        }
    }

    /** Ear clipping of a simple polygon: triangles as vertex index triples. */
    static int[] triangulate(double[] x, double[] z) {
        int n = x.length;
        List<Integer> idx = new ArrayList<>(n);
        for (int i = 0; i < n; i++) idx.add(i);
        double area = 0;
        for (int i = 0; i < n; i++) { int j = (i + 1) % n; area += x[i] * z[j] - x[j] * z[i]; }
        int sgn = area > 0 ? 1 : -1;
        int[] out = new int[3 * Math.max(0, n - 2)];
        int k = 0;
        while (idx.size() > 3) {
            int m = idx.size(), best = -1;
            double bestQ = -1;
            for (int i = 0; i < m; i++) {
                int a = idx.get((i + m - 1) % m), b = idx.get(i), c = idx.get((i + 1) % m);
                double cr = (x[b] - x[a]) * (z[c] - z[a]) - (z[b] - z[a]) * (x[c] - x[a]);
                if (cr * sgn <= 1e-9) continue;
                boolean inside = false;
                for (int q : idx) {
                    if (q == a || q == b || q == c) continue;
                    if (inTri(x[q], z[q], x[a], z[a], x[b], z[b], x[c], z[c], sgn)) { inside = true; break; }
                }
                if (inside) continue;
                // the best shaped ear (area over the longest side squared): no slivers where a choice exists
                double l = Math.max(Math.hypot(x[c] - x[a], z[c] - z[a]), Math.max(Math.hypot(x[b] - x[a], z[b] - z[a]), Math.hypot(x[c] - x[b], z[c] - z[b])));
                double q = Math.abs(cr) / (l * l);
                if (q > bestQ) { bestQ = q; best = i; }
            }
            if (best < 0) {
                // only collinear or degenerate corners left: drop the flattest
                double flat = Double.POSITIVE_INFINITY;
                for (int i = 0; i < m; i++) {
                    int a = idx.get((i + m - 1) % m), b = idx.get(i), c = idx.get((i + 1) % m);
                    double cr = Math.abs((x[b] - x[a]) * (z[c] - z[a]) - (z[b] - z[a]) * (x[c] - x[a]));
                    if (cr < flat) { flat = cr; best = i; }
                }
                idx.remove(best);
                continue;
            }
            out[k++] = idx.get((best + m - 1) % m);
            out[k++] = idx.get(best);
            out[k++] = idx.get((best + 1) % m);
            idx.remove(best);
        }
        if (idx.size() == 3) { out[k++] = idx.get(0); out[k++] = idx.get(1); out[k++] = idx.get(2); }
        return java.util.Arrays.copyOf(out, k);
    }

    private static boolean inTri(double x, double z, double ax, double az, double bx, double bz, double cx, double cz, int sgn) {
        double d1 = ((bx - ax) * (z - az) - (bz - az) * (x - ax)) * sgn;
        double d2 = ((cx - bx) * (z - bz) - (cz - bz) * (x - bx)) * sgn;
        double d3 = ((ax - cx) * (z - cz) - (az - cz) * (x - cx)) * sgn;
        return d1 >= -1e-9 && d2 >= -1e-9 && d3 >= -1e-9;
    }

    // ------------------------------------------------------------------ queries

    /** The pad's plan bounds {x0, z0, x1, z1}. */
    public double[] bounds() { return new double[]{bx0, bz0, bx1, bz1}; }

    /** Outline vertices (x, z), and whether the edge from vertex i to i + 1 is free (a bevel). */
    public double[] outlineX() { return px.clone(); }

    public double[] outlineZ() { return pz.clone(); }

    public boolean free(int i) { return free[i]; }

    /** Triangles of the outline (vertex index triples). */
    public int[] triangles() { return tris.clone(); }

    /** Whether (x, z) lies inside the pad (crossing number). */
    public boolean contains(double x, double z) {
        if (x < bx0 || x > bx1 || z < bz0 || z > bz1) return false;
        boolean in = false;
        int n = px.length;
        for (int i = 0, j = n - 1; i < n; j = i++)
            if ((pz[i] > z) != (pz[j] > z) && x < (px[j] - px[i]) * (z - pz[i]) / (pz[j] - pz[i]) + px[i]) in = !in;
        return in;
    }

    /** Whether (x, z) lies within r of the pad's plan bounds (a cheap test). */
    public boolean near(double x, double z, double r) { return x >= bx0 - r && x <= bx1 + r && z >= bz0 - r && z <= bz1 + r; }

    /**
     * The nearest point of the outline to (x, z): {distance, x, z, free (1 or 0)}; free: it lies on a free edge (an arc or
     * the minor's edges).
     */
    public double[] nearest(double x, double z) {
        double best = Double.POSITIVE_INFINITY, bx = 0, bz = 0;
        boolean bf = false;
        int n = px.length;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double ex = px[j] - px[i], ez = pz[j] - pz[i], l2 = ex * ex + ez * ez;
            double t = l2 < 1e-12 ? 0 : Math.max(0, Math.min(1, ((x - px[i]) * ex + (z - pz[i]) * ez) / l2));
            double qx = px[i] + ex * t, qz = pz[i] + ez * t, d = Math.hypot(x - qx, z - qz);
            if (d < best) { best = d; bx = qx; bz = qz; bf = free[i]; }
        }
        return new double[]{best, bx, bz, bf ? 1 : 0};
    }

    /**
     * The pad's pavement height at (x, z): the host's paved edge height carried out at gAway per metre (Hh), the
     * minor's end line height carried on toward the host at the same grade (Hm), blended by the distances q (from the
     * end line toward the host) and d (to the host's edge along the minor's axis): q / (q + d) of the way to Hh. Exact
     * on both lines. Hh is taken where the minor's axis through (x, z) meets the host's edge (the host straight over
     * the few metres), so on the centreline Hh and Hm are one line at gAway: neither the host's grade along its edge nor
     * the skew bends the landing.
     */
    public double height(double x, double z) {
        double eh = host.section.edge, em = minor.section.edge;
        double[] lh = host.curve.local(x, z, REACH, EXT), lm = minor.curve.local(x, z, REACH, SCAN);
        if (lh == null || lm == null) return minor.pavementY(sEnd, 0);
        double sh = clampHost(host, lh[0]);
        double[] th = BridgeMesh.tangent(host, sh);
        // the axis' parts across the host (toward its centre: > 0) and along it; never flatter than SKEW_MIN across
        double across = Math.max(SKEW_MIN, -side * (ax * -th[1] + az * th[0])), along = ax * th[0] + az * th[1];
        double d = Math.max(0, side * lh[1] - eh) / across;
        double hh = host.pavementY(clampHost(host, sh + along * d), side * eh) + gAway * d;
        double q = Math.max(0, dir * (lm[0] - sEnd));
        double hm = minor.pavementY(sEnd, Math.max(-em, Math.min(em, lm[1]))) - gAway * q;
        double w = q + d < 1e-9 ? 1 : q / (q + d);
        return hm + (hh - hm) * w;
    }

    /** The pad's upward unit normal at (x, z) (central differences of #height). */
    public double[] normal(double x, double z) {
        double e = 0.05;
        double gx = (height(x + e, z) - height(x - e, z)) / (2 * e), gz = (height(x, z + e) - height(x, z - e)) / (2 * e);
        double n = Math.sqrt(gx * gx + gz * gz + 1);
        return new double[]{-gx / n, 1 / n, -gz / n};
    }

    /** The landing grade the pad carries (rise per metre away from the host). */
    public double grade() { return gAway; }

    // ------------------------------------------------------------------ sight triangles

    private void sightTriangles() {
        double lane = minor.section.lane / 2, hl = host.section.lane / 2;
        double sStop = stationAt(dir * minor.section.lane / 2, STOP_FAR);
        if (Double.isNaN(sStop)) sStop = sEdge - dir * STOP_FAR;
        double[] eye = minor.point(sStop - dir * SIGHT_BACK, dir * lane);
        sight = new double[2][];
        for (int k = 0; k < 2; k++) {
            // traffic toward the mouth from below (travelling +s, on u > 0) and from above (travelling -s, on u < 0)
            int sg = k == 0 ? -1 : 1;
            double far = clampHost(host, sMouth + sg * SIGHT_ALONG), u = -sg * hl;
            double[] a = host.point(far, u), m = host.point(sMouth, u);
            sight[k] = new double[]{eye[0], eye[1], m[0], m[1], a[0], a[1]};
        }
    }

    /** Whether (x, z) lies in one of the two sight triangles (no plants). */
    public boolean inSight(double x, double z) {
        for (double[] t : sight) if (inTri(x, z, t[0], t[1], t[2], t[3], t[4], t[5], orient(t))) return true;
        return false;
    }

    private static int orient(double[] t) {
        return (t[2] - t[0]) * (t[5] - t[1]) - (t[3] - t[1]) * (t[4] - t[0]) > 0 ? 1 : -1;
    }

    /** The sight triangles' plan bounds {x0, z0, x1, z1}. */
    public double[] sightBounds() {
        double[] b = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (double[] t : sight) for (int i = 0; i < 6; i += 2) {
            b[0] = Math.min(b[0], t[i]); b[2] = Math.max(b[2], t[i]); b[1] = Math.min(b[1], t[i + 1]); b[3] = Math.max(b[3], t[i + 1]);
        }
        return b;
    }
}
