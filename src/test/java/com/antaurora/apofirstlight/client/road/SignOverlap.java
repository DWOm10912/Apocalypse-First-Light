package com.antaurora.apofirstlight.client.road;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Offline check of one guide sign's client mesh (GuideSignParts.build): faces drawn over each other (coplanar quads
 * whose areas overlap, in one stream or across the solid and the legend streams), the in-game look of z-fighting.
 * Town Framework V1, 2026-10-17: the street name blades showed hatched legends in game.
 */
public final class SignOverlap {
    private SignOverlap() {}

    /** A line per finding kind (counts and the first example), empty: none. */
    public static List<String> check(String bone) {
        GuideSignParts.Local l = GuideSignParts.build(bone, new float[]{0, 1, 0, 1});
        List<double[][]> solid = quads(l.solid()), over = quads(l.overlay());
        List<String> out = new ArrayList<>();
        report(out, "solid with solid", solid, solid, true);
        report(out, "legend with legend", over, over, true);
        report(out, "legend with solid", over, solid, false);
        return out;
    }

    private static void report(List<String> out, String what, List<double[][]> a, List<double[][]> b, boolean same) {
        int n = 0;
        String first = null;
        for (int i = 0; i < a.size(); i++)
            for (int j = same ? i + 1 : 0; j < b.size(); j++) {
                double[][] p = a.get(i), q = b.get(j);
                double[] np = normal(p), nq = normal(q);
                if (np == null || nq == null) continue;
                double dot = np[0] * nq[0] + np[1] * nq[1] + np[2] * nq[2];
                // back to back faces are never both seen (culled), only faces turned the same way z-fight
                if (dot < 0.9999) continue;
                double dp = np[0] * p[0][0] + np[1] * p[0][1] + np[2] * p[0][2], dq = np[0] * q[0][0] + np[1] * q[0][1] + np[2] * q[0][2];
                if (Math.abs(dp - dq) > 5e-4) continue;
                double area = overlap(p, q, np);
                if (area < 1e-6) continue;
                n++;
                if (first == null)
                    first = String.format(Locale.ROOT, "quad %d %s and %d %s, %s, %.0f mm apart, %.1f cm2", i, centre(p), j, centre(q),
                            dot > 0 ? "same facing" : "back to back", Math.abs(dp - dq) * 1000, area * 1e4);
            }
        if (n > 0) out.add(what + ": " + n + " overlapping pairs, e.g. " + first);
    }

    private static List<double[][]> quads(float[] v) {
        List<double[][]> out = new ArrayList<>();
        int s = RoadClient.STRIDE;
        for (int i = 0; i + 4 * s <= v.length; i += 4 * s) {
            double[][] q = new double[4][];
            for (int k = 0; k < 4; k++) q[k] = new double[]{v[i + k * s], v[i + k * s + 1], v[i + k * s + 2]};
            out.add(q);
        }
        return out;
    }

    private static double[] normal(double[][] q) {
        double nx = 0, ny = 0, nz = 0;
        for (int k = 0; k < 4; k++) {
            double[] a = q[k], b = q[(k + 1) % 4];
            nx += (a[1] - b[1]) * (a[2] + b[2]);
            ny += (a[2] - b[2]) * (a[0] + b[0]);
            nz += (a[0] - b[0]) * (a[1] + b[1]);
        }
        double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
        return l < 1e-9 ? null : new double[]{nx / l, ny / l, nz / l};
    }

    private static String centre(double[][] q) {
        double x = 0, y = 0, z = 0;
        for (double[] p : q) { x += p[0] / 4; y += p[1] / 4; z += p[2] / 4; }
        return String.format(Locale.ROOT, "(%.3f %.3f %.3f)", x, y, z);
    }

    /** The overlapping area of two coplanar convex quads (repeated vertices allowed), in their plane. */
    private static double overlap(double[][] p, double[][] q, double[] n) {
        // a basis of the plane
        double[] u = Math.abs(n[0]) < 0.9 ? new double[]{1, 0, 0} : new double[]{0, 1, 0};
        double[] e1 = cross(n, u), e2;
        double l = Math.sqrt(dot(e1, e1));
        e1 = new double[]{e1[0] / l, e1[1] / l, e1[2] / l};
        e2 = cross(n, e1);
        List<double[]> a = flat(p, e1, e2), b = flat(q, e1, e2);
        if (a.size() < 3 || b.size() < 3) return 0;
        List<double[]> c = a;
        for (int i = 0; i < b.size() && c.size() >= 3; i++) c = clip(c, b.get(i), b.get((i + 1) % b.size()));
        return c.size() < 3 ? 0 : Math.abs(area(c));
    }

    private static List<double[]> flat(double[][] q, double[] e1, double[] e2) {
        List<double[]> out = new ArrayList<>();
        for (double[] p : q) {
            double[] f = {dot(p, e1), dot(p, e2)};
            if (!out.isEmpty() && Math.hypot(f[0] - out.get(out.size() - 1)[0], f[1] - out.get(out.size() - 1)[1]) < 1e-7) continue;
            out.add(f);
        }
        if (out.size() > 1 && Math.hypot(out.get(0)[0] - out.get(out.size() - 1)[0], out.get(0)[1] - out.get(out.size() - 1)[1]) < 1e-7) out.remove(out.size() - 1);
        if (out.size() >= 3 && area(out) < 0) java.util.Collections.reverse(out);
        return out;
    }

    /** Sutherland-Hodgman: the part of a counter-clockwise polygon left of edge a -> b. */
    private static List<double[]> clip(List<double[]> poly, double[] a, double[] b) {
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < poly.size(); i++) {
            double[] p = poly.get(i), q = poly.get((i + 1) % poly.size());
            double sp = side(a, b, p), sq = side(a, b, q);
            if (sp >= 0) out.add(p);
            if (sp * sq < 0) {
                double t = sp / (sp - sq);
                out.add(new double[]{p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t});
            }
        }
        return out;
    }

    private static double side(double[] a, double[] b, double[] p) { return (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0]); }

    private static double area(List<double[]> c) {
        double s = 0;
        for (int i = 0; i < c.size(); i++) {
            double[] a = c.get(i), b = c.get((i + 1) % c.size());
            s += a[0] * b[1] - b[0] * a[1];
        }
        return s / 2;
    }

    private static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    private static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
}
