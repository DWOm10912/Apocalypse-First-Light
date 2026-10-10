package com.antaurora.apofirstlight.worldgen;

public interface RandomStateSeedAccess {
    long apocalypse$getSeed();
    boolean apocalypse$hasMacroGeography();
    /** True when this noise router runs on the Terrain V2 plan (plan_terrain / plan_height bound to the seed). */
    boolean apocalypse$hasTerrainPlan();
}
