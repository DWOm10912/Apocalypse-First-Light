package com.antaurora.apofirstlight.worldgen.network.road;

/**
 * What this build actually builds in the world (C3.2, C3.3a; docs/worldgen/federal_highways_c3.md, 10 and 11). C3.2
 * built one sample interstate leg; C3.3a builds every planned route of the plan (RoadNetworkBuild: legs through towns
 * joined, ends trimmed short of junctions and shared towns until C3.3b). The stage, the rules and the versions are part
 * of the world lock: changing them changes generated chunks.
 */
public final class RoadNetworkStage {
    private RoadNetworkStage() {}

    /** Stage label recorded in the world lock. */
    public static final String STAGE = RoadNetworkBuild.STAGE;

    /** C3.3a: the whole planned network (5 interstate and 15 state legs of the C3.1 plan). */
    public static final String BUILT = "all";

    /**
     * The terrain works rules (RoadWorks.surface, CrossSection, the earthworks columns); bump when they change (2: the R2
     * state section, the works of several roads combined, pier columns on the ground; 3: the turf's outer edge cut to the
     * natural surface the noise makes, RoadWorks#naturalTop; 4: the embankment continues over a box culvert's stream,
     * turf under the walls' copings, group D).
     */
    public static final int WORKS_VERSION = 4;

    /**
     * What players see and touch: RoadMesh, BridgeMesh, the collision and bridge structure columns (2: bridge mesh,
     * road_structure; 3: the natural ground under and beside a bridge kept, walls with copings and tails, collision shapes
     * from the mesh; 4: the structure stands on the ground's top block instead of replacing it; 5: the R2 state section
     * with its centre line, undivided decks, piers, slab culverts; 6: the biome's plants on graded turf; 7: none on the turf beside the pavement, RoadWorks#bare;
     * 8: box culverts, wing walls to the embankment's reach as continuous strips, walls on the lowest ground in front,
     * structure waterlogged in water, group D).
     */
    public static final int SURFACE_VERSION = 8;

    /** The road_network system version: the plan's rules, the stage, the built set and the surface version. */
    public static String networkVersion(String planVersion) {
        return planVersion + "#" + STAGE + "#" + BUILT + "#surface" + SURFACE_VERSION;
    }

    /** The terrain_works system version. */
    public static String worksVersion() { return "road_works#" + WORKS_VERSION; }
}
