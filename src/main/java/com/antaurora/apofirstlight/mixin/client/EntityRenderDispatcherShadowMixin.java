package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.camera.CameraDirector;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A body drawn away from its entity (client/camera/CameraDirector#bodyOffset, the camera director's body offset; today
 * player actions V1's render-only approach, client/skeleton/SkeletonActions#bodyOffset) takes its round shadow along. The shadow is laid on the blocks under the stepped body: its
 * sample point moves by the offset (x and z lerps) and the pose by the same offset, so the quads land where the body is.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherShadowMixin {
    @Unique
    private static Vec3 afl$shadowShift;

    @Inject(method = "renderShadow", at = @At("HEAD"))
    private static void afl$shadowHead(PoseStack pose, MultiBufferSource buffers, Entity entity, float opacity, float partial,
                                      LevelReader level, float radius, CallbackInfo ci) {
        afl$shadowShift = CameraDirector.bodyOffset(entity);
        if (afl$shadowShift == null) return;
        pose.pushPose();
        pose.translate(afl$shadowShift.x, 0, afl$shadowShift.z);
    }

    @Inject(method = "renderShadow", at = @At("RETURN"))
    private static void afl$shadowReturn(PoseStack pose, MultiBufferSource buffers, Entity entity, float opacity, float partial,
                                        LevelReader level, float radius, CallbackInfo ci) {
        if (afl$shadowShift == null) return;
        pose.popPose();
        afl$shadowShift = null;
    }

    @Redirect(method = "renderShadow", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp(DDD)D", ordinal = 0))
    private static double afl$shadowX(double t, double from, double to) {
        return Mth.lerp(t, from, to) + (afl$shadowShift == null ? 0 : afl$shadowShift.x);
    }

    @Redirect(method = "renderShadow", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp(DDD)D", ordinal = 2))
    private static double afl$shadowZ(double t, double from, double to) {
        return Mth.lerp(t, from, to) + (afl$shadowShift == null ? 0 : afl$shadowShift.z);
    }
}
