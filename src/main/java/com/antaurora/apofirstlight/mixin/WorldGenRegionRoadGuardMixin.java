package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.worldgen.network.RoadChunkPass;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * C3.2 (docs/worldgen/federal_highways_c3.md, 10.4): features and structure pieces written during world generation
 * (this chunk's or a neighbour's, in any order) never land in a finished road column at or above its guard height:
 * no tree, snow layer, boulder or house wall on the pavement, the slopes or the deck; on graded turf only the plants
 * that grow there (RoadChunkPass#plantOnTurf). Only generation writes pass here; players and the running world are
 * unaffected.
 */
@Mixin(WorldGenRegion.class)
public abstract class WorldGenRegionRoadGuardMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"), cancellable = true)
    private void apocalypse$roadGuard(BlockPos pos, BlockState state, int flags, int recursion, CallbackInfoReturnable<Boolean> cir) {
        if (RoadChunkPass.guarded((WorldGenRegion) (Object) this, pos, state)) cir.setReturnValue(false);
    }
}
