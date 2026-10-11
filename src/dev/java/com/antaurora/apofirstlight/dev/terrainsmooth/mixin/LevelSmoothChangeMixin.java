package com.antaurora.apofirstlight.dev.terrainsmooth.mixin;

import com.antaurora.apofirstlight.dev.terrainsmooth.SmoothWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Terrain smoothing prototype P1 (development runs only). Every block change passes #onBlockStateChange (called from
 * markAndNotifyBlock whatever the update flags; on the client also for blocks the server sends): near the test area it
 * drops that side's shape cache and, on the client, re-meshes the sections the slopes around it depend on. This catches the
 * client only: ServerLevel overrides the method without calling it, see ServerLevelSmoothChangeMixin.
 */
@Mixin(Level.class)
public abstract class LevelSmoothChangeMixin {
    @Inject(method = "onBlockStateChange", at = @At("HEAD"))
    private void afl$smoothChanged(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        if (SmoothWorld.isAnyActive()) SmoothWorld.changed((Level) (Object) this, pos);
    }
}
