package com.antaurora.apofirstlight.worldgen.network.road;

import java.util.Arrays;

/**
 * The vertical profile of a planned road (C3): design datum E(s) every {@link #DS} m from station 0 to the length,
 * linear between samples (the R4 sample's R4Profile, generalised). E is the height of the class's crown line (R4: the
 * inner edge lines of both carriageways; two-lane classes: the centre line).
 * <p>
 * Design ({@link #design}):
 * <ol>
 *   <li>the ground Gaussian-smoothed and raised, then held to a per-sample grade limit (the class grade, or its
 *   mountain grade where the planner allows it) by forward / backward clamping with light re-smoothing;</li>
 *   <li>lifted over every lower bound (bridge decks over water) with grade-limited approach ramps: the upper envelope of
 *   a grade-limited line and grade-limited cones on the bounds is itself grade-limited;</li>
 *   <li>vertical curves ({@link #vertical}): one smoothing with the class's vertical sigma, then, wherever a crest or
 *   sag is still sharper than the class's K (measured over {@link #K_CHORD} m chords), local smoothing with a growing
 *   sigma until none is (every grade break, ramp foot and bridge crest becomes a rounded curve). The sigma never grows
 *   beyond what the sharpest possible break (full grade up to full grade down) needs for that K, so a profile is never
 *   flattened beyond its curves;</li>
 *   <li>where the vertical curves undercut a bound, the bound is raised by the deficit and steps 2-3 repeat, so the
 *   final lift moves the line by millimetres at most.</li>
 * </ol>
 * Pure Java, StrictMath, deterministic.
 */
public final class RoadProfile {
    public static final double DS = 2.0;
    /** Chord of the K measurement, m (shorter chords would count millimetre steps of the final lift as curves). */
    public static final double K_CHORD = 10;
    private final double[] e;
    public final double length;

    private RoadProfile(double[] e, double length) {
        if (e.length < 2 || Math.abs((e.length - 1) * DS - length) > 1e-6) throw new IllegalArgumentException("ROAD_PROFILE_SAMPLES");
        for (double v : e) if (!Double.isFinite(v)) throw new IllegalArgumentException("ROAD_PROFILE_NON_FINITE");
        this.e = e.clone();
        this.length = length;
    }

    /** Number of samples for a length (the length is rounded to whole DS steps by the planner). */
    public static int samples(double length) { return (int) Math.round(length / DS) + 1; }

    /** A recorded profile. */
    public static RoadProfile of(double[] e) { return new RoadProfile(e, (e.length - 1) * DS); }

    /**
     * Design from ground samples (s = i * DS): sigma, raise and vSigma in m, kMin the smallest vertical curve K
     * (m per % of grade change), grade[i] the limit on the step from i - 1 to i (grade[0] unused), lower[i] the lowest E
     * allowed (NaN for none; the array may be null).
     */
    public static RoadProfile design(double[] ground, double sigma, double raise, double vSigma, double kMin, double[] grade, double[] lower) {
        int n = ground.length;
        double[] e = gauss(ground, sigma / DS);
        for (int i = 0; i < n; i++) e[i] += raise;
        for (int pass = 0; pass < 4; pass++) {
            clamp(e, grade);
            e = gauss(e, Math.max(1, sigma * 0.3) / DS);
        }
        clamp(e, grade);
        double[] extra = new double[n], bound = lower == null ? null : new double[n], out = e;
        for (int it = 0; it < 8; it++) {
            if (bound != null) for (int i = 0; i < n; i++) bound[i] = Double.isNaN(lower[i]) ? Double.NaN : lower[i] + extra[i];
            double[] f = e.clone();
            lift(f, grade, bound);
            f = vertical(f, grade, vSigma, kMin);
            out = f;
            double worst = 0;
            if (lower != null)
                for (int i = 0; i < n; i++) {
                    if (Double.isNaN(lower[i])) continue;
                    double d = lower[i] - f[i];
                    if (d > 0) { extra[i] += d + 0.01; worst = Math.max(worst, d); }
                }
            if (worst <= 0.005) break;
        }
        lift(out, grade, lower);
        return new RoadProfile(out, (n - 1) * DS);
    }

