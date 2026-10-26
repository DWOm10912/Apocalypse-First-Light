package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.liquid.world.LiquidWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Physical water V1 package 2 (docs/gameplay/water_v1_design.md 16.3): every block change on the server reaches
 * ServerLevel#onBlockStateChange (which does not call Level's); one next to the liquid rebuilds its columns. Stands in for
 * C's GroundEvents until that merges.
 */
@Mixin(ServerLevel.class)
public abstract class LiquidServerLevelMixin {
    @Inject(method = "onBlockStateChange", at = @At("HEAD"))
    private void afl$liquidGroundChanged(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        LiquidWorld.blockChanged((ServerLevel) (Object) this, pos, oldState, newState);
    }
}
