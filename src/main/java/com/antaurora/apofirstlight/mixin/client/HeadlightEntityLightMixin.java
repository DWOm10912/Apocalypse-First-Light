package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.vehicle.HeadlightField;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The T15 headlamps' dynamic block light (client/vehicle/HeadlightField, design doc 8.10) on entities and the first person
 * hand: EntityRenderer#getBlockLightLevel, max(stored, field).
 */
@Mixin(EntityRenderer.class)
public abstract class HeadlightEntityLightMixin {
    @Inject(method = "getBlockLightLevel", at = @At("RETURN"), cancellable = true)
    private void afl$headlights(Entity entity, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        int d = HeadlightField.level(pos);
        if (d > cir.getReturnValueI()) cir.setReturnValue(d);
    }
}
