package com.antaurora.apofirstlight.weapon;

/** Visual units: blocks and ticks. TRACER is reserved, not an implemented ammunition type. */
public record NativeTrailProfile(Mode mode, double speed, double length, double coreWidth,
                                 double outerWidth, int coreColor, int outerColor,
                                 double coreAlpha, double outerAlpha, double hideDistance) {
    public enum Mode { NONE, SUBTLE, TRACER }
    public static final NativeTrailProfile SUBTLE_PISTOL = new NativeTrailProfile(
            Mode.SUBTLE, 18, 3, .015, .032, 0xFFF5CD, 0xFFBE50, .65, .12, .40);
    public NativeTrailProfile {
        java.util.Objects.requireNonNull(mode);
        for (double v : new double[]{speed, length, coreWidth, outerWidth, coreAlpha, outerAlpha, hideDistance})
            if (!Double.isFinite(v) || v < 0) throw new IllegalArgumentException("Invalid trail parameter");
        if (speed == 0 || length == 0 || coreWidth == 0 || outerWidth < coreWidth
                || coreAlpha > 1 || outerAlpha > 1) throw new IllegalArgumentException("Invalid trail geometry");
    }
}
