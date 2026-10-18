package com.antaurora.apofirstlight.worldgen.network.road;

/**
 * What this build actually builds in the world (C3.2, C3.3a, C3.3b; docs/worldgen/federal_highways_c3.md, 10 and 11;
 * docs/worldgen/federal_junctions_c3_3b.md). C3.2 built one sample interstate leg; C3.3a builds every planned route of
 * the plan (RoadNetworkBuild: legs through towns joined, ends trimmed short of junctions); C3.3b joins the state roads at
 * town corners and T junctions (the interstate meetings stay trimmed until its groups 2 and 3). The stage, the rules
 * and the versions are part of the world lock: changing them changes generated chunks.
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
     * turf under the walls' copings, group D; 5: tunnels, Tunnel V1: the mountain kept over a bore, the portals' mounds,
     * the approach cuts, TunnelWorks#surface; 6: Tunnel V1 fix 2026-10-16: the shelf runs on under the walls, a bench at
     * the wall's top behind them, never natural over the density's rock bands, the rock kept round the bores and under the
     * approaches against caves and carvers, TunnelWorks#rock; 7: C3.3b junction pads, their roadside blended into the roads'
     * works, the host's ditch filled across the mouth, no plants on a pad or in its sight triangles, RoadWorks#padSurface;
     * 8: C3.3b group 2 interchanges: attached ramps give way on the interstate's band, RoadWorks#yields, the ramps' roadside
     * turned into the interstate's, the overpass's spill-through slopes, two-level columns under a deck, Column#deck; 9: Town
     * Framework V1: the framework towns' exits built (RoadNetworkBuild with the terrain), their streets, intersections,
     * lots and slopes, TownWorks, RoadWorks#townColumn).
     */
    public static final int WORKS_VERSION = 9;

    /**
     * What players see and touch: RoadMesh, BridgeMesh, the collision and bridge structure columns (2: bridge mesh,
     * road_structure; 3: the natural ground under and beside a bridge kept, walls with copings and tails, collision shapes
     * from the mesh; 4: the structure stands on the ground's top block instead of replacing it; 5: the R2 state section
     * with its centre line, undivided decks, piers, slab culverts; 6: the biome's plants on graded turf; 7: none on the turf beside the pavement, RoadWorks#bare;
     * 8: box culverts, wing walls to the embankment's reach as continuous strips, walls on the lowest ground in front,
     * structure waterlogged in water, group D; 9: multi-column bents, their cap beams and columns, group E; 10: tunnels,
     * Tunnel V1: built tunnel spans, bores and cross passages carved, lining and portals of road_structure, the drawn
     * lining and the equipment parts, solid lane lines through a tunnel; 11: Tunnel V1 fix 2026-10-16: walls, lead-ins and a
     * portal's front stand on the road's finish, RoadWorks.TUNNEL_OVER, the walls' caps and backfill collision; 12: the
     * tunnel equipment's collision, TunnelPartSolids, 2026-10-16; 13: the equipment and the handrail segments as
     * removable fixture blocks, TunnelFixtures / TunnelFixtureBlock, 2026-10-16; 14: the wall parts' boxes from the wall's face
     * out (they hang on any wall), no collision for the thin ones, 2026-10-16; 15: C3.3b junction pads and their markings,
     * JunctionMesh, the host's edge line open across a mouth, the pads' collision columns, the junction and town corner
     * road signs as fixtures, RoadSigns; 16: C3.3b group 2 interchanges: the ramps' pavement clipped to the interstate's
     * edge, gore chevrons, the interstate's dotted edge lines along a ramp, no lines at an entrance's terminal, DO NOT
     * ENTER / WRONG WAY at an exit's terminal, an overpass's deck over the road under it, RoadWorks.RampMarks; 17: the
     * interchanges' guide signs, RoadGuides / GuideSigns, 2026-10-17; 18: C3.3b group 3 system interchanges (trumpets,
     * RoadSystems): open ends without end bevels, the direct ramps' marks on their left-turn ramps, 2026-10-17; 19: Town
     * Framework V1: the framework towns' street asphalt, curbs and sidewalks, TownMesh, their paved columns, 2026-10-17).
     */
    public static final int SURFACE_VERSION = 19;

    /** The road_network system version: the plan's rules, the stage, the built set and the surface version. */
    public static String networkVersion(String planVersion) {
        return planVersion + "#" + STAGE + "#" + BUILT + "#surface" + SURFACE_VERSION;
    }

    /** The terrain_works system version. */
    public static String worksVersion() { return "road_works#" + WORKS_VERSION; }
}
