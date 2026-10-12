package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.terrainsmooth.TerrainSmooth;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Terrain Smooth V1.1 (docs/worldgen/terrain_smooth_v1.md, R): MoveControl#tick jumps when the block at the mob's feet
 * has a shape whose highest point is above the feet. On a smoothed top the highest point is the slope's highest corner,
 * so a mob walking on any slope jumped. Here the height of a smoothed top is the column the mob stands on; every other
 * block keeps VoxelShape#max(Y). The only VoxelShape#max call in #tick.
 */
@Mixin(MoveControl.class)
public abstract class TerrainSmoothMoveControlMixin {
    @Shadow @Final protected Mob mob;

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/VoxelShape;max(Lnet/minecraft/core/Direction$Axis;)D"))
    private double afl$standingTop(VoxelShape shape, Direction.Axis axis) {
        if (axis == Direction.Axis.Y) {
            BlockPos pos = mob.blockPosition();
            double top = TerrainSmooth.standingTop(mob.level().getBlockState(pos).getBlock(), mob.level(), pos, mob.getX() - pos.getX(), mob.getZ() - pos.getZ());
            if (!Double.isNaN(top)) return top;
        }
        return shape.max(axis);
    }
}
