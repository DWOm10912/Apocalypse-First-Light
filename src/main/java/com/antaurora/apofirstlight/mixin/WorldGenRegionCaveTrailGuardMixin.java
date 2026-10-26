package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.worldgen.underground.CaveTrailPass;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * U5b (docs/worldgen/underground_geology_caves_v0_design.md 24.5): no plant lands on a cave trail's tread during world
 * generation, whichever chunk's features write it (CaveTrailPass#refused). Players and the running world are unaffected.
 */
@Mixin(WorldGenRegion.class)
public abstract class WorldGenRegionCaveTrailGuardMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"), cancellable = true)
    private void apocalypse$caveTrailGuard(BlockPos pos, BlockState state, int flags, int recursion, CallbackInfoReturnable<Boolean> cir) {
        if (CaveTrailPass.refused((WorldGenRegion) (Object) this, pos, state)) cir.setReturnValue(false);
    }
}
