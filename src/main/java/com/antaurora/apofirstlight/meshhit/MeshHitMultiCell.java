package com.antaurora.apofirstlight.meshhit;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block built of several cells whose model is drawn by one of them, the master (docs/rendering/mesh_hit_runtime_v1.md,
 * P2 multi-cell): an underground tank's port cell, a dispenser's a0 cell, a locker's lower half. Every cell of it hits on
 * the master's whole model (MeshHitModels#shape), so a ray or the crosshair meets the model in any cell, and the outline
 * of any cell is the whole model.
 */
public interface MeshHitMultiCell {
    /** The cell drawing the model of the block {@code pos} belongs to ({@code pos} itself for the master). */
    BlockPos meshHitMaster(BlockState state, BlockPos pos);
}
