package com.antaurora.apofirstlight.fluid;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block with AFL fluid ports: the faces where a Fluid Pipe V2 connects (its port flange meets the device's port plate,
 * see docs/models/fluid_pipe_v2.md "流体接口规格" and tools/afl-fluid-port.mjs). The port cell's block entity provides
 * the FLUID_HANDLER capability on that face. Every new fluid device declares its ports here; the older ones (vertical
 * fluid tank, thermal generator, chemical reactor) are still recognised by FluidPipeBlock directly.
 */
public interface AflFluidPortBlock {
    boolean hasFluidPort(BlockState state, Direction face);
}
