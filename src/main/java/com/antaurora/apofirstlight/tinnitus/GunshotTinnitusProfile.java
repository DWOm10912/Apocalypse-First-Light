package com.antaurora.apofirstlight.tinnitus;

/** Deterministic server-side gunshot exposure tuning; independent of infected hearing distance. */
public final class GunshotTinnitusProfile {
    public static final double LISTENER_RANGE = 16.0;
    public static final double MIN_ACOUSTIC_RADIUS = 64.0;
    public static final double MAX_ACOUSTIC_RADIUS = 160.0;
    public static final double SHOT_SCALE = 0.55;
    public static final double LARGE_CALIBER_THRESHOLD = 128.0;
    public static final double LARGE_CALIBER_BOOST = 1.25;
    public static final double EXTREME_CALIBER_THRESHOLD = 160.0;
    public static final double EXTREME_CALIBER_BOOST = 1.35;
    public static final double MAX_SHOT_EXPOSURE = 0.75;
    public static final double MAX_ACCUMULATED_EXPOSURE = 1.5;
    public static final double DECAY_PER_TICK = 0.008;
    public static final double LIGHT_THRESHOLD = 0.35;
    public static final double MEDIUM_THRESHOLD = 0.60;
    public static final double STRONG_THRESHOLD = 0.90;
    public static final int IMPULSE_COOLDOWN_TICKS = 6;
    public static final int EXTENSION_PACKET_INTERVAL_TICKS = 20;
    public static final float SIGNIFICANT_SEVERITY_DELTA = 0.15F;
    public static final double POST_IMPULSE_RETENTION = 0.45;

    private GunshotTinnitusProfile() {
    }

    public static double loudness(double effectiveRadius) {
        if (!Double.isFinite(effectiveRadius)) {
            return 0.0;
        }
        return clamp01((effectiveRadius - MIN_ACOUSTIC_RADIUS)
                / (MAX_ACOUSTIC_RADIUS - MIN_ACOUSTIC_RADIUS));
    }

    public static double distanceFactor(double distance) {
        if (!Double.isFinite(distance) || distance < 0.0 || distance > LISTENER_RANGE) {
            return 0.0;
        }
        double linear = clamp01(1.0 - distance / LISTENER_RANGE);
        return linear * linear;
    }

    public static double shotExposure(double effectiveRadius, double distance) {
        double exposure = loudness(effectiveRadius) * distanceFactor(distance) * SHOT_SCALE;
        if (effectiveRadius >= LARGE_CALIBER_THRESHOLD) {
            exposure *= LARGE_CALIBER_BOOST;
        }
        if (effectiveRadius >= EXTREME_CALIBER_THRESHOLD) {
            exposure *= EXTREME_CALIBER_BOOST;
        }
        return Math.max(0.0, Math.min(MAX_SHOT_EXPOSURE, exposure));
    }

    public static float severity(double exposure) {
        if (!Double.isFinite(exposure)) {
            return 0.0F;
        }
        return (float) clamp01(exposure);
    }

    public static int thresholdBand(double exposure) {
        if (!Double.isFinite(exposure) || exposure < LIGHT_THRESHOLD) {
            return 0;
        }
        if (exposure >= STRONG_THRESHOLD) {
            return 3;
        }
        if (exposure >= MEDIUM_THRESHOLD) {
            return 2;
        }
        return 1;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
