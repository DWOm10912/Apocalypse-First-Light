package com.antaurora.apofirstlight.weapon.client;

import com.mojang.blaze3d.vertex.PoseStack;

/** Ownership boundary: a render branch receives matrix values, never the other branch's stack. */
public final class ServicePistolRenderMatrices {
    private ServicePistolRenderMatrices() {}

    public static PoseStack detachedCopy(PoseStack source) {
        var copy = new PoseStack();
        copy.last().pose().set(source.last().pose());
        copy.last().normal().set(source.last().normal());
        return copy;
    }
}
