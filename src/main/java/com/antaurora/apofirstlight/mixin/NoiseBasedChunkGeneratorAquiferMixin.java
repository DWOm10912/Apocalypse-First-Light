package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.worldgen.aquifer.ScorchedAquiferContext;
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
        ScorchedAquiferContext.begin(biomeSource, sampler);
    }

    @Inject(method = "doFill", at = @At("RETURN"))
    private void apocalypse$endAquiferContext(Blender blender, StructureManager structureManager,
                                                RandomState randomState, ChunkAccess chunk, int cellNoiseMinY,
                                                int cellCountY, CallbackInfoReturnable<ChunkAccess> cir) {
        ScorchedAquiferContext.end();
    }
}
