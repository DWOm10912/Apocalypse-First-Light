package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * U2 (docs/worldgen/underground_geology_caves_v0_design.md section 20): a deep world has only its planned caves, so the
 * vanilla carvers (caves, canyons) never run in it. Other worlds keep them (WorldCarverStabilityMixin still guards them).
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorUndergroundMixin {
    @Inject(method = "applyCarvers", at = @At("HEAD"), cancellable = true)
    private void apocalypse$noCarversInDeepWorld(WorldGenRegion region, long seed, RandomState randomState,
                                                 BiomeManager biomeManager, StructureManager structureManager,
                                                 ChunkAccess chunk, GenerationStep.Carving step, CallbackInfo ci) {
        if (((RandomStateSeedAccess) (Object) randomState).apocalypse$underground() != null) ci.cancel();
    }
}
