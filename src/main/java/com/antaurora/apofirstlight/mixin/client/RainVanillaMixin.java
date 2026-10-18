package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.client.rain.RainRenderer;
import com.antaurora.apofirstlight.client.rain.RainShaderPack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rain V1 (client/rain/RainRenderer, docs/rendering/rain_v1.md): while AFL draws the rain (no shader pack), vanilla's rain
 * sheets and pixel rain splashes are left out. Its snow, the steam on lava and the rain sounds stay.
 */
@Mixin(LevelRenderer.class)
public abstract class RainVanillaMixin {
    /**
     * renderSnowAndRain left out while AFL (or the shader pack from AFL's data) draws the rain and no column within 10 of
     * the camera may snow (RainColumns#snowNear): with only rain around it would draw nothing, after a biome lookup per
     * column every frame.
     */
    @Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true, require = 0)
    private void afl$skipWithoutSnow(net.minecraft.client.renderer.LightTexture light, float partialTick, double camX, double camY, double camZ,
                                     CallbackInfo ci) {
        if ((RainRenderer.active() || RainShaderPack.active())
                && !RainRenderer.snowNear(net.minecraft.util.Mth.floor(camX), net.minecraft.util.Mth.floor(camZ), 10))
            ci.cancel();
    }

    /** renderSnowAndRain: a column where it rains counts as dry (no sheet); snow columns as they are. */
    @Redirect(method = "renderSnowAndRain", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    private Biome.Precipitation afl$noRainSheets(Biome biome, BlockPos pos) {
        Biome.Precipitation p = biome.getPrecipitationAt(pos);
        return p == Biome.Precipitation.RAIN && (RainRenderer.active() || RainShaderPack.active()) ? Biome.Precipitation.NONE : p;
    }

    /** tickRain: the rain splash particles not added (the column search and the sounds go on as before). */
    @Redirect(method = "tickRain", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private void afl$noRainParticles(ClientLevel level, ParticleOptions particle, double x, double y, double z, double vx, double vy, double vz) {
        if (particle == ParticleTypes.RAIN && (RainRenderer.active() || RainShaderPack.active())) return;
        level.addParticle(particle, x, y, z, vx, vy, vz);
    }
}
