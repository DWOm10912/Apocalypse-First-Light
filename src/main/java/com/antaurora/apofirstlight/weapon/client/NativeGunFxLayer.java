package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.weapon.ServicePistolItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtils;

/** Read the actual traversal matrix, including Display, animation and world compatibility. */
public final class NativeGunFxLayer extends GeoRenderLayer<ServicePistolItem> {
    private final ServicePistolRenderer renderer;
    public NativeGunFxLayer(ServicePistolRenderer renderer) { super(renderer); this.renderer = renderer; }

    @Override
    public void renderForBone(PoseStack pose, ServicePistolItem item, GeoBone bone, RenderType type,
            MultiBufferSource buffers, VertexConsumer buffer, float partial, int light, int overlay) {
        if (!renderer.isHeldFxPass()) return;
        String name = bone.getName();
        if (!name.equals("muzzle_anchor") && !name.equals("ejection_anchor")) return;
        var anchor = ServicePistolRenderMatrices.detachedCopy(pose);
        RenderUtils.translateToPivotPoint(anchor, bone);
        try {
            NativeGunFx.anchor(renderer.getInstanceId(item), renderer.isFirstPersonPass(), name,
                    anchor, buffers, partial);
        } finally { buffers.getBuffer(type); }
    }
}
