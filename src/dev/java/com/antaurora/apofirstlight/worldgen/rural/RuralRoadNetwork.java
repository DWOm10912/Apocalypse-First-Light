package com.antaurora.apofirstlight.worldgen.rural;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import java.util.*;

/** Derived graph over saved geometry; contains no chunk-time planning or randomness. */
public record RuralRoadNetwork(List<Node> nodes, List<RuralRoadSegment> segments,
                               List<RuralLotAnchor> anchors, List<BoundingBox> occupiedAccess) {
    public enum Kind { START, END, TURN, THROUGH, T_JUNCTION }
    public record Node(BlockPos position, Kind kind) { }
    public RuralRoadNetwork {
        nodes = List.copyOf(nodes); segments = List.copyOf(segments);
        anchors = List.copyOf(anchors); occupiedAccess = List.copyOf(occupiedAccess);
    }
    public static RuralRoadNetwork from(List<RuralPlan.Road> roads, List<RuralPlan.Lot> lots,
                                        BoundingBox reservation) {
        List<RuralRoadSegment> source = roads.stream().map(RuralPlan.Road::segment).filter(Objects::nonNull).toList();
        Set<BlockPos> points = new LinkedHashSet<>();
        for (var s : source) { points.add(s.start()); points.add(s.end()); }
        List<RuralRoadSegment> edges = new ArrayList<>();
        for (var s : source) {
            List<BlockPos> cuts = points.stream().filter(p -> s.distance(p.getX(), p.getZ()) == 0)
                    .sorted(Comparator.comparingInt(p -> p.distManhattan(s.start()))).toList();
            for (int i = 1; i < cuts.size(); i++) edges.add(new RuralRoadSegment(cuts.get(i - 1), cuts.get(i), s.type()));
        }
        List<Node> nodes = new ArrayList<>();
        for (BlockPos p : points) {
            Set<Direction> directions = EnumSet.noneOf(Direction.class);
            for (var s : edges) {
                if (s.start().equals(p)) directions.add(s.direction());
                if (s.end().equals(p)) directions.add(s.direction().getOpposite());
            }
            Kind kind = directions.size() >= 3 ? Kind.T_JUNCTION : directions.size() == 1
                    ? (nodes.isEmpty() ? Kind.START : Kind.END)
                    : directions.size() == 2 && directions.contains(directions.iterator().next().getOpposite())
                    ? Kind.THROUGH : Kind.TURN;
            nodes.add(new Node(p, kind));
        }
        List<RuralLotAnchor> anchors = new ArrayList<>();
        // Small uncommitted road-side candidate envelopes; real NBT lots still use the legacy adapter.
        for (var s : edges) {
            int length = s.start().distManhattan(s.end());
            for (int d = 6; d < length - 3; d += 12) for (int sign : new int[]{-1, 1}) {
                Direction outward = sign > 0 ? s.direction().getClockWise() : s.direction().getCounterClockWise();
                BlockPos connection = s.start().relative(s.direction(), d);
                BlockPos front = connection.relative(outward, s.type().radius() + 5);
                BlockPos back = front.relative(outward, 12);
                BoundingBox area = new BoundingBox(Math.min(front.getX(), back.getX()) - 4, 0,
                        Math.min(front.getZ(), back.getZ()) - 4, Math.max(front.getX(), back.getX()) + 4, 0,
                        Math.max(front.getZ(), back.getZ()) + 4);
                if (RuralAccessPlanner.inside(area, reservation)
                        && roads.stream().noneMatch(r -> RuralAccessPlanner.intersects(area, r.bounds())))
                    anchors.add(new RuralLotAnchor(connection, front, outward.getOpposite(), area,
                            List.of(new RuralRoadSegment(front, connection, RuralRoadType.FARM_TRACK))));
            }
        }
        List<BoundingBox> occupied = new ArrayList<>();
        for (var lot : lots) if (lot.access() != null) {
            anchors.add(lot.access()); occupied.addAll(lot.access().accessBounds());
        }
        return new RuralRoadNetwork(nodes, edges, anchors, occupied);
    }
}
