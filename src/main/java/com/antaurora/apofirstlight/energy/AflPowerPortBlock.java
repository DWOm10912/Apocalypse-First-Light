package com.antaurora.apofirstlight.energy;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block with AFL power ports: the faces where a power cable connects (and plugs into the port's socket, see
 * docs/models/power_cable_v2.md for the port standard). The default is the existing machines' single port on the back,
 * the face opposite FACING; Pure Mesh machines declare their own faces.
 */
public interface AflPowerPortBlock {
    default boolean hasPowerPort(BlockState state, Direction face) {
        return state.hasProperty(HorizontalDirectionalBlock.FACING) && face == state.getValue(HorizontalDirectionalBlock.FACING).getOpposite();
    }
}
