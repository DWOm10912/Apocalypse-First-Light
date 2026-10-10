package com.antaurora.apofirstlight.worldgen.terrain.v2;

/**
 * Terrain V2 ecology V1 (2026-10-10; docs/worldgen/terrain_v2_ecology_v1.md): the surface biome of a column from the
 * r1 ecology zones (EcologyPlan). Pure Java and deterministic; the preview tools and the game (MainNationBiomeRegionPlan,
 * user approval 2026-10-10 with six decisions) use the same rule.
 * <ul>
 *   <li>Ecotone: zones are read through a smooth domain warp (about 30 m: 380, 110 and 37 m gradient noise, on top of
 *   the shore warp; the 380 m octave lets forest edges wander about a straight landform line such as the fold belt's
 *   front foot instead of tracing it, user decision 6) and then softly: each zone gets the bilinear weight of the four surrounding cells that hold it plus its
 *   own small noise (28 m), and the heaviest wins, so borders are irregular curves, not the 16 m cells (= chunk
 *   borders) or their warped squares.</li>
 *   <li>Patches: the agricultural till plain is open temperate grassland with broadleaf woodlots (about a fifth,
 *   gathered on valley sides and along streams); the coastal pine-oak forest has small open clearings (about 1 in 8).</li>
 *   <li>Shore: dry land within about 10 m of the open sea, low (planned surface under Y65.5), is a sand beach; on
 *   sheltered estuary shores only scattered sandy reaches (about one in eight, a 70 m noise; user decision 5), the
 *   rest keeps its marsh / swamp / riparian vegetation.</li>
 *   <li>Wide valley floors (user decision 4): the r1 riparian zone (HAND under 3 m) is riparian forest only in a
 *   corridor along the river water (20 m + 2.2 x the river width from the water edge, 0.5..1.5 x by a 90 m noise);
 *   beyond it the floodplain is wet meadow where it is lowest, flattest and wettest (HAND under 1.2 m, slope under
 *   0.012, a 120 m wetness noise), otherwise open meadow / grassland with a few woodland islands, which keeps the
 *   open valley land later farms, villages and roads need.</li>
 *   <li>Open water (sea, drowned valleys) is sea; rivers take the biome of the land they cross.</li>
 * </ul>
 */
public final class EcologyBiomes {
    private EcologyBiomes() {
    }

    public static final int SEA = 0, BEACH = 1, GRASSLAND = 2, WOODLAND = 3, SLOPE_FOREST = 4, RIDGE_FOREST = 5,
            HEMLOCK_HOLLOW = 6, RIPARIAN_FOREST = 7, COASTAL_PINE = 8, COASTAL_CLEARING = 9, SWAMP_FOREST = 10,
            TIDAL_MARSH = 11, WET_PRAIRIE = 12;
    public static final String[] IDS = {"sea", "sandy_beach", "temperate_grassland", "oak_hickory_woodland",
            "mixed_mesophytic_forest", "ridge_oak_forest", "hemlock_hollow", "riparian_forest", "coastal_pine_oak_forest",
            "coastal_sandy_clearing", "coastal_swamp_forest", "tidal_marsh", "wet_prairie"};
    public static final int[] COLOURS = {0x1F3F66, 0xE6D9A4, 0xC9C58A, 0x8FAE62, 0x6E9A57, 0x9A8C5E, 0x46705A, 0x5E9E6E,
            0x9DB577, 0xD2C892, 0x5F8874, 0x95BAA8, 0xB4C896};

    static final double ECO_A0 = 40, ECO_L0 = 380, ECO_A1 = 16, ECO_L1 = 110, ECO_A2 = 6, ECO_L2 = 37;

    /** The biome index at a column from the surface's own ecology zones (GRASSLAND when the surface has none). */
    public static int biomeAt(TerrainPlanSurface s, double x, double z) {
        if (s.ecology == null) {
            double h = s.heightAt(x, z);
            if (h < 63 && s.seaFloodAt(x, z, h)) return SEA;
            return beachAt(s, x, z) ? BEACH : GRASSLAND;
        }
        return biomeAt(s, s.ecology, x, z);
    }

    /** The biome index at a column; zones from EcologyPlan.classify on the same plan. */
    public static int biomeAt(TerrainPlanSurface s, EcologyPlan.Zones zones, double x, double z) {
        int wc = s.waterClass(x, z);
        double h = s.heightAt(x, z);
        if (h < 63 && s.seaFloodAt(x, z, h)) return SEA;            // the sea fill's own rule
        // the beach: dry, low, gentle land right at the open sea (not in estuaries)
        if (beachAt(s, x, z, wc, h)) return BEACH;
        double ex = ECO_A0 * PlanNoise.noise(s.noiseSeed, x / ECO_L0, z / ECO_L0, 605) + ECO_A1 * PlanNoise.noise(s.noiseSeed, x / ECO_L1, z / ECO_L1, 601)
                + ECO_A2 * PlanNoise.noise(s.noiseSeed, x / ECO_L2, z / ECO_L2, 603);
        double ez = ECO_A0 * PlanNoise.noise(s.noiseSeed, x / ECO_L0, z / ECO_L0, 606) + ECO_A1 * PlanNoise.noise(s.noiseSeed, x / ECO_L1, z / ECO_L1, 602)
                + ECO_A2 * PlanNoise.noise(s.noiseSeed, x / ECO_L2, z / ECO_L2, 604);
        int zone = softZone(s, zones.zone(), x + ex, z + ez, x, z);
        switch (zone) {
            case EcologyPlan.AGRI_PLAIN: {
                // woodlots gather on valley sides and along streams (low HAND, some slope); the open uplands keep a
                // few groves; the bias is read bilinearly so it never steps on a cell border
                double hand = bilinearHand(s, zones.hand(), x + ex, z + ez);
                double bias = 0.9 * Math.max(0, Math.min(1, (14 - hand) / 14)) + 3.0 * Math.min(0.12, s.slopeAt(x, z));
                double wood = PlanNoise.fbm(s.noiseSeed, x, z, 240, 3, 0.5, 611) + bias;
                return wood > 0.95 ? WOODLAND : GRASSLAND;
            }
            case EcologyPlan.RIPARIAN: return floodplain(s, zones, x, z, ex, ez);
            case EcologyPlan.SLOPE_FOREST: return SLOPE_FOREST;
            case EcologyPlan.RIDGE_OAK: return RIDGE_FOREST;
            case EcologyPlan.HOLLOW_CONIFER: return HEMLOCK_HOLLOW;
            case EcologyPlan.COASTAL_PINE:
                // clearings: small openings (110 m noise, about 1 in 8); 2026-10-10 acceptance fix, was 180 m / 1 in 6
                return PlanNoise.fbm(s.noiseSeed, x, z, 110, 2, 0.5, 612) > 1.1 ? COASTAL_CLEARING : COASTAL_PINE;
            case EcologyPlan.SWAMP_FOREST: return SWAMP_FOREST;
            case EcologyPlan.TIDAL_MARSH: return TIDAL_MARSH;
            case EcologyPlan.WET_PRAIRIE: return WET_PRAIRIE;
            default: return GRASSLAND;
        }
    }

