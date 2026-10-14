package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.wiring.WiringChunk;
import com.antaurora.apofirstlight.wiring.WiringChunkHolder;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Building Wiring V1: the wires in a chunk's blocks live on the chunk (saved with it by wiring.WiringStorage). */
@Mixin(LevelChunk.class)
public abstract class WiringLevelChunkMixin implements WiringChunkHolder {
    @Unique private WiringChunk afl$wiring;

    @Override
    public WiringChunk afl$wiring() {
        return afl$wiring;
    }

    @Override
    public void afl$setWiring(WiringChunk wiring) {
        afl$wiring = wiring;
    }
}
