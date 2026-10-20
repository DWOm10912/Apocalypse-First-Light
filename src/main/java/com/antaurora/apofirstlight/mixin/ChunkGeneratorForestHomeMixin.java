package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.worldgen.home.ForestHomeChunkPass;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * M4 round 1 (docs/worldgen/forest_home_b_m4_placement.md): the forest start home's chunk pass runs at the start of the
 * chunk's feature step, before structures and features (ForestHomeChunkPass). Worlds without an active start home
 * reservation return at once.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorForestHomeMixin {
    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"))
    private void apocalypse$forestHome(WorldGenLevel level, ChunkAccess chunk, StructureManager structures, CallbackInfo ci) {
        ForestHomeChunkPass.apply(level, chunk);
    }

    /** County road G3: the driveway's farm gate and mailbox after the road's pass and the features (ForestHomeChunkPass#fixtures). */
    @Inject(method = "applyBiomeDecoration", at = @At("RETURN"))
    private void apocalypse$forestHomeFixtures(WorldGenLevel level, ChunkAccess chunk, StructureManager structures, CallbackInfo ci) {
        ForestHomeChunkPass.fixtures(level, chunk);
    }
}
