package com.antaurora.apofirstlight.weapon.client;

import com.mojang.blaze3d.vertex.PoseStack;

/** Minecraft equip transition only. Static Display and action poses are authored assets. */
public final class ServicePistolPresentation {
    private ServicePistolPresentation() {}
    public static void applyEquip(PoseStack pose, float equipProgress) {
        pose.translate(0, -equipProgress * 0.6F, 0);
    }
}
