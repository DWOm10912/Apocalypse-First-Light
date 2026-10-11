package com.antaurora.apofirstlight.dev.highway.r4;

import java.util.Arrays;

/**
 * The vertical profile: design datum E(s) of the crown line, sampled every DS metres from station 0 to the segment length,
 * linear between samples (the pavement mesh puts its vertices on the same 2 m lattice, so mesh, collision and turf read one
 * function). Pure Java.
 * <p>
 * Design rule (docs/worldgen/highway_r4_sample_v1.md, B): the trimmed mean of the surveyed ground across the formation,
 * Gaussian-smoothed (sigma 40 m), raised 0.7 m (a rural freeway sits slightly above the land for drainage), grade limited
 * to 3 % by forward / backward clamping with light re-smoothing.
 */
public final class R4Profile {
    public static final double DS = 2.0, RAISE = 0.7, MAX_GRADE = 0.03, SIGMA = 40, SIGMA_FINE = 12;
    private final double[] e;
    public final double length;

    private R4Profile(double[] e, double length) {
        if (e.length < 2 || Math.abs((e.length - 1) * DS - length) > 1e-9) throw new IllegalArgumentException("PROFILE_SAMPLES");
        for (double v : e) if (!Double.isFinite(v)) throw new IllegalArgumentException("NON_FINITE_PROFILE");
        this.e = e.clone();
        this.length = length;
    }

    /** A recorded profile (the recipe). */
    public static R4Profile of(double[] e, double length) { return new R4Profile(e, length); }

    /** Design from ground samples at s = i * DS (i = 0 .. length / DS). */
    public static R4Profile design(double[] ground, double length) {
        double[] e = gauss(ground, SIGMA / DS);
        for (int i = 0; i < e.length; i++) e[i] += RAISE;
        for (int pass = 0; pass < 4; pass++) {
            clampGrade(e);
            e = gauss(e, SIGMA_FINE / DS);
        }
        clampGrade(e);
        // the last clamp can leave a grade corner; one more light pass, then a final clamp keeps it within the limit
        e = gauss(e, 4 / DS);
        clampGrade(e);
        return new R4Profile(e, length);
    }

    private static void clampGrade(double[] e) {
        double step = MAX_GRADE * DS;
        for (int i = 1; i < e.length; i++) e[i] = Math.max(e[i - 1] - step, Math.min(e[i - 1] + step, e[i]));
        for (int i = e.length - 2; i >= 0; i--) e[i] = Math.max(e[i + 1] - step, Math.min(e[i + 1] + step, e[i]));
    }

    /** Gaussian smoothing, sigma in samples, mirrored at the ends (the ends keep their own level, no pull to zero). */
    static double[] gauss(double[] in, double sigma) {
        int n = in.length, r = (int) Math.ceil(3 * sigma);
        double[] w = new double[r + 1];
        double sum = 0;
        for (int k = 0; k <= r; k++) { w[k] = Math.exp(-0.5 * k * k / (sigma * sigma)); sum += k == 0 ? w[k] : 2 * w[k]; }
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            double acc = 0;
            for (int k = -r; k <= r; k++) {
                int j = i + k;
                // odd reflection about the end sample keeps the end slope (a straight grade stays straight)
                double v = j < 0 ? 2 * in[0] - in[-j] : j >= n ? 2 * in[n - 1] - in[2 * (n - 1) - j] : in[j];
                if (j < 0 && -j >= n || j >= n && 2 * (n - 1) - j < 0) v = in[Math.max(0, Math.min(n - 1, j))];
                acc += w[Math.abs(k)] * v;
            }
            out[i] = acc / sum;
        }
        return out;
    }

    /** E at station s, linear between samples; constant beyond the ends. */
    public double at(double s) {
        double x = Math.max(0, Math.min(length, s)) / DS;
        int i = Math.min(e.length - 2, (int) Math.floor(x));
        double t = x - i;
        return e[i] + (e[i + 1] - e[i]) * t;
    }

    /** dE/ds at station s (of the linear segment). */
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

    /** The smallest vertical curve K (m per % of grade change) over the profile; infinity when straight. */
    public double minK() {
        double k = Double.POSITIVE_INFINITY;
        for (int i = 1; i + 1 < e.length; i++) {
            double second = Math.abs(e[i + 1] - 2 * e[i] + e[i - 1]) / (DS * DS);
            if (second > 1e-12) k = Math.min(k, 1 / (100 * second));
        }
        return k;
    }

    public double[] samples() { return e.clone(); }

    public int size() { return e.length; }

    @Override public String toString() { return "R4Profile" + Arrays.toString(e); }
}
