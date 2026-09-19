package com.antaurora.apofirstlight.worldgen.rural;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import java.util.List;

/** A frontage candidate or an accepted building's reserved access, in world XZ coordinates. */
public record RuralLotAnchor(BlockPos connection, BlockPos frontage, Direction facing,
                             BoundingBox usableBounds, List<RuralRoadSegment> access,
                             RuralRoadSegment sourceRoad, BlockPos entry, Direction entryFacing) {
    public RuralLotAnchor { access = List.copyOf(access); }
    /** Compatibility constructor for saved V1 plans and the legacy development planner. */
    public RuralLotAnchor(BlockPos connection, BlockPos frontage, Direction facing,
                          BoundingBox usableBounds, List<RuralRoadSegment> access) {
        this(connection, frontage, facing, usableBounds, access, null, frontage, facing);
    }
    public int depth() { return facing.getAxis() == Direction.Axis.X
            ? usableBounds.getXSpan() : usableBounds.getZSpan(); }
    public RuralLotAnchor atOriginY(int originY) {
        return new RuralLotAnchor(connection, frontage, facing, usableBounds, access,
                sourceRoad, entry.above(originY), entryFacing);
    }
    public List<BoundingBox> accessBounds() { return access.stream().map(RuralRoadSegment::bounds).toList(); }
}
