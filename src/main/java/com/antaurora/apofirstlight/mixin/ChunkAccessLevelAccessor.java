package com.antaurora.apofirstlight.mixin;

import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** N1c: the level a chunk in generation belongs to (ChunkMap makes every ProtoChunk with its ServerLevel). Read only. */
@Mixin(ChunkAccess.class)
public interface ChunkAccessLevelAccessor {
    @Accessor("levelHeightAccessor")
    LevelHeightAccessor apocalypse$level();
}
