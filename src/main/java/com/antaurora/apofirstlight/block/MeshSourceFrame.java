package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * The two-column Pure Mesh source frame of tools/build-beverage-cooler-v2.mjs and tools/build-charging-station-v1.mjs:
 * px, x -8..24 across both columns with the master the left column (x 8..24) as the viewer faces the front, y up from
 * the master's floor, front toward -z. Used for sound, prompt and particle positions.
 */
public final class MeshSourceFrame {
    private MeshSourceFrame() {
    }

    public static Vec3 toWorld(BlockPos master, Direction facing, double x, double y, double z) {
        Direction leftward = facing.getClockWise();
        double midX = master.getX() + 0.5 - leftward.getStepX() * 0.5, midZ = master.getZ() + 0.5 - leftward.getStepZ() * 0.5;
        double across = (x - 8) / 16, forward = -z / 16;
        return new Vec3(midX + leftward.getStepX() * across + facing.getStepX() * forward, master.getY() + y / 16,
                midZ + leftward.getStepZ() * across + facing.getStepZ() * forward);
    }

    /** A direction or velocity in the same axes, units unchanged: +x toward the viewer's left, -z toward the front. */
    public static Vec3 toWorldDirection(Direction facing, double x, double y, double z) {
        Direction leftward = facing.getClockWise();
        return new Vec3(leftward.getStepX() * x - facing.getStepX() * z, y, leftward.getStepZ() * x - facing.getStepZ() * z);
    }
}
