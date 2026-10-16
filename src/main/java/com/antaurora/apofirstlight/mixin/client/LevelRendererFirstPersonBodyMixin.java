package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.FirstPersonBody;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First-person body V1 (docs/rendering/first_person_body_v1.md): the entity loop skips the camera entity unless the camera
 * is detached (the only isDetached call in LevelRenderer); in first person it is let through when client/FirstPersonBody
 * draws the body this frame. renderEntity, also used by Oculus's shadow pass, marks the camera entity's own draw, and
 * tells the player skeleton (client/skeleton/PlayerSkeleton) which entity the world pass draws (GUI previews are not).
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererFirstPersonBodyMixin {
    @Redirect(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;isDetached()Z"))
    private boolean afl$firstPersonBody(Camera camera) {
        return camera.isDetached() || FirstPersonBody.shown(camera);
    }

    @Inject(method = "renderEntity", at = @At("HEAD"))
    private void afl$bodyBegin(Entity entity, double camX, double camY, double camZ, float partialTick, PoseStack pose,
            MultiBufferSource buffers, CallbackInfo ci) {
        FirstPersonBody.begin(entity);
        com.antaurora.apofirstlight.client.skeleton.PlayerSkeleton.worldBegin(entity);
    }

    @Inject(method = "renderEntity", at = @At("RETURN"))
    private void afl$bodyEnd(Entity entity, double camX, double camY, double camZ, float partialTick, PoseStack pose,
            MultiBufferSource buffers, CallbackInfo ci) {
        FirstPersonBody.end();
        com.antaurora.apofirstlight.client.skeleton.PlayerSkeleton.worldEnd();
    }
}
