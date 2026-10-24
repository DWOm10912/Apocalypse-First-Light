package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.earthwork.CarveChunk;
import com.antaurora.apofirstlight.earthwork.CarveChunkHolder;
import com.antaurora.apofirstlight.earthwork.EarthChunk;
import com.antaurora.apofirstlight.earthwork.EarthChunkHolder;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Earthwork A1: the server chunk carries its own height field, so it loads, saves and unloads with its blocks; U3a-3
 * round 2: and its carved rock (CarveChunk).
 */
@Mixin(LevelChunk.class)
public abstract class EarthworkLevelChunkMixin implements EarthChunkHolder, CarveChunkHolder {
    @Unique private EarthChunk afl$earth;
    @Unique private CarveChunk afl$carve;

    @Override
    public EarthChunk afl$earth() {
        return afl$earth;
    }

    @Override
    public void afl$setEarth(EarthChunk data) {
        afl$earth = data;
    }

    @Override
    public CarveChunk afl$carve() {
        return afl$carve;
    }

    @Override
    public void afl$setCarve(CarveChunk data) {
        afl$carve = data;
    }
}
