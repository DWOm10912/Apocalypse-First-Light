package com.antaurora.apofirstlight.block;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

final class HorizontalShapeUtils {
    private HorizontalShapeUtils() {
    }

    static Map<Direction, VoxelShape> rotations(VoxelShape north) {
        Map<Direction, VoxelShape> result = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            int turns = switch (facing) {
                case EAST -> 1;
                case SOUTH -> 2;
                case WEST -> 3;
                default -> 0;
            };
            VoxelShape rotated = north;
            for (int turn = 0; turn < turns; turn++) {
                VoxelShape previous = rotated;
                final VoxelShape[] accumulator = {Shapes.empty()};
                previous.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) ->
                        accumulator[0] = Shapes.or(accumulator[0],
                                Shapes.box(1.0 - maxZ, minY, minX, 1.0 - minZ, maxY, maxX)));
                rotated = accumulator[0].optimize();
            }
            result.put(facing, rotated);
        }
        return result;
    }
}
