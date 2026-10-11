package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.terrainsmooth.TerrainSmooth;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Terrain Smooth V1 (docs/worldgen/terrain_smooth_v1.md): a natural surface top of the six ground blocks collides and is
 * selected as the slope boxes and does not suffocate; every other block and position gets exactly the vanilla call.
 * Redirects of the calls inside BlockStateBase, not cancellable injections: these run for every shape query in the
 * game, and a redirect allocates nothing.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class TerrainSmoothBlockStateMixin {
    @Redirect(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getCollisionShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private VoxelShape afl$smoothCollision(Block block, BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape s = TerrainSmooth.shape(block, level, pos, true);
        return s != null ? s : block.getCollisionShape(state, level, pos, context);
    }

    @Redirect(method = "getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private VoxelShape afl$smoothShape(Block block, BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape s = TerrainSmooth.shape(block, level, pos, false);
        return s != null ? s : block.getShape(state, level, pos, context);
    }

    @Redirect(method = "isSuffocating(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockBehaviour$StatePredicate;test(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean afl$smoothSuffocating(BlockBehaviour.StatePredicate predicate, BlockState state, BlockGetter level, BlockPos pos) {
        return !TerrainSmooth.notSuffocating(state.getBlock(), level, pos) && predicate.test(state, level, pos);
    }
}
