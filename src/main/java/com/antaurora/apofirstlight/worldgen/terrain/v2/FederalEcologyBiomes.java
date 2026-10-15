package com.antaurora.apofirstlight.worldgen.terrain.v2;

/**
 * The surface biome of a column in the federal experimental world (C2, 2026-10-14; docs/worldgen/federal_six_state_c2.md):
 * EcologyBiomes' rules (sea, beach, soft ecotones through the 30 m domain warp, woodlots, the riparian corridor) on the
 * federal zones (FederalEcology), plus the federal biomes. Two federal differences on the r1 zones:
 * <ul>
 *   <li>woodlots: the open layer (-1..1) moves the woodlot threshold: the plains (S2) are open farm country with
 *   scattered groves, the hill country (S1, the forest state) is mostly wooded with farm clearings, elsewhere r1's share;</li>
 *   <li>indices from {@link #MONTANE_CONIFER} on are the federal biomes (AflBiomes.FEDERAL, in this order).</li>
 * </ul>
 */
public final class FederalEcologyBiomes {
    private FederalEcologyBiomes() {}

    public static final int MONTANE_CONIFER = 13, ALPINE_MEADOW = 14, ALPINE_PEAKS = 15, LAKESHORE_FOREST = 16, LAKE_FEN = 17,
            FRESHWATER_LAKE = 18, PLATEAU_STEPPE = 19, CANYON_BADLANDS = 20;
    public static final String[] IDS = {"montane_conifer_forest", "alpine_meadow", "alpine_peaks", "lakeshore_mixed_forest",
            "lake_fen", "freshwater_lake", "plateau_steppe", "canyon_badlands"};
    public static final int[] COLOURS = {0x3E5E48, 0xA9C08A, 0xE8ECEF, 0x5C8A60, 0x8FAF95, 0x3F78A8, 0xC9B27A, 0xB8643C};
    public static final int COUNT = EcologyBiomes.IDS.length + IDS.length;

