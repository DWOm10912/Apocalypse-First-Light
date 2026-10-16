package com.antaurora.apofirstlight.worldgen.network.road;

import java.util.Arrays;

/**
 * The horizontal alignment of a planned road (C3; docs/worldgen/federal_highways_c3.md, 4): a uniform cubic B-spline
 * over the planner's control polyline (RoutePolyline, about 40 m spacing), with natural ends (a phantom control point
 * mirrored through each end, so the curve starts and ends exactly on the end points with zero curvature).
 * Continuous curvature everywhere (no tangent / arc joins). Stations are arc length from 0 to {@link #length}; beyond
 * the ends the alignment continues straight along the end tangents (the end tapers of the earthworks).
 * <p>
 * Pure Java, immutable, deterministic: the arc-length table and every query depend only on the control points, never
 * on chunks or load order. Lateral offset u is positive to the right of the direction of travel: (-tz, tx), the R4
 * convention; signed curvature is positive when the road turns towards +u.
 * <p>
 * A trimmed curve (C3.3a: a route stopped short of a junction or a town) is the station window [from, to] of the full
 * spline, renumbered 0 .. to - from; its straight extensions continue from the trimmed ends.
 */
public final class RoadCurve {
    private static final int SUB = 32;   // arc-length table subdivisions per span
    private final double[] px, pz;        // control points with the two phantom ends: n + 2
    private final int spans;
    private final double[] arc;           // spans * SUB + 1 cumulative lengths
    public final double length;
    /** Station of this curve's 0 on the full spline (the start trim), and the full spline's length. */
    public final double offset, fullLength;
    private final double[] ctrlX, ctrlZ;
    // projection index: a sample every STEP m, bucketed by BUCKET m cells
    private static final double STEP = 2, BUCKET = 32;
    private final double[] sx, sz;
    private final int bx0, bz0, nbx, nbz;
    private final int[] bucketStart, bucketSample;

    public RoadCurve(double[] xs, double[] zs) { this(xs, zs, 0, Double.POSITIVE_INFINITY); }

