package com.antaurora.apofirstlight.weapon;

/** Dimensionless stance multipliers; base spread remains a cone half-angle in degrees. */
public record NativeAccuracyProfile(double crouchStill, double crouchMoving, double walking,
                                    double sprinting, double airborne) {
    public static final NativeAccuracyProfile DEFAULT = new NativeAccuracyProfile(.45,.65,1.4,2,2.5);
    public static final NativeAccuracyProfile BATTLE_RIFLE = new NativeAccuracyProfile(.45,.65,2.2,4.5,7);
    public NativeAccuracyProfile {
        for (double v : new double[]{crouchStill,crouchMoving,walking,sprinting,airborne})
            if (!Double.isFinite(v) || v <= 0) throw new IllegalArgumentException("Invalid accuracy multiplier");
    }
}
