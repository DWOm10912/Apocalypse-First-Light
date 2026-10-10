package com.antaurora.apofirstlight.worldgen.terrain.v2;

/**
 * The frozen Terrain V2 r1 ecology zones (potential natural vegetation; docs/worldgen/terrain_v2_real_terrain_research_v1.md
 * K section, tools/terrain-v2-research/national_plan.py ecology()), ported unchanged to the 16 m plan grid in Java
 * (2026-10-10, ecology stage) so the preview and the game read the same zones. Rules, later ones overriding earlier
 * ones, on land cells (water class 0; marsh is its own zone):
 * <ol>
 *   <li>default: agricultural till plain (potential oak-hickory / beech-maple; "agriculture-suitable" is not farmland);</li>
 *   <li>slope over 0.08: slope and ravine broadleaf forest (mixed mesophytic). Since the ecology stage the threshold
 *   varies with a 300 m noise (about 0.06..0.10; user decision 6, 2026-10-10): the fold belt's outer front rises at an
 *   even 0.074..0.081 for kilometres, and a fixed 0.08 drew a straight forest band along it; now the front carries
 *   irregular forest blocks and the landform stays as r1 planned it; slopes under 0.12 are forest only where a 250 m
 *   patch noise allows (about 60 %), steeper ones always;</li>
 *   <li>coastal plain weight over 0.5 and slope at most the slope-forest threshold: coastal pine-oak. Since the ecology
 *   stage the 0.5 varies with a 500 m noise (0.3..0.7): where r1's coastal weight falls off along a straight
 *   province edge (near the fold belt's ends) a fixed 0.5 drew a straight forest edge for kilometres;</li>
 *   <li>resistant rock over 0.2, or fold belt over 0.5 with slope over 0.15: ridge oak (mountain forest);</li>
 *   <li>belt + foothills over 0.5, slope over 0.12, facing north (over 0.5), HAND under 25 m: hemlock-white pine
 *   hollows (local conifer);</li>
 *   <li>HAND under 3 m (height above the nearest channel of 2 km2 or more): riparian / floodplain forest, or coastal
 *   swamp forest on the coastal plain;</li>
 *   <li>till plain over 0.6, HAND under 1 m, slope under 0.006, drainage under 0.2 km2: wet prairie and swales;</li>
 *   <li>marsh cells: tidal marsh.</li>
 * </ol>
 */
public final class EcologyPlan {
    private EcologyPlan() {
    }

    public static final byte NONE = -1, AGRI_PLAIN = 0, RIPARIAN = 1, SLOPE_FOREST = 2, RIDGE_OAK = 3, HOLLOW_CONIFER = 4,
            COASTAL_PINE = 5, SWAMP_FOREST = 6, TIDAL_MARSH = 7, WET_PRAIRIE = 8;
    public static final String[] NAMES = {"agri_plain", "riparian", "slope_forest", "ridge_oak", "hollow_conifer",
            "coastal_pine", "swamp_forest", "tidal_marsh", "wet_prairie"};

    /** Height above the nearest channel of at least minArea along the final receivers (national_plan.hand). */
    static float[] hand(TerrainPlanV2 p, double minArea) {
        int n = p.n;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        // receivers are lower: ascending height visits every receiver before its donors (stable for ties)
        java.util.Arrays.sort(order, (a, b) -> Float.compare(p.h[a], p.h[b]));
        float[] ref = new float[n];
        java.util.Arrays.fill(ref, Float.NaN);
        for (int i = 0; i < n; i++) if (!(p.water[i] == 0 || p.water[i] == 3)) ref[i] = p.h[i];
        for (Integer boxed : order) {
            int i = boxed;
            if (!(p.water[i] == 0 || p.water[i] == 3)) continue;
            if (p.area[i] >= minArea) ref[i] = p.h[i];
            else {
                int j = p.recv[i];
                if (j >= 0 && !Float.isNaN(ref[j])) ref[i] = ref[j];
                else if (j < 0) ref[i] = 63f;
            }
        }
        float[] out = new float[n];
        for (int i = 0; i < n; i++) out[i] = Float.isNaN(ref[i]) ? 99f : p.h[i] - ref[i];
        return out;
    }

    /** Zones per cell, plus the HAND of each cell in whole metres (0..120; the woodlot bias reads it). */
    public record Zones(byte[] zone, byte[] hand) {
    }

    public static Zones classify(TerrainPlanV2 p) {
        final int N = TerrainPlanV2.N;
        float[] hand = hand(p, 2e6);
        byte[] z = new byte[p.n], hb = new byte[p.n];
        for (int i = 0; i < p.n; i++) hb[i] = (byte) Math.max(0, Math.min(120, Math.round(hand[i])));
        double cell = TerrainPlanV2.CELL;
        for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) {
            int i = r * N + c;
            if (p.water[i] == 3) { z[i] = TIDAL_MARSH; continue; }
            if (p.water[i] != 0) { z[i] = NONE; continue; }
            // numpy.gradient: central differences inside, one-sided at the edges; gy +row = south
            double gx = c == 0 ? (p.h[i + 1] - p.h[i]) / cell : c == N - 1 ? (p.h[i] - p.h[i - 1]) / cell : (p.h[i + 1] - p.h[i - 1]) / (2 * cell);
            double gy = r == 0 ? (p.h[i + N] - p.h[i]) / cell : r == N - 1 ? (p.h[i] - p.h[i - N]) / cell : (p.h[i + N] - p.h[i - N]) / (2 * cell);
            double sl = Math.hypot(gx, gy);
            double north = -gy / Math.max(sl, 1e-6);
            double plain = p.wPlain[i], coastal = p.wCoastal[i], foot = p.wFoot[i], belt = p.wBelt[i], res = p.resist[i];
            byte k = AGRI_PLAIN;
            double thr = 0.08 * (1 + 0.25 * Math.max(-1.6, Math.min(1.6, p.fbm(TerrainPlanV2.cx(c), TerrainPlanV2.cx(r), 300, 2, 0.5, 640))));
            // gentle slopes just over the threshold carry forest in patches (about 60 %, 250 m noise), steeper ones always
            if (sl > thr && (sl > 0.12 || p.fbm(TerrainPlanV2.cx(c), TerrainPlanV2.cx(r), 250, 2, 0.5, 642) > -0.25)) k = SLOPE_FOREST;
            double coastThr = 0.5 + 0.2 * Math.max(-1, Math.min(1, p.fbm(TerrainPlanV2.cx(c), TerrainPlanV2.cx(r), 500, 2, 0.5, 641) / 1.6));
            if (coastal > coastThr && sl <= thr) k = COASTAL_PINE;
            if (res > 0.2 || (belt > 0.5 && sl > 0.15)) k = RIDGE_OAK;
            if (belt + foot > 0.5 && sl > 0.12 && north > 0.5 && hand[i] < 25) k = HOLLOW_CONIFER;
            if (hand[i] < 3) k = coastal > coastThr ? SWAMP_FOREST : RIPARIAN;
            if (plain > 0.6 && hand[i] < 1.0 && sl < 0.006 && p.area[i] < 2e5) k = WET_PRAIRIE;
            z[i] = k;
        }
        return new Zones(z, hb);
    }
}
