package com.antaurora.apofirstlight.worldgen.rural;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import java.util.*;

/** Checks the building and its full access footprint before either is accepted. */
public final class RuralAccessPlanner {
    private static final int BUILDING_GAP = 4;
    private RuralAccessPlanner() { }

    /** V2: connect only to the owning frontage, using a straight or single-elbow path. */
    public static RuralLotAnchor connect(RuralLotAnchor anchor, BoundingBox reservation,
                                         List<RuralPlan.Road> roads, List<RuralPlan.Lot> accepted) {
        BoundingBox lot = anchor.usableBounds();
        if (!inside(lot, reservation)) return null;
        for (var road : roads) if (intersects(lot, road.bounds())) return null;
        for (var previous : accepted) {
            if (intersects(expanded(lot, BUILDING_GAP), previous.bounds())) return null;
            if (previous.access() != null) for (var box : previous.access().accessBounds())
                if (intersects(padded(lot), box)) return null;
        }
        BlockPos entry = new BlockPos(anchor.entry().getX(), 0, anchor.entry().getZ());
        // The square cap reaches the block immediately outside the socket, never the template itself.
        BlockPos start = entry.relative(anchor.entryFacing(), RuralRoadType.FARM_TRACK.radius() + 1);
        BlockPos target = anchor.connection();
        for (BlockPos elbow : List.of(new BlockPos(start.getX(), 0, target.getZ()),
                new BlockPos(target.getX(), 0, start.getZ()))) {
            List<RuralRoadSegment> path = new ArrayList<>();
            add(path, start, elbow); add(path, elbow, target);
            if (path.isEmpty()) continue;
            boolean valid = true;
            for (var segment : path) {
                BoundingBox b = segment.bounds();
                if (!inside(b, reservation) || intersects(b, lot)) { valid = false; break; }
                for (var road : roads) if (intersects(b, road.bounds())) {
                    // Only the terminal connection may enter its own road's edge/shoulder.
                    boolean owner = road.segment() != null && sameRoad(anchor.sourceRoad(), road.segment());
                    if (!owner || !terminalOverlap(b, road.bounds(), target)) { valid = false; break; }
                }
                if (!valid) break;
                for (var previous : accepted) {
                    if (intersects(b, padded(previous.bounds()))) { valid = false; break; }
                    if (previous.access() != null) for (var other : previous.access().accessBounds())
                        if (intersects(b, other)) { valid = false; break; }
                    if (!valid) break;
                }
                if (!valid) break;
            }
            if (valid) return new RuralLotAnchor(target, anchor.frontage(), anchor.facing(), lot, path,
                    anchor.sourceRoad(), anchor.entry(), anchor.entryFacing());
        }
        return null;
    }
    private static boolean sameRoad(RuralRoadSegment child, RuralRoadSegment road) {
        return child != null && child.type() == road.type()
                && road.distance(child.start().getX(), child.start().getZ()) == 0
                && road.distance(child.end().getX(), child.end().getZ()) == 0;
    }
    private static boolean terminalOverlap(BoundingBox path, BoundingBox road, BlockPos target) {
        int radius = RuralRoadType.FARM_TRACK.radius() + RuralRoadType.MAIN.shoulder + RuralRoadType.MAIN.transition;
        return Math.max(path.minX(), road.minX()) >= target.getX() - radius
                && Math.min(path.maxX(), road.maxX()) <= target.getX() + radius
                && Math.max(path.minZ(), road.minZ()) >= target.getZ() - radius
                && Math.min(path.maxZ(), road.maxZ()) <= target.getZ() + radius;
    }
    private static BoundingBox expanded(BoundingBox b, int margin) {
        return new BoundingBox(b.minX() - margin, 0, b.minZ() - margin,
                b.maxX() + margin, 0, b.maxZ() + margin);
    }
    public static RuralLotAnchor connect(BoundingBox lot, Direction facing, BoundingBox reservation,
                                         List<RuralPlan.Road> roads, List<RuralPlan.Lot> accepted) {
        for (var previous : accepted) if (previous.access() != null)
            for (var box : previous.access().accessBounds()) if (intersects(padded(lot), box)) return null;
        BlockPos front = midpoint(lot, facing);
        BlockPos start = front.relative(facing, 2);
        var targets = new ArrayList<BlockPos>();
        for (var road : roads) if (road.segment() != null)
            targets.add(road.segment().nearest(start.getX(), start.getZ()));
        targets.sort(Comparator.comparingInt(start::distManhattan));
        for (BlockPos target : targets) {
            BlockPos lead = start.relative(facing, 2);
            BlockPos elbow = facing.getAxis() == Direction.Axis.X
                    ? new BlockPos(lead.getX(), 0, target.getZ()) : new BlockPos(target.getX(), 0, lead.getZ());
            List<RuralRoadSegment> path = new ArrayList<>();
            add(path, start, lead); add(path, lead, elbow); add(path, elbow, target);
            boolean valid = true;
            for (var segment : path) {
                BoundingBox b = segment.bounds();
                if (!inside(b, reservation) || intersects(b, lot)) { valid = false; break; }
                for (var previous : accepted) {
                    if (intersects(b, padded(previous.bounds()))) { valid = false; break; }
                    if (previous.access() != null) for (var other : previous.access().accessBounds())
                        if (overlapOutsideRoad(b, other, roads)) { valid = false; break; }
                    if (!valid) break;
                }
                if (!valid) break;
            }
            if (valid) return new RuralLotAnchor(target, front, facing, lot, path);
        }
        return null;
    }
    private static void add(List<RuralRoadSegment> path, BlockPos a, BlockPos b) {
        if (!a.equals(b)) path.add(new RuralRoadSegment(a, b, RuralRoadType.FARM_TRACK));
    }
    private static boolean overlapOutsideRoad(BoundingBox a, BoundingBox b, List<RuralPlan.Road> roads) {
        if (!intersects(a, b)) return false;
        for (int x = Math.max(a.minX(), b.minX()); x <= Math.min(a.maxX(), b.maxX()); x++)
            for (int z = Math.max(a.minZ(), b.minZ()); z <= Math.min(a.maxZ(), b.maxZ()); z++) {
                boolean roadCell = false;
                for (var road : roads) if (contains(road.bounds(), x, z)) { roadCell = true; break; }
                if (!roadCell) return true;
            }
        return false;
    }
    public static BlockPos midpoint(BoundingBox box, Direction facing) {
        int x = Math.floorDiv(box.minX() + box.maxX(), 2), z = Math.floorDiv(box.minZ() + box.maxZ(), 2);
        return switch (facing) {
            case NORTH -> new BlockPos(x, 0, box.minZ()); case SOUTH -> new BlockPos(x, 0, box.maxZ());
            case WEST -> new BlockPos(box.minX(), 0, z); case EAST -> new BlockPos(box.maxX(), 0, z);
            default -> throw new IllegalArgumentException("Horizontal frontage required");
        };
    }
    public static boolean contains(BoundingBox b, int x, int z) {
        return x >= b.minX() && x <= b.maxX() && z >= b.minZ() && z <= b.maxZ();
    }
    private static BoundingBox padded(BoundingBox b) {
        return new BoundingBox(b.minX() - 2, 0, b.minZ() - 2, b.maxX() + 2, 0, b.maxZ() + 2);
    }
    public static boolean intersects(BoundingBox a, BoundingBox b) {
        return a.minX() <= b.maxX() && a.maxX() >= b.minX() && a.minZ() <= b.maxZ() && a.maxZ() >= b.minZ();
    }
    public static boolean inside(BoundingBox b, BoundingBox outer) {
        return b.minX() >= outer.minX() && b.maxX() <= outer.maxX() && b.minZ() >= outer.minZ() && b.maxZ() <= outer.maxZ();
    }
}
