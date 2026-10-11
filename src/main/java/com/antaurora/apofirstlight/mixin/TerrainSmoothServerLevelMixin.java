package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.terrainsmooth.TerrainSmooth;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Terrain Smooth V1, the server half of TerrainSmoothLevelMixin: ServerLevel overrides Level#onBlockStateChange (points of
 * interest) without calling it, so without this the server would keep the slopes of dug and placed blocks and its move
 * check would pull players back from every position the client's updated slopes allow (P1 user test, 2026-10-11).
 */
@Mixin(ServerLevel.class)
public abstract class TerrainSmoothServerLevelMixin {
    @Inject(method = "onBlockStateChange", at = @At("HEAD"))
    private void afl$smoothChanged(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        TerrainSmooth.changed((ServerLevel) (Object) this, pos);
    }
}