    /**
     * Vertical curves: smoothing with vSigma, then local smoothing (a growing sigma up to the cap, blended in with a
     * mask that is 1 within 1.5 sigma of every curve sharper than kMin and fades out over two more) until no curve is
     * sharper. Grade limits are re-clamped after every round.
     */
    static double[] vertical(double[] in, double[] grade, double vSigma, double kMin) {
        int n = in.length, c = (int) Math.round(K_CHORD / DS);
        double maxSecond = 1 / (100 * kMin), gMax = 0;
        for (int i = 1; i < n; i++) gMax = Math.max(gMax, grade[i]);
        // a Gaussian rounds a grade break dg to a curve of K = sigma sqrt(2 pi) / (100 dg): the sigma for the sharpest
        // break (dg = 2 gMax), with a fifth to spare
        double sigMax = Math.max(vSigma, 1.2 * 100 * kMin * 2 * gMax / Math.sqrt(2 * Math.PI)) / DS;
        double[] f = smooth(in, vSigma / DS);
        clamp(f, grade);
        double sig = vSigma / DS;
        boolean[] sharp = new boolean[n];
        double[] dist = new double[n];
        for (int round = 0; round < 12; round++) {
            boolean any = false;
            Arrays.fill(sharp, false);
            for (int i = c; i + c < n; i++) {
                double second = Math.abs(f[i + c] - 2 * f[i] + f[i - c]) / (K_CHORD * K_CHORD);
                if (second > maxSecond) { sharp[i] = true; any = true; }
            }
            if (!any) break;
            sig = Math.min(sig * 1.4, sigMax);
            double[] g = smooth(f, sig);
            // distance (samples) to the nearest sharp sample: two sweeps
            double far = 1e9;
            for (int i = 0; i < n; i++) dist[i] = sharp[i] ? 0 : (i > 0 ? dist[i - 1] + 1 : far);
            for (int i = n - 2; i >= 0; i--) dist[i] = Math.min(dist[i], dist[i + 1] + 1);
            for (int i = 0; i < n; i++) {
                double m = dist[i] <= 1.5 * sig ? 1 : dist[i] >= 3.5 * sig ? 0 : (3.5 * sig - dist[i]) / (2 * sig);
                f[i] += m * (g[i] - f[i]);
            }
            clamp(f, grade);
        }
        return f;
    }

    /** Forward / backward grade clamp with per-step limits. */
    static void clamp(double[] e, double[] grade) {
        for (int i = 1; i < e.length; i++) { double st = grade[i] * DS; e[i] = Math.max(e[i - 1] - st, Math.min(e[i - 1] + st, e[i])); }
        for (int i = e.length - 2; i >= 0; i--) { double st = grade[i + 1] * DS; e[i] = Math.max(e[i + 1] - st, Math.min(e[i + 1] + st, e[i])); }
    }

    /** E = max(E, cones of the lower bounds): every bound met, approach ramps at the step limits. */
    static void lift(double[] e, double[] grade, double[] lower) {
        if (lower == null) return;
        int n = e.length;
        double[] c = new double[n];
        Arrays.fill(c, Double.NEGATIVE_INFINITY);
        for (int i = 0; i < n; i++) if (!Double.isNaN(lower[i])) c[i] = lower[i];
        for (int i = 1; i < n; i++) c[i] = Math.max(c[i], c[i - 1] - grade[i] * DS);
        for (int i = n - 2; i >= 0; i--) c[i] = Math.max(c[i], c[i + 1] - grade[i + 1] * DS);
        for (int i = 0; i < n; i++) e[i] = Math.max(e[i], c[i]);
    }

    /** Gaussian smoothing (sigma in samples): exact up to 20 samples, three box passes beyond (same odd reflection). */
    public static double[] smooth(double[] in, double sigma) {
        return sigma <= 20 ? gauss(in, sigma) : boxGauss(in, sigma);
    }

