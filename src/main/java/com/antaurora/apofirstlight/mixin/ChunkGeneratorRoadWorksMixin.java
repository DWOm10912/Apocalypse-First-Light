package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.worldgen.network.RoadChunkPass;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * C3.2 (docs/worldgen/federal_highways_c3.md, 10.4): the road's chunk pass runs at the start of the chunk's feature
 * step, before structures and features, on the chunk's own columns only (RoadChunkPass). Worlds without a bound road
 * network (every world but federal ones created with the road gates) return at once.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorRoadWorksMixin {
    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"))
    private void apocalypse$roadWorks(WorldGenLevel level, ChunkAccess chunk, StructureManager structures, CallbackInfo ci) {
        RoadChunkPass.apply(level, chunk);
    }
}
