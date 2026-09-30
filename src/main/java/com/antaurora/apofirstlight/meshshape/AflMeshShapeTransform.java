package com.antaurora.apofirstlight.meshshape;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Canonical NORTH, bottom-centred source pixels -> queried cell's block-local coordinates. */
public final class AflMeshShapeTransform {
    private AflMeshShapeTransform() {}

    /** Deliberately NOT Direction.get2DDataValue(), whose zero is SOUTH. */
    public static int index(Direction facing) {
        return switch (facing) { case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0; };
    }

    public static Vec3 point(double x, double y, double z, int cell, Direction facing) {
        Vec3 p = new Vec3((x + 8) / 16, y / 16 - cell, (z + 8) / 16);
        // Same quarter turn as HorizontalShapeUtils and the native BER's -90 degree Y rotation.
        for (int i = 0; i < index(facing); i++) p = new Vec3(1 - p.z, p.y, p.x);
        return p;
    }

    public static AABB box(double[] b, int cell, Direction facing) {
        // Quarter turns preserve axis alignment; AABB normalizes the transformed opposite corners.
        return new AABB(point(b[0], b[1], b[2], cell, facing), point(b[3], b[4], b[5], cell, facing));
    }
}
