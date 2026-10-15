package com.antaurora.apofirstlight.worldgen.terrain.v2;

/**
 * Ecology zones of the federal experimental plan (C2, 2026-10-14; docs/worldgen/federal_six_state_c2.md). The r1 rules
 * (EcologyPlan: till plain, slope and ridge forest, hemlock hollows, coastal pine-oak, riparian, swamp, marsh, wet
 * prairie) run on the federal province weights first; the federal provinces then add their own zones, all from the
 * landform, the climate and the water, never from the state borders:
 * <ul>
 *   <li>elevation belts (any province, the range above all): montane conifer forest above a montane line (about Y140,
 *   lower on cold and north-facing ground), alpine meadow above the treeline (about Y198), alpine rock on steep or high
 *   ground above it;</li>
 *   <li>lake lowland: northern mixed forest on its slopes, around the lakes and in patches (the rest stays open farm
 *   country), fen on the low flat ground beside the lakes, the lakes themselves;</li>
 *   <li>tableland: dry steppe on the tableland and the dry ground round it, bare canyon walls and badland slopes where it is
 *   steep (river bottoms in the canyons keep their riparian strip).</li>
 * </ul>
 * Zone values 0..8 are EcologyPlan's; 9..16 are federal only.
 */
public final class FederalEcology {
    private FederalEcology() {}

    public static final byte MONTANE = 9, ALPINE_MEADOW = 10, ALPINE_ROCK = 11, LAKESHORE = 12, FEN = 13, LAKE = 14, STEPPE = 15,
            CANYON = 16;
    public static final String[] NAMES = {"agri_plain", "riparian", "slope_forest", "ridge_oak", "hollow_conifer",
            "coastal_pine", "swamp_forest", "tidal_marsh", "wet_prairie", "montane_conifer", "alpine_meadow", "alpine_rock",
            "lakeshore_forest", "fen", "lake", "steppe", "canyon"};

    public static String name(int zone) { return zone < 0 ? "water" : zone < NAMES.length ? NAMES[zone] : "zone_" + zone; }

