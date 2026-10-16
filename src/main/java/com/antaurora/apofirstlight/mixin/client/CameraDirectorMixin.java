package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.camera.CameraDirector;
import com.antaurora.apofirstlight.client.camera.CameraPose;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Camera director V1 (client/camera/CameraDirector, docs/dev/camera_director_v1_plan.md 2.1): the open shots place the
 * main camera after vanilla has. Priority 1100: applied after the views not yet moved over (MaintenanceCameraMixin,
 * PortableGeneratorCameraMixin, both default 1000), so at the shared TAIL its callback runs last and starts from theirs.
 */
@Mixin(value = Camera.class, priority = 1100)
public abstract class CameraDirectorMixin {
    @Shadow protected abstract void setPosition(Vec3 pos);
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "setup", at = @At("TAIL"))
    private void afl$directorShots(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partial, CallbackInfo ci) {
        Camera self = (Camera) (Object) this;
        if (self != Minecraft.getInstance().gameRenderer.getMainCamera()) return;
        CameraPose pose = CameraDirector.place(self, partial);
        if (pose == null) return;
        setPosition(pose.position());
        setRotation(pose.yaw(), pose.pitch());
    }
}
