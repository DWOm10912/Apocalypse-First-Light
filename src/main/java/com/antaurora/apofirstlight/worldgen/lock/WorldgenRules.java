package com.antaurora.apofirstlight.worldgen.lock;

import com.antaurora.apofirstlight.tree.ForestPlan;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeography;
import com.antaurora.apofirstlight.worldgen.lock.WorldgenSystem.Severity;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanV2;
import com.antaurora.apofirstlight.worldgen.terrain.v2.FederalTerrainPlan;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainProfile;
import com.antaurora.apofirstlight.worldgen.federal.FederalTemplate;

import java.util.List;

/**
 * The generation rules this build applies to an AFL overworld, as world-compatibility versions (docs/worldgen/
 * worldgen_version_lock_v1.md). A world records them when it is created; every later load compares.
 *
 * <p>These are NOT cache versions. {@code TerrainPlanSurface.FORMAT} and the plan cache file name only say how a plan is
 * stored on this machine; a new layout that decodes to the same values changes nothing here. A version below changes
 * when chunks generated after the change could differ from chunks generated before it (heights, water, caves, biomes,
 * surface blocks, trees). The plan's values and queries are also fingerprinted automatically
 * (TerrainPlanFingerprint), so a forgotten bump of the plan rules is still caught; the chunk-level rules (density
 * functions, biome mapping) are probed as well (WorldgenProbe), but the declared versions stay the contract: carvers,
 * the surface rule and features are covered only by them.
 *
 * <p>Bump rule: raise the number after '#', never reuse one, and add the change to the doc's history table.
 */
public final class WorldgenRules {
    private WorldgenRules() {}

    /** Profile of every r1 overworld this build generates with Terrain V2 (the r1 national map). */
    public static final String PROFILE = "afl_terrain_v2";

    // national template: the land / sea layout the plan starts from (MacroGeography.VERSION 3: fixed three islands)
    static final String NATIONAL_TEMPLATE = "macro_geography#" + MacroGeography.VERSION;
    // terrain plan output. History (all 2026-10-10, before the lock existed; the planner label stayed r1):
    // #1 Phase 2 r1 heights; #2 Phase 2b rivers; #3 shore warp; #4 ecology stage (open-coast smoothing + ecology zones)
    static final int TERRAIN_PLAN_OUTPUT = 4;
    // chunk terrain from the plan: noise_settings/overworld.json, Plan*Density, caves, aquifer and carver stability
    static final int TERRAIN_DENSITY = 1;
    // river water and banks cut into chunks: RiverCarver, RiverWaterFill (the network itself is in the plan)
    static final int RIVER_CARVING = 1;
    // ecology: EcologyBiomes mapping, the AFL biome JSONs, the overworld surface rule
    static final int ECOLOGY = 1;

    // C2 (2026-10-14): the federal experimental profile (world preset apocalypse_firstlight:federal_experimental). Its own
    // systems, so an r1 world and a federal world never share a baseline (the profile itself is a HARD finding too).
    // federal template: FederalTemplate.VERSION (C1 layout: coast, six states, provinces, climate)
    static final int FEDERAL_TEMPLATE = 1;
    // federal plan output: FederalTerrainPlan (provinces, landforms, lakes, layers, ecology zones)
    static final int FEDERAL_PLAN_OUTPUT = 1;
    // federal chunk terrain: noise_settings/federal_experimental.json, density_function/federal/*, raised top slide
    static final int FEDERAL_DENSITY = 1;
    // federal water in chunks: RiverCarver / RiverWaterFill with lakes
    static final int FEDERAL_WATER = 1;
    // federal ecology: FederalEcology zones, FederalEcologyBiomes mapping, federal biome JSONs, surface rule branch
    static final int FEDERAL_ECOLOGY = 1;

    /** The rules of this build for an r1 overworld; forestEnabled is the world's mesh forest switch. */
    public static List<WorldgenSystem> current(boolean forestEnabled) {
        return current(TerrainProfile.R1, forestEnabled);
    }

    /** The rules of this build for an overworld on a Terrain V2 plan of the given profile. */
    public static List<WorldgenSystem> current(TerrainProfile profile, boolean forestEnabled) {
        if (profile == TerrainProfile.FEDERAL) return List.of(
                new WorldgenSystem("national_template", "federal_template#" + FederalTemplate.VERSION + "#" + FEDERAL_TEMPLATE, Severity.HARD,
                        "federal coast, six states, provinces and climate"),
                new WorldgenSystem("terrain_plan", FederalTerrainPlan.VERSION + "#" + FEDERAL_PLAN_OUTPUT, Severity.HARD,
                        "planned heights, water, lakes, rivers and ecology zones"),
                new WorldgenSystem("terrain_density", "federal_density#" + FEDERAL_DENSITY, Severity.HARD,
                        "chunk terrain shape, caves and aquifers"),
                new WorldgenSystem("river_carving", "federal_water#" + FEDERAL_WATER, Severity.HARD, "river and lake water and banks in chunks"),
                new WorldgenSystem("ecology", "ecology_federal#" + FEDERAL_ECOLOGY, Severity.HARD, "biomes and surface blocks"),
                // C3.2 (gated: only worlds created with them have them, WorldgenGates)
                new WorldgenSystem(WorldgenGates.ROAD_NETWORK,
                        com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkStage.networkVersion(
                                com.antaurora.apofirstlight.worldgen.network.route.RoadNetworkPlan.VERSION), Severity.HARD,
                        "public road routes, pavement, collision and bridges"),
                new WorldgenSystem(WorldgenGates.TERRAIN_WORKS,
                        com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkStage.worksVersion(), Severity.HARD,
                        "road cuts, fills, slopes and ditches in the terrain"),
                new WorldgenSystem("forest", forestEnabled ? "mesh_forest#" + ForestPlan.VERSION : "vanilla_trees", Severity.SOFT,
                        "natural trees"));
        return List.of(
                new WorldgenSystem("national_template", NATIONAL_TEMPLATE, Severity.HARD, "land / sea layout and satellite islands"),
                new WorldgenSystem("terrain_plan", TerrainPlanV2.VERSION + "#" + TERRAIN_PLAN_OUTPUT, Severity.HARD,
                        "planned heights, water, rivers and ecology zones"),
                new WorldgenSystem("terrain_density", "overworld_density#" + TERRAIN_DENSITY, Severity.HARD,
                        "chunk terrain shape, caves and aquifers"),
                new WorldgenSystem("river_carving", "river_carver#" + RIVER_CARVING, Severity.HARD, "river water and banks in chunks"),
                new WorldgenSystem("ecology", "ecology_r1#" + ECOLOGY, Severity.HARD, "biomes and surface blocks"),
                new WorldgenSystem("forest", forestEnabled ? "mesh_forest#" + ForestPlan.VERSION : "vanilla_trees", Severity.SOFT,
                        "natural trees"));
    }
}
