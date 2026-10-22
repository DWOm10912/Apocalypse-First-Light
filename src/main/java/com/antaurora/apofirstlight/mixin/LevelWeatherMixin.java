package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.weather.AflWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Regional weather V1 (weather/AflWeather): it rains on a block where the place's weather rains (instead of vanilla's one
 * switch for the world), under the open sky, on top, where the biome rains. Both sides.
 */
@Mixin(Level.class)
public abstract class LevelWeatherMixin {
    @Inject(method = "isRainingAt", at = @At("HEAD"), cancellable = true)
    private void afl$localRain(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        Level level = (Level) (Object) this;
        if (!AflWeather.active(level)) return;
        cir.setReturnValue(level.canSeeSky(pos)
                && level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos).getY() <= pos.getY()
                && AflWeather.wet(level, pos.getX() + 0.5, pos.getZ() + 0.5)
                && level.getBiome(pos).value().getPrecipitationAt(pos) == Biome.Precipitation.RAIN);
    }
}
