package com.antaurora.apofirstlight.worldgen.terrain.v2;

/**
 * Checks the Java port of the r1 ecology zones (EcologyPlan) against the frozen research shares
 * (docs/worldgen/terrain_v2_real_terrain_research_v1.md K: agricultural plain 44 %, coastal pine 23 %, ridge oak 11 %,
 * riparian 9 %, slope forest 7 %, swamp 2.5 %, hollow conifer 1.4 %, marsh 0.9 %, wet prairie 0.3 %).
 * <pre>java EcologyZoneCheck SEED</pre>
 */
public final class EcologyZoneCheck {
    public static void main(String[] args) {
        long seed = Long.parseLong(args[0]);
        TerrainPlanV2 p = new TerrainPlanV2(seed);
        long t0 = System.nanoTime();
        byte[] z = EcologyPlan.classify(p).zone();
        long ms = (System.nanoTime() - t0) / 1_000_000;
        int[] count = new int[EcologyPlan.NAMES.length];
        int tot = 0;
        for (byte k : z) if (k >= 0) { count[k]++; tot++; }
        System.out.println("classified in " + ms + " ms, " + tot + " land + marsh cells");
        for (int k = 0; k < count.length; k++) System.out.printf("%-15s %6.2f %%%n", EcologyPlan.NAMES[k], 100.0 * count[k] / tot);
    }
}
