package com.antaurora.apofirstlight.client.lightning;

import net.minecraft.util.RandomSource;

/**
 * Lightning V1 (docs/rendering/lightning_v1.md): one flash on the client, its channel and its strokes in time. The stepped
 * leader comes down faintly for {@link #LEADER} s; the first return stroke lights the channel and every branch at once; then
 * 1-4 more strokes 35-120 ms apart come down the same channel without the branches (the dart leaders follow the conditioned
 * path), each weaker; one in four keeps glowing 80-180 ms (continuing current). Each stroke is full bright for a frame,
 * holds half that, then fades in ~12 ms, so the channel goes dark between strokes. Timed from the bolt's first client tick.
 */
public final class LightningFlash {
    /** The stepped leader before the first stroke (s). */
    public static final double LEADER = 0.03;
    /** Each stroke's own flash (s): one frame at 60 fps. */
    private static final double PEAK = 0.017;
    /** After its peak and hold, a stroke's glow fades with this time constant (s); the branches' faster. */
    private static final double FADE = 0.012, BRANCH_FADE = 0.010;
    /** The channel's height above the strike point (blocks): where it leaves the cloud (vanilla's clouds at 192). */
    static final double MIN_HEIGHT = 150, MAX_HEIGHT = 230;
    /**
     * With a shader pack the channel's top is at this world Y plus up to {@link #PACK_TOP_SPREAD} (Aureveil's cloud base,
     * its CLOUD_ALTITUDE default; a changed one AFL does not know), at least {@link #PACK_MIN_HEIGHT} above the strike.
     */
    static final double PACK_CLOUD_Y = 768, PACK_TOP_SPREAD = 40, PACK_MIN_HEIGHT = 60;

    private final double[] strokeT, strokeK, strokeHold;
    public final LightningChannel channel;
    public final double height;
    /** Client ticks since the bolt appeared. */
    int age;
    boolean sounded;

    /** This instant's brightness (0..1) of the main channel, of the branches, and of the sky (set by {@link #at}). */
    public double main, branch, sky;

    /** A flash striking at world Y strikeY; tall: up to the shader pack's cloud base instead of vanilla's. */
    LightningFlash(long seed, double strikeY, boolean tall) {
        RandomSource r = RandomSource.create(seed * 31 + 7);
        double share = r.nextDouble();
        height = tall ? Math.max(PACK_MIN_HEIGHT, PACK_CLOUD_Y + PACK_TOP_SPREAD * share - strikeY)
                : MIN_HEIGHT + (MAX_HEIGHT - MIN_HEIGHT) * share;
        int n = 2 + r.nextInt(4);   // 2-5 strokes in all
        strokeT = new double[n];
        strokeK = new double[n];
        strokeHold = new double[n];
        strokeK[0] = 1;
        strokeHold[0] = 0.02 + 0.03 * r.nextDouble();
        double t = 0;
        for (int i = 1; i < n; i++) {
            t += 0.035 + 0.085 * r.nextDouble();
            strokeT[i] = t;
            strokeK[i] = 0.4 + 0.4 * r.nextDouble();
            strokeHold[i] = r.nextDouble() < 0.25 ? 0.08 + 0.1 * r.nextDouble() : 0.008 + 0.015 * r.nextDouble();
        }
        channel = new LightningChannel(seed, height);
    }

    /** Seconds since the first stroke at this partial tick (negative during the leader). */
    public double time(float partialTick) {
        return (age + partialTick) * 0.05 - LEADER;
    }

    /** When the last stroke has faded (s after the first). */
    public double end() {
        int last = strokeT.length - 1;
        return strokeT[last] + Math.max(strokeHold[last], PEAK) + 8 * FADE;
    }

    /** Sets {@link #main}, {@link #branch} and {@link #sky} for t s after the first stroke; returns this. */
    public LightningFlash at(double t) {
        double m = 0, b = 0;
        for (int i = 0; i < strokeT.length; i++) {
            double dt = t - strokeT[i];
            if (dt < 0) continue;
            double v = dt < PEAK ? 1 : dt < strokeHold[i] ? 0.5 : 0.5 * Math.exp(-(dt - Math.max(strokeHold[i], PEAK)) / FADE);
            m = Math.max(m, strokeK[i] * v);
            if (i == 0) b = Math.max(b, dt < PEAK ? 1 : 0.35 * Math.exp(-(dt - PEAK) / BRANCH_FADE));
        }
        main = m;
        branch = b;
        sky = Math.max(m, b * 0.7);
        return this;
    }

    /** The brightest the sky gets from t to t + span (s), sampled per millisecond. */
    public double skyPeak(double t, double span) {
        double peak = 0;
        for (double s = t; s < t + span; s += 0.001) peak = Math.max(peak, at(s).sky);
        return peak;
    }
}