    /** Height above the nearest channel of at least minArea, or above the lake surface for ground draining to a lake. */
    static float[] hand(FederalTerrainPlan p, double minArea) {
        int n = p.n;
        long[] order = new long[n];
        for (int i = 0; i < n; i++) order[i] = ((long) sortable(p.h[i]) << 32) | i;
        java.util.Arrays.parallelSort(order);           // ascending height: every receiver before its donors
        float[] ref = new float[n];
        java.util.Arrays.fill(ref, Float.NaN);
        for (int i = 0; i < n; i++) {
            if (p.water[i] == TerrainPlanSurface.LAKE) ref[i] = p.lakeTop[i] + 1;
            else if (!(p.water[i] == 0 || p.water[i] == 3)) ref[i] = p.h[i];
        }
        for (long o : order) {
            int i = (int) o;
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

    private static int sortable(float f) {
        int b = Float.floatToIntBits(f);
        return b ^ ((b >> 31) & 0x7fffffff);            // signed int order = float order (negatives reversed back)
    }

    public static EcologyPlan.Zones classify(FederalTerrainPlan p) {
        final int N = p.gridN;
        float[] hand = hand(p, 2e6);
        // distance (cells, up to 12) to the nearest lake cell: lakeshore forest and fen gather round the lakes
        byte[] lakeDist = lakeDistance(p, 12);
        byte[] z = new byte[p.n], hb = new byte[p.n];
        for (int i = 0; i < p.n; i++) hb[i] = (byte) Math.max(0, Math.min(120, Math.round(hand[i])));
        double cell = TerrainPlanV2.CELL;
        java.util.stream.IntStream.range(0, N).parallel().forEach(r -> { for (int c = 0; c < N; c++) {
            int i = r * N + c;
            if (p.water[i] == TerrainPlanSurface.LAKE) { z[i] = LAKE; continue; }
            if (p.water[i] == 3) { z[i] = EcologyPlan.TIDAL_MARSH; continue; }
            if (p.water[i] != 0) { z[i] = EcologyPlan.NONE; continue; }
            double gx = c == 0 ? (p.h[i + 1] - p.h[i]) / cell : c == N - 1 ? (p.h[i] - p.h[i - 1]) / cell : (p.h[i + 1] - p.h[i - 1]) / (2 * cell);
            double gy = r == 0 ? (p.h[i + N] - p.h[i]) / cell : r == N - 1 ? (p.h[i] - p.h[i - N]) / cell : (p.h[i + N] - p.h[i - N]) / (2 * cell);
            double sl = Math.hypot(gx, gy);
            double north = -gy / Math.max(sl, 1e-6);
            double x = p.cellX(c), zz = p.cellX(r);
            double plain = p.wPlain[i], coastal = p.wCoastal[i], foot = p.wFoot[i], belt = p.wBelt[i], res = p.resist[i];
            // ---- the r1 rules (EcologyPlan), unchanged
            byte k = EcologyPlan.AGRI_PLAIN;
            double thr = 0.08 * (1 + 0.25 * Math.max(-1.6, Math.min(1.6, p.fbm(x, zz, 300, 2, 0.5, 640))));
            if (sl > thr && (sl > 0.12 || p.fbm(x, zz, 250, 2, 0.5, 642) > -0.25)) k = EcologyPlan.SLOPE_FOREST;
            double coastThr = 0.5 + 0.2 * Math.max(-1, Math.min(1, p.fbm(x, zz, 500, 2, 0.5, 641) / 1.6));
            if (coastal > coastThr && sl <= thr) k = EcologyPlan.COASTAL_PINE;
            if (res > 0.2 || (belt > 0.5 && sl > 0.15)) k = EcologyPlan.RIDGE_OAK;
            if (belt + foot > 0.5 && sl > 0.12 && north > 0.5 && hand[i] < 25) k = EcologyPlan.HOLLOW_CONIFER;
            if (hand[i] < 3) k = coastal > coastThr ? EcologyPlan.SWAMP_FOREST : EcologyPlan.RIPARIAN;
            if (plain > 0.6 && hand[i] < 1.0 && sl < 0.006 && p.area[i] < 2e5) k = EcologyPlan.WET_PRAIRIE;
            // ---- lake lowland
            double lk = p.wLake[i];
            int ld = lakeDist[i];
            if (lk > 0.35 + 0.12 * p.fbm(x, zz, 900, 2, 0.5, 650) / 1.6) {
                boolean wooded = sl > 0.035 || ld <= 8 || p.fbm(x, zz, 420, 2, 0.5, 651) > 0.35;
                if ((k == EcologyPlan.AGRI_PLAIN && wooded) || k == EcologyPlan.SLOPE_FOREST || k == EcologyPlan.HOLLOW_CONIFER
                        || k == EcologyPlan.RIDGE_OAK) k = LAKESHORE;
            }
            if (ld <= 4 && hand[i] < 1.6 && sl < 0.02) k = FEN;
            // ---- tableland: steppe and canyon walls
            double pl = p.wPlateau[i], table = p.tableland[i];
            if (pl > 0.3 || table > 0.3) {
                boolean bottom = hand[i] < 2 && p.area[i] >= 1e6;          // a river in the canyon keeps its strip
                if (sl > 0.42) k = CANYON;
                else if (!bottom && (table > 0.35 || p.moist[i] < 0.42)) k = STEPPE;
            }
            // ---- elevation belts
            double h = p.h[i], t = p.temp[i];
            double tl = 210 + 12 * p.fbm(x, zz, 400, 2, 0.5, 661) / 1.6 - 14 * Math.max(0, north) + 40 * (t - 0.3);
            double ml = 138 + 12 * p.fbm(x, zz, 600, 2, 0.5, 662) / 1.6 + 45 * (t - 0.35) - 10 * Math.max(0, north);
            // alpine ground only in the range (a high hill-belt crest keeps its montane forest, as on real eastern ridges)
            if (h > tl && p.wMount[i] > 0.25) k = sl > 0.6 || h > tl + 28 ? ALPINE_ROCK : ALPINE_MEADOW;
            else if (h > ml && (p.wMount[i] > 0.2 || h > ml + 25) && table < 0.3 && k != CANYON) k = MONTANE;
            z[i] = k;
        } });
        return new EcologyPlan.Zones(z, hb);
    }

    /** Chessboard distance in cells to the nearest lake cell, capped at max (two-pass chamfer). */
    static byte[] lakeDistance(FederalTerrainPlan p, int max) {
        int N = p.gridN;
        int[] d = new int[p.n];
        for (int i = 0; i < p.n; i++) d[i] = p.water[i] == TerrainPlanSurface.LAKE ? 0 : max;
        for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) {
            int i = r * N + c, v = d[i];
            if (c > 0) v = Math.min(v, d[i - 1] + 1);
            if (r > 0) {
                v = Math.min(v, d[i - N] + 1);
                if (c > 0) v = Math.min(v, d[i - N - 1] + 1);
                if (c < N - 1) v = Math.min(v, d[i - N + 1] + 1);
            }
            d[i] = v;
        }
        for (int r = N - 1; r >= 0; r--) for (int c = N - 1; c >= 0; c--) {
            int i = r * N + c, v = d[i];
            if (c < N - 1) v = Math.min(v, d[i + 1] + 1);
            if (r < N - 1) {
                v = Math.min(v, d[i + N] + 1);
                if (c < N - 1) v = Math.min(v, d[i + N + 1] + 1);
                if (c > 0) v = Math.min(v, d[i + N - 1] + 1);
            }
            d[i] = v;
        }
        byte[] out = new byte[p.n];
        for (int i = 0; i < p.n; i++) out[i] = (byte) Math.min(max, d[i]);
        return out;
    }
}
