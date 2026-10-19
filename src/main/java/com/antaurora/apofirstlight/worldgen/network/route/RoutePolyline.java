package com.antaurora.apofirstlight.worldgen.network.route;

import java.util.Arrays;

/**
 * Polyline tools of the national road planner (C3.1): the A* cell path to a smooth control polyline. The C1 corridor
 * steps (NationalRoadPlan: Douglas-Peucker, Chaikin, resampling, curvature relaxation), with the tolerance and the
 * radius taken from the road class, and StrictMath, so a route never depends on the CPU's math library.
 */
public final class RoutePolyline {
    private RoutePolyline() {}

    /** Control spacing of a planned route, m (the B-spline of RouteCurve runs through these). */
    public static final double SPACING = 40;

    /**
     * Cell path to the control polyline: from (ax, az) through the cell centres to (bx, bz); simplified within tol,
     * Chaikin-smoothed five times, resampled every SPACING m, and relaxed until every vertex's local radius is at
     * least 1.1 x rmin (ends fixed).
     */
    public static double[][] smooth(RouteGrid g, int[] cells, double ax, double az, double bx, double bz, double tol, double rmin) {
        int n = cells.length;
        double[] xs = new double[n], zs = new double[n];
        for (int k = 0; k < n; k++) { xs[k] = g.cx(cells[k] % g.nx); zs[k] = g.cz(cells[k] / g.nx); }
        xs[0] = ax; zs[0] = az;
        xs[n - 1] = bx; zs[n - 1] = bz;
        if (n == 1) return new double[][]{{ax, bx}, {az, bz}};
        double[][] p = simplify(xs, zs, tol);
        for (int it = 0; it < 5; it++) p = chaikin(p[0], p[1]);
        p = resample(p[0], p[1], SPACING);
        relax(p[0], p[1], 1.1 * rmin);
        return p;
    }

    /** Spreads every bend tighter than rmin over more vertices (local Laplacian moves, end points fixed). */
    static void relax(double[] xs, double[] zs, double rmin) { relax(xs, zs, 0, xs.length - 1, rmin); }

    /**
     * #relax over the vertices i0 .. i1 alone (those two fixed): County road G3's mouth, held to a larger radius than the
     * rest of the road (RoadCounty).
     */
    public static void relax(double[] xs, double[] zs, int i0, int i1, double rmin) {
        for (int it = 0; it < 6000; it++) {
            boolean moved = false;
            for (int i = Math.max(1, i0 + 1); i < Math.min(xs.length - 1, i1); i++) {
                if (radius(xs, zs, i) >= rmin) continue;
                xs[i] += 0.5 * (0.5 * (xs[i - 1] + xs[i + 1]) - xs[i]);
                zs[i] += 0.5 * (0.5 * (zs[i - 1] + zs[i + 1]) - zs[i]);
                moved = true;
            }
            if (!moved) return;
        }
    }

    /** Local radius at vertex i: mean adjacent segment length over the turn angle (infinite when straight). */
    public static double radius(double[] xs, double[] zs, int i) {
        double ax = xs[i] - xs[i - 1], az = zs[i] - zs[i - 1], bx = xs[i + 1] - xs[i], bz = zs[i + 1] - zs[i];
        double turn = Math.abs(StrictMath.atan2(ax * bz - az * bx, ax * bx + az * bz));
        return turn < 1e-9 ? Double.POSITIVE_INFINITY : 0.5 * (Math.hypot(ax, az) + Math.hypot(bx, bz)) / turn;
    }

    public static double length(double[] xs, double[] zs) {
        double l = 0;
        for (int i = 1; i < xs.length; i++) l += Math.hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
        return l;
    }

    static double[][] simplify(double[] xs, double[] zs, double tol) {
        boolean[] keep = new boolean[xs.length];
        keep[0] = keep[xs.length - 1] = true;
        dp(xs, zs, 0, xs.length - 1, tol, keep);
        int n = 0;
        for (boolean k : keep) if (k) n++;
        double[] ox = new double[n], oz = new double[n];
        for (int i = 0, j = 0; i < xs.length; i++) if (keep[i]) { ox[j] = xs[i]; oz[j] = zs[i]; j++; }
        return new double[][]{ox, oz};
    }

