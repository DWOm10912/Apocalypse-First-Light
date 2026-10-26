package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.time.WorldTimeServer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * World Time V1 (time/WorldTimeServer, docs/gameplay/world_time_v1_design.md 4): only the day time's step in tickTime changes
 * (vanilla: time + 1 a tick while the daylight cycle is on); game time and scheduled events in the same method do not. Every
 * other set of the time is watched for a jump.
 */
@Mixin(ServerLevel.class)
public abstract class WorldTimeServerLevelMixin {
    @ModifyArg(method = "tickTime", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setDayTime(J)V"))
    private long afl$slowerDay(long next) {
        return WorldTimeServer.advance((ServerLevel) (Object) this, next);
    }

    @Inject(method = "setDayTime", at = @At("HEAD"))
    private void afl$beforeSetTime(long time, CallbackInfo ci) {
        WorldTimeServer.beforeSet((ServerLevel) (Object) this, time);
    }

    @Inject(method = "setDayTime", at = @At("TAIL"))
    private void afl$afterSetTime(long time, CallbackInfo ci) {
        WorldTimeServer.afterSet((ServerLevel) (Object) this, time);
    }
}
