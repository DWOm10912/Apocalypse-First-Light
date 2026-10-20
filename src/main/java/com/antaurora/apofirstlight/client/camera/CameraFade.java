package com.antaurora.apofirstlight.client.camera;

/**
 * Camera director V1 (docs/dev/camera_director_v1_plan.md 2.3, 2.4): a request's weight over time, 0 .. 1 .. 0. Eases in
 * over {@code in} seconds from {@link #open}, out over {@code out} seconds from {@link #close} (from wherever it stood, as
 * the maintenance view's exit), smoothstep both ways. Times are the request's clock (CameraDirector.Clock), in seconds.
 */
public final class CameraFade {
    private final double in, out;
    private double openedAt = Double.NaN, closedAt = Double.NaN, closedFrom;

    public CameraFade(double in, double out) {
        this.in = in;
        this.out = out;
    }

    public void open(double now) {
        open(now, 0);
    }

    /** Opens from {@code weight} (0..1): a request opened again while it eased out goes on from where it stood. */
    public void open(double now, double weight) {
        // the smoothstep's inverse: the time into the ease-in that has this weight
        double w = Math.max(0, Math.min(1, weight));
        openedAt = now - in * (0.5 - Math.sin(Math.asin(1 - 2 * w) / 3));
        closedAt = Double.NaN;
    }

    /** Begins easing out (once; a second call keeps the first). */
    public void close(double now) {
        if (!Double.isNaN(closedAt) || Double.isNaN(openedAt)) return;
        closedFrom = weight(now);
        closedAt = now;
    }

    public boolean closing() {
        return !Double.isNaN(closedAt);
    }

    /** Eased out completely (or never opened): the request can go. */
    public boolean finished(double now) {
        return Double.isNaN(openedAt) || closing() && weight(now) <= 0;
    }

    public double weight(double now) {
        if (Double.isNaN(openedAt)) return 0;
        if (!Double.isNaN(closedAt)) return closedFrom * (1 - smooth(span(now - closedAt, out)));
        return smooth(span(now - openedAt, in));
    }

    private static double span(double t, double length) {
        return length <= 0 ? 1 : t / length;
    }

    /** Smoothstep on 0..1 (clamped). */
    public static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }
}
