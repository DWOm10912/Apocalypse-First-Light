package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.world.biome.StartupSurfaceBiomeContext;
import com.antaurora.apofirstlight.world.biome.SurfaceBiomeCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies Startup surface semantics only inside the SurfaceSystem thread-local scope. */
@Mixin(BiomeManager.class)
public abstract class BiomeManagerStartupSurfaceMixin {
    @Shadow
    @Final
    private BiomeManager.NoiseBiomeSource noiseBiomeSource;

    /** Inside a surface pass: skips the corner hashing when the 8 corners agree (SurfaceBiomeCache, design 23.5). */
    @Inject(method = "getBiome", at = @At("HEAD"), cancellable = true)
    private void apocalypse$uniformSurfaceBiome(BlockPos pos, CallbackInfoReturnable<Holder<Biome>> callbackInfo) {
        Holder<Biome> uniform = SurfaceBiomeCache.uniformCorners((BiomeManager) (Object) this, noiseBiomeSource,
                pos.getX() - 2 >> 2, pos.getY() - 2 >> 2, pos.getZ() - 2 >> 2);
        // returning here skips the RETURN injector below, so the Startup semantics are applied here too
        if (uniform != null)
            callbackInfo.setReturnValue(StartupSurfaceBiomeContext.resolve(pos.getX(), pos.getY(), pos.getZ(), uniform));
    }

    @Inject(method = "getBiome", at = @At("RETURN"), cancellable = true)
    private void apocalypse$resolveStartupSurfaceBiome(BlockPos pos,
                                                        CallbackInfoReturnable<Holder<Biome>> callbackInfo) {
        callbackInfo.setReturnValue(StartupSurfaceBiomeContext.resolve(
                pos.getX(), pos.getY(), pos.getZ(), callbackInfo.getReturnValue()));
    }
}
