package com.antaurora.apofirstlight.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Deliberately simple in-cell stair approximations for the fixed brace geometry. */
public final class FixedDiagonalBraceShapes {
    public static final VoxelShape A_X = ascendingX(false);
    public static final VoxelShape B_X = ascendingX(true);
    public static final VoxelShape X_X = Shapes.or(A_X, B_X).optimize();
    public static final VoxelShape A_Z = ascendingZ(false);
    public static final VoxelShape B_Z = ascendingZ(true);
    public static final VoxelShape X_Z = Shapes.or(A_Z, B_Z).optimize();

    private static VoxelShape ascendingX(boolean mirrored) {
        VoxelShape result = Shapes.empty();
        for (int step = 0; step < 8; step++) {
            int x = mirrored ? 14 - step * 2 : step * 2;
            int y = step * 2;
            result = Shapes.or(result, Block.box(x, y, 6.5D, x + 2, y + 2, 9.5D));
        }
        return result.optimize();
    }

    private static VoxelShape ascendingZ(boolean mirrored) {
        VoxelShape result = Shapes.empty();
        for (int step = 0; step < 8; step++) {
            int z = mirrored ? 14 - step * 2 : step * 2;
            int y = step * 2;
            result = Shapes.or(result, Block.box(6.5D, y, z, 9.5D, y + 2, z + 2));
        }
        return result.optimize();
    }

    private FixedDiagonalBraceShapes() {}
}
