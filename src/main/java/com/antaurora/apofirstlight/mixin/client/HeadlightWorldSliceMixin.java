package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.vehicle.HeadlightField;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The T15 headlamps' dynamic block light (client/vehicle/HeadlightField, design doc 8.10) in Embeddium's chunk build
 * world: its smooth light reads each cell's block light here (LightDataAccess "BL"), not through
 * LevelRenderer#getLightColor. Embeddium 5681725 (the dev mapping keeps the official name, a release jar the SRG name);
 * without Embeddium the mixin is skipped. max(stored, field).
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.world.WorldSlice", remap = false)
public abstract class HeadlightWorldSliceMixin {
    @Inject(method = {"getBrightness", "m_45517_"}, at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void afl$headlights(LightLayer layer, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (layer != LightLayer.BLOCK) return;
        int d = HeadlightField.level(pos);
        if (d > cir.getReturnValueI()) cir.setReturnValue(d);
    }
}
