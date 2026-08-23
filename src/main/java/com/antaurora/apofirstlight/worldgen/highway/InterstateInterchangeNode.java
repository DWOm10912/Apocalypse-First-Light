package com.antaurora.apofirstlight.worldgen.highway;

import static com.antaurora.apofirstlight.worldgen.highway.PrimaryHighwayNetwork.Orientation;

/** Deterministic grade-separated crossing; ramps and route connectivity are intentionally absent. */
public record InterstateInterchangeNode(
        PrimaryHighwayNetwork.Corridor northSouth,
        PrimaryHighwayNetwork.Corridor eastWest,
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

    public String id() {
        return "I_" + northSouth.id() + "_" + eastWest.id();
    }

    public Orientation lower() {
        return upper == Orientation.PRIMARY_NORTH_SOUTH
                ? Orientation.PRIMARY_EAST_WEST : Orientation.PRIMARY_NORTH_SOUTH;
    }

    public int station(Orientation orientation) {
        return orientation == Orientation.PRIMARY_NORTH_SOUTH ? z : x;
    }

    public int baseRoadY(Orientation orientation) {
        return orientation == Orientation.PRIMARY_NORTH_SOUTH
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
