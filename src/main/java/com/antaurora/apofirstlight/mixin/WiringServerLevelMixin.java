package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.wiring.WiringRemoval;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Building Wiring V1 (docs/gameplay/building_wiring_v1_design.md 8.2): every block change on the server (explosions,
 * pistons, commands, fluids, fire, code) passes Level#markAndNotifyBlock into ServerLevel#onBlockStateChange, which does not
 * call Level's: a cell that can no longer hold wire loses its nodes there, without a refund.
 */
@Mixin(ServerLevel.class)
public abstract class WiringServerLevelMixin {
    @Inject(method = "onBlockStateChange", at = @At("HEAD"))
    private void afl$wiringChanged(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        WiringRemoval.changed((ServerLevel) (Object) this, pos, newState);
    }
}
