package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.weather.AflWeather;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Regional weather V1 (weather/AflWeather): rain puts fire out where it rains. Vanilla first asks whether it rains
 * anywhere (its one switch, off under AFL's weather), then near the fire (isRainingAt, the place's weather).
 */
@Mixin(FireBlock.class)
public abstract class FireBlockRainMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isRaining()Z"))
    private boolean afl$rainHere(ServerLevel level) {
        return level.isRaining() || AflWeather.active(level);
    }
}
