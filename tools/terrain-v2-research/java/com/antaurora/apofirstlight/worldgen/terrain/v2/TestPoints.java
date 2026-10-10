package com.antaurora.apofirstlight.worldgen.terrain.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.IntToDoubleFunction;

/**
 * Terrain V2 Phase 2 acceptance points for a seed (offline): the natural spawn and one representative place per
 * landform, picked from the plan by fixed rules, printed as TSV (id, x, z, plan surface Y, note).
 *   java -cp CLASSES com.antaurora.apofirstlight.worldgen.terrain.v2.TestPoints SEED
 */
public final class TestPoints {
    public static void main(String[] args) {
        long seed = Long.parseLong(args[0]);
        TerrainPlanV2 p = new TerrainPlanV2(seed);
        TerrainPlanSurface s = p.toSurface();
        List<String> out = new ArrayList<>();
        int[] spawn = s.naturalSpawn(4096);
        add(out, s, "spawn", spawn[0], spawn[1], "natural-land spawn (TerrainV2SpawnEvents)");
        int n = TerrainPlanV2.N * TerrainPlanV2.N;
        IntPredicate land = i -> p.water[i] == 0 && p.landmass[i] == 0;
        pick(out, s, p, "plain_flat", i -> land.test(i) && p.wPlain[i] > 0.9 && p.area[i] < 2e5 && p.slopeAt(i) < 0.01 && p.dCoast[i] > 2000,
                i -> -dist(p, i, spawn) , "flat till plain upland (interfluve) nearest the spawn");
        pick(out, s, p, "plain_valley", i -> land.test(i) && p.wPlain[i] > 0.8 && p.area[i] >= 5e6 && p.dCoast[i] > 1500,
                i -> p.area[i], "the largest plain river reach (floodplain, bluffs)");
        pick(out, s, p, "moraine_or_divide", i -> land.test(i) && p.wPlain[i] > 0.8, i -> p.h[i], "highest point of the till plain");
        pick(out, s, p, "belt_crest", i -> land.test(i) && p.resist[i] > 0.4, i -> p.h[i], "highest fold-belt ridge crest");
        pick(out, s, p, "belt_valley", i -> land.test(i) && p.wBelt[i] > 0.9 && p.resist[i] < 0.03 && p.area[i] < 5e5,
                i -> -p.h[i] - dist(p, i, centre(p)) / 200, "a strike valley between the ridges");
        pick(out, s, p, "water_gap", i -> land.test(i) && p.trunk[i] && p.wBelt[i] > 0.5, i -> p.area[i], "trunk river cutting through the ridges");
        pick(out, s, p, "foothills", i -> land.test(i) && p.wFoot[i] > 0.8, i -> p.slopeAt(i), "steepest dissected foothill slope");
        pick(out, s, p, "estuary", i -> p.water[i] == 2, i -> p.area[i], "the largest drowned valley (estuary)");
        pick(out, s, p, "marsh", i -> p.water[i] == 3, i -> -dist(p, i, spawn), "tidal marsh nearest the spawn");
        pick(out, s, p, "coastal_plain", i -> land.test(i) && p.wCoastal[i] > 0.9 && p.dCoast[i] > 300 && p.slopeAt(i) < 0.02,
                i -> -dist(p, i, spawn), "coastal plain terrace nearest the spawn");
        pick(out, s, p, "seam_test", i -> land.test(i) && p.wBelt[i] > 0.5 && (i % TerrainPlanV2.N) % 1 == 0, i -> p.slopeAt(i),
                "steepest belt flank (check the chunk borders here)");
        pick(out, s, p, "satellite", i -> p.water[i] == 0 && p.landmass[i] == 1, i -> p.dCoast[i], "satellite island 1, its middle");
        for (String line : out) System.out.println(line);
    }

    static int[] centre(TerrainPlanV2 p) { return new int[]{(int) p.frameCx, (int) p.frameCz}; }

    static double dist(TerrainPlanV2 p, int i, int[] xz) {
        return Math.hypot(TerrainPlanV2.cx(i % TerrainPlanV2.N) - xz[0], TerrainPlanV2.cx(i / TerrainPlanV2.N) - xz[1]);
    }

    static void pick(List<String> out, TerrainPlanSurface s, TerrainPlanV2 p, String id, IntPredicate ok, IntToDoubleFunction score, String note) {
        int best = -1;
        double bs = -Double.MAX_VALUE;
        for (int i = 0; i < TerrainPlanV2.N * TerrainPlanV2.N; i++) {
            if (!ok.test(i)) continue;
            double v = score.applyAsDouble(i);
            if (v > bs) { bs = v; best = i; }
        }
        if (best < 0) { out.add(id + "\t-\t-\t-\tnone"); return; }
        add(out, s, id, (int) TerrainPlanV2.cx(best % TerrainPlanV2.N), (int) TerrainPlanV2.cx(best / TerrainPlanV2.N), note);
    }

    static void add(List<String> out, TerrainPlanSurface s, String id, int x, int z, String note) {
        out.add(String.format("%s\t%d\t%d\t%.1f\t%s (water %d, stable %d, shore %d)", id, x, z, s.heightAt(x, z), note,
                s.waterClass(x, z), s.stableDepth(x, z), s.shoreDistance(x, z)));
    }
}
