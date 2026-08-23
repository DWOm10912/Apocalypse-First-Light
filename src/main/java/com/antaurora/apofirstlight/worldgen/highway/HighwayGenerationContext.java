package com.antaurora.apofirstlight.worldgen.highway;

import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Padded resolver window plus the target chunk's deterministic corridor/node identity. */
public record HighwayGenerationContext(ChunkPos targetChunk,
                                       PrimaryHighwayNetwork.Corridor corridor,
                                       HighwayPlan plan,
                                       List<InterstateInterchangeNode> nodes,
                                       HighwayNodeConstraints nodeConstraints) {
    public static final int ENGINEERING_PADDING = 192;

    public static HighwayGenerationContext create(ChunkPos chunk,
                                                  PrimaryHighwayNetwork network,
                                                  PrimaryHighwayNetwork.Corridor corridor,
                                                  HighwayTerrainSampler terrain) {
        int stationMin;
        int stationMax;
        HighwayPlan.Point start;
        HighwayPlan.Point end;
        if (corridor.orientation() == PrimaryHighwayNetwork.Orientation.PRIMARY_NORTH_SOUTH) {
            stationMin = chunk.getMinBlockZ() - ENGINEERING_PADDING;
            stationMax = chunk.getMaxBlockZ() + ENGINEERING_PADDING;
            start = new HighwayPlan.Point(corridor.fixedCoordinate(), stationMin);
            end = new HighwayPlan.Point(corridor.fixedCoordinate(), stationMax);
        } else {
            stationMin = chunk.getMinBlockX() - ENGINEERING_PADDING;
            stationMax = chunk.getMaxBlockX() + ENGINEERING_PADDING;
            start = new HighwayPlan.Point(stationMin, corridor.fixedCoordinate());
            end = new HighwayPlan.Point(stationMax, corridor.fixedCoordinate());
        }
        List<InterstateInterchangeNode> nodes = new ArrayList<>();
        if (corridor.orientation() == PrimaryHighwayNetwork.Orientation.PRIMARY_NORTH_SOUTH) {
            for (PrimaryHighwayNetwork.Corridor ew : network.nearby(
                    PrimaryHighwayNetwork.Orientation.PRIMARY_EAST_WEST,
                    stationMin, stationMax, InterstateInterchangeNode.APPROACH_LENGTH)) {
                nodes.add(network.node(corridor, ew, terrain));
            }
        } else {
            for (PrimaryHighwayNetwork.Corridor ns : network.nearby(
                    PrimaryHighwayNetwork.Orientation.PRIMARY_NORTH_SOUTH,
                    stationMin, stationMax, InterstateInterchangeNode.APPROACH_LENGTH)) {
                nodes.add(network.node(ns, corridor, terrain));
            }
        }
        nodes.sort(Comparator.comparing(InterstateInterchangeNode::id));
        HighwayPlan plan = HighwayPlan.linear(start, end, HighwayPlan.MAIN_WIDTH, stationMin);
        HighwayNodeConstraints constraints = new HighwayNodeConstraints(corridor.orientation(), nodes);
        return new HighwayGenerationContext(chunk, corridor, plan, List.copyOf(nodes), constraints);
    }

    public boolean upperAtNode() {
        return nodes.stream().anyMatch(node -> node.upper() == corridor.orientation());
    }
}
