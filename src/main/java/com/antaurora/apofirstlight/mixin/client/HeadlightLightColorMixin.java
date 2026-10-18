package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.vehicle.HeadlightField;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The T15 headlamps' dynamic block light (client/vehicle/HeadlightField, docs/vehicles/first_pickup_v0_design.md 8.10) in
 * the packed light of a cell: vanilla and Embeddium chunk meshing (Embeddium's LightDataAccess takes its "LU" value here),
 * particles, block entities, the road mesh (RoadClient#lights). max(stored, field).
 */
@Mixin(LevelRenderer.class)
public abstract class HeadlightLightColorMixin {
    @Inject(method = "getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"), cancellable = true)
    private static void afl$headlights(BlockAndTintGetter level, BlockState state, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        int packed = cir.getReturnValueI(), lit = HeadlightField.lightColor(packed, pos);
        if (lit != packed) cir.setReturnValue(lit);
    }
}
