package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Ray-tests the swung leaves even where they project into a neighboring air cell. */
public final class BeverageCoolerDoorRaycast {
    private BeverageCoolerDoorRaycast() {}

    public record DoorHit(BlockPos master, boolean left, BlockHitResult hit) {}

    public static DoorHit find(Level level, Player player, Vec3 start, Vec3 end) {
        int minX = (int) Math.floor(Math.min(start.x, end.x)) - 2;
        int minY = (int) Math.floor(Math.min(start.y, end.y)) - 2;
        int minZ = (int) Math.floor(Math.min(start.z, end.z)) - 2;
        int maxX = (int) Math.floor(Math.max(start.x, end.x)) + 2;
        int maxY = (int) Math.floor(Math.max(start.y, end.y)) + 2;
        int maxZ = (int) Math.floor(Math.max(start.z, end.z)) + 2;
        DoorHit nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;

        for (BlockPos cursor : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            if (!level.hasChunkAt(cursor)) continue;
            BlockState masterState = level.getBlockState(cursor);
            if (!(masterState.getBlock() instanceof BeverageCoolerBlock)
                    || masterState.getValue(BeverageCoolerBlock.PART) != BeverageCoolerBlock.Part.LOWER_LEFT
                    || (!masterState.getValue(BeverageCoolerBlock.LEFT_OPEN)
                    && !masterState.getValue(BeverageCoolerBlock.RIGHT_OPEN))) continue;

            BlockPos master = cursor.immutable();
            for (BeverageCoolerBlock.Part part : BeverageCoolerBlock.Part.values()) {
                boolean left = part.isLeft();
                if (!masterState.getValue(left ? BeverageCoolerBlock.LEFT_OPEN : BeverageCoolerBlock.RIGHT_OPEN))
                    continue;
                BlockPos partPos = BeverageCoolerBlock.partPosition(master,
                        masterState.getValue(BeverageCoolerBlock.FACING), part);
                if (!level.hasChunkAt(partPos)) continue;
                BlockState partState = level.getBlockState(partPos);
                if (!(partState.getBlock() instanceof BeverageCoolerBlock)
                        || partState.getValue(BeverageCoolerBlock.PART) != part) continue;
                BlockHitResult hit = BeverageCoolerBlock.openDoorTarget(part,
                        masterState.getValue(BeverageCoolerBlock.FACING)).clip(start, end, partPos);
                if (hit == null) continue;
                double distance = start.distanceToSqr(hit.getLocation());
                if (distance >= nearestDistance) continue;

                BlockHitResult obstruction = level.clip(new ClipContext(start, hit.getLocation(),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                if (obstruction.getType() == HitResult.Type.BLOCK
                        && start.distanceToSqr(obstruction.getLocation()) + 1.0e-4 < distance) continue;
                nearest = new DoorHit(master, left, hit);
                nearestDistance = distance;
            }
        }
        return nearest;
    }
}
