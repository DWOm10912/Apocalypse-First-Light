package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.ground.NaturalGroundPass;
import com.mojang.datafixers.util.Either;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * N1c (docs/worldgen/natural_ground_n1_design.md 12): the natural ground swap (NaturalGroundPass) at the start of a
 * generating chunk's LIGHT step, when its neighbours are at INITIALIZE_LIGHT, so no feature writes into it any more.
 * Only a chunk being generated (still at INITIALIZE_LIGHT, not a saved chunk wrapped for loading); worlds without the
 * gate return at once.
 */
@Mixin(ChunkStatus.class)
public abstract class ChunkStatusNaturalGroundMixin {
    @Inject(method = "lightChunk", at = @At("HEAD"))
    private static void apocalypse$naturalGround(ThreadedLevelLightEngine light, ChunkAccess chunk,
                                                 CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> cir) {
        if (!(chunk instanceof ProtoChunk) || chunk instanceof ImposterProtoChunk || chunk.getStatus() != ChunkStatus.INITIALIZE_LIGHT) return;
        if (((ChunkAccessLevelAccessor) chunk).apocalypse$level() instanceof ServerLevel level) NaturalGroundPass.apply(level, chunk);
    }
}
