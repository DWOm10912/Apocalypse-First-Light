package com.antaurora.apofirstlight.mixin;

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

/** Supplies the actual generator biome source to the final noise-fill hook. */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorAquiferMixin {
    @Inject(method = "doFill", at = @At("HEAD"))
    private void apocalypse$beginAquiferContext(Blender blender, StructureManager structureManager,
                                                  RandomState randomState, ChunkAccess chunk, int cellNoiseMinY,
                                                  int cellCountY, CallbackInfoReturnable<ChunkAccess> cir) {
        BiomeSource biomeSource = ((NoiseBasedChunkGenerator) (Object) this).getBiomeSource();
        Climate.Sampler sampler = randomState.sampler();
        ScorchedAquiferContext.begin(biomeSource, sampler, ((RandomStateSeedAccess) (Object) randomState).apocalypse$getSeed());
    }

    @Inject(method = "doFill", at = @At("RETURN"))
    private void apocalypse$endAquiferContext(Blender blender, StructureManager structureManager,
                                                RandomState randomState, ChunkAccess chunk, int cellNoiseMinY,
                                                int cellCountY, CallbackInfoReturnable<ChunkAccess> cir) {
        ScorchedAquiferContext.end();
    }

    @Inject(method = "doCreateBiomes", at = @At("HEAD"))
    private void apocalypse$beginBiomeContext(Blender blender, RandomState randomState,
                                               StructureManager structureManager, ChunkAccess chunk,
                                               CallbackInfo ci) {
        BiomeSource biomeSource = ((NoiseBasedChunkGenerator) (Object) this).getBiomeSource();
        ScorchedAquiferContext.begin(biomeSource, randomState.sampler(),
                ((RandomStateSeedAccess) (Object) randomState).apocalypse$getSeed());
    }

    @Inject(method = "doCreateBiomes", at = @At("RETURN"))
    private void apocalypse$endBiomeContext(Blender blender, RandomState randomState,
                                             StructureManager structureManager, ChunkAccess chunk,
                                             CallbackInfo ci) {
        ScorchedAquiferContext.end();
    }

}
