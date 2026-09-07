package com.antaurora.apofirstlight.weapon.client;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * AFL canonical arm frame, model units (16 units = one render unit).
 * Origin: centre of the distal hand cap. Forearm: -Y; palm normal: +Z;
 * lateral: +X. Preview bounds: [-width/2,-12,-2] .. [width/2,0,2].
 * B_skin = T(-vanillaCentreX, -10, 0)/16, after ModelPart pose is zeroed.
 * No weapon, action, camera, direction quaternion or scale compensation.
 * Receives the contact-centred universal presentation frame from
 * NativePlayerArmRenderer. Presentation scaling precedes this rigid binding,
 * so the baked Vanilla hand cap maps to the unchanged canonical origin.
 */
public final class NativeHandBinding {
    // Standard player-arm units. Weapon assets must fit this frame, not vice versa.
    public static final float SCALE = 1F;
    public static final float DISTAL_Y = 10F;
    private NativeHandBinding() {}

    public static float centreX(boolean right, boolean slim) {
        return (right ? -1F : 1F) * (slim ? .5F : 1F);
    }

    public static void apply(PoseStack locator, boolean right, boolean slim) {
        locator.translate(-centreX(right, slim) / 16F, -DISTAL_Y / 16F, 0);
    }
}
