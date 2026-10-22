package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.earthwork.EarthChunk;
import com.antaurora.apofirstlight.earthwork.EarthChunkHolder;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Earthwork A1: the server chunk carries its own height field, so it loads, saves and unloads with its blocks. */
@Mixin(LevelChunk.class)
public abstract class EarthworkLevelChunkMixin implements EarthChunkHolder {
    @Unique private EarthChunk afl$earth;

    @Override
    public EarthChunk afl$earth() {
        return afl$earth;
    }

    @Override
    public void afl$setEarth(EarthChunk data) {
        afl$earth = data;
    }
}
