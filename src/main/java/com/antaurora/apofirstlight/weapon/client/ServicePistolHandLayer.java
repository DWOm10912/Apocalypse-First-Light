package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.weapon.ServicePistolItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtils;

/** Weapon adapter only: select named animated locators; all arm rendering is universal. */
public final class ServicePistolHandLayer extends GeoRenderLayer<ServicePistolItem> {
    private final ServicePistolRenderer pistolRenderer;

    public ServicePistolHandLayer(ServicePistolRenderer renderer) {
        super(renderer);
        this.pistolRenderer = renderer;
    }

    @Override
    public void renderForBone(PoseStack pose, ServicePistolItem item, GeoBone bone,
                              RenderType gunType, MultiBufferSource buffers, VertexConsumer gunBuffer,
                              float partialTick, int light, int overlay) {
        var player = Minecraft.getInstance().player;
        if (!pistolRenderer.isFirstPersonPass() || player == null
                || !(player.getMainHandItem().getItem() instanceof ServicePistolItem)) return;
        var rig = ServicePistolRenderer.RIG;
        boolean right = bone.getName().equals(rig.rightLocator());
        if (!right && !bone.getName().equals(rig.leftLocator())) return;
        var locator = ServicePistolRenderMatrices.detachedCopy(pose);
        RenderUtils.translateToPivotPoint(locator, bone);
        try {
            NativePlayerArmRenderer.render(locator, right, buffers, light, overlay);
        } finally {
            buffers.getBuffer(gunType);
        }
    }
}
