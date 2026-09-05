package com.antaurora.apofirstlight.explosion;

/** Presentation tuning only; never used to change explosion damage or knockback. */
public final class ExplosionTinnitusProfile {
    public static final double RANGE_MULTIPLIER = 3.0;
    public static final float MIN_AUDIO_SEVERITY = 0.15F;
    public static final float MIN_OVERLAY_SEVERITY = 0.08F;
    // The supplied one-shot is 10.03 s. Never extend a single playback beyond its media.
    public static final int MAX_PLAYBACK_TICKS = 200;
    public static final int WEAK_EXTENSION_TICKS = 20;

    private ExplosionTinnitusProfile() {}

    public static float severity(float radius, double distance) {
        if (!Float.isFinite(radius) || radius <= 0 || !Double.isFinite(distance) || distance < 0) {
            return 0;
        }
        double normalized = Math.max(0, Math.min(1, 1 - distance / (radius * RANGE_MULTIPLIER)));
        return (float) (normalized * normalized);
    }

    public static boolean shouldTrigger(float severity) {
        return Float.isFinite(severity) && severity >= MIN_AUDIO_SEVERITY && severity <= 1;
    }

    /** The existing S2C scalar remains n squared; recover n without changing audio or the wire format. */
    public static float overlaySeverity(float audioSeverity) {
        return Float.isFinite(audioSeverity) && audioSeverity >= 0 && audioSeverity <= 1
                ? (float) Math.sqrt(audioSeverity) : 0;
    }

    /** Input is the original audio severity, just as in shouldTrigger and in the existing packet. */
    public static boolean shouldTriggerOverlay(float audioSeverity) {
        return overlaySeverity(audioSeverity) >= MIN_OVERLAY_SEVERITY;
    }

    public static float initialVolume(float severity) {
        return 0.15F + 0.55F * severity;
    }

    public static int durationTicks(float severity) {
        return Math.round(40 + 120 * severity);
    }

    public static float peakOverlayAlpha(float overlaySeverity) {
        if (!Float.isFinite(overlaySeverity)) return 0;
        return Math.min(0.60F, 0.18F + 0.42F * Math.max(0, Math.min(1, overlaySeverity)));
    }

    /** Half-cosine with zero slope at the hold/release junction and at the end. */
    public static float envelope(float age, int duration, float holdFraction, float endFraction) {
        if (duration <= 0) return 0;
        float progress = age / duration;
        if (progress <= holdFraction) return 1;
        if (progress >= endFraction) return 0;
        double fade = (progress - holdFraction) / (endFraction - holdFraction);
        return (float) (0.5 + 0.5 * Math.cos(Math.PI * fade));
    }
}
