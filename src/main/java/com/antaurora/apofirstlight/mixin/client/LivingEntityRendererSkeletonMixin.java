package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.skeleton.PlayerSkeleton;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Player skeleton V1 prototype (docs/dev/player_skeleton_v1_design.md 1.3, 3; client/skeleton/PlayerSkeleton): a player's
 * draw is marked at HEAD (the render-origin frame), after vanilla's setupAnim the skeleton's pose is solved and written
 * to the six vanilla parts (so the layers follow it), and the model's renderToBuffer becomes the skeleton's skin mesh.
 * Everything is a no-op while {@code [skeleton] enabled} is off or for poses the skeleton does not draw.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererSkeletonMixin {
    private static final String RENDER = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    @Inject(method = RENDER, at = @At("HEAD"))
    private void afl$skeletonBegin(LivingEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers,
            int light, CallbackInfo ci) {
        PlayerSkeleton.begin(entity, partial, pose, buffers, light);
    }

    @Inject(method = RENDER, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"))
    private void afl$skeletonPose(LivingEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers,
            int light, CallbackInfo ci) {
        PlayerSkeleton.afterSetupAnim(entity, ((LivingEntityRenderer<?, ?>) (Object) this).getModel(), pose);
    }

    @SuppressWarnings("rawtypes")
    @Redirect(method = RENDER, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"))
    private void afl$skeletonDraw(EntityModel model, PoseStack pose, VertexConsumer consumer, int light, int overlay,
            float r, float g, float b, float a) {
        if (!PlayerSkeleton.draw(model, pose, consumer, light, overlay, r, g, b, a))
            model.renderToBuffer(pose, consumer, light, overlay, r, g, b, a);
    }

    @Inject(method = RENDER, at = @At("RETURN"))
    private void afl$skeletonEnd(LivingEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers,
            int light, CallbackInfo ci) {
        PlayerSkeleton.end(entity);
    }
}
