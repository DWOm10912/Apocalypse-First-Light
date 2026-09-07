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
    public static final NativeGunRig RIG = new NativeGunRig(
            "gun_model_root", "right_hand_anchor", "left_hand_anchor", "fp_root");
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
        // fp_root is an authoring context container, not a world-item animation.
        // In non-FP contexts traverse its children without applying that container.
        if (!isFirstPersonPass() && bone.getName().equals(RIG.firstPersonRoot())) {
            for (var child : bone.getChildBones())
                renderRecursively(pose, item, child, type, buffers, buffer, reRender,
                        partialTick, light, overlay, red, green, blue, alpha);
            return;
        }
        boolean worldCompatibility = !isFirstPersonPass() && bone.getName().equals("gun");
        if (worldCompatibility) {
            pose.pushPose();
            preserveNonFirstPersonSize(pose);
        }
        try {
            super.renderRecursively(pose, item, bone, type, buffers, buffer, reRender,
                    partialTick, light, overlay, red, green, blue, alpha);
        } finally {
            if (worldCompatibility) pose.popPose();
        }
    }

    /** Existing world-item compatibility only: undo the historical .5 physical
     * migration and the newly baked .8 around their respective source pivots.
     * This branch never executes in first person and never touches player arms.
     */
    public static void preserveNonFirstPersonSize(PoseStack pose) {
        pose.translate(0, 8 / 16F, 6 / 16F);
        pose.scale(2, 2, 2);
        pose.translate(0, -8 / 16F, -6 / 16F);
        pose.translate(.1 / 16F, 7.75 / 16F, 9.2 / 16F);
        pose.scale(1.25F, 1.25F, 1.25F);
        pose.translate(-.1 / 16F, -7.75 / 16F, -9.2 / 16F);
    }
}