    private static void dp(double[] xs, double[] zs, int a, int b, double tol, boolean[] keep) {
        if (b <= a + 1) return;
        double dx = xs[b] - xs[a], dz = zs[b] - zs[a], l = Math.hypot(dx, dz);
        int best = -1;
        double bd = tol;
        for (int i = a + 1; i < b; i++) {
            double d = l < 1e-9 ? Math.hypot(xs[i] - xs[a], zs[i] - zs[a]) : Math.abs((xs[i] - xs[a]) * dz - (zs[i] - zs[a]) * dx) / l;
            if (d > bd) { bd = d; best = i; }
        }
        if (best < 0) return;
        keep[best] = true;
        dp(xs, zs, a, best, tol, keep);
        dp(xs, zs, best, b, tol, keep);
    }

    static double[][] chaikin(double[] xs, double[] zs) {
        int n = xs.length;
        if (n < 3) return new double[][]{xs, zs};
        double[] ox = new double[2 * (n - 1)], oz = new double[2 * (n - 1)];
        int k = 0;
        ox[k] = xs[0]; oz[k++] = zs[0];
        for (int i = 0; i < n - 1; i++) {
            if (i > 0) { ox[k] = 0.75 * xs[i] + 0.25 * xs[i + 1]; oz[k++] = 0.75 * zs[i] + 0.25 * zs[i + 1]; }
            if (i < n - 2) { ox[k] = 0.25 * xs[i] + 0.75 * xs[i + 1]; oz[k++] = 0.25 * zs[i] + 0.75 * zs[i + 1]; }
        }
        ox[k] = xs[n - 1]; oz[k++] = zs[n - 1];
        return new double[][]{Arrays.copyOf(ox, k), Arrays.copyOf(oz, k)};
    }

    static double[][] resample(double[] xs, double[] zs, double step) {
        double total = length(xs, zs);
        int n = Math.max(2, (int) Math.round(total / step) + 1);
        double[] ox = new double[n], oz = new double[n];
        double seg = total / (n - 1), run = 0;
        int j = 1;
        ox[0] = xs[0]; oz[0] = zs[0];
        for (int k = 1; k < n - 1; k++) {
            double target = k * seg;
            while (j < xs.length - 1 && run + Math.hypot(xs[j] - xs[j - 1], zs[j] - zs[j - 1]) < target) {
                run += Math.hypot(xs[j] - xs[j - 1], zs[j] - zs[j - 1]);
                j++;
            }
            double l = Math.hypot(xs[j] - xs[j - 1], zs[j] - zs[j - 1]), t = l < 1e-9 ? 0 : (target - run) / l;
            ox[k] = xs[j - 1] + (xs[j] - xs[j - 1]) * t;
            oz[k] = zs[j - 1] + (zs[j] - zs[j - 1]) * t;
        }
        ox[n - 1] = xs[xs.length - 1]; oz[n - 1] = zs[zs.length - 1];
        return new double[][]{ox, oz};
    }

    /** Sub-polyline between two arc-length positions (both clamped), resampled at the original spacing. */
    public static double[][] cut(double[] xs, double[] zs, double from, double to) {
        double total = length(xs, zs);
        from = Math.max(0, Math.min(total, from));
        to = Math.max(from, Math.min(total, to));
        int n = Math.max(2, (int) Math.round((to - from) / SPACING) + 1);
        double[] ox = new double[n], oz = new double[n];
        for (int k = 0; k < n; k++) {
            double[] p = at(xs, zs, from + (to - from) * k / (n - 1));
            ox[k] = p[0];
            oz[k] = p[1];
        }
        return new double[][]{ox, oz};
    }

    /** Point at an arc-length position along a polyline. */
    public static double[] at(double[] xs, double[] zs, double s) {
        double run = 0;
        for (int i = 1; i < xs.length; i++) {
            double l = Math.hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
            if (run + l >= s || i == xs.length - 1) {
                double t = l < 1e-9 ? 0 : Math.max(0, Math.min(1, (s - run) / l));
                return new double[]{xs[i - 1] + (xs[i] - xs[i - 1]) * t, zs[i - 1] + (zs[i] - zs[i - 1]) * t};
            }
            run += l;
        }
        return new double[]{xs[0], zs[0]};
    }
}
