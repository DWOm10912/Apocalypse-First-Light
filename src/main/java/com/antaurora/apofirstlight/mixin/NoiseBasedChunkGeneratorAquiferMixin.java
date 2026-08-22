package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.worldgen.StartupBiomeGenerationContext;
import com.antaurora.apofirstlight.worldgen.aquifer.ScorchedAquiferContext;
import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Supplies the actual generator biome source to the final noise-fill hook. */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorAquiferMixin {
    private static final ConcurrentHashMap<String, AtomicInteger> APOCALYPSE_CONTEXT_LOG_COUNTS = new ConcurrentHashMap<>();
    @Inject(method = "doFill", at = @At("HEAD"))
    private void apocalypse$beginAquiferContext(Blender blender, StructureManager structureManager,
                                                  RandomState randomState, ChunkAccess chunk, int cellNoiseMinY,
                                                  int cellCountY, CallbackInfoReturnable<ChunkAccess> cir) {
        BiomeSource biomeSource = ((NoiseBasedChunkGenerator) (Object) this).getBiomeSource();
        Climate.Sampler sampler = randomState.sampler();
        long seed = ((RandomStateSeedAccess) (Object) randomState).apocalypse$getSeed();
        StartupBiomeGenerationContext.register(biomeSource, seed);
        ScorchedAquiferContext.begin(biomeSource, sampler, seed);
        apocalypse$logContext("ECOLOGY_BEGIN_FILL", seed);
    }

    @Inject(method = "doFill", at = @At("RETURN"))
    private void apocalypse$endAquiferContext(Blender blender, StructureManager structureManager,
                                                RandomState randomState, ChunkAccess chunk, int cellNoiseMinY,
                                                int cellCountY, CallbackInfoReturnable<ChunkAccess> cir) {
        ScorchedAquiferContext.end();
        apocalypse$logContext("ECOLOGY_END_FILL", 0L);
    }

    /** Registers immutable seed state before biome generation can fan out to worker continuations. */
    @Inject(method = "doCreateBiomes", at = @At("HEAD"))
    private void apocalypse$registerBiomeGenerationSeed(Blender blender, RandomState randomState,
                                                         StructureManager structureManager, ChunkAccess chunk,
                                                         CallbackInfo callbackInfo) {
        BiomeSource biomeSource = ((NoiseBasedChunkGenerator) (Object) this).getBiomeSource();
        StartupBiomeGenerationContext.register(biomeSource,
                ((RandomStateSeedAccess) (Object) randomState).apocalypse$getSeed());
    }

    private static void apocalypse$logContext(String reason, long seed) {
        AtomicInteger count = APOCALYPSE_CONTEXT_LOG_COUNTS.computeIfAbsent(reason, ignored -> new AtomicInteger());
        if (count.getAndIncrement() >= 4) return;
        ApocalypseFirstLight.LOGGER.info(
                "[AFL STARTUP DIAG] reason={} thread={} seed={} scorchedAquiferContext={} contextIdentity={}",
                reason, Thread.currentThread().getName(), seed,
                "ECOLOGY_END_BIOMES".equals(reason) || "ECOLOGY_END_FILL".equals(reason) ? "NO" : "YES",
                "ECOLOGY_END_BIOMES".equals(reason) || "ECOLOGY_END_FILL".equals(reason)
                        ? "<cleared>" : Integer.toHexString(System.identityHashCode(ScorchedAquiferContext.current())));
    }

}
