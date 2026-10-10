package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.PortableGeneratorPull;
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
 * (PortableGeneratorPull#camera), after vanilla has placed the camera (as MaintenanceCameraMixin).
 */
@Mixin(Camera.class)
public abstract class PortableGeneratorCameraMixin {
    @Shadow protected abstract void setPosition(Vec3 pos);
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "setup", at = @At("TAIL"))
    private void afl$recoilPullCamera(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partial, CallbackInfo ci) {
        if (detached) return;
        Object[] bent = PortableGeneratorPull.camera((Camera) (Object) this, partial);
        if (bent == null) return;
        setPosition((Vec3) bent[0]);
        setRotation((Float) bent[1], (Float) bent[2]);
    }
}
