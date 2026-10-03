package com.antaurora.apofirstlight.meshshape;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * A block with parts standing above its own cell that the crosshair should still pick, such as an open lid. Vanilla only
 * tests a block while the view ray crosses its cell; client/AflMeshShapePicking also tests these boxes for the blocks up
 * to {@link #MAX_CELLS_BELOW} cells below the cells the ray crosses and keeps the nearer result, pulling the hit point
 * back into the block's cell for the server. The block must then decide what was aimed at from the player's own view
 * ray, not from that point.
 */
public interface AflOverhangPickBlock {
    int MAX_CELLS_BELOW = 2;

    /** World-space boxes above this cell that pick it (empty when there is nothing standing up). */
    List<AABB> overhangPickBoxes(BlockGetter level, BlockState state, BlockPos pos);
}
