package com.antaurora.apofirstlight.dev.terrainsmooth.mixin;

import com.antaurora.apofirstlight.dev.terrainsmooth.SmoothWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Terrain smoothing prototype P1 (development runs only): the server half of LevelSmoothChangeMixin. ServerLevel overrides
 * Level#onBlockStateChange (points of interest) without calling it, so the Level hook never ran on the server and the
 * server kept the slopes of blocks that had been dug or placed; its move check then pulled the player back from every
 * position the client's updated slopes allowed (user test 2026-10-11: "like quicksand" where sand and gravel were dug).
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelSmoothChangeMixin {
    @Inject(method = "onBlockStateChange", at = @At("HEAD"))
    private void afl$smoothChanged(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        if (SmoothWorld.isAnyActive()) SmoothWorld.changed((ServerLevel) (Object) this, pos);
    }
}
