package com.antaurora.apofirstlight.worldgen.federal;

import java.util.List;

import static com.antaurora.apofirstlight.worldgen.federal.FederalStateId.*;

/**
 * Federal six-state national map, template V1 PROTOTYPE (C1, 2026-10-14; docs/worldgen/federal_six_state_c1.md).
 * The fixed layout every seed shares; {@link FederalMap} adds the per-seed variation. NOT used by world generation:
 * the live overworld is still the r1 MacroGeography map. Nothing here may be read by the r1 generator.
 *
 * <p>Coordinates are template metres (= blocks at scale 1), fixed north: -Z is north, +X is east (no random rotation).
 * Angles are {@code atan2(z, x)} in degrees: 0 east, 90 south, -90 north, 180 west. The mainland is about 20 x 16 km.
 *
 * <p>Two independent layers, on purpose:
 * <ul>
 *   <li>physical: {@link #ANCHORS} (physiographic provinces, blended smoothly) and the climate gradients; terrain and
 *   ecology may only ever follow these;</li>
 *   <li>political: the state borders ({@link #WEST_MERIDIAN} ...), survey lines plus one natural line; names, signs,
 *   broadcasts, structures and the spawn follow these. A border never decides a block or a biome.</li>
 * </ul>
 */
public final class FederalTemplate {
    private FederalTemplate() {}

    public static final String VERSION = "federal_template_v1_proto";

    // ------------------------------------------------------------------ mainland outline
    /** Half extents of the mainland's superellipse (m) and its exponent (2: ellipse; higher: squarer). */
    public static final double HALF_X = 10000, HALF_Z = 8000, SHAPE_P = 2.6;

    /** A named coastal feature: a bay (depth > 0, m inland) or a cape (depth < 0), at a direction, Gaussian half-width in radians. */
    public record CoastFeature(String id, double angleDeg, double width, double depth) {}

    public static final List<CoastFeature> COAST = List.of(
            new CoastFeature("east_estuary_bay", 8, 0.085, 1700),     // S1's trunk river mouth (Chesapeake-like)
            new CoastFeature("southern_gulf", 97, 0.13, 1300),        // between the plains and the wetland coast
            new CoastFeature("north_sound", -68, 0.08, 1000),         // the lake state's harbour
            new CoastFeature("northwest_inlet", -138, 0.035, 900),    // a fjord-like inlet below the cold range
            new CoastFeature("west_cove", 172, 0.05, 550),
            new CoastFeature("southeast_cape", 38, 0.07, -650),
            new CoastFeature("northeast_headland", -28, 0.06, -450));

    /** Coast roughness (0 smooth .. 1 rugged) by direction: rocky fjords in the north-west, beaches and marsh in the south-east. */
    public static final double[][] ROUGHNESS = {
            {-180, 0.75}, {-140, 1.0}, {-100, 0.8}, {-60, 0.45}, {-20, 0.35}, {20, 0.25}, {60, 0.15}, {100, 0.3}, {140, 0.55}, {180, 0.75}};

    // ------------------------------------------------------------------ satellite islands (SatelliteIslandPolicy roles)
    /** Directions of the three island slots (deg), the gap to the mainland shore and the island size, m. */
    public static final double[] ISLAND_SLOT_DEG = {-60, 128, 24};
    public static final double ISLAND_GAP = 450, ISLAND_MAJOR = 650, ISLAND_MINOR = 450;

    // ------------------------------------------------------------------ political layer: the state borders
    // A decision tree of survey lines (meridians and parallels, straight like most North American borders) and one
    // natural line (S1 / S5, to follow a river once the national plan exists). Each value is jittered per seed.
    /** x of the western meridian: S3 / S6 west of it. */
    public static final double WEST_MERIDIAN = -4700;
    /** z of the western parallel: S3 north of it, S6 south. */
    public static final double WEST_PARALLEL = -1500;
    /** z of the northern parallel east of the western meridian: S4 north of it; it steps north east of NORTH_STEP_X. */
    public static final double NORTH_PARALLEL = -3300, NORTH_STEP_X = 3000, NORTH_PARALLEL_EAST = -4600;
    /** x of the central meridian: S2 west of it, S1 / S5 east. */
    public static final double CENTRE_MERIDIAN = -900;
    /** mean z of the natural S1 / S5 line and its meander amplitude, m. */
    public static final double SOUTH_LINE = 1900, SOUTH_LINE_MEANDER = 450;
    /** Seed jitter of every border value, m. */
    public static final double BORDER_JITTER = 250;

    // ------------------------------------------------------------------ environments (design targets per state)
    public enum Theme {
        TEMPERATE_FOREST_VALLEY, AGRICULTURAL_PLAINS, COLD_MOUNTAIN_CONIFER, INDUSTRIAL_LAKES_MIXED_FOREST,
        SOUTHERN_WETLAND_COAST, WESTERN_DESERT_PLATEAU
    }

    /** The environment a state is designed around (the dominant one, not the only one: provinces blend across borders). */
    public static Theme theme(FederalStateId s) {
        return switch (s) {
            case S1 -> Theme.TEMPERATE_FOREST_VALLEY;
            case S2 -> Theme.AGRICULTURAL_PLAINS;
            case S3 -> Theme.COLD_MOUNTAIN_CONIFER;
            case S4 -> Theme.INDUSTRIAL_LAKES_MIXED_FOREST;
            case S5 -> Theme.SOUTHERN_WETLAND_COAST;
            case S6 -> Theme.WESTERN_DESERT_PLATEAU;
        };
    }

    /** V1 depth: S1 and S2 get cities, roads and structures first; the others get their natural environment. */
    public static boolean deepInV1(FederalStateId s) { return s == S1 || s == S2; }

