package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.weapon.P901Item;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtils;

/** Weapon adapter only: select named animated locators; all arm rendering is universal. */
public final class P901HandLayer extends GeoRenderLayer<P901Item> {
    private final P901Renderer pistolRenderer;

    public P901HandLayer(P901Renderer renderer) {
        super(renderer);
        this.pistolRenderer = renderer;
    }

    @Override
    public void renderForBone(PoseStack pose, P901Item item, GeoBone bone,
                              RenderType gunType, MultiBufferSource buffers, VertexConsumer gunBuffer,
                              float partialTick, int light, int overlay) {
        var player = Minecraft.getInstance().player;
        if (!pistolRenderer.isFirstPersonPass() || player == null) return;
        var rig = P901Renderer.RIG;
        boolean right = bone.getName().equals(rig.rightLocator());
        if (!right && !bone.getName().equals(rig.leftLocator())) return;
        var locator = P901RenderMatrices.detachedCopy(pose);
        RenderUtils.translateToPivotPoint(locator, bone);
        try {
            NativePlayerArmRenderer.render(locator, right, buffers, light, overlay);
        } finally {
            buffers.getBuffer(gunType);
        }
    }
}
