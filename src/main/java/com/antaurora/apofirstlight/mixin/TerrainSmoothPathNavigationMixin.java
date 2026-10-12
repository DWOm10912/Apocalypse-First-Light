package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.terrainsmooth.TerrainSmooth;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Terrain Smooth V1.1 (docs/worldgen/terrain_smooth_v1.md, R): the height a mob is sent to (PathNavigation#getGroundY,
 * MoveControl's wanted Y) is WalkNodeEvaluator#getFloorLevel of the node, the shape's highest point over the block
 * under it. On a smoothed top that is the slope's highest corner, up to half a block above where the mob will stand,
 * so the next node looked more than a step up and the mob jumped. Here a smoothed top under the node gives the height at
 * the node's centre; the node graph itself (pathfinding regions see vanilla blocks) is unchanged.
 */
@Mixin(PathNavigation.class)
public abstract class TerrainSmoothPathNavigationMixin {
    @Redirect(method = "getGroundY", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/pathfinder/WalkNodeEvaluator;getFloorLevel(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)D"))
    private double afl$floorAtCentre(BlockGetter level, BlockPos pos) {
        BlockPos below = pos.below();
        double top = TerrainSmooth.standingTop(level.getBlockState(below).getBlock(), level, below, 0.5D, 0.5D);
        return Double.isNaN(top) ? WalkNodeEvaluator.getFloorLevel(level, pos) : below.getY() + top;
    }
}
