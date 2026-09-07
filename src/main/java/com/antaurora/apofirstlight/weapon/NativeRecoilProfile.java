package com.antaurora.apofirstlight.weapon;

/** Per-weapon recoil tuning. Angles are degrees, durations seconds, back kick render units. */
public record NativeRecoilProfile(double verticalMin, double verticalMax,
                                 double horizontalMin, double horizontalMax,
                                 double maxVertical, double maxHorizontal,
                                 double recoveryDelay, double cameraRecoveryTime,
                                 double modelPitch, double modelBack,
                                 double modelYaw, double modelRoll, double modelRecoveryTime) {
    public NativeRecoilProfile {
        double[] values = {verticalMin, verticalMax, horizontalMin, horizontalMax, maxVertical,
                maxHorizontal, recoveryDelay, cameraRecoveryTime, modelPitch, modelBack,
                modelYaw, modelRoll, modelRecoveryTime};
        for (double value : values) if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite recoil");
        if (verticalMin < 0 || verticalMax < verticalMin || horizontalMax < horizontalMin
                || maxVertical < 0 || maxHorizontal < 0 || recoveryDelay < 0
                || cameraRecoveryTime <= 0 || modelRecoveryTime <= 0
                || modelPitch < 0 || modelBack < 0 || modelYaw < 0 || modelRoll < 0)
            throw new IllegalArgumentException("Invalid recoil profile");
    }
}
