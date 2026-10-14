package com.antaurora.apofirstlight.worldgen.lock;

import com.antaurora.apofirstlight.tree.ForestPlan;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeography;
import com.antaurora.apofirstlight.worldgen.lock.WorldgenSystem.Severity;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanV2;

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

    /** Profile of every overworld this build generates with Terrain V2 (the r1 national map). */
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

    /** The rules of this build for an overworld with a Terrain V2 plan; forestEnabled is the world's mesh forest switch. */
    public static List<WorldgenSystem> current(boolean forestEnabled) {
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
