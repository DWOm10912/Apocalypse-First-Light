package com.antaurora.apofirstlight.worldgen;

public interface RandomStateSeedAccess {
    long apocalypse$getSeed();
    boolean apocalypse$hasMacroGeography();
    /** True when this noise router runs on the Terrain V2 plan (plan_terrain / plan_height bound to the seed). */
    boolean apocalypse$hasTerrainPlan();
    /** The Terrain V2 plan this router was bound to (null without a plan); C2, 2026-10-14. Generation code reads the plan
     *  through this, never by seed: an r1 and a federal world of the same seed have different plans. */
    com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface apocalypse$terrainPlanSurface();
    /** The profile of that plan (null without a plan). */
    com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainProfile apocalypse$terrainProfile();
    /** The gated generation systems on for this world (WorldgenGates; empty until the version lock binds them). C3. */
    java.util.Set<String> apocalypse$worldgenGates();
    void apocalypse$setWorldgenGates(java.util.Set<String> on);
}
