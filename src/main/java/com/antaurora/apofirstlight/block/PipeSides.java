package com.antaurora.apofirstlight.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A PipeBlock's six side flags turned or mirrored with its structure. Vanilla's PipeBlock does not override rotate or mirror,
 * so a cable or pipe in a turned template kept its links pointing the old way. The template's shape updates then
 * mirrored the equally wrong neighbours, and every run along the turned axis came apart. Found by
 * dev/GasStation01GameTests (2026-10-10): about 70 % of A1's cables and pipes changed state at 90 / 180 / 270 degrees.
 */
public final class PipeSides {
    private PipeSides() {
    }

    public static BlockState rotate(BlockState state, Rotation rotation) {
        BlockState turned = state;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            turned = turned.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(rotation.rotate(side)), state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side)));
        }
        return turned;
    }

    public static BlockState mirror(BlockState state, Mirror mirror) {
        BlockState turned = state;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            turned = turned.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(mirror.mirror(side)), state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side)));
        }
        return turned;
    }
}
