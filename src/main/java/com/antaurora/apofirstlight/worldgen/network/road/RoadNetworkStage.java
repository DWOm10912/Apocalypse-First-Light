package com.antaurora.apofirstlight.worldgen.network.road;

import java.util.List;
import java.util.Set;

/**
 * Which planned routes this build actually builds in the world (C3.2; docs/worldgen/federal_highways_c3.md, 10). The
 * whole network is planned (RoadNetworkPlan) and recorded, but a world gets pavement, earthworks and bridges only on
 * these routes; the rest stays plan-only until C3.3 (national rollout after the user's in-game acceptance of the
 * sample). Changing the list changes generated chunks: it is part of the world lock's road_network version.
 */
public final class RoadNetworkStage {
    private RoadNetworkStage() {}

    /** Stage label recorded in the world lock. */
    public static final String STAGE = "c3.2";

    /** C3.2: the S1 -> S2 sample, interstate EW from County Seat to Valley Town (6.6 km, one river bridge). */
    public static final List<String> BUILT = List.of("interstate_ew:county_seat-valley_town");

    public static Set<String> built() { return Set.copyOf(BUILT); }

    /** The terrain works rules (RoadWorks, R4CrossSection, the chunk finish); bump when finished columns change. */
    public static final int WORKS_VERSION = 1;

    /** The pavement mesh and collision rules seen by players (RoadMesh, the collision columns). */
    public static final int SURFACE_VERSION = 1;

    /** The road_network system version: the plan's rules, the stage and the built list. */
    public static String networkVersion(String planVersion) {
        return planVersion + "#" + STAGE + "#" + String.join("+", BUILT) + "#surface" + SURFACE_VERSION;
    }

    /** The terrain_works system version. */
    public static String worksVersion() { return "road_works_r4#" + WORKS_VERSION; }
}