    public static int biomeAt(TerrainPlanSurface s, EcologyPlan.Zones zones, double x, double z) {
        int wc = s.waterClass(x, z);
        double h = s.heightAt(x, z);
        if (h < 63 && s.seaFloodAt(x, z, h)) return EcologyBiomes.SEA;
        if (EcologyBiomes.beachAt(s, x, z, wc, h)) return EcologyBiomes.BEACH;
        double ex = EcologyBiomes.ECO_A0 * PlanNoise.noise(s.noiseSeed, x / EcologyBiomes.ECO_L0, z / EcologyBiomes.ECO_L0, 605)
                + EcologyBiomes.ECO_A1 * PlanNoise.noise(s.noiseSeed, x / EcologyBiomes.ECO_L1, z / EcologyBiomes.ECO_L1, 601)
                + EcologyBiomes.ECO_A2 * PlanNoise.noise(s.noiseSeed, x / EcologyBiomes.ECO_L2, z / EcologyBiomes.ECO_L2, 603);
        double ez = EcologyBiomes.ECO_A0 * PlanNoise.noise(s.noiseSeed, x / EcologyBiomes.ECO_L0, z / EcologyBiomes.ECO_L0, 606)
                + EcologyBiomes.ECO_A1 * PlanNoise.noise(s.noiseSeed, x / EcologyBiomes.ECO_L1, z / EcologyBiomes.ECO_L1, 602)
                + EcologyBiomes.ECO_A2 * PlanNoise.noise(s.noiseSeed, x / EcologyBiomes.ECO_L2, z / EcologyBiomes.ECO_L2, 604);
        // a lake's water and bed read the lake directly (no warp: the shore line is the lake's own)
        double[] share = new double[1];
        int top = s.lakeTopAt(x, z, share);
        if (top != Integer.MIN_VALUE && share[0] >= 0.5 && h < top + 0.5) return FRESHWATER_LAKE;
        int zone = softZone(s, zones.zone(), x + ex, z + ez, x, z);
        // openness -1 (wooded hill country) .. 0 (r1) .. 1 (open plains)
        double open = s.layers != null && s.layers.open() != null ? s.bilinear(s.layers.open(), gx(s, x), gx(s, z)) / 127.5 - 1 : 0;
        switch (zone) {
            case EcologyPlan.AGRI_PLAIN: {
                double hand = EcologyBiomes.bilinearHand(s, zones.hand(), x + ex, z + ez);
                double bias = 0.9 * Math.max(0, Math.min(1, (14 - hand) / 14)) + 3.0 * Math.min(0.12, s.slopeAt(x, z));
                double wood = PlanNoise.fbm(s.noiseSeed, x, z, 240, 3, 0.5, 611) + bias;
                return wood > 0.95 + (open > 0 ? 0.85 : 1.2) * open ? EcologyBiomes.WOODLAND : EcologyBiomes.GRASSLAND;
            }
            case EcologyPlan.RIPARIAN: {
                int b = EcologyBiomes.floodplain(s, zones, x, z, ex, ez);
                // open country: the floodplain beyond the riparian corridor keeps fewer woodland islands
                if (b == EcologyBiomes.WOODLAND && open > 0.3 && PlanNoise.fbm(s.noiseSeed, x, z, 200, 3, 0.5, 632) < 1.15 + 0.6 * open)
                    return EcologyBiomes.GRASSLAND;
                return b;
            }
            case EcologyPlan.SLOPE_FOREST: return EcologyBiomes.SLOPE_FOREST;
            case EcologyPlan.RIDGE_OAK: return EcologyBiomes.RIDGE_FOREST;
            case EcologyPlan.HOLLOW_CONIFER: return EcologyBiomes.HEMLOCK_HOLLOW;
            case EcologyPlan.COASTAL_PINE:
                return PlanNoise.fbm(s.noiseSeed, x, z, 110, 2, 0.5, 612) > 1.1 ? EcologyBiomes.COASTAL_CLEARING : EcologyBiomes.COASTAL_PINE;
            case EcologyPlan.SWAMP_FOREST: return EcologyBiomes.SWAMP_FOREST;
            case EcologyPlan.TIDAL_MARSH: return EcologyBiomes.TIDAL_MARSH;
            case EcologyPlan.WET_PRAIRIE: return EcologyBiomes.WET_PRAIRIE;
            case FederalEcology.MONTANE: return MONTANE_CONIFER;
            case FederalEcology.ALPINE_MEADOW: return ALPINE_MEADOW;
            case FederalEcology.ALPINE_ROCK: return ALPINE_PEAKS;
            case FederalEcology.LAKESHORE: return LAKESHORE_FOREST;
            case FederalEcology.FEN: return LAKE_FEN;
            case FederalEcology.LAKE: return top != Integer.MIN_VALUE && h < top + 0.5 ? FRESHWATER_LAKE : LAKE_FEN;
            case FederalEcology.STEPPE: return PLATEAU_STEPPE;
            case FederalEcology.CANYON: return CANYON_BADLANDS;
            default: return EcologyBiomes.GRASSLAND;
        }
    }

    private static double gx(TerrainPlanSurface s, double coord) { return (coord - s.gridOrigin) / TerrainPlanSurface.CELL - 0.5; }

    /** EcologyBiomes#softZone over the federal zone values. */
    static int softZone(TerrainPlanSurface s, byte[] zone, double wx, double wz, double x, double z) {
        double gx = (wx - s.gridOrigin) / TerrainPlanSurface.CELL - 0.5, gz = (wz - s.gridOrigin) / TerrainPlanSurface.CELL - 0.5;
        int c0 = (int) Math.floor(gx), r0 = (int) Math.floor(gz);
        double fx = gx - c0, fz = gz - r0;
        double[] w = new double[FederalEcology.NAMES.length];
        for (int b = 0; b < 2; b++) for (int a = 0; a < 2; a++) {
            int k = zone[s.cellIdx(s.clampCell(c0 + a), s.clampCell(r0 + b))];
            if (k < 0) k = EcologyPlan.AGRI_PLAIN;
            w[k] += (a == 0 ? 1 - fx : fx) * (b == 0 ? 1 - fz : fz);
        }
        int best = EcologyPlan.AGRI_PLAIN;
        double bw = -1;
        for (int k = 0; k < w.length; k++) {
            if (w[k] <= 0) continue;
            double v = w[k] + 0.22 * PlanNoise.noise(s.noiseSeed, x / 28, z / 28, 620 + k);
            if (v > bw) { bw = v; best = k; }
        }
        return best;
    }
}
