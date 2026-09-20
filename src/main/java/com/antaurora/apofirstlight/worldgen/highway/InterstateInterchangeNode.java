package com.antaurora.apofirstlight.worldgen.highway;

import static com.antaurora.apofirstlight.worldgen.highway.HighwayRouteGraph.Orientation;

/** Deterministic grade-separated crossing; ramps and route connectivity are intentionally absent. */
public record InterstateInterchangeNode(
        HighwayRouteGraph.Edge northSouth,
        HighwayRouteGraph.Edge eastWest,
        int x,
        int z,
        Orientation upper,
        int baseNorthSouthRoadY,
        int baseEastWestRoadY,
        int lowerCrossingRoadY,
        int upperCrossingRoadY
) {
    public static final int LOWER_FURNITURE_TOP_OFFSET = 1;
    public static final int INTERCHANGE_VERTICAL_CLEARANCE = 6;
    public static final int UPPER_DECK_STRUCTURAL_THICKNESS = 3;
    public static final int REQUIRED_SURFACE_SEPARATION = LOWER_FURNITURE_TOP_OFFSET + 1
            + INTERCHANGE_VERTICAL_CLEARANCE + UPPER_DECK_STRUCTURAL_THICKNESS;
    public static final int APPROACH_LENGTH = 96;
    public static final int GRADE_RUN_PER_BLOCK_RISE = 4;
    public static final int CROSSING_CORE_HALF_LENGTH = HighwayCorridor.BRIDGE_WIDTH / 2 + 6;
    public static final int INTERCHANGE_RESERVE_RADIUS = APPROACH_LENGTH;

    /** Existing grade-separated crossing policy, now fed by the two finite graph edges. */
    public static InterstateInterchangeNode fromGraph(HighwayRouteGraph graph, HighwayTerrainSampler terrain) {
        HighwayRouteGraph.Edge ns = graph.edge(Orientation.NORTH_SOUTH);
        HighwayRouteGraph.Edge ew = graph.edge(Orientation.EAST_WEST);
        int x = graph.intersection().x();
        int z = graph.intersection().z();
        int baseNs = terrain.globalRoadY(ns, z);
        int baseEw = terrain.globalRoadY(ew, x);
        int nsRaise = Math.max(0, baseEw + REQUIRED_SURFACE_SEPARATION - baseNs);
        int ewRaise = Math.max(0, baseNs + REQUIRED_SURFACE_SEPARATION - baseEw);
        Orientation upper;
        if (nsRaise < ewRaise) upper = Orientation.NORTH_SOUTH;
        else if (ewRaise < nsRaise) upper = Orientation.EAST_WEST;
        else upper = (HighwayRouteGraph.mix(graph.seed() ^ graph.intersection().id().hashCode()) & 1L) == 0L
                ? Orientation.NORTH_SOUTH : Orientation.EAST_WEST;
        int lowerRoadY = upper == Orientation.NORTH_SOUTH ? baseEw : baseNs;
        int upperBaseY = upper == Orientation.NORTH_SOUTH ? baseNs : baseEw;
        return new InterstateInterchangeNode(ns, ew, x, z, upper, baseNs, baseEw,
                lowerRoadY, Math.max(upperBaseY, lowerRoadY + REQUIRED_SURFACE_SEPARATION));
    }

    public String id() {
        return northSouth.junctionNodeId();
    }

    public Orientation lower() {
        return upper == Orientation.NORTH_SOUTH
                ? Orientation.EAST_WEST : Orientation.NORTH_SOUTH;
    }

    public int station(Orientation orientation) {
        return orientation == Orientation.NORTH_SOUTH ? z : x;
    }

    public int baseRoadY(Orientation orientation) {
        return orientation == Orientation.NORTH_SOUTH
                ? baseNorthSouthRoadY : baseEastWestRoadY;
    }

    public int upperRaise() {
        return upperCrossingRoadY - baseRoadY(upper);
    }

    public boolean affects(Orientation orientation, double globalStation) {
        return Math.abs(globalStation - station(orientation)) <= APPROACH_LENGTH;
    }

    public int adjustedRoadY(Orientation orientation, double globalStation, int baseRoadY) {
        if (orientation != upper) return baseRoadY;
        double distance = Math.abs(globalStation - station(orientation));
        if (distance > APPROACH_LENGTH) return baseRoadY;
        int permittedRise = (int) Math.floor((APPROACH_LENGTH - distance) / GRADE_RUN_PER_BLOCK_RISE);
        return baseRoadY + Math.min(upperRaise(), Math.max(0, permittedRise));
    }

    public boolean crossingCore(Orientation orientation, double globalStation) {
        return Math.abs(globalStation - station(orientation)) <= CROSSING_CORE_HALF_LENGTH;
    }

    public boolean upperCrossingCore(Orientation orientation, double globalStation) {
        return orientation == upper && crossingCore(orientation, globalStation);
    }

    public boolean lowerCrossingCore(Orientation orientation, double globalStation) {
        return orientation == lower() && crossingCore(orientation, globalStation);
    }

    public String reservedBounds() {
        return "[" + (x - INTERCHANGE_RESERVE_RADIUS) + "," + (z - INTERCHANGE_RESERVE_RADIUS)
                + " -> " + (x + INTERCHANGE_RESERVE_RADIUS) + "," + (z + INTERCHANGE_RESERVE_RADIUS) + "]";
    }
}
