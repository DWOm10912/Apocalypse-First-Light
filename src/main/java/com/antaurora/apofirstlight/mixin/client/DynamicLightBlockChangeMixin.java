package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.light.DynamicLights;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A block changed on the client: the dynamic lights' block grid builds its sections again (client/light/OccupancyGrid) and
 * the rain's column map reads that column again (client/rain/RainColumns).
 */
@Mixin(ClientLevel.class)
public abstract class DynamicLightBlockChangeMixin {
    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void afl$lights(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        DynamicLights.blockChanged((ClientLevel) (Object) this, pos);
        com.antaurora.apofirstlight.client.rain.RainRenderer.blockChanged((ClientLevel) (Object) this, pos);
    }
}
