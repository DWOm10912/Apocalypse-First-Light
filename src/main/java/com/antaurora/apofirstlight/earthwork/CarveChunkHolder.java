package com.antaurora.apofirstlight.earthwork;

/** Implemented by LevelChunk (mixin EarthworkLevelChunkMixin): the chunk's carved rock on the server, null when none. */
public interface CarveChunkHolder {
    CarveChunk afl$carve();

    void afl$setCarve(CarveChunk data);
}
