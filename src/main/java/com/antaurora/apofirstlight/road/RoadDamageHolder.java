package com.antaurora.apofirstlight.road;

/** Implemented by LevelChunk (mixin EarthworkLevelChunkMixin): the chunk's broken asphalt on the server, null when none. */
public interface RoadDamageHolder {
    RoadDamageChunk afl$roadDamage();

    void afl$setRoadDamage(RoadDamageChunk data);
}
