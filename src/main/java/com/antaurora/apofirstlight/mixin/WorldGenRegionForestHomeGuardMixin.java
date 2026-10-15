package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.worldgen.home.ForestHomeChunkPass;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * M4 round 1 (docs/worldgen/forest_home_b_m4_placement.md): features and structure pieces written during world
 * generation (this chunk's or a neighbour's, in any order) never land in the forest start home's template box; only the
 * template's own slices pass. Players and the running world are unaffected.
 */
@Mixin(WorldGenRegion.class)
public abstract class WorldGenRegionForestHomeGuardMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"), cancellable = true)
    private void apocalypse$forestHomeGuard(BlockPos pos, BlockState state, int flags, int recursion, CallbackInfoReturnable<Boolean> cir) {
        if (ForestHomeChunkPass.guarded((WorldGenRegion) (Object) this, pos)) cir.setReturnValue(false);
    }
}
