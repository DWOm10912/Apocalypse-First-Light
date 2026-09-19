package com.antaurora.apofirstlight.worldgen.rural;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Inclusive axis-aligned centerline, with a square cap for connected right-angle joins. */
public record RuralRoadSegment(BlockPos start, BlockPos end, RuralRoadType type) {
    public RuralRoadSegment {
        start = new BlockPos(start.getX(), 0, start.getZ());
        end = new BlockPos(end.getX(), 0, end.getZ());
        if (start.equals(end) || (start.getX() != end.getX() && start.getZ() != end.getZ()))
            throw new IllegalArgumentException("Road segment must be nonzero and axis aligned");
        java.util.Objects.requireNonNull(type);
    }
    public Direction direction() {
        return start.getX() == end.getX() ? (end.getZ() > start.getZ() ? Direction.SOUTH : Direction.NORTH)
                : (end.getX() > start.getX() ? Direction.EAST : Direction.WEST);
    }
    public BoundingBox bounds() {
        int r = type.radius();
        return new BoundingBox(Math.min(start.getX(), end.getX()) - r, 0,
                Math.min(start.getZ(), end.getZ()) - r, Math.max(start.getX(), end.getX()) + r, 0,
                Math.max(start.getZ(), end.getZ()) + r);
    }
    public BlockPos nearest(int x, int z) {
        return new BlockPos(Math.max(Math.min(start.getX(), end.getX()), Math.min(Math.max(start.getX(), end.getX()), x)),
                0, Math.max(Math.min(start.getZ(), end.getZ()), Math.min(Math.max(start.getZ(), end.getZ()), z)));
    }
    public int distance(int x, int z) {
        BlockPos p = nearest(x, z);
        return Math.max(Math.abs(p.getX() - x), Math.abs(p.getZ() - z));
    }
    public RuralPlan.Road road(boolean branch) {
        return new RuralPlan.Road(direction(), bounds(), type.width, branch, this);
    }
}
