package com.antaurora.apofirstlight.worldgen.highway;

import net.minecraft.world.level.WorldGenLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Immutable CORE + HALO engineering result aligned to global corridor station. */
public record CorridorEngineeringSegment(
        HighwayRouteGraph.Edge corridor,
        long segmentIndex,
        long coreStartStation,
        long coreEndStation,
        HighwayPlan plan,
        List<InterstateInterchangeNode> nodes,
        HighwayNodeConstraints nodeConstraints,
        HighwayProfile profile,
        HighwayCorridor engineeredCorridor) {

    public static final int ENGINEERING_SEGMENT_LENGTH = 256;
    public static final int ENGINEERING_HALO = 192;
    public static final int ENGINEERING_VERSION = 4;

    public static long segmentIndex(long globalStation) {
        return Math.floorDiv(globalStation, ENGINEERING_SEGMENT_LENGTH);
    }

    public static CorridorEngineeringSegment build(WorldGenLevel level,
                                                     HighwayRouteGraph graph,
                                                     HighwayRouteGraph.Edge corridor,
                                                     long segmentIndex,
                                                     HighwayTerrainSampler terrain,
                                                     NaturalHighwayCacheManager.WorldCache cache) {
        long buildStart = System.nanoTime();
        long contextStart = buildStart;
        long segmentStart = segmentIndex * ENGINEERING_SEGMENT_LENGTH;
        long coreStart = Math.max(corridor.startStation(), segmentStart);
        long coreEnd = Math.min(corridor.endStation(), segmentStart + ENGINEERING_SEGMENT_LENGTH - 1L);
        if (coreStart > coreEnd) throw new IllegalArgumentException("Engineering segment outside finite route");
        long paddedStart = Math.max(corridor.startStation(), segmentStart - ENGINEERING_HALO);
        long paddedEnd = Math.min(corridor.endStation(), segmentStart + ENGINEERING_SEGMENT_LENGTH - 1L + ENGINEERING_HALO);

        HighwayPlan.Point start;
        HighwayPlan.Point end;
        if (corridor.orientation() == HighwayRouteGraph.Orientation.NORTH_SOUTH) {
            start = new HighwayPlan.Point(corridor.fixedCoordinate(), (int) paddedStart);
            end = new HighwayPlan.Point(corridor.fixedCoordinate(), (int) paddedEnd);
        } else {
            start = new HighwayPlan.Point((int) paddedStart, corridor.fixedCoordinate());
            end = new HighwayPlan.Point((int) paddedEnd, corridor.fixedCoordinate());
        }

        List<InterstateInterchangeNode> nodes = new ArrayList<>();
        long crossingStation = corridor.globalStation(graph.intersection().x(), graph.intersection().z());
        if (corridor.routeType() == HighwayRouteGraph.RouteType.NATIONAL_TRUNK
                && crossingStation >= paddedStart - InterstateInterchangeNode.APPROACH_LENGTH
                && crossingStation <= paddedEnd + InterstateInterchangeNode.APPROACH_LENGTH) {
            nodes.add(cachedNode(cache, graph, terrain));
        }
        nodes.sort(Comparator.comparing(InterstateInterchangeNode::id));
        HighwayPlan plan = HighwayPlan.linear(start, end, HighwayPlan.MAIN_WIDTH, paddedStart);
        if (corridor.geometry() != null) plan = HighwayPlan.ribbon(corridor.geometry(), paddedStart,
                Math.min(paddedEnd, corridor.geometry().length()));
        HighwayNodeConstraints constraints = new HighwayNodeConstraints(corridor.orientation(), nodes);
        NaturalHighwayRuntimeStats.contextBuild(System.nanoTime() - contextStart);

        HighwayBranchGrade attachmentGrade = sharedGrade(level, graph, corridor, coreStart, coreEnd,
                paddedStart, paddedEnd, terrain, cache);
        HighwayProfile profile = HighwayProfile.sampleNatural(plan, corridor, terrain, constraints, attachmentGrade);
        HighwayCorridor engineered = HighwayCorridor.buildNatural(level, plan, profile,
                corridor.bounds(HighwayRouteGraph.CONSTRUCTION_HALF_WIDTH));
        if (engineered.geometryDeferred()) com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.debug(
                "[AFL HIGHWAY] geometry deferred: edge={} segment={} diagonal tunnel unsupported",
                corridor.id(), segmentIndex);
        CorridorEngineeringSegment result = new CorridorEngineeringSegment(corridor, segmentIndex,
                coreStart, coreEnd, plan, List.copyOf(nodes), constraints, profile, engineered);
        NaturalHighwayRuntimeStats.engineeringSegmentBuild(System.nanoTime() - buildStart);
        return result;
    }

    private static HighwayBranchGrade sharedGrade(WorldGenLevel level, HighwayRouteGraph graph,
                                                    HighwayRouteGraph.Edge corridor,
                                                    long coreStart, long coreEnd,
                                                    long paddedStart, long paddedEnd,
                                                    HighwayTerrainSampler terrain,
                                                    NaturalHighwayCacheManager.WorldCache cache) {
        if (corridor.routeType() != HighwayRouteGraph.RouteType.STRATEGIC_BRANCH) return null;
        List<HighwayRouteGraph.Node> candidates = new ArrayList<>(2);
        if (corridor.startNode().kind() == HighwayRouteGraph.NodeKind.BRANCH_JUNCTION
                || corridor.startNode().kind() == HighwayRouteGraph.NodeKind.TURN)
            candidates.add(corridor.startNode());
        if (corridor.endNode().kind() == HighwayRouteGraph.NodeKind.TURN)
            candidates.add(corridor.endNode());
        double center = (coreStart + coreEnd) * .5;
        HighwayRouteGraph.Node node = candidates.stream()
                .filter(n -> near(n, corridor, paddedStart, paddedEnd))
                .min(Comparator.<HighwayRouteGraph.Node>comparingDouble(
                                n -> Math.abs(corridor.globalStation(n.x(), n.z()) - center))
                        .thenComparing(HighwayRouteGraph.Node::id)).orElse(null);
        if (node == null) return null;
        if (node.kind() == HighwayRouteGraph.NodeKind.BRANCH_JUNCTION) {
            var attachment = corridor.parentAttachment().orElseThrow();
            var parent = graph.getEdgeById(attachment.parentEdgeId()).orElseThrow();
            long parentIndex = segmentIndex(attachment.parentStation());
            var key = new NaturalHighwayCacheManager.SegmentKey(parent.routeId(), parent.id(), parentIndex, ENGINEERING_VERSION);
            var parentSegment = cache.segment(key, () -> build(level, graph, parent, parentIndex, terrain, cache));
            return HighwayBranchGrade.junction(corridor, parent, parentSegment.profile());
        }
        List<HighwayRouteGraph.Edge> incident = graph.edges().stream()
                .filter(e -> e.routeType() == HighwayRouteGraph.RouteType.STRATEGIC_BRANCH
                        && (e.startNode().equals(node) || e.endNode().equals(node)))
                .sorted(Comparator.comparing(HighwayRouteGraph.Edge::id)).toList();
        if (incident.size() != 2) throw new IllegalStateException("TURN requires two incident branch edges: " + node.id());
        int sum = 0;
        for (var edge : incident) sum += terrain.globalRoadY(edge, edge.globalStation(node.x(), node.z()));
        int turnNodeGrade = (int) Math.round(sum / (double) incident.size());
        return HighwayBranchGrade.turn(corridor, node, turnNodeGrade);
    }

    private static boolean near(HighwayRouteGraph.Node node, HighwayRouteGraph.Edge corridor,
                                long paddedStart, long paddedEnd) {
        double station = corridor.globalStation(node.x(), node.z());
        return station >= paddedStart - HighwayBranchGrade.BLEND_END
                && station <= paddedEnd + HighwayBranchGrade.BLEND_END;
    }

    private static InterstateInterchangeNode cachedNode(NaturalHighwayCacheManager.WorldCache cache,
                                                         HighwayRouteGraph graph,
                                                         HighwayTerrainSampler terrain) {
        NaturalHighwayCacheManager.NodeKey key = new NaturalHighwayCacheManager.NodeKey(
                graph.intersection().id(), HighwayRouteGraph.VERSION);
        return cache.node(key, () -> {
            NaturalHighwayRuntimeStats.nodePlanCall();
            long started = System.nanoTime();
            InterstateInterchangeNode node = InterstateInterchangeNode.fromGraph(graph, terrain);
            NaturalHighwayRuntimeStats.interchangePlanning(System.nanoTime() - started);
            return node;
        });
    }

    public boolean upperAtNode() {
        return nodes.stream().anyMatch(node -> node.upper() == corridor.orientation());
    }

    public double localDistance(double globalStation) {
        return plan.localDistance(globalStation);
    }
}
