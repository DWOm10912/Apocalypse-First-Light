package com.antaurora.apofirstlight.weapon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

/** First-person composition only; never changes source bones, gameplay or audio. */
public final class ServicePistolPresentation {
    public static final float BASE_X = 0.60F;
    public static final float BASE_Y = -0.54F;
    public static final float BASE_Z = -0.64F;
    // The authored first-person display scale stays 0.3; offsets below are camera units.
    private static final float DISPLAY_SCALE = 0.3F;

    private ServicePistolPresentation() {}

    public static float reloadWeight(double seconds) {
        if (seconds < 0 || seconds >= 1.18) return 0;
        if (seconds < 0.24) return smooth((float)(seconds / 0.24));
        if (seconds <= 0.85) return 1;
        return 1 - smooth((float)((seconds - 0.85) / 0.33));
    }

    private static float smooth(float value) {
        float t = Math.max(0, Math.min(1, value));
        return t * t * (3 - 2 * t);
    }

    public static void applyReload(PoseStack pose, float weight) {
        if (weight <= 0) return;
        // Pull back enough to expose the existing long magazine extraction. Moving
        // closer would push it below the viewport again. Extra roll combines with
        // authored -14 degrees to about -30 degrees, rather than doubling the pose.
        pose.translate(0.05F * weight / DISPLAY_SCALE, 0.28F * weight / DISPLAY_SCALE,
                -0.25F * weight / DISPLAY_SCALE);
        pose.translate(0, 8 / 16F, 6 / 16F);
        pose.mulPose(Axis.ZP.rotationDegrees(-16 * weight));
        // The barrel points down local -Z: positive yaw opens the muzzle LEFT
        // in camera space. Negative yaw incorrectly swept it to the right.
        pose.mulPose(Axis.YP.rotationDegrees(32 * weight));
        pose.mulPose(Axis.XP.rotationDegrees(8 * weight));
        pose.translate(0, -8 / 16F, -6 / 16F);
    }
}
