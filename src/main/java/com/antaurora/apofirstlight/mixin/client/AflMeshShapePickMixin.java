package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.AflMeshShapePicking;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mesh Shape blocks with out-of-cell selection (open doors) take part in the crosshair pick. */
@Mixin(GameRenderer.class)
public abstract class AflMeshShapePickMixin {
    @Inject(method = "pick", at = @At("TAIL"))
    private void afl$pickMeshShapes(float partialTick, CallbackInfo ci) {
        AflMeshShapePicking.refine(partialTick);
    }
}
