package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.camera.CameraDirector;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Camera director V1 (client/camera/CameraDirector Lock.LOOK, docs/dev/camera_director_v1_plan.md 2.7): while a shot locks
 * the look, the mouse movement gathered since the last frame is dropped before it turns the player, so the player's
 * rotation (and what the server sees) stays where it was, and nothing is stored up to jump in when the lock ends. The
 * orbit camera (client/camera/OrbitCamera) takes the same movement to turn itself instead of the player.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerLookLockMixin {
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void afl$lookLock(CallbackInfo ci) {
        if (CameraDirector.lookLocked() || com.antaurora.apofirstlight.client.camera.OrbitCamera.takeMouse(accumulatedDX, accumulatedDY)) {
            accumulatedDX = 0;
            accumulatedDY = 0;
        }
    }
}