    static int floodplain(TerrainPlanSurface s, EcologyPlan.Zones zones, double x, double z, double ex, double ez) {
        if (s.rivers != null) {
            double[] w = new double[1];
            double edge = s.rivers.nearestEdge(x, z, 72, w);
            double corridor = (20 + 2.2 * w[0]) * (1 + 0.5 * Math.max(-1, Math.min(1, 1.4 * PlanNoise.noise(s.noiseSeed, x / 90, z / 90, 630))));
            if (edge <= corridor) return RIPARIAN_FOREST;
        }
        double hand = bilinearHand(s, zones.hand(), x + ex, z + ez);
        double wet = PlanNoise.fbm(s.noiseSeed, x, z, 120, 2, 0.5, 631);
        if (hand < 1.2 && s.slopeAt(x, z) < 0.012 && wet > 0.35) return WET_PRAIRIE;
        return PlanNoise.fbm(s.noiseSeed, x, z, 200, 3, 0.5, 632) > 1.15 ? WOODLAND : GRASSLAND;
    }

    /** The zone with the largest bilinear weight among the four cells round (wx, wz), each zone nudged by its own noise. */
    static int softZone(TerrainPlanSurface s, byte[] zone, double wx, double wz, double x, double z) {
        double gx = (wx - TerrainPlanSurface.ORIGIN) / TerrainPlanSurface.CELL - 0.5, gz = (wz - TerrainPlanSurface.ORIGIN) / TerrainPlanSurface.CELL - 0.5;
        int c0 = (int) Math.floor(gx), r0 = (int) Math.floor(gz);
        double fx = gx - c0, fz = gz - r0;
        double[] w = new double[EcologyPlan.NAMES.length];
        for (int b = 0; b < 2; b++) for (int a = 0; a < 2; a++) {
            int k = zone[TerrainPlanSurface.idx(TerrainPlanSurface.clampI(c0 + a), TerrainPlanSurface.clampI(r0 + b))];
            if (k < 0) k = EcologyPlan.AGRI_PLAIN;          // a water cell next to land: its weight goes to the shore land
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

    static double bilinearHand(TerrainPlanSurface s, byte[] hand, double x, double z) {
        double gx = (x - TerrainPlanSurface.ORIGIN) / TerrainPlanSurface.CELL - 0.5, gz = (z - TerrainPlanSurface.ORIGIN) / TerrainPlanSurface.CELL - 0.5;
        int c0 = (int) Math.floor(gx), r0 = (int) Math.floor(gz);
        double fx = gx - c0, fz = gz - r0, v = 0;
        for (int b = 0; b < 2; b++) for (int a = 0; a < 2; a++)
            v += hand[TerrainPlanSurface.idx(TerrainPlanSurface.clampI(c0 + a), TerrainPlanSurface.clampI(r0 + b))]
                    * (a == 0 ? 1 - fx : fx) * (b == 0 ? 1 - fz : fz);
        return v;
    }

    /** Dry, low (planned surface under Y65.5) land within about 10 m of the open sea (not estuary or marsh shores). */
    public static boolean beachAt(TerrainPlanSurface s, double x, double z) {
        return beachAt(s, x, z, s.waterClass(x, z), s.heightAt(x, z));
    }

    static boolean beachAt(TerrainPlanSurface s, double x, double z, int wc, double h) {
        if (wc == 3 || h < 63 || h >= 65.5 || s.shoreDistance(x, z) > 32) return false;
        int near = nearWater(s, x, z);
        if (near == 1) return true;                                  // the open sea
        // a sheltered estuary shore: only scattered sandy reaches, slightly lower
        return near == 2 && h < 64.5 && PlanNoise.fbm(s.noiseSeed, x, z, 70, 2, 0.5, 633) > 1.15;
    }

    /** 1 open sea, 2 estuary, 0 neither: open water (ground under Y63) within about 10 m (8 probes). */
    static int nearWater(TerrainPlanSurface s, double x, double z) {
        int found = 0;
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4, qx = x + 10 * Math.cos(a), qz = z + 10 * Math.sin(a);
            int wc = s.waterClass(qx, qz);
            if ((wc == 1 || wc == 2) && s.heightAt(qx, qz) < 63) {
                if (wc == 1) return 1;
                found = 2;
            }
        }
        return found;
    }

}