    // ------------------------------------------------------------------ physical layer: physiographic provinces
    public enum Physiography { MOUNTAIN, PLATEAU, PLAINS, LAKE_LOWLAND, HILLS, COASTAL_LOWLAND }

    /** A province core as a capsule (segment a-b with a core radius) and the width over which it fades out, m. */
    public record Anchor(Physiography kind, double ax, double az, double bx, double bz, double core, double fade) {}

    public static final List<Anchor> ANCHORS = List.of(
            new Anchor(Physiography.MOUNTAIN, -8800, -6600, -5600, -900, 1600, 1700),     // the cold range, NNW-SSE
            new Anchor(Physiography.PLATEAU, -7600, 1800, -6600, 6000, 2000, 1700),       // the desert tableland
            new Anchor(Physiography.PLAINS, -2700, -2200, -2400, 6400, 1800, 2000),       // the farm belt
            new Anchor(Physiography.LAKE_LOWLAND, -2600, -5800, 3400, -5900, 1500, 1500), // glacial lakes and mill towns
            new Anchor(Physiography.HILLS, 600, -1700, 7200, -800, 1700, 1600),           // forested ridges and river valleys
            new Anchor(Physiography.COASTAL_LOWLAND, 2000, 5600, 8200, 4200, 1800, 1600));// bottomland swamps and marsh

    /** Province warp: amplitude and wavelength, m (no province is an ellipse). */
    public static final double WARP_AMP = 650, WARP_WAVE = 3200;

    // ------------------------------------------------------------------ settlements and roads
    public enum CityRole { CITY, TOWN, VILLAGE, PORT, INDUSTRIAL, MINING, OUTPOST }

    /** A city candidate: a place a settlement should go, checked against the national plan later (C2). rank 1 = largest. */
    public record CitySite(String id, FederalStateId state, CityRole role, int rank, double x, double z) {}

    public static final List<CitySite> CITIES = List.of(
            new CitySite("valley_town", S1, CityRole.TOWN, 2, 2400, -1100),
            new CitySite("river_city", S1, CityRole.CITY, 1, 7300, 900),
            new CitySite("hill_village", S1, CityRole.VILLAGE, 3, 4700, -2700),
            new CitySite("county_seat", S2, CityRole.TOWN, 2, -2700, 1600),
            new CitySite("farm_town_north", S2, CityRole.VILLAGE, 3, -3000, -2300),
            new CitySite("farm_town_south", S2, CityRole.VILLAGE, 3, -2100, 5600),
            new CitySite("mining_town", S3, CityRole.MINING, 3, -7300, -4300),
            new CitySite("pass_village", S3, CityRole.VILLAGE, 3, -5300, -2300),
            new CitySite("industrial_city", S4, CityRole.INDUSTRIAL, 1, -200, -5500),
            new CitySite("port_town", S4, CityRole.PORT, 3, 5600, -6300),
            new CitySite("mill_town", S4, CityRole.VILLAGE, 3, -3300, -6000),
            new CitySite("port_city", S5, CityRole.PORT, 2, 6900, 5200),
            new CitySite("bayou_village", S5, CityRole.VILLAGE, 3, 2600, 5800),
            new CitySite("shore_town", S5, CityRole.TOWN, 3, 8300, 3000),
            new CitySite("desert_town", S6, CityRole.TOWN, 2, -6900, 2600),
            new CitySite("canyon_outpost", S6, CityRole.OUTPOST, 3, -7300, 4700));

    /** City candidate jitter per seed, m. */
    public static final double CITY_JITTER = 220;

    public enum RoadClass { INTERSTATE, STATE_HIGHWAY, ISLAND_BRIDGE }

    /** A corridor through city candidates in order; the route between them is planned (NationalRoadPlan). */
    public record CorridorSpec(String id, RoadClass roadClass, List<String> via) {}

    public static final List<CorridorSpec> CORRIDORS = List.of(
            new CorridorSpec("interstate_ew", RoadClass.INTERSTATE, List.of("desert_town", "county_seat", "valley_town", "river_city")),
            new CorridorSpec("interstate_ns", RoadClass.INTERSTATE, List.of("industrial_city", "valley_town", "port_city")),
            new CorridorSpec("sh_mountain", RoadClass.STATE_HIGHWAY, List.of("mining_town", "pass_village", "farm_town_north")),
            new CorridorSpec("sh_range_front", RoadClass.STATE_HIGHWAY, List.of("pass_village", "desert_town")),
            new CorridorSpec("sh_canyon", RoadClass.STATE_HIGHWAY, List.of("desert_town", "canyon_outpost")),
            new CorridorSpec("sh_lakes", RoadClass.STATE_HIGHWAY, List.of("mill_town", "industrial_city", "port_town")),
            new CorridorSpec("sh_farm_north", RoadClass.STATE_HIGHWAY, List.of("farm_town_north", "mill_town")),
            new CorridorSpec("sh_farm_belt", RoadClass.STATE_HIGHWAY, List.of("farm_town_north", "county_seat", "farm_town_south")),
            new CorridorSpec("sh_gulf", RoadClass.STATE_HIGHWAY, List.of("farm_town_south", "bayou_village", "port_city", "shore_town", "river_city")),
            new CorridorSpec("sh_hills", RoadClass.STATE_HIGHWAY, List.of("valley_town", "hill_village", "port_town")));

    // ------------------------------------------------------------------ start
    /** The national start (the forest-house start, M4): a valley in S1 near the S1 / S2 border; the house site is
     *  searched within START_RADIUS by the forest-home planner, which owns that choice. */
    public static final double START_X = 300, START_Z = -400, START_RADIUS = 2000;
}
