package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.FireBlockFlames;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every block change on this client goes through here (ClientLevel#sendBlockUpdated): fire blocks appearing or going
 * are followed by client/FireBlockFlames, which draws them with the fuel flames (docs/gameplay/fuel_fire_v1.md).
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererFireTrackMixin {
    @Inject(method = "blockChanged", at = @At("HEAD"))
    private void afl$followFire(BlockGetter level, BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        FireBlockFlames.changed(pos, oldState, newState);
    }
}
