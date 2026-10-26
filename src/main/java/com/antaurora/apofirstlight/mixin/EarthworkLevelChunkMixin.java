package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.earthwork.CarveChunk;
import com.antaurora.apofirstlight.earthwork.CarveChunkHolder;
import com.antaurora.apofirstlight.earthwork.EarthChunk;
import com.antaurora.apofirstlight.earthwork.EarthChunkHolder;
import com.antaurora.apofirstlight.road.RoadDamageChunk;
import com.antaurora.apofirstlight.road.RoadDamageHolder;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Earthwork A1: the server chunk carries its own height field, so it loads, saves and unloads with its blocks; U3a-3
 * round 2: and its carved rock (CarveChunk); road Mesh-ification P1: and its broken asphalt (RoadDamageChunk).
 */
@Mixin(LevelChunk.class)
public abstract class EarthworkLevelChunkMixin implements EarthChunkHolder, CarveChunkHolder, RoadDamageHolder {
    @Unique private EarthChunk afl$earth;
    @Unique private CarveChunk afl$carve;
    @Unique private RoadDamageChunk afl$roadDamage;

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

    @Override
    public RoadDamageChunk afl$roadDamage() {
        return afl$roadDamage;
    }

    @Override
    public void afl$setRoadDamage(RoadDamageChunk data) {
        afl$roadDamage = data;
    }
}
