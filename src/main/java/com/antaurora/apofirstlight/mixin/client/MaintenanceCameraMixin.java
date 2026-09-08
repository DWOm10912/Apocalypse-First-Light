package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.*;
import net.minecraft.client.Camera;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class MaintenanceCameraMixin {
    @Shadow protected abstract void setPosition(Vec3 pos);
    @Shadow protected abstract void setRotation(float yaw,float pitch);
    @Inject(method="setup",at=@At("TAIL"))
    private void afl$maintenanceCamera(BlockGetter level,Entity entity,boolean detached,boolean mirrored,float partial,CallbackInfo ci){
        var state=MaintenanceModeClientState.INSTANCE;if(!state.active())return;
        setRotation(MaintenanceCameraController.yaw(state.facing()),MaintenanceCameraController.PITCH);
        setPosition(state.cameraPosition());
    }
}
