package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.weather.AflWeather;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Regional weather V1 (weather/AflWeather): where AFL has the weather, vanilla's one switch for the whole world stays off
 * (no rain and thunder timers, no world-wide rain sent to the clients, no strikes in every chunk). Rain is asked by place
 * (mixin LevelWeatherMixin); storms strike from AflWeather.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelWeatherMixin {
    @Shadow
    @Final
    private ServerLevelData serverLevelData;

    @Inject(method = "advanceWeatherCycle", at = @At("HEAD"), cancellable = true)
    private void afl$ownWeather(CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (!AflWeather.active(level)) return;
        serverLevelData.setRaining(false);
        serverLevelData.setThundering(false);
        level.setRainLevel(0);
        level.setThunderLevel(0);
        ci.cancel();
    }
}
