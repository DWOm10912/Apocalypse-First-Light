package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.worldgen.spatial.BoundsXZ;
import java.util.ArrayList;
import java.util.List;

/** Derived claim strips only. Never changes the graph, its clipping or its 65-block core. */
public final class HighwayReservedSeams {
    public static final int MAX_LENGTH = 7;
    private HighwayReservedSeams() {}

    public record Seam(String id, String zoneId, String edgeId, BoundsXZ bounds, int length) {}

    public static List<Seam> forZone(HighwayRouteGraph graph, HighwayRouteGraph.ReservedZone zone) {
        List<Seam> result = new ArrayList<>();
        for (var edge : graph.edges()) {
            if (edge.routeType() != HighwayRouteGraph.RouteType.STRATEGIC_BRANCH
                    || !edge.routeId().equals(zone.routeId())
                    || (!edge.startNode().equals(zone.node()) && !edge.endNode().equals(zone.node()))) continue;
            if (edge.geometry() != null) throw new IllegalArgumentException("Reserved seam requires axial edge");
            boolean ns = edge.orientation() == HighwayRouteGraph.Orientation.NORTH_SOUTH;
            int nodeStation = ns ? zone.node().z() : zone.node().x();
            int expectedFixed = ns ? zone.node().x() : zone.node().z();
            if (edge.fixedCoordinate() != expectedFixed) throw new IllegalArgumentException("Misaligned reserved port");
            int lower = ns ? zone.bounds().minZ() : zone.bounds().minX();
            int upper = ns ? zone.bounds().maxZExclusive() : zone.bounds().maxXExclusive();
            int from, to;
            if (edge.endStation() < nodeStation) {
                from = edge.endStation() + 1;
                to = lower;
            } else if (edge.startStation() > nodeStation) {
                from = upper;
                to = edge.startStation();
            } else throw new IllegalArgumentException("Reserved port crosses logical node");
            int length = to - from;
            if (length < 0 || length > MAX_LENGTH) throw new IllegalArgumentException("Seam exceeds authorized 0..7 blocks: " + edge.id());
            if (length == 0) continue;
            int half = HighwayRouteGraph.CONSTRUCTION_HALF_WIDTH;
            BoundsXZ bounds = ns
                    ? new BoundsXZ(expectedFixed - half, from, expectedFixed + half + 1, to)
                    : new BoundsXZ(from, expectedFixed - half, to, expectedFixed + half + 1);
            result.add(new Seam(zone.id() + "/seam/" + edge.id(), zone.id(), edge.id(), bounds, length));
        }
        return List.copyOf(result);
    }
}
