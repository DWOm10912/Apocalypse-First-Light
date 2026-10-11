package com.antaurora.apofirstlight.terrainsmooth;

/** Implemented by LevelChunk (mixin TerrainSmoothLevelChunkMixin): the chunk's Terrain Smooth data, null until computed. */
public interface SmoothChunkHolder {
    SmoothChunkData afl$smoothData();

    void afl$setSmoothData(SmoothChunkData data);
}
