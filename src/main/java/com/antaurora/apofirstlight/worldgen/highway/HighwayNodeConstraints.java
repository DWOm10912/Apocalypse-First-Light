package com.antaurora.apofirstlight.worldgen.highway;

import java.util.List;

/** Node-local profile rules layered over the ordinary terrain profile. */
public final class HighwayNodeConstraints {
    public static final HighwayNodeConstraints NONE = new HighwayNodeConstraints(null, List.of());

    private final PrimaryHighwayNetwork.Orientation orientation;
    private final List<InterstateInterchangeNode> nodes;

    public HighwayNodeConstraints(PrimaryHighwayNetwork.Orientation orientation,
                                  List<InterstateInterchangeNode> nodes) {
        this.orientation = orientation;
        this.nodes = List.copyOf(nodes);
    }

    public List<InterstateInterchangeNode> nodes() {
        return nodes;
    }

    public int adjustRoadY(double globalStation, int baseRoadY) {
        int result = baseRoadY;
        for (InterstateInterchangeNode node : nodes) {
            result = node.adjustedRoadY(orientation, globalStation, result);
        }
        return result;
    }

    public HighwayTerrainMode overrideMode(double globalStation, HighwayTerrainMode computed) {
        for (InterstateInterchangeNode node : nodes) {
            if (node.upperCrossingCore(orientation, globalStation)) return HighwayTerrainMode.VIADUCT;
            if (node.lowerCrossingCore(orientation, globalStation)) {
                return computed == HighwayTerrainMode.VIADUCT ? HighwayTerrainMode.GROUND : computed;
            }
        }
        return computed;
    }

    public boolean tunnelAllowed(double globalStation) {
        return nodes.stream().noneMatch(node -> node.affects(orientation, globalStation));
    }

    public boolean pierAllowed(double globalStation) {
        return nodes.stream().noneMatch(node -> node.upperCrossingCore(orientation, globalStation));
    }
}
