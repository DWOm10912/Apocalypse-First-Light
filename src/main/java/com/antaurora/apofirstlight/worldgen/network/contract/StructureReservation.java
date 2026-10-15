package com.antaurora.apofirstlight.worldgen.network.contract;

import java.util.List;

/**
 * A structure's place in the world plan (contract section 8, Main): planned once from the seed and the plan, stored in
 * the save, then each chunk places its own slice while it generates.
 *
 * @param template  the structure asset, e.g. {@code apocalypse_firstlight:forest_home_b}
 * @param x         the template origin corner (block)
 * @param y         the template origin corner (block)
 * @param z         the template origin corner (block)
 * @param rotation  quarter turns clockwise about the origin corner (vanilla Rotation order: 0 NONE .. 3 COUNTERCLOCKWISE_90)
 * @param mirror    vanilla Mirror ordinal (0 NONE)
 * @param terrain   TerrainWork requests (contract section 6); empty until C3.2's works layer is in Main
 */
public record StructureReservation(String id, String template, int x, int y, int z, int rotation, int mirror,
                                   List<SpatialClaim> claims, List<RoadNode> nodes, List<String> terrain, State state, int version) {
    public static final int VERSION = 1;

    /** PLANNED; PLACED (every chunk placed); SKIPPED (an old world: never placed). */
    public enum State { PLANNED, PLACED, SKIPPED }

    public StructureReservation {
        claims = List.copyOf(claims);
        nodes = List.copyOf(nodes);
        terrain = List.copyOf(terrain);
    }
}
