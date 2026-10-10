package com.antaurora.apofirstlight.worldgen.terrain.v2;

/**
 * Plan profile along a line (2026-10-10, ecology stage): height, slope, provinces, resistance, HAND, zone every STEP
 * metres, to see what a straight feature on the maps is made of.
 * <pre>java PlanProbe SEED X0 Z0 X1 Z1 STEP</pre>
 */
public final class PlanProbe {
    public static void main(String[] a) {
        long seed = Long.parseLong(a[0]);
        double x0 = Double.parseDouble(a[1]), z0 = Double.parseDouble(a[2]), x1 = Double.parseDouble(a[3]), z1 = Double.parseDouble(a[4]);
        double step = Double.parseDouble(a[5]);
        TerrainPlanV2 p = new TerrainPlanV2(seed);
        TerrainPlanSurface s = p.toSurface();
        EcologyPlan.Zones zones = EcologyPlan.classify(p);
        float[] hand = EcologyPlan.hand(p, 2e6);
        double len = Math.hypot(x1 - x0, z1 - z0);
        System.out.println("   d      x      z      h   slope  plain coast  foot  belt resist  hand  zone  uu      vv");
        for (double d = 0; d <= len; d += step) {
            double x = x0 + (x1 - x0) * d / len, z = z0 + (z1 - z0) * d / len;
            int i = TerrainPlanV2.idx(TerrainPlanSurface.cellOf(x), TerrainPlanSurface.cellOf(z));
            System.out.printf("%5.0f %6.0f %6.0f %6.1f %6.3f %5.2f %5.2f %5.2f %5.2f %5.2f %6.1f  %-14s %7.0f %7.0f%n", d, x, z, s.heightAt(x, z),
                    s.slopeAt(x, z), p.wPlain[i], p.wCoastal[i], p.wFoot[i], p.wBelt[i], p.resist[i], hand[i],
                    zones.zone()[i] < 0 ? "water" : EcologyPlan.NAMES[zones.zone()[i]], p.u(x, z), p.v(x, z));
        }
    }
}
