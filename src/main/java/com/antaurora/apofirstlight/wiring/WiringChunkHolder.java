package com.antaurora.apofirstlight.wiring;

import org.jetbrains.annotations.Nullable;

/** Implemented by LevelChunk (mixin WiringLevelChunkMixin): the chunk's Building Wiring nodes, server side; null until any. */
public interface WiringChunkHolder {
    @Nullable WiringChunk afl$wiring();

    void afl$setWiring(@Nullable WiringChunk wiring);
}
