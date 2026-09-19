package com.antaurora.apofirstlight.worldgen.rural;

import com.antaurora.apofirstlight.worldgen.structure.StructureTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Road-derived candidate geometry only; terrain acceptance remains with the natural planner. */
public final class RuralFrontagePlanner {
    private static final int FRONTAGE_STEP = 6;
    private static final int MIN_SETBACK = 3;
    private static final int LARGE_AGRICULTURAL_MIN_SETBACK = 2;
    private static final int DEEP_SETBACK = 6;
    private RuralFrontagePlanner() {}

    public record Frontage(RuralRoadSegment road, BlockPos center, Direction facing) {}
    public record Placement(BlockPos origin, Rotation rotation, BoundingBox bounds,
                            RuralLotAnchor anchor) {}

    public static List<Frontage> frontages(RuralRoadNetwork network, long seed, BlockPos center) {
        List<Frontage> result = new ArrayList<>();
        for (var road : network.segments()) {
            int length = road.start().distManhattan(road.end());
            for (int d = 2; d <= length - 2; d += FRONTAGE_STEP) {
                BlockPos p = road.start().relative(road.direction(), d);
                for (Direction outward : new Direction[]{road.direction().getClockWise(),
                        road.direction().getCounterClockWise()})
                    result.add(new Frontage(road, p, outward.getOpposite()));
            }
        }
        // Stable seed permutation avoids always filling the same end or side of the road first.
        result.sort(Comparator.comparingLong(f -> order(seed ^ center.asLong() ^ f.center().asLong()
                ^ ((long) f.facing().ordinal() * 0x9e3779b97f4a7c15L))));
        return List.copyOf(result);
    }

    public static List<Placement> placements(List<Frontage> frontages, StructureTemplate template,
            RuralStructurePool.Definition definition, BoundingBox reservation) {
        List<Placement> result = new ArrayList<>();
        var ordered = new ArrayList<>(frontages);
        ordered.sort(Comparator.comparingInt(f -> preference(definition.role(), f.road().type())));
        boolean deep = definition.role() == RuralStructurePool.Role.FARMHOUSE
                || definition.role() == RuralStructurePool.Role.AGRICULTURAL_LARGE;
        int minimum = definition.role() == RuralStructurePool.Role.AGRICULTURAL_LARGE
                ? LARGE_AGRICULTURAL_MIN_SETBACK : MIN_SETBACK;
        for (Frontage f : ordered) for (int setback : deep ? new int[]{DEEP_SETBACK, minimum}
                : new int[]{MIN_SETBACK}) {
            Rotation rotation = RuralLayoutPlanner.rotationFor(definition.frontDirection(), f.facing());
            BoundingBox local = RuralLayoutPlanner.boundsAt(template, rotation, BlockPos.ZERO);
            BlockPos localFront = RuralAccessPlanner.midpoint(local, f.facing());
            Direction outward = f.facing().getOpposite();
            BlockPos front = f.center().relative(outward, f.road().type().radius() + setback);
            BlockPos origin = front.subtract(localFront);
            BoundingBox bounds = RuralLayoutPlanner.boundsAt(template, rotation, origin);
            if (!RuralAccessPlanner.inside(bounds, reservation) || !fitsFrontage(bounds, f.road())) continue;
            Entry entry = entry(template, definition, rotation, origin, bounds, f.facing());
            BlockPos accessStart = entry.position().relative(entry.facing(), RuralRoadType.FARM_TRACK.radius() + 1);
            BlockPos connection = f.road().nearest(accessStart.getX(), accessStart.getZ())
                    .relative(outward, f.road().type().width / 2 + f.road().type().shoulder);
            RuralLotAnchor anchor = new RuralLotAnchor(connection, front, f.facing(), bounds,
                    List.of(), f.road(), entry.position(), entry.facing());
            result.add(new Placement(origin, rotation, bounds, anchor));
        }
        return result;
    }

    private static boolean fitsFrontage(BoundingBox bounds, RuralRoadSegment road) {
        return road.direction().getAxis() == Direction.Axis.X
                ? bounds.minX() >= Math.min(road.start().getX(), road.end().getX())
                    && bounds.maxX() <= Math.max(road.start().getX(), road.end().getX())
                : bounds.minZ() >= Math.min(road.start().getZ(), road.end().getZ())
                    && bounds.maxZ() <= Math.max(road.start().getZ(), road.end().getZ());
    }

    private record Entry(BlockPos position, Direction facing) {}
    private static Entry entry(StructureTemplate template, RuralStructurePool.Definition definition,
            Rotation rotation, BlockPos origin, BoundingBox bounds, Direction fallbackFacing) {
        var metadata = RuralStructurePool.catalog().metadata(definition.id());
        if (metadata != null) for (var socket : metadata.sockets()) {
            BlockPos p = socket.localPosition();
            var size = template.getSize();
            if (p.getX() < 0 || p.getX() >= size.getX() || p.getY() < 0 || p.getY() >= size.getY()
                    || p.getZ() < 0 || p.getZ() >= size.getZ()) continue;
            boolean boundary = switch (socket.facing()) {
                case NORTH -> p.getZ() == 0;
                case SOUTH -> p.getZ() == size.getZ() - 1;
                case WEST -> p.getX() == 0;
                case EAST -> p.getX() == size.getX() - 1;
                default -> false;
            };
            if (boundary) return new Entry(StructureTransform.world(p, rotation, origin),
                    rotation.rotate(socket.facing()));
        }
        return new Entry(RuralAccessPlanner.midpoint(bounds, fallbackFacing), fallbackFacing);
    }

    private static int preference(RuralStructurePool.Role role, RuralRoadType road) {
        return switch (role) {
            case AGRICULTURAL_LARGE, AGRICULTURAL_UTILITY, LANDMARK ->
                    road == RuralRoadType.FARM_TRACK ? 0 : road == RuralRoadType.SIDE ? 1 : 2;
            default -> road == RuralRoadType.SIDE ? 0 : road == RuralRoadType.MAIN ? 1 : 2;
        };
    }
    private static long order(long n) {
        n = (n ^ (n >>> 30)) * 0xbf58476d1ce4e5b9L;
        n = (n ^ (n >>> 27)) * 0x94d049bb133111ebL;
        return n ^ (n >>> 31);
    }
}
