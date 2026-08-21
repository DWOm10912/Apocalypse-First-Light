package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.world.biome.StartupSurfaceBiomeContext;
import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Makes vanilla SurfaceRules use the same Startup biome semantics as chunk biome filling. */
@Mixin(SurfaceSystem.class)
public abstract class SurfaceSystemStartupBiomeMixin {
    @Inject(method = "buildSurface", at = @At("HEAD"))
    private void apocalypse$beginStartupSurfaceContext(RandomState randomState, BiomeManager biomeManager,
                                                        Registry<Biome> registry, boolean useLegacyRandomSource,
                                                        WorldGenerationContext generationContext, ChunkAccess chunk,
                                                        NoiseChunk noiseChunk, SurfaceRules.RuleSource ruleSource,
                                                        CallbackInfo callbackInfo) {
        StartupSurfaceBiomeContext.begin(
                ((RandomStateSeedAccess) (Object) randomState).apocalypse$getSeed(), registry);
    }

    @Inject(method = "buildSurface", at = @At("RETURN"))
    private void apocalypse$endStartupSurfaceContext(RandomState randomState, BiomeManager biomeManager,
                                                      Registry<Biome> registry, boolean useLegacyRandomSource,
                                                      WorldGenerationContext generationContext, ChunkAccess chunk,
                                                      NoiseChunk noiseChunk, SurfaceRules.RuleSource ruleSource,
                                                      CallbackInfo callbackInfo) {
        StartupSurfaceBiomeContext.end();
    }
}
