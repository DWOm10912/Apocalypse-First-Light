package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.world.biome.StartupSurfaceBiomeContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies Startup surface semantics only inside the SurfaceSystem thread-local scope. */
@Mixin(BiomeManager.class)
public abstract class BiomeManagerStartupSurfaceMixin {
    @Inject(method = "getBiome", at = @At("RETURN"), cancellable = true)
    private void apocalypse$resolveStartupSurfaceBiome(BlockPos pos,
                                                        CallbackInfoReturnable<Holder<Biome>> callbackInfo) {
        callbackInfo.setReturnValue(StartupSurfaceBiomeContext.resolve(
                pos.getX(), pos.getZ(), callbackInfo.getReturnValue()));
    }
}
