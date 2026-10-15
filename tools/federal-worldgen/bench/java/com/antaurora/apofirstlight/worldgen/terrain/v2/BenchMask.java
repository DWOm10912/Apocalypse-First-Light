package com.antaurora.apofirstlight.worldgen.terrain.v2;

import com.antaurora.apofirstlight.worldgen.federal.FederalMap;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeography;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeographySample;

/**
 * Scale bench only (tools/federal-worldgen/bench.sh): the land / sea mask the PATCHED copy of TerrainPlanV2 reads
 * (bench.sh rewrites {@code geo.sample(} to {@code BenchMask.sample(geo, seed, }). -Dafl.planMask=federal swaps the
 * r1 MacroGeography map for the federal map at -Dafl.planScale; the planner itself is unchanged. Never in the mod.
 */
final class BenchMask {
    static final String MODE = System.getProperty("afl.planMask", "r1");
    static final double SCALE = Double.parseDouble(System.getProperty("afl.planScale", "1"));

    private record Holder(long seed, FederalMap map) {}

    private static volatile Holder holder;

    static MacroGeographySample sample(MacroGeography geo, long seed, int x, int z) {
        if (!MODE.equals("federal")) return geo.sample(x, z);
        Holder h = holder;
        if (h == null || h.seed != seed) holder = h = new Holder(seed, FederalMap.of(seed, SCALE));
        return h.map.sample(x, z);
    }
}
