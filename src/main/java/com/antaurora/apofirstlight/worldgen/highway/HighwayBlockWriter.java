package com.antaurora.apofirstlight.worldgen.highway;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Placement boundary shared by reversible debug construction and owned worldgen writes. */
public interface HighwayBlockWriter {
    boolean set(BlockPos pos, BlockState state);

    default boolean owns(BlockPos pos) {
        return true;
    }

    default boolean mayAffectHorizontal(int centerX, int centerZ, int radius) {
        return true;
    }
}
