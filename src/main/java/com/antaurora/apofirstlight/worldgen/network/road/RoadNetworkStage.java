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
     * lots and slopes, TownWorks, RoadWorks#townColumn; 10: County road G3, 2026-10-18: the forest home's county road
     * (C2, RoadCounty) and its works, its mouth into a town's outer intersection, the home's gravel driveway, Driveway /
     * RoadWorks.Kind.DRIVE; 11: County road G3 side join, 2026-10-19: the county road runs past the home, the driveway
     * joins its paved edge from the side through a filleted apron, Driveway#near / RoadCounty#mouth; 12: the farm gate's
     * and the mailbox's pads, 2026-10-19: level turf at their whole Y, the driveway level there, Driveway#padY /
     * RoadWorks#padded; 13: a town intersection's centre square crowned toward each arm's own width at its edge, so a
     * narrower arm (a county road's mouth) meets it without a step, TownWorks#node, 2026-10-19; 14: RoadWorks#ROCK under
     * every road's works against caves and carvers, no springs in or beside them, RoadWorksRef#rock / #springBlocked,
     * SpringFeatureRoadMixin, and a deep cut's rock cut, CrossSection#rockCut, RoadWorks.ROCK_CUT, 2026-10-19; 15: the
     * framework towns' grids on the block grid, their exits' crossroads turning onto it, TownGrid, 2026-10-19; 16: Linden's
     * fixed layout, TownLayout, 2026-10-19; 17: Linden's site chosen before the network, FixedTownSite, the interstate run
     * straight past its front, its exit on 6th St, RoadNetworkPlan#pins, 2026-10-20; 18: 16 and 17 withdrawn (user
     * 2026-10-20: every town is built, the story in its landmarks), the towns grown round their exits again, a landmark's
     * whole block, the fallback search out to TownExits.FALLBACK_SEARCH with steeper blocks, 2026-10-20; 19: Fuel Stop A1's
     * corner lot takes its template's two entrances as driveways, the side one on the cross street, TownBuildings#fuelStop,
     * 2026-10-20; 20: Fuel Stop A1's lot at its template's surface, banked along its streets, its aprons up to that
     * surface at the lot line, TownBuildings#bank, 2026-10-20; 21: the roadside turf blended toward the natural surface
     * only at its joint with the natural ground, RoadWorks#joint, a corner left natural amid the works on the planned
     * ground, RoadWorks#joins, every turf corner the highest its neighbours fit it to, RoadWorks#agreed, and no inverted
     * crown between reverse curves, CrossSection#noInvertedCrown, 2026-10-25).
     */
    public static final int WORKS_VERSION = 21;

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
     * Framework V1: the framework towns' street asphalt, curbs and sidewalks, TownMesh, their paved columns, 2026-10-17;
     * 20: County road G3: the county road's C2 pavement and markings, its town mouth, its limit sign, its T junction's pad
     * and signs, 2026-10-18; 21: County road G3 side join: the county road past the home, the driveway's apron mesh onto
     * its paved edge, DriveMesh, 2026-10-19; 22: the gravel level and crownless at the gate's pad, PAD_LIFT over its whole
     * Y, 2026-10-19; 23: Town control V1: the towns' stop bars, crosswalks and centre lines, TownControl, their STOP
     * signs, speed limits, population lines and hundred blocks, TownSigns, 2026-10-19; 24: the towns' streets on the
     * block grid, TownGrid, 2026-10-19; 25: Linden's fixed layout, the same in every world, TownLayout, 2026-10-19; 26: Linden's site first, the network
     * planned round it, FixedTownSite / RoadNetworkPlan#pins, 2026-10-20; 27: 25 and 26 withdrawn, the towns grown round
     * their exits again with a landmark's block and the fallback search, TownSites#landmarks / TownExits#site, 2026-10-20; 28:
     * Fuel Stop A1's driveway aprons, TownBuildings#fuelStop, 2026-10-20; 29: its lot at its surface, its banked strips and
     * the objects on them raised, its aprons up to its surface, TownBuildings#bank, 2026-10-20; 30: an undivided road's
     * pavement one plane turning through level between reverse curves, no inverted crown, CrossSection#noInvertedCrown,
     * 2026-10-25; 31: road Mesh-ification P0, under the pavement the aggregate base and subbase in AFL gravel and the fill
     * in AFL backfill, no asphalt cube or vanilla dirt, gravel and cobblestone, RoadChunkPass, 2026-10-25; 32: the county road's driveway never crosses its road to the far edge,
     * RoadCounty#drive, 2026-10-25).
     */
    public static final int SURFACE_VERSION = 32;

    /** The road_network system version: the plan's rules, the stage, the built set and the surface version. */
    public static String networkVersion(String planVersion) {
        return planVersion + "#" + STAGE + "#" + BUILT + "#surface" + SURFACE_VERSION;
    }

    /** The terrain_works system version. */
    public static String worksVersion() { return "road_works#" + WORKS_VERSION; }
}
