package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.terrainsmooth.TerrainSmooth;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Terrain Smooth V1: every block change passes #onBlockStateChange (called from markAndNotifyBlock whatever the update
 * flags; on the client also for blocks the server sends). This catches the client: ServerLevel overrides the method
 * without calling it (TerrainSmoothServerLevelMixin), which P1 learned from 389 silent server corrections on 2026-10-11.
 */
@Mixin(Level.class)
public abstract class TerrainSmoothLevelMixin {
    @Inject(method = "onBlockStateChange", at = @At("HEAD"))
    private void afl$smoothChanged(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        TerrainSmooth.changed((Level) (Object) this, pos);
    }
}