    /** Gaussian smoothing, sigma in samples, odd reflection at the ends (a straight grade stays straight). */
    static double[] gauss(double[] in, double sigma) {
        int n = in.length, r = (int) Math.ceil(3 * sigma);
        double[] w = new double[r + 1];
        double sum = 0;
        for (int k = 0; k <= r; k++) { w[k] = StrictMath.exp(-0.5 * k * k / (sigma * sigma)); sum += k == 0 ? w[k] : 2 * w[k]; }
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            double acc = 0;
            for (int k = -r; k <= r; k++) acc += w[Math.abs(k)] * reflect(in, i + k);
            out[i] = acc / sum;
        }
        return out;
    }

    /** Three box filters of odd width w (variance 3 (w^2 - 1) / 12 = sigma^2), on the odd-reflected padded line. */
    static double[] boxGauss(double[] in, double sigma) {
        int n = in.length;
        int w = (int) Math.round(Math.sqrt(4 * sigma * sigma + 1));
        if (w % 2 == 0) w++;
        int h = w / 2, pad = 3 * h + 1;
        double[] a = new double[n + 2 * pad];
        for (int i = 0; i < a.length; i++) a[i] = reflect(in, i - pad);
        double[] pre = new double[a.length + 1];
        for (int pass = 0; pass < 3; pass++) {
            for (int i = 0; i < a.length; i++) pre[i + 1] = pre[i] + a[i];
            double[] b = new double[a.length];
            for (int i = 0; i < a.length; i++) {
                int lo = Math.max(0, i - h), hi = Math.min(a.length - 1, i + h);
                b[i] = (pre[hi + 1] - pre[lo]) / (hi - lo + 1);
            }
            a = b;
        }
        return Arrays.copyOfRange(a, pad, pad + n);
    }

    /** Odd reflection about the end samples (beyond twice the length: about the far end too). */
    static double reflect(double[] in, int j) {
        int n = in.length;
        if (j < 0) return -j < n ? 2 * in[0] - in[-j] : 2 * in[0] - in[n - 1];
        if (j >= n) return 2 * (n - 1) - j >= 0 ? 2 * in[n - 1] - in[2 * (n - 1) - j] : 2 * in[n - 1] - in[0];
        return in[j];
    }

    /** E at station s, linear between samples; constant beyond the ends. */
    public double at(double s) {
        double x = Math.max(0, Math.min(length, s)) / DS;
        int i = Math.min(e.length - 2, (int) Math.floor(x));
        double t = x - i;
        return e[i] + (e[i + 1] - e[i]) * t;
    }

    /** dE/ds at station s (of the linear piece). */
    public double grade(double s) {
        double x = Math.max(0, Math.min(length, s)) / DS;
        int i = Math.min(e.length - 2, (int) Math.floor(x));
        return (e[i + 1] - e[i]) / DS;
    }

    public double maxGrade() {
        double m = 0;
        for (int i = 1; i < e.length; i++) m = Math.max(m, Math.abs(e[i] - e[i - 1]) / DS);
        return m;
    }

    /** Smallest vertical curve K (m per % of grade change) over K_CHORD chords, infinite when straight. */
    public double minK() {
        int c = (int) Math.round(K_CHORD / DS);
        double k = Double.POSITIVE_INFINITY;
        for (int i = c; i + c < e.length; i++) {
            double second = Math.abs(e[i + c] - 2 * e[i] + e[i - c]) / (K_CHORD * K_CHORD);
            if (second > 1e-12) k = Math.min(k, 1 / (100 * second));
        }
        return k;
    }

    /** Length of profile whose K (over K_CHORD chords) is below kMin, m. */
    public double lengthBelowK(double kMin) {
        int c = (int) Math.round(K_CHORD / DS);
        double len = 0;
        for (int i = c; i + c < e.length; i++) {
            double second = Math.abs(e[i + c] - 2 * e[i] + e[i - c]) / (K_CHORD * K_CHORD);
            if (second > 1e-12 && 1 / (100 * second) < kMin) len += DS;
        }
        return len;
    }

    public double[] samples() { return e.clone(); }

    public int size() { return e.length; }
}
