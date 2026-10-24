package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.worldgen.underground.GeologyPass;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * U3a (docs/worldgen/underground_u3_rocks_ores_design.md 2.2): a deep world's stone becomes the geology's rocks right
 * after the chunk's surface is built, before the features (GeologyPass). Worlds without planned caves return at once.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseGeneratorGeologyMixin {
    @Inject(method = "buildSurface(Lnet/minecraft/server/level/WorldGenRegion;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
            at = @At("RETURN"))
    private void apocalypse$geology(WorldGenRegion region, StructureManager structures, RandomState random, ChunkAccess chunk, CallbackInfo ci) {
        GeologyPass.apply(chunk, random);
    }
}
