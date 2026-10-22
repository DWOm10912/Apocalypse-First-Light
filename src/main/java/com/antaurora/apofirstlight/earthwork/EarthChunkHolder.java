package com.antaurora.apofirstlight.earthwork;

/** Implemented by LevelChunk (mixin EarthworkLevelChunkMixin): the chunk's earthwork data on the server, null when none. */
public interface EarthChunkHolder {
    EarthChunk afl$earth();

    void afl$setEarth(EarthChunk data);
}
