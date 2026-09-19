package com.antaurora.apofirstlight.worldgen.rural;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import java.util.List;

/** A frontage candidate or an accepted building's reserved access, in world XZ coordinates. */
public record RuralLotAnchor(BlockPos connection, BlockPos frontage, Direction facing,
                             BoundingBox usableBounds, List<RuralRoadSegment> access) {
    public RuralLotAnchor { access = List.copyOf(access); }
    public List<BoundingBox> accessBounds() { return access.stream().map(RuralRoadSegment::bounds).toList(); }
}
