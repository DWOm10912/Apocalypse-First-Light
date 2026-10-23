package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.world.biome.SurfaceBiomeCache;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Serves uncached noise biomes from the per-chunk cache while SurfaceSystem runs (SurfaceBiomeCache, design 23.5). */
@Mixin(WorldGenRegion.class)
public abstract class WorldGenRegionSurfaceBiomeCacheMixin {
    @Shadow
    public abstract ServerLevel getLevel();

    @Inject(method = "getUncachedNoiseBiome", at = @At("HEAD"), cancellable = true)
    private void apocalypse$cachedSurfaceBiome(int x, int y, int z, CallbackInfoReturnable<Holder<Biome>> cir) {
        Holder<Biome> biome = SurfaceBiomeCache.uncached(getLevel(), x, y, z);
        if (biome != null) cir.setReturnValue(biome);
    }

    @Inject(method = "getUncachedNoiseBiome", at = @At("RETURN"))
    private void apocalypse$storeSurfaceBiome(int x, int y, int z, CallbackInfoReturnable<Holder<Biome>> cir) {
        SurfaceBiomeCache.putUncached(getLevel(), x, y, z, cir.getReturnValue());
    }
}
