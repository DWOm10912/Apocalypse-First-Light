package com.antaurora.apofirstlight.worldgen.highway;

import net.minecraft.world.level.WorldGenLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Immutable CORE + HALO engineering result aligned to global corridor station. */
public record CorridorEngineeringSegment(
        PrimaryHighwayNetwork.Corridor corridor,
        long segmentIndex,
        long coreStartStation,
        long coreEndStation,
        HighwayPlan plan,
        List<InterstateInterchangeNode> nodes,
        HighwayNodeConstraints nodeConstraints,
        HighwayProfile profile,
        HighwayCorridor engineeredCorridor) {

    public static final int ENGINEERING_SEGMENT_LENGTH = 256;
    public static final int ENGINEERING_HALO = HighwayGenerationContext.ENGINEERING_PADDING;
    public static final int ENGINEERING_VERSION = 1;

    public static long segmentIndex(long globalStation) {
        return Math.floorDiv(globalStation, ENGINEERING_SEGMENT_LENGTH);
    }

    public static CorridorEngineeringSegment build(WorldGenLevel level,
                                                     PrimaryHighwayNetwork network,
                                                     PrimaryHighwayNetwork.Corridor corridor,
                                                     long segmentIndex,
                                                     HighwayTerrainSampler terrain,
                                                     NaturalHighwayCacheManager.WorldCache cache) {
        long buildStart = System.nanoTime();
        long contextStart = buildStart;
        long coreStart = segmentIndex * ENGINEERING_SEGMENT_LENGTH;
        long coreEnd = coreStart + ENGINEERING_SEGMENT_LENGTH - 1L;
        long paddedStart = coreStart - ENGINEERING_HALO;
        long paddedEnd = coreEnd + ENGINEERING_HALO;
        if (paddedStart < Integer.MIN_VALUE || paddedEnd > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Highway engineering segment lies outside Minecraft coordinates");
        }

        HighwayPlan.Point start;
        HighwayPlan.Point end;
        if (corridor.orientation() == PrimaryHighwayNetwork.Orientation.PRIMARY_NORTH_SOUTH) {
            start = new HighwayPlan.Point(corridor.fixedCoordinate(), (int) paddedStart);
            end = new HighwayPlan.Point(corridor.fixedCoordinate(), (int) paddedEnd);
        } else {
            start = new HighwayPlan.Point((int) paddedStart, corridor.fixedCoordinate());
            end = new HighwayPlan.Point((int) paddedEnd, corridor.fixedCoordinate());
        }

        List<InterstateInterchangeNode> nodes = new ArrayList<>();
        if (corridor.orientation() == PrimaryHighwayNetwork.Orientation.PRIMARY_NORTH_SOUTH) {
            for (PrimaryHighwayNetwork.Corridor ew : network.nearby(
                    PrimaryHighwayNetwork.Orientation.PRIMARY_EAST_WEST,
                    (int) paddedStart, (int) paddedEnd, InterstateInterchangeNode.APPROACH_LENGTH)) {
                nodes.add(cachedNode(cache, network, corridor, ew, terrain));
            }
        } else {
            for (PrimaryHighwayNetwork.Corridor ns : network.nearby(
                    PrimaryHighwayNetwork.Orientation.PRIMARY_NORTH_SOUTH,
                    (int) paddedStart, (int) paddedEnd, InterstateInterchangeNode.APPROACH_LENGTH)) {
                nodes.add(cachedNode(cache, network, ns, corridor, terrain));
            }
        }
        nodes.sort(Comparator.comparing(InterstateInterchangeNode::id));
        HighwayPlan plan = HighwayPlan.linear(start, end, HighwayPlan.MAIN_WIDTH, paddedStart);
        HighwayNodeConstraints constraints = new HighwayNodeConstraints(corridor.orientation(), nodes);
        NaturalHighwayRuntimeStats.contextBuild(System.nanoTime() - contextStart);

        HighwayProfile profile = HighwayProfile.sampleNatural(plan, corridor, terrain, constraints);
        HighwayCorridor engineered = HighwayCorridor.buildNatural(level, plan, profile);
        CorridorEngineeringSegment result = new CorridorEngineeringSegment(corridor, segmentIndex,
                coreStart, coreEnd, plan, List.copyOf(nodes), constraints, profile, engineered);
        NaturalHighwayRuntimeStats.engineeringSegmentBuild(System.nanoTime() - buildStart);
        return result;
    }

    private static InterstateInterchangeNode cachedNode(NaturalHighwayCacheManager.WorldCache cache,
                                                         PrimaryHighwayNetwork network,
                                                         PrimaryHighwayNetwork.Corridor ns,
                                                         PrimaryHighwayNetwork.Corridor ew,
                                                         HighwayTerrainSampler terrain) {
        NaturalHighwayCacheManager.NodeKey key = new NaturalHighwayCacheManager.NodeKey(ns.index(), ew.index());
        return cache.node(key, () -> {
            NaturalHighwayRuntimeStats.nodePlanCall();
            long started = System.nanoTime();
            InterstateInterchangeNode node = network.node(ns, ew, terrain);
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
