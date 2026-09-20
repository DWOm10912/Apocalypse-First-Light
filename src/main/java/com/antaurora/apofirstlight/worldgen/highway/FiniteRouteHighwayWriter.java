package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.worldgen.spatial.BoundsXZ;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Applies the graph's finite approval envelope to all existing placement/clearance passes. */
final class FiniteRouteHighwayWriter implements HighwayBlockWriter {
    private final HighwayBlockWriter delegate;
    private final BoundsXZ bounds;

    FiniteRouteHighwayWriter(HighwayBlockWriter delegate, HighwayRouteGraph.Edge edge) {
        this.delegate = delegate;
        this.bounds = edge.bounds(HighwayRouteGraph.CONSTRUCTION_HALF_WIDTH);
    }

    @Override
    public boolean owns(BlockPos pos) {
        return bounds.contains(pos.getX(), pos.getZ()) && delegate.owns(pos);
    }

    @Override
    public boolean set(BlockPos pos, BlockState state) {
        return owns(pos) && delegate.set(pos, state);
    }

    @Override
    public boolean mayAffectHorizontal(int x, int z, int radius) {
        return (long) x + radius >= bounds.minX() && (long) x - radius < bounds.maxXExclusive()
                && (long) z + radius >= bounds.minZ() && (long) z - radius < bounds.maxZExclusive()
                && delegate.mayAffectHorizontal(x, z, radius);
    }
}
