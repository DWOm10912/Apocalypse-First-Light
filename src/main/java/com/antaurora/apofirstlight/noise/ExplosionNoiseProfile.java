package com.antaurora.apofirstlight.noise;

public final class ExplosionNoiseProfile {
    public static final double BASE_RADIUS = 96.0;
    public static final double STRENGTH_MULTIPLIER = 24.0;
    public static final double ROUNDING_STEP = 8.0;
    public static final double MIN_RADIUS = 96.0;
    public static final double MAX_RADIUS = 256.0;

    private ExplosionNoiseProfile() {
    }

    public static double radius(float explosionStrength) {
        if (Float.isNaN(explosionStrength) || explosionStrength == Float.NEGATIVE_INFINITY) {
            return MIN_RADIUS;
        }
        if (explosionStrength == Float.POSITIVE_INFINITY) {
            return MAX_RADIUS;
        }
        double rawRadius = BASE_RADIUS + explosionStrength * STRENGTH_MULTIPLIER;
        double roundedRadius = Math.round(rawRadius / ROUNDING_STEP) * ROUNDING_STEP;
        return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, roundedRadius));
    }
}
