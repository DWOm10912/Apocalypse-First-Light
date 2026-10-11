package com.antaurora.apofirstlight.dev.terrainsmooth.mixin;

import com.antaurora.apofirstlight.dev.terrainsmooth.SmoothWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Terrain smoothing prototype P1 (development runs only: apocalypse_firstlight_dev.mixins.json, never in the release
 * jar). Inside the test area, natural ground blocks with a smoothed top collide and are selected as GRID x GRID boxes up to
 * the drawn slope (SmoothWorld#hook); in mode B those boxes can rise up to 0.75 above the cell, which vanilla's collision
 * sweep only looks at for states that report a large collision shape. Everything else returns the vanilla answer.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateSmoothShapeMixin {
    @Shadow protected abstract BlockState asState();

    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("HEAD"), cancellable = true)
    private void afl$smoothCollision(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (!SmoothWorld.hooksSeen()) SmoothWorld.markHooksSeen();
        if (!SmoothWorld.isAnyActive()) return;
        VoxelShape shape = SmoothWorld.hook(asState(), level, pos, true);
        if (shape != null) cir.setReturnValue(shape);
    }

    @Inject(method = "getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("HEAD"), cancellable = true)
    private void afl$smoothShape(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (!SmoothWorld.isAnyActive()) return;
        VoxelShape shape = SmoothWorld.hook(asState(), level, pos, false);
        if (shape != null) cir.setReturnValue(shape);
    }

    /** A smoothed top no longer suffocates (else the local player's push-out shoves the player off every slope box). */
    @Inject(method = "isSuffocating(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"), cancellable = true)
    private void afl$smoothSuffocating(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (SmoothWorld.isAnyActive() && SmoothWorld.notSuffocating(asState(), level, pos)) cir.setReturnValue(false);
    }

    @Inject(method = "hasLargeCollisionShape", at = @At("HEAD"), cancellable = true)
    private void afl$smoothLarge(CallbackInfoReturnable<Boolean> cir) {
        if (SmoothWorld.isAnyActive() && SmoothWorld.large(asState())) cir.setReturnValue(true);
    }
}
