package com.antaurora.apofirstlight.worldgen.roads;

import com.antaurora.apofirstlight.worldgen.spatial.BoundsXZ;
import com.antaurora.apofirstlight.worldgen.spatial.SpatialClaim;
import net.minecraft.core.Direction;
import java.util.List;

/** Read-only, bounded planning product. PLANNED is never permission to write a world. */
public record RoadPlan(String specVersion, String planId, String candidateId, Layout layout, Status status,
        BoundsXZ candidateBounds, List<Node> nodes, List<Edge> edges, List<RoadLotPlanner.Lot> lots,
        List<SpatialClaim> claims, List<String> diagnostics, int terrainSamples, int attempts, boolean connected) {
    public RoadPlan {
        nodes = List.copyOf(nodes); edges = List.copyOf(edges); lots = List.copyOf(lots);
        claims = List.copyOf(claims); diagnostics = List.copyOf(diagnostics);
    }
    public enum Layout { RESIDENTIAL, COMMERCIAL, INDUSTRIAL, MIXED, T }
    public enum Status { PLANNED, REJECTED }
    public enum NodeKind { END, STRAIGHT, TURN, T_JUNCTION, CROSS }
    public record Arm(Direction direction, RoadType type, String edgeId) {}
    public record Node(String id, int x, int z, int groundY, NodeKind kind, List<Arm> arms,
                       List<BoundsXZ> footprint) {
        public Node { arms = List.copyOf(arms); footprint = List.copyOf(footprint); }
        public RoadJunction junction() { return RoadJunction.of(x,z,arms); }
        public int surfaceH16() { return Math.subtractExact(Math.multiplyExact(groundY, 16), 3); }
    }
    /** End coordinates are shared node coordinates; station phase begins at (x1,z1). */
    public record Edge(String id, String from, String to, RoadType type, int x1, int z1, int x2, int z2,
                       int groundY, BoundsXZ corridor) {
        public Edge {
            if(!RoadCrossSection.of(x1,z1,x2,z2,type).corridorBounds().equals(corridor))
                throw new IllegalArgumentException("Edge corridor does not match its complete right of way");
        }
        public RoadCrossSection crossSection() { return RoadCrossSection.of(x1,z1,x2,z2,type); }
        public int length() { return Math.abs(x2-x1) + Math.abs(z2-z1); }
        public int surfaceH16() { return Math.subtractExact(Math.multiplyExact(groundY, 16), 3); }
    }
    public boolean successful() { return status == Status.PLANNED; }
}
