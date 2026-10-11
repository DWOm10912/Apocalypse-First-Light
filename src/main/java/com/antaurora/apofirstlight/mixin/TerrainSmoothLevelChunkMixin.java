package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.terrainsmooth.SmoothChunkData;
import com.antaurora.apofirstlight.terrainsmooth.SmoothChunkHolder;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Terrain Smooth V1: the chunk carries its own data (natural surfaces, cached slopes), so it is dropped with the chunk. */
@Mixin(LevelChunk.class)
public abstract class TerrainSmoothLevelChunkMixin implements SmoothChunkHolder {
    @Unique private volatile SmoothChunkData afl$smoothData;

    @Override
    public SmoothChunkData afl$smoothData() {
        return afl$smoothData;
    }

    @Override
    public void afl$setSmoothData(SmoothChunkData data) {
        afl$smoothData = data;
    }
}
