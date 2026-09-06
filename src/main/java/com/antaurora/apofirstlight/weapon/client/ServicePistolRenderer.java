package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.weapon.ServicePistolItem;
import com.antaurora.apofirstlight.weapon.ServicePistolAnimationController;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/** Vanilla builtin/entity display transforms are supplied by the exported item JSON. */
public final class ServicePistolRenderer extends GeoItemRenderer<ServicePistolItem> {
    public ServicePistolRenderer() {
        super(new ServicePistolModel());
        addRenderLayer(new ServicePistolHandLayer(this));
    }

    public boolean isFirstPersonPass() {
        return renderPerspective != null && renderPerspective.firstPerson();
    }

    public double getReloadSeconds() {
        if (animatable == null || currentItemStack == null) return -1;
        var controller = animatable.getAnimatableInstanceCache().<ServicePistolItem>getManagerForId(getInstanceId(animatable))
                .getAnimationControllers().get(ServicePistolItem.CONTROLLER);
        return controller instanceof ServicePistolAnimationController pistol ? pistol.getReloadSeconds() : -1;
    }

    @Override
    public void renderRecursively(PoseStack pose, ServicePistolItem item, GeoBone bone, RenderType type,
                                  MultiBufferSource buffers, VertexConsumer buffer, boolean reRender,
                                  float partialTick, int light, int overlay, float red, float green, float blue, float alpha) {
        // Animation has already been evaluated by GeoItemRenderer.actuallyRender.
        // One parent transform moves the gun AND the animated hand layers together.
        boolean presentationRoot = isFirstPersonPass() && bone.getName().equals("root");
        if (presentationRoot) {
            pose.pushPose();
            ServicePistolPresentation.applyReload(pose, ServicePistolPresentation.reloadWeight(getReloadSeconds()));
        }
        try {
            super.renderRecursively(pose, item, bone, type, buffers, buffer, reRender,
                    partialTick, light, overlay, red, green, blue, alpha);
        } finally { if (presentationRoot) pose.popPose(); }
    }
}
