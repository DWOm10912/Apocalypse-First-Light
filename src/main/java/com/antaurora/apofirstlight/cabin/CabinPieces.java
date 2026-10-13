package com.antaurora.apofirstlight.cabin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Cabin Exterior V1 (docs/models/cabin_exterior_v1.md): a block drawn by {@link CabinMeshModel}. Its look is a list of mesh
 * pieces (assets/apocalypse_firstlight/cabin_mesh/&lt;name&gt;.json, written by tools/build-cabin-exterior-v1.mjs, drawn for
 * the north side and turned in quarter turns), picked from the state and a few flags read from the neighbours at meshing
 * (the joins of a log wall, the edges of a roof, ...). Every neighbour read lies within one block (diagonals included), so
 * a change next to the block rebuilds its section and the look follows.
 */
public interface CabinPieces {
    /** This block's look flags at pos (from its neighbours); the same state and flags always give the same pieces. */
    int cabinFlags(BlockState state, BlockGetter level, BlockPos pos);

    /** The pieces of state with flags, each with its quarter turns clockwise seen from above (0: as drawn, north). */
    void cabinPieces(BlockState state, int flags, Sink sink);

    interface Sink {
        void add(String piece, int turn);
    }
}