    /** The window [from, to] of the spline through the control points (to clamped to its length). */
    public RoadCurve(double[] xs, double[] zs, double from, double to) {
        int n = xs.length;
        if (n < 2 || zs.length != n) throw new IllegalArgumentException("ROAD_CURVE_POINTS");
        for (int i = 0; i < n; i++) if (!Double.isFinite(xs[i]) || !Double.isFinite(zs[i])) throw new IllegalArgumentException("ROAD_CURVE_NON_FINITE");
        ctrlX = xs.clone();
        ctrlZ = zs.clone();
        px = new double[n + 2];
        pz = new double[n + 2];
        for (int i = 0; i < n; i++) { px[i + 1] = xs[i]; pz[i + 1] = zs[i]; }
        px[0] = 2 * xs[0] - xs[1];
        pz[0] = 2 * zs[0] - zs[1];
        px[n + 1] = 2 * xs[n - 1] - xs[n - 2];
        pz[n + 1] = 2 * zs[n - 1] - zs[n - 2];
        spans = n - 1;
        arc = new double[spans * SUB + 1];
        for (int sp = 0; sp < spans; sp++)
            for (int k = 0; k < SUB; k++) {
                double a = (double) k / SUB, b = (double) (k + 1) / SUB;
                arc[sp * SUB + k + 1] = arc[sp * SUB + k] + simpson(sp, a, b);
            }
        fullLength = arc[arc.length - 1];
        offset = Math.max(0, from);
        length = Math.min(fullLength, to) - offset;
        if (!(length > 1e-6)) throw new IllegalArgumentException("ROAD_CURVE_ZERO_LENGTH");
        int m = (int) Math.ceil(length / STEP) + 1;
        sx = new double[m];
        sz = new double[m];
        double[] f = new double[5];
        double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int i = 0; i < m; i++) {
            frame(Math.min(length, i * STEP), f);
            sx[i] = f[0];
            sz[i] = f[1];
            minX = Math.min(minX, f[0]); maxX = Math.max(maxX, f[0]); minZ = Math.min(minZ, f[1]); maxZ = Math.max(maxZ, f[1]);
        }
        bx0 = (int) Math.floor(minX / BUCKET);
        bz0 = (int) Math.floor(minZ / BUCKET);
        nbx = (int) Math.floor(maxX / BUCKET) - bx0 + 1;
        nbz = (int) Math.floor(maxZ / BUCKET) - bz0 + 1;
        int[] count = new int[nbx * nbz + 1];
        for (int i = 0; i < m; i++) count[bucket(sx[i], sz[i]) + 1]++;
        for (int k = 0; k < nbx * nbz; k++) count[k + 1] += count[k];
        bucketStart = count.clone();
        bucketSample = new int[m];
        int[] at = Arrays.copyOf(count, count.length);
        for (int i = 0; i < m; i++) bucketSample[at[bucket(sx[i], sz[i])]++] = i;
    }

    private int bucket(double x, double z) {
        return ((int) Math.floor(z / BUCKET) - bz0) * nbx + ((int) Math.floor(x / BUCKET) - bx0);
    }

    public double[] controlX() { return ctrlX.clone(); }

    public double[] controlZ() { return ctrlZ.clone(); }

    // ------------------------------------------------------------------ the spline
    private double speed(int sp, double t) {
        double dx = d1(px, sp, t), dz = d1(pz, sp, t);
        return Math.sqrt(dx * dx + dz * dz);
    }

    private double simpson(int sp, double a, double b) {
        return (b - a) / 6 * (speed(sp, a) + 4 * speed(sp, (a + b) / 2) + speed(sp, b));
    }

    /** Position of span sp (control points sp .. sp + 3 of the padded array) at t in [0, 1]. */
    private static double pos(double[] p, int sp, double t) {
        double u = 1 - t, t2 = t * t, t3 = t2 * t;
        return (u * u * u * p[sp] + (3 * t3 - 6 * t2 + 4) * p[sp + 1] + (-3 * t3 + 3 * t2 + 3 * t + 1) * p[sp + 2] + t3 * p[sp + 3]) / 6;
    }

    private static double d1(double[] p, int sp, double t) {
        double u = 1 - t;
        return (-3 * u * u * p[sp] + (9 * t * t - 12 * t) * p[sp + 1] + (-9 * t * t + 6 * t + 3) * p[sp + 2] + 3 * t * t * p[sp + 3]) / 6;
    }

    private static double d2(double[] p, int sp, double t) {
        return (6 * (1 - t) * p[sp] + (18 * t - 12) * p[sp + 1] + (-18 * t + 6) * p[sp + 2] + 6 * t * p[sp + 3]) / 6;
    }

    /** Span and parameter of a full-spline station (0 <= s <= fullLength): table search, then Newton steps on the true arc length. */
    private double param(double s, int[] spanOut) {
        s = Math.max(0, Math.min(fullLength, s));
        int k = Arrays.binarySearch(arc, s);
        if (k < 0) k = -k - 2;
        k = Math.max(0, Math.min(arc.length - 2, k));
        int sp = k / SUB;
        double a = (double) (k % SUB) / SUB, lo = a, hi = a + 1.0 / SUB;
        double t = a + (s - arc[k]) / Math.max(1e-12, arc[k + 1] - arc[k]) / SUB;
        for (int it = 0; it < 6; it++) {
            double err = arc[k] + simpson(sp, a, t) - s;
            if (Math.abs(err) < 1e-9) break;
            if (err > 0) hi = t; else lo = t;
            double next = t - err / Math.max(1e-9, speed(sp, t));
            t = next > lo && next < hi ? next : (lo + hi) / 2;
        }
        spanOut[0] = sp;
        return t;
    }

    /**
     * Frame at station s into out: {x, z, tx, tz, curvature}. Beyond the ends: the straight extension (curvature 0).
     */
    public void frame(double s, double[] out) { frame(s, out, new int[1]); }

    /** #frame(double, double[]) with the caller's span buffer (no allocation; the same result). */
    public void frame(double s, double[] out, int[] span) {
        if (s < 0 || s > length) {
            double e = s < 0 ? 0 : length;
            frame(e, out, span);
            out[0] += out[2] * (s - e);
            out[1] += out[3] * (s - e);
            out[4] = 0;
            return;
        }
        int[] sp = span;
        double t = param(s + offset, sp);
        double x = pos(px, sp[0], t), z = pos(pz, sp[0], t);
        double vx = d1(px, sp[0], t), vz = d1(pz, sp[0], t), ax = d2(px, sp[0], t), az = d2(pz, sp[0], t);
        double v = Math.sqrt(vx * vx + vz * vz);
        out[0] = x;
        out[1] = z;
        out[2] = vx / v;
        out[3] = vz / v;
        out[4] = (vx * az - vz * ax) / (v * v * v);
    }

    public double curvature(double s) {
        double[] f = new double[5];
        frame(s, f);
        return f[4];
    }

    /** World XZ of (station, lateral). */
    public double[] point(double s, double u) {
        double[] f = new double[5];
        frame(s, f);
        return new double[]{f[0] - f[3] * u, f[1] + f[2] * u};
    }

    /**
     * Station and lateral offset of a world point within maxDist of the centreline (stations may run up to `ext` beyond
     * either end, on the straight extensions); null farther out. Unique wherever maxDist is below the curve's radius.
     */
    public double[] local(double x, double z, double maxDist, double ext) {
        Scratch k = new Scratch();
        return local(x, z, maxDist, ext, k) ? new double[]{k.s, k.u} : null;
    }

    /** #local(double, double, double, double) into k.s / k.u, false where that gives null (no allocation; the same result). */
    public boolean local(double x, double z, double maxDist, double ext, Scratch k) {
        int r = (int) Math.ceil((maxDist + STEP) / BUCKET);
        int cx = (int) Math.floor(x / BUCKET) - bx0, cz = (int) Math.floor(z / BUCKET) - bz0;
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (int bz = Math.max(0, cz - r); bz <= Math.min(nbz - 1, cz + r); bz++)
            for (int bx = Math.max(0, cx - r); bx <= Math.min(nbx - 1, cx + r); bx++) {
                int key = bz * nbx + bx;
                for (int q = bucketStart[key]; q < bucketStart[key + 1]; q++) {
                    int i = bucketSample[q];
                    double d = (x - sx[i]) * (x - sx[i]) + (z - sz[i]) * (z - sz[i]);
                    if (d < bd || (d == bd && i < best)) { bd = d; best = i; }
                }
            }
        if (best < 0) {
            // beyond the indexed curve: only the straight end extensions can still be near
            if (ext <= 0) return false;
            best = Math.hypot(x - sx[0], z - sz[0]) <= Math.hypot(x - sx[sx.length - 1], z - sz[sz.length - 1]) ? 0 : sx.length - 1;
            if (Math.min(Math.hypot(x - sx[0], z - sz[0]), Math.hypot(x - sx[sx.length - 1], z - sz[sz.length - 1])) > maxDist + ext) return false;
        }
        double s = Math.min(length, best * STEP);
        double[] f = k.f;
        for (int it = 0; it < 10; it++) {
            frame(s, f, k.span);
            double along = (x - f[0]) * f[2] + (z - f[1]) * f[3];
            double next = Math.max(-ext, Math.min(length + ext, s + along));
            if (Math.abs(next - s) < 1e-9) { s = next; break; }
            s = next;
        }
        frame(s, f, k.span);
        double along = (x - f[0]) * f[2] + (z - f[1]) * f[3];
        if (Math.abs(along) > 0.01) return false;
        double u = -(x - f[0]) * f[3] + (z - f[1]) * f[2];
        if (Math.abs(u) > maxDist) return false;
        k.s = s;
        k.u = u;
        return true;
    }

    /** Reusable buffers of the allocation-free lookups (one per thread: the road ground query, RoadSurfaceQuery). */
    public static final class Scratch {
        final double[] f = new double[5];
        final int[] span = new int[1];
        /** #local's result: station and lateral offset. */
        public double s, u;
    }

    /** Smallest radius along the curve, sampled every 2 m (infinite when straight). */
    public double minRadius() {
        double k = 0;
        double[] f = new double[5];
        for (double s = 0; s <= length; s += 2) { frame(s, f); k = Math.max(k, Math.abs(f[4])); }
        return k < 1e-12 ? Double.POSITIVE_INFINITY : 1 / k;
    }
}
