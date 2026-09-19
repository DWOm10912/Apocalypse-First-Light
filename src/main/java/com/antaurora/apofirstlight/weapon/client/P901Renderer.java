package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.weapon.P901Item;
import com.antaurora.apofirstlight.weapon.P901AnimationController;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import software.bernie.geckolib.cache.object.GeoBone;

/** Vanilla builtin/entity display transforms are supplied by the exported item JSON. */
public final class P901Renderer extends NativeGunContextRenderer<P901Item> {
    public static final NativeGunRig RIG = new NativeGunRig(
            "gun", "right_hand_anchor", "left_hand_anchor", "root");
    public P901Renderer() {
        super(new P901Model(), "static_idle");
        addRenderLayer(new P901HandLayer(this));
        addRenderLayer(new NativeGunFxLayer(this));
        addRenderLayer(new P901SightLayer(this));
    }

    public boolean isFirstPersonPass() {
        return renderPerspective != null && renderPerspective.firstPerson();
    }
    public net.minecraft.world.item.ItemStack sightStack(){return currentItemStack;}

    public boolean isHeldFxPass() {
        return isFirstPersonPass() || renderPerspective == net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || renderPerspective == net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }

    public double getReloadSeconds() {
        if (animatable == null || currentItemStack == null) return -1;
        var controller = animatable.getAnimatableInstanceCache().<P901Item>getManagerForId(getInstanceId(animatable))
                .getAnimationControllers().get(P901Item.CONTROLLER);
        return controller instanceof P901AnimationController pistol ? pistol.getReloadSeconds() : -1;
    }

    @Override
    public void renderRecursively(PoseStack pose, P901Item item, GeoBone bone, RenderType type,
                                  MultiBufferSource buffers, VertexConsumer buffer, boolean reRender,
                                  float partialTick, int light, int overlay, float red, float green, float blue, float alpha) {
        // Visual-only spare instance: never leak into idle, canceled reloads,
        // GUI/ground/third person. The authored scale keys own its visible interval.
        if (bone.getName().equals("empty_old_magazine")) {
            if (!isFirstPersonPass() || animatable == null || currentItemStack == null) return;
            var controller = animatable.getAnimatableInstanceCache().<P901Item>getManagerForId(getInstanceId(animatable))
                    .getAnimationControllers().get(P901Item.CONTROLLER);
            if (!(controller instanceof P901AnimationController pistol) || !pistol.isEmptyReloadPlaying()) return;
        }
        boolean worldCompatibility = !isFirstPersonPass() && bone.getName().equals("g19_and_mag");
        if (worldCompatibility) {
            pose.pushPose();
            preserveNonFirstPersonSize(pose);
        }
        try {
            if(NativeMagazineRendering.replaces(currentItemStack,bone)){
                pose.pushPose();
                software.bernie.geckolib.util.RenderUtils.prepMatrixForBone(pose,bone);
                NativeMagazineRendering.render(currentItemStack,bone,pose,buffers,light,overlay);
                for(var child:bone.getChildBones())renderRecursively(pose,item,child,type,buffers,buffers.getBuffer(type),reRender,partialTick,light,overlay,red,green,blue,alpha);
                pose.popPose();buffers.getBuffer(type);return;
            }
            super.renderRecursively(pose, item, bone, type, buffers, buffer, reRender,
                    partialTick, light, overlay, red, green, blue, alpha);
        } finally {
            if (worldCompatibility) pose.popPose();
        }
    }

    /** Keep the earlier world-item footprint for the artist's smaller gun mesh.
     * The source's root pivot replaces the two obsolete prototype pivots.
     */
    public static void preserveNonFirstPersonSize(PoseStack pose) {
        pose.translate(0, 4 / 16F, 2 / 16F);
        pose.scale(2.5F, 2.5F, 2.5F);
        pose.translate(0, -4 / 16F, -2 / 16F);
    }
}
