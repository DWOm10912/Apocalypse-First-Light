package com.antaurora.apofirstlight.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * Shapes of the Steel-frame doors V1 (docs/models/steel_frame_doors_v1.md), from tools/build-steel-frame-doors-v1.mjs.
 * Canonical frame: the face toward the placer at z 0 (north), as the mesh is authored; the block turns it to
 * FACING.getOpposite(). Both halves keep the 1 px jambs (lining and casings); the upper half adds the head. The leaf hangs
 * at the face; HINGE RIGHT (the placer's right) is the hinge at -X.
 * Steel door: opens out of its cell toward the placer, the leaf then about 0.9 block in front of the face (a large
 * collision shape, within one block).
 * Wood door: opens into its own cell, the leaf along the hinge-side jamb.
 */
final class SteelFrameDoorShapes {
    private static final double LEAF_X0 = 1.05, LEAF_X1 = 14.95, LEAF_Z0 = 0.05, LEAF_Z1 = 0.76, LEAF_Y0 = 0.3, LEAF_TOP = 14.95;
    // open leaves for the right hinge (hinge at -X); the left hinge mirrors x
    private static final double[] STEEL_OPEN = {1.45, 2.16, -14.35, -0.45};
    private static final double[] WOOD_OPEN = {1.24, 1.95, 1.05, 14.95};

    private static final Map<Direction, VoxelShape>[] SHAPES = build();

    private SteelFrameDoorShapes() {}

    /** Index: steel 0 / wood 1, upper half, open, hinge left. */
    private static int key(boolean wood, boolean upper, boolean open, boolean left) {
        return (wood ? 8 : 0) | (upper ? 4 : 0) | (open ? 2 : 0) | (left ? 1 : 0);
    }

    @SuppressWarnings("unchecked")
    private static Map<Direction, VoxelShape>[] build() {
        Map<Direction, VoxelShape>[] out = new Map[16];
        for (int k = 0; k < 16; k++) {
            boolean wood = (k & 8) != 0, upper = (k & 4) != 0, open = (k & 2) != 0, left = (k & 1) != 0;
            double y0 = upper ? 0 : LEAF_Y0, y1 = upper ? LEAF_TOP : 16;
            VoxelShape frame = Shapes.or(Block.box(0, 0, 0, 1, 16, 16), Block.box(15, 0, 0, 16, 16, 16));
            if (upper) frame = Shapes.or(frame, Block.box(1, 15, 0, 15, 16, 16));
            VoxelShape leaf;
            if (!open) {
                leaf = Block.box(LEAF_X0, y0, LEAF_Z0, LEAF_X1, y1, LEAF_Z1);
            } else {
                double[] o = wood ? WOOD_OPEN : STEEL_OPEN;
                double x0 = left ? 16 - o[1] : o[0], x1 = left ? 16 - o[0] : o[1];
                leaf = Block.box(x0, y0, o[2], x1, y1, o[3]);
            }
            out[k] = HorizontalShapeUtils.rotations(Shapes.or(frame, leaf));
        }
        return out;
    }

    static VoxelShape shape(BlockState state, boolean wood) {
        Direction front = state.getValue(DoorBlock.FACING).getOpposite();
        return SHAPES[key(wood, state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER, state.getValue(DoorBlock.OPEN),
                state.getValue(DoorBlock.HINGE) == DoorHingeSide.LEFT)].get(front);
    }
}
