package com.antaurora.apofirstlight.weapon;

/** Client-independent ADS calibration carried by a native-gun definition. */
public record NativeAdsCalibration(String anchor, float ax, float ay, float az, float eyeRelief,
        float hx, float hy, float hz, float rx, float ry, float rz, float scale,
        float compositionX, float compositionY, float rootPitch) {
    public static NativeAdsCalibration defaults(WeaponClass weaponClass) {
        return switch (weaponClass) {
            case PISTOL, SMG -> new NativeAdsCalibration("sight_anchor/rear-axis",
                    1.50F, 5.80F, 2.97F, .47F, 3.24148F, -7.4945F, -14.19624F,
                    .54547F, .19151F, -.27948F, .45F, .07F, .045F, 3F);
            default -> new NativeAdsCalibration("octagon9/rear-aperture-axis",
                    0F, 13.6875F, 15.46875F, .16F, 3.8F, -7.2F, -11.5F,
                    0F, 4F, 0F, .45F, 0F, 0F, 0F);
        };
    }
}
