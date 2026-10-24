package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.worldgen.underground.CaveTrailPass;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * U5b (docs/worldgen/underground_geology_caves_v0_design.md 24.5): the cave trails' chunk pass runs at the end of the
 * chunk's feature step, after the plants and the forest (CaveTrailPass). Worlds without planned trails return at once.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorCaveTrailMixin {
    @Inject(method = "applyBiomeDecoration", at = @At("RETURN"))
    private void apocalypse$caveTrail(WorldGenLevel level, ChunkAccess chunk, StructureManager structures, CallbackInfo ci) {
        CaveTrailPass.apply(level, chunk);
    }
}
