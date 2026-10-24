package com.antaurora.apofirstlight.mixin.client;

import com.antaurora.apofirstlight.vegetation.client.VegetationDynamics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A mesh plant's state changed (a footprint, a wheel, rising: MeshPlantBlock): no rebuild at all where the near layer
 * draws the block (the chunk mesh leaves it out either way), otherwise one off the render thread
 * (VegetationRebuildEmbeddiumMixin; docs/gameplay/vegetation_v1_design.md 12.5). Both ways a client block change asks:
 * the block's own (setBlockDirty with the states) and the update's (blockChanged).
 */
@Mixin(LevelRenderer.class)
public abstract class VegetationRebuildMixin {
    @Inject(method = "blockChanged", at = @At("HEAD"), cancellable = true)
    private void afl$plantChanged(BlockGetter level, BlockPos pos, BlockState old, BlockState now, int flags, CallbackInfo ci) {
        if (VegetationDynamics.plantChanged(pos, old, now)) ci.cancel();
    }

    @Inject(method = "blockChanged", at = @At("RETURN"))
    private void afl$plantChangedEnd(BlockGetter level, BlockPos pos, BlockState old, BlockState now, int flags, CallbackInfo ci) {
        VegetationDynamics.plantChangeDone();
    }

    @Inject(method = "setBlockDirty(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At("HEAD"), cancellable = true)
    private void afl$plantDirty(BlockPos pos, BlockState old, BlockState now, CallbackInfo ci) {
        if (VegetationDynamics.plantChanged(pos, old, now)) ci.cancel();
    }

    @Inject(method = "setBlockDirty(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At("RETURN"))
    private void afl$plantDirtyEnd(BlockPos pos, BlockState old, BlockState now, CallbackInfo ci) {
        VegetationDynamics.plantChangeDone();
    }
}
