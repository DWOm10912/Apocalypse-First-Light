package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.PortableGeneratorPull;
import com.antaurora.apofirstlight.client.skeleton.SkeletonActions;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Portable diesel generator V1: the first-person view bends down toward the recoil starter during a pull
 * (PortableGeneratorPull#camera), after vanilla has placed the camera (as MaintenanceCameraMixin). With the player skeleton
 * drawing an action that steps the body (client/skeleton/SkeletonActions#camera), the view sits on the posed body's eye.
 */
@Mixin(Camera.class)
public abstract class PortableGeneratorCameraMixin {
    @Shadow protected abstract void setPosition(Vec3 pos);
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "setup", at = @At("TAIL"))
    private void afl$recoilPullCamera(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partial, CallbackInfo ci) {
        if (detached) return;
        Object[] bent = PortableGeneratorPull.camera((Camera) (Object) this, partial);
        if (bent != null) {
            setPosition((Vec3) bent[0]);
            setRotation((Float) bent[1], (Float) bent[2]);
        }
        // player actions V1: while an action steps the skeleton body away from the player's spot, the view stays on its eye
        Camera self = (Camera) (Object) this;
        Vec3 eye = SkeletonActions.camera(self);
        if (eye == null) return;
        setPosition(eye);
        float[] turn = SkeletonActions.cameraRotation(self.getYRot(), self.getXRot());
        if (turn != null) setRotation(turn[0], turn[1]);
    }
}
