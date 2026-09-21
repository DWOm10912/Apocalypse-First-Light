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
    public static final int ENGINEERING_VERSION = 3;

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

        HighwayBranchGrade attachmentGrade = null;
        if (corridor.geometry() != null && corridor.startNode().kind() == HighwayRouteGraph.NodeKind.BRANCH_JUNCTION
                && paddedStart <= HighwayBranchGrade.BLEND_END && corridor.parentAttachment().isPresent()) {
            var attachment = corridor.parentAttachment().orElseThrow();
            var parent = graph.getEdgeById(attachment.parentEdgeId()).orElseThrow();
            // Reuse the same CORE + HALO engineering window selected by the attachment chunk.
            int x = Math.floorDiv(corridor.startNode().x(), 16) * 16;
            int z = Math.floorDiv(corridor.startNode().z(), 16) * 16;
            long parentIndex = segmentIndex(parent.clampStation(parent.globalStation(x, z)));
            var key = new NaturalHighwayCacheManager.SegmentKey(parent.routeId(), parent.id(), parentIndex, ENGINEERING_VERSION);
            var parentSegment = cache.segment(key, () -> build(level, graph, parent, parentIndex, terrain, cache));
            attachmentGrade = new HighwayBranchGrade(parent, parentSegment.profile());
        }
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
