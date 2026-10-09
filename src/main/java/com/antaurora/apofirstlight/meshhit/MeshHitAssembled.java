package com.antaurora.apofirstlight.meshhit;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * A block whose model is assembled at run time from turned pieces (the curbs: a ground cube and the curb pieces its
 * neighbours call for; docs/rendering/mesh_hit_runtime_v1.md). Unlike {@link MeshHitProvider} the pieces are turned and
 * merged into one hit model, so the selection outline has no seams where two pieces meet.
 */
public interface MeshHitAssembled {
    /** A forge:obj piece model, turned {@code y} degrees (a multiple of 90, as BlockModelRotation y: north to east) about the block's centre. */
    record Piece(ResourceLocation model, int y) {}

    List<Piece> meshHitPieces(BlockGetter level, BlockPos pos, BlockState state);
}
