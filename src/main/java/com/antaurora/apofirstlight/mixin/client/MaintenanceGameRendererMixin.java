package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(GameRenderer.class)
public abstract class MaintenanceGameRendererMixin {
    @Inject(method="getFov",at=@At("RETURN"),cancellable=true)
    private void afl$fixedFov(Camera camera,float partial,boolean useSetting,CallbackInfoReturnable<Double> cir){
        var state=MaintenanceModeClientState.INSTANCE;
        if(state.active())cir.setReturnValue(net.minecraft.util.Mth.lerp(state.blend(),cir.getReturnValue(),MaintenanceCameraController.FOV));
    }
    @Inject(method={"bobHurt","bobView"},at=@At("HEAD"),cancellable=true)
    private void afl$noBob(PoseStack pose,float partial,CallbackInfo ci){if(MaintenanceModeClientState.INSTANCE.active())ci.cancel();}
    @Inject(method="renderItemInHand",at=@At("HEAD"),cancellable=true)
    private void afl$noHands(PoseStack pose,Camera camera,float partial,CallbackInfo ci){if(MaintenanceModeClientState.INSTANCE.active())ci.cancel();}
}
