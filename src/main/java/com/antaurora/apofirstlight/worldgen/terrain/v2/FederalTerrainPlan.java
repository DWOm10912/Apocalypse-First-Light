package com.antaurora.apofirstlight.worldgen.terrain.v2;

import com.antaurora.apofirstlight.worldgen.federal.FederalMap;
import com.antaurora.apofirstlight.worldgen.federal.FederalTemplate;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeographySample;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.SplittableRandom;
import java.util.stream.IntStream;

/**
 * The experimental six-state Terrain V2 plan (C2, 2026-10-14; docs/worldgen/federal_six_state_c2.md). The r1 planner's
 * drainage, carving, river and surface machinery (TerrainPlanV2, unchanged for r1) on the C1 federal map
 * (FederalMap: coast, satellite islands, six physiographic provinces, climate), on a 1760 x 1760 grid of 16 m cells
 * centred on 0 (28.2 km, the 20 x 16 km country with its sea). The provinces are blended by FederalMap#physiography
 * (soft weights, warped capsules); the state borders are never read, so landforms run through them.
 * <ul>
 *   <li>HILLS (S1, the forest-survival heartland): the r1 landscape on a smaller frame, a plunging fold belt along the
 *   province axis with foothills (piedmont) around it;</li>
 *   <li>PLAINS (S2): the r1 till plain (broad swells, hummocky ground moraine) with end-moraine arcs and gentle hills;</li>
 *   <li>LAKE_LOWLAND (S4): a low ground moraine with drumlin swells, cut by glacial troughs (finger lakes) and kettles;
 *   closed basins hold real lakes (water class 4, a flat water surface at the spill level);</li>
 *   <li>COASTAL_LOWLAND (S5): a low coastal plain, wide floodplains, marsh where it reaches the sea;</li>
 *   <li>MOUNTAIN (S3): a massif with ridged crests (peaks about Y230-270), steep channels and valley walls, crags;</li>
 *   <li>PLATEAU (S6): a tableland at about Y145-160 behind an escarpment, mesas and buttes off its edge, canyons where
 *   the drainage cuts back into it, terraced (bench and cliff) walls.</li>
 * </ul>
 * Every coast gets a narrow coastal plain behind it, except where the mountains and the tableland meet the sea.
 */
public class FederalTerrainPlan extends TerrainPlanV2 {
    public static final String VERSION = "afl_federal_plan_c2_v1";
    public static final int GRID_N = 1760;
    public static final double GRID_ORIGIN = -GRID_N * CELL / 2;   // -14080

    static final int MOUNTAIN = FederalTemplate.Physiography.MOUNTAIN.ordinal(), PLATEAU = FederalTemplate.Physiography.PLATEAU.ordinal(),
            PLAINS = FederalTemplate.Physiography.PLAINS.ordinal(), LAKE = FederalTemplate.Physiography.LAKE_LOWLAND.ordinal(),
            HILLS = FederalTemplate.Physiography.HILLS.ordinal(), COASTAL = FederalTemplate.Physiography.COASTAL_LOWLAND.ordinal();

    public final FederalMap map;
    /** Federal province weights beside the r1 ones (wPlain = plains, wCoastal = coastal lowland + shore, wFoot / wBelt = hills). */
    public final float[] wMount, wPlateau, wLake;
    /** Climate of each land cell, 0..1 (FederalMap#temperature / #moisture). */
    public final float[] temp, moist;
    /** Height weight of the tableland (the province weight sharpened into an escarpment), 0..1. */
    public final float[] tableland;
    /** Lake water top block per cell (water class 4 only). */
    public final short[] lakeTop;
    final byte[] terrace, rough, open;

    // the fold belt of the hills: along the HILLS anchor's axis
    final double hax, haz, hdx, hdz, hlen, beltLb;
    static final double FED_BELT_HALF = 950, FED_BELT_EDGE = 450;

    public FederalTerrainPlan(long seed) {
        super(seed, GRID_N, GRID_ORIGIN, null);
        map = FederalMap.forSeed(seed);
        wMount = new float[n];
        wPlateau = new float[n];
        wLake = new float[n];
        temp = new float[n];
        moist = new float[n];
        tableland = new float[n];
        lakeTop = new short[n];
        terrace = new byte[n];
        rough = new byte[n];
        open = new byte[n];
        FederalTemplate.Anchor hills = anchor(FederalTemplate.Physiography.HILLS);
        double ax = hills.ax() * map.scale, az = hills.az() * map.scale, bx = hills.bx() * map.scale, bz = hills.bz() * map.scale;
        hlen = Math.hypot(bx - ax, bz - az);
        hdx = (bx - ax) / hlen;
        hdz = (bz - az) / hlen;
        hax = (ax + bx) / 2;
        haz = (az + bz) / 2;
        beltLb = hlen / 2 + 250;
        build();
        report.put("lakes", lakeCount);
        report.put("lake_cells", lakeCells);
    }

    static FederalTemplate.Anchor anchor(FederalTemplate.Physiography kind) {
        for (FederalTemplate.Anchor a : FederalTemplate.ANCHORS) if (a.kind() == kind) return a;
        throw new IllegalStateException("no anchor " + kind);
    }

    private static void par(int from, int to, java.util.function.IntConsumer body) {
        IntStream.range(from, to).parallel().forEach(body);
    }

    @Override protected String version() { return VERSION; }

    // ------------------------------------------------------------------ mask
    @Override
    protected void sampleMask() {
        par(0, gridN, r -> {
            for (int c = 0; c < gridN; c++) {
                MacroGeographySample s = map.sample((int) Math.floor(cellX(c)), (int) Math.floor(cellX(r)));
                int i = cellIdx(c, r);
                landmass[i] = (short) s.landmassId();
                water[i] = (byte) (s.isLand() ? 0 : 1);
                seaFloor[i] = (float) s.surfaceHeight();
            }
        });
    }

    // ------------------------------------------------------------------ provinces
    /** Along (p) and across (q) the hills axis, m. */
    double hp(double x, double z) { return (x - hax) * hdx + (z - haz) * hdz; }

    double hq(double x, double z) { return -(x - hax) * hdz + (z - haz) * hdx; }

    double beltCentreQ(double p) {
        return beltBend * (1 - Math.min(1, (p / beltLb) * (p / beltLb))) + 140 * Math.sin(p / 1700 + beltPhase);
    }

    double fedBeltHalf(double p, double x, double z) {
        double t = Math.min(1, Math.abs(p) / beltLb);
        return FED_BELT_HALF * Math.sqrt(Math.max(0, 1 - t * t * t)) * (1 + 0.15 * fbm(x, z, 2200, 2, 0.5, 5));
    }

    double fedBeltEnvelope(double x, double z) {
        double p = hp(x, z), q = hq(x, z) - beltCentreQ(p);
        return (1 - smooth((Math.abs(q) - fedBeltHalf(p, x, z)) / FED_BELT_EDGE)) * (1 - smooth((Math.abs(p) - beltLb) / 450));
    }

    @Override
    protected void provinces() {
        SplittableRandom rnd = new SplittableRandom(mix(seed ^ 0x51DE5EEDL));
        beltSide = rnd.nextBoolean() ? 1 : -1;
        beltPhase = rnd.nextDouble() * Math.PI * 2;
        beltBend = (0.5 + 0.4 * rnd.nextDouble()) * 380 * beltSide;
        beltPlunge = rnd.nextDouble() * Math.PI * 2;
        beltShift = 0;
        dividePhase = rnd.nextDouble() * Math.PI * 2;
        par(0, gridN, r -> {
            double[] w = new double[6];
            for (int c = 0; c < gridN; c++) {
                int i = cellIdx(c, r);
                if (water[i] != 0) continue;
                double x = cellX(c), z = cellX(r), dc = dCoast[i];
                temp[i] = (float) map.temperature(x, z);
                moist[i] = (float) map.moisture(x, z);
                if (landmass[i] != 0) {                  // satellite islands: coastal plain and low till plain
                    double cw = 1 - smooth((dc - 250) / 500);
                    wCoastal[i] = (float) cw;
                    wPlain[i] = (float) (1 - cw);
                    continue;
                }
                map.physiography(x, z, w);
                double m = w[MOUNTAIN], pl = w[PLATEAU], pn = w[PLAINS], lk = w[LAKE], hi = w[HILLS], co = w[COASTAL];
                // a narrow coastal plain behind every shore, none where the range or the tableland meets the sea
                double cw = 520 * (1 + 0.38 * fbm(x, z, 3200, 2, 0.5, 6));
                // (wider where the range comes down to it, so its front is a slope, not a scarp at a fixed shore distance)
                double shore = (1 - smooth((dc - cw * 0.55) / (cw * 0.6 * (1 + 3 * m)))) * Math.max(0, 1 - 0.95 * m - 0.7 * pl);
                double belt = hi * fedBeltEnvelope(x, z), foot = hi - belt;
                double k = 1 - shore;
                wMount[i] = (float) (m * k);
                wPlateau[i] = (float) (pl * k);
                wPlain[i] = (float) (pn * k);
                wLake[i] = (float) (lk * k);
                wBelt[i] = (float) (belt * k);
                wFoot[i] = (float) (foot * k);
                wCoastal[i] = (float) (co * k + shore);
            }
        });
    }

    // ------------------------------------------------------------------ initial surface
    @Override
    protected void initialSurface() {
        SplittableRandom rnd = new SplittableRandom(mix(seed ^ 0x40AA1E5L));
        // end moraines across the plains: arcs convex to the south (the ice came from the north), 8-14 m, 700-1100 m wide
        FederalTemplate.Anchor plainsA = anchor(FederalTemplate.Physiography.PLAINS);
        int moraines = 2 + rnd.nextInt(2);
        double[][] arcs = new double[moraines][];
        for (int k = 0; k < moraines; k++) {
            double t = (k + 0.5 + 0.4 * (rnd.nextDouble() - 0.5)) / moraines;
            double mx = map.scale * (plainsA.ax() + t * (plainsA.bx() - plainsA.ax())), mz = map.scale * (plainsA.az() + t * (plainsA.bz() - plainsA.az()));
            double rad = 2600 + 1800 * rnd.nextDouble();
            arcs[k] = new double[]{mx + (rnd.nextDouble() - 0.5) * 900, mz - rad, rad, 8 + 6 * rnd.nextDouble(), 350 + 220 * rnd.nextDouble()};
        }
        double tilt = (rnd.nextBoolean() ? 1 : -1) * (1.5 + 1.5 * rnd.nextDouble());
        par(0, gridN, r -> { for (int c = 0; c < gridN; c++) {
            int i = cellIdx(c, r);
            if (water[i] != 0) { h0[i] = h0NoRidge[i] = seaFloor[i]; continue; }
            double x = cellX(c), z = cellX(r), dc = dCoast[i];
            if (landmass[i] != 0) {
                h0[i] = h0NoRidge[i] = (float) (SEA + 2.5 + 14 * smooth(dc / 900) + 2 * fbm(x, z, 1200, 2, 0.5, 9));
                continue;
            }
            // the regional surface: a low terrace behind every shore, rising inland (lower on the coastal lowland)
            double inland = 9 * smooth(dc / 5500) + tilt * x / 14080 + 3 * fbm(x, z, 7000, 2, 0.5, 10);
            double regional = SEA + 2.5 + 4 * smooth(dc / 700) + (1 - 0.7 * wCoastal[i]) * inland;
            // till plain (r1 spectra) with the moraine arcs and the gentle hills of the plains
            double plain = 1.5 * fbm(x, z, 3600, 2, 0.55, 11) + 1.3 * fbm(x, z, 800, 3, 0.5, 12) + 0.6 * fbm(x, z, 300, 2, 0.5, 13)
                    + 3.2 * Math.max(0, fbm(x, z, 1900, 2, 0.5, 16) + 0.3);
            for (double[] a : arcs) {
                double rr = Math.hypot(x - a[0], z - a[1]) - a[2] - 220 * fbm(x, z, 2600, 2, 0.5, 14);
                double crest = a[3] * (0.75 + 0.35 * fbm(x, z, 1800, 2, 0.5, 15));
                plain += crest * Math.exp(-(rr / a[4]) * (rr / a[4])) * smooth(dc / 1500) * (z > a[1] ? 1 : 0);
            }
            double coastal = 1.4 * fbm(x, z, 1500, 3, 0.5, 21) + 0.5 * fbm(x, z, 300, 2, 0.5, 22);
            // lake lowland: ground moraine with drumlin swells along the ice flow (north - south), 3-6 m
            double drum = noise(x / 160, z / 520, 26);
            double lake = 4 + 1.2 * fbm(x, z, 2500, 2, 0.5, 24) + 0.5 * fbm(x, z, 300, 2, 0.5, 25) + 4.5 * Math.max(0, drum * drum * Math.signum(drum))
                    + 20 * smooth((dc - 250) / 1500);   // a till upland: lake levels 15-25 m above the sea, a short fall to it
            double foot = 22 + 6 * fbm(x, z, 1300, 3, 0.55, 31) + 4.5 * fbm(x, z, 560, 2, 0.5, 33) + 0.8 * fbm(x, z, 300, 2, 0.5, 32);
            double p = hp(x, z), q = hq(x, z) - beltCentreQ(p);
            double rg = fedFoldRidges(p, q, x, z) * fedBeltEnvelope(x, z);
            double crestVar = 1 + 0.18 * fbm(x, z, 1400, 3, 0.55, 42) - 0.30 * Math.max(0, fbm(x, z, 950, 2, 0.5, 44) - 0.9);
            double beltBase = 24 + 5 * fbm(x, z, 2000, 2, 0.5, 41) + 1.0 * fbm(x, z, 300, 2, 0.5, 45);
            double low = wPlain[i] + wCoastal[i] + wLake[i] + wFoot[i] + wBelt[i];
            double lowland = low < 1e-6 ? 0 : (wPlain[i] * plain + wCoastal[i] * coastal + wLake[i] * lake + wFoot[i] * foot
                    + wBelt[i] * beltBase) / low;
            // the highlands: the range (smooth front) and the tableland (sharpened into an escarpment)
            double tm = smooth(wMount[i] * 1.25 - 0.05);
            double tp = tablelandWeight(i, x, z);
            tableland[i] = (float) tp;
            double hs = tm + tp;
            if (hs > 1) { tm /= hs; tp /= hs; hs = 1; }
            double base = regional + (1 - hs) * lowland + tm * mountainRelief(x, z, wMount[i]) + tp * plateauRelief(x, z);
            h0NoRidge[i] = (float) base;
            // the belt's ridges stand only on the hills' share (the belt frame is geometric, the provinces are not)
            double ridgeShare = (1 - hs) * (low < 1e-6 ? 0 : Math.min(1, (wBelt[i] + wFoot[i]) / low));
            resist[i] = (float) (rg * ridgeShare);
            double roughR = rg * (4.5 * fbm(x, z, 230, 2, 0.5, 47) + 0.6 * fbm(x, z, 90, 2, 0.5, 48));
            h0[i] = (float) (base + ridgeShare * (105 * rg * crestVar + roughR));
        } });
    }

    /** The tableland's height weight: the province weight pushed through a narrow ramp with a 700 m noise, so the
     *  escarpment is a few hundred metres wide and irregular, with mesas and buttes standing off it. */
    double tablelandWeight(int i, double x, double z) {
        double pl = wPlateau[i];
        if (pl < 0.05) return 0;
        double e = (pl - 0.5 + 0.20 * fbm(x, z, 700, 2, 0.5, 51) + 0.06 * fbm(x, z, 180, 2, 0.5, 52)) / 0.07;
        return smooth(0.5 + e * 0.5);
    }

    /** Relief of the range above the regional surface: ridged crests and spurs (the skeleton) on a low base rising to
     *  the province core; the rivers then cut the valleys between them (crests about Y230-265, valley heads Y120-150). */
    double mountainRelief(double x, double z, double m) {
        double core = smooth(m * 1.4 - 0.25);
        double ridged = 0, amp = 1, wave = 3000, norm = 0;
        for (int o = 0; o < 4; o++) {
            double v = 1 - Math.abs(noise(x / wave, z / wave, 81 + o) * 1.35);
            v = Math.max(0, v);
            ridged += amp * v * v;
            norm += amp;
            amp *= 0.5;
            wave *= 0.45;
        }
        ridged /= norm;                                   // 0..1, sharp crests
        return 22 + 30 * core + (20 + 130 * core) * ridged + 6 * fbm(x, z, 5000, 2, 0.5, 86);
    }

    /** Relief of the tableland above the regional surface (top at about Y145-160). */
    double plateauRelief(double x, double z) {
        return 76 + 5 * fbm(x, z, 3000, 2, 0.5, 53) + 1.5 * fbm(x, z, 600, 2, 0.5, 54);
    }

    /** r1's plunging fold train (TerrainPlanV2#foldRidges) on the hills frame (p along, q across the belt). */
    double fedFoldRidges(double p, double q, double x, double z) {
        double drift = 0.85 * Math.sin(p / 2600 + beltPhase) + 0.40 * Math.sin(p / 1150 + 2.3 * beltPhase)
                + 0.18 * fbm(x, z, 1800, 2, 0.5, 46);
        double phase = 2 * Math.PI * (q + 400) / 2600 + drift;
        double end = smooth((Math.abs(p) / beltLb - 0.55) / 0.45);
        double fold = Math.floor((q + 400) / 2600 + drift / (2 * Math.PI) + 0.5);
        double culm = 0.50 * Math.sin(p / 1800 + beltPlunge + 1.7 * fold) + 0.28 * Math.sin(p / 760 + 2.9 * fold + beltPhase)
                + end * 1.25 * (((long) fold & 1) == 0 ? 1 : -1);
        double L = Math.cos(phase) + culm;
        double best = 0;
        double[] levels = {0.05, 0.85}, widths = {0.36, 0.20}, tops = {1.0, 0.55};
        for (int k = 0; k < levels.length; k++) {
            double d = (L - levels[k]) / widths[k], ad = Math.abs(d);
            double core = d > 0 ? Math.exp(-Math.pow(ad, 1.9)) : Math.exp(-Math.pow(ad * 1.3, 1.9));
            double apron = Math.exp(-(d / 2.4) * (d / 2.4));
            best = Math.max(best, (0.80 * core + 0.20 * apron) * tops[k]);
        }
        return best * (0.85 + 0.15 * fbm(x, z, 600, 2, 0.5, 43));
    }

    // ------------------------------------------------------------------ drainage parameters
    @Override
    protected double routingNoise(int i) {
        double flat = wPlain[i] + wCoastal[i] + wLake[i] + 0.6 * wFoot[i] + 0.8 * wBelt[i] * (1 - Math.min(1, resist[i] * 2))
                + 0.3 * wPlateau[i] + 0.2 * wMount[i];
        if (flat < 0.01) return 0;
        double x = cellX(i % gridN), z = cellX(i / gridN);
        return flat * (1.1 * fbm(x, z, 170, 2, 0.5, 71) + 0.45 * fbm(x, z, 60, 2, 0.5, 72));
    }

    @Override
    protected double ks(int i) {
        return wPlain[i] * 3.0 + wCoastal[i] * 4.0 + wLake[i] * 3.0 + wFoot[i] * 4 + wBelt[i] * 3.6 + wMount[i] * 12 + wPlateau[i] * 8
                + 36 * resist[i];
    }

    @Override
    protected double depthCap(int i, double a) {
        double young = wPlain[i] + wCoastal[i] + wLake[i];
        if (young < 0.02) return 1e9;
        double lg = Math.log10(Math.max(1, a / 5e4));
        double d = (wPlain[i] * (0.5 + 2.6 * lg) + wCoastal[i] * (0.8 + 3.6 * lg) + wLake[i] * (0.4 + 2.2 * lg)) / young;
        return young > 0.98 ? d : d / young;
    }

    @Override
    protected double channelThreshold(int i) {
        return 5e4 * (wPlain[i] + wCoastal[i] + wLake[i]) + 3e4 * (wBelt[i] + wFoot[i] + wMount[i]) + 2.5e5 * wPlateau[i];
    }

    @Override
    protected double sideSlope(int i, double aChan, double incision) {
        double lg = Math.log10(Math.max(1e4, aChan) / 1e4);
        double plain = Math.min(0.36, 0.035 + 0.016 * incision + 0.01 * lg);
        double coastal = Math.min(0.40, 0.05 + 0.018 * incision + 0.01 * lg);
        double belt = 0.22 + (0.55 - 0.22) * Math.min(1, resist[i] * 1.6);
        double rk = Math.min(1, resist[i] * 1.6);
        double mount = 0.55 + 0.25 * smooth(incision / 60);
        double table = 0.95;
        return (1 - rk) * (wPlain[i] * plain + wCoastal[i] * coastal + wLake[i] * plain + wFoot[i] * 0.18 + wBelt[i] * belt
                + wMount[i] * mount + wPlateau[i] * table) + rk * belt;
    }

    /** The shore spreads only through its band: beyond 900 m (shoreSlope 0 in r1) the ground keeps its channel
     *  profiles, so the range and the tableland are not cut down to the band's top (about Y108). */
    @Override
    protected boolean floatRelax() { return true; }

    @Override
    protected double shoreSpread(int k) {
        return dCoast[k] < 900 ? super.shoreSpread(k) : Double.POSITIVE_INFINITY;
    }

    @Override
    protected double meanderFactor(int cell) {
        return 1 - 0.8 * Math.min(1, wBelt[cell] + 0.5 * wFoot[cell] + wMount[cell] + 0.7 * wPlateau[cell]);
    }

    @Override
    protected double floodplain(int i, double a) {
        if (a < 1e6) return 0;
        return (35 + 25 * wCoastal[i]) * Math.sqrt(a / 1e6) * (1 - 0.7 * Math.min(1, wMount[i] + wPlateau[i]));
    }

    @Override
    protected double stableDepth(int i, boolean wet) {
        if (wet) return 6;
        if (water[i] == 3 || water[i] == TerrainPlanSurface.LAKE) return 12;
        return 12 * (wPlain[i] + wCoastal[i] + wLake[i]) + 6 * wFoot[i] + 4 * wBelt[i] + 2 * wMount[i] + 8 * wPlateau[i];
    }

    // ------------------------------------------------------------------ lakes
    int lakeCount, lakeCells;

    /** Glacial troughs and kettles in the lake lowland (after the valleys are cut), then the sea floods only the low
     *  ground connected to it (a closed basin under Y63 stays a basin: a lake or filled ground, never sea). */
    @Override
    protected void drown() {
        excavateLakes();
        boolean[] seen = new boolean[n];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < n; i++) if (water[i] == 1) { seen[i] = true; queue.add(i); }
        int est = 0, marsh = 0;
        while (!queue.isEmpty()) {
            int i = queue.poll();
            int c = i % gridN, r = i / gridN;
            for (int d = 0; d < 8; d++) {
                int cc = c + DC[d], rr = r + DR[d];
                if (cc < 0 || rr < 0 || cc >= gridN || rr >= gridN) continue;
                int j = rr * gridN + cc;
                if (seen[j] || water[j] != 0 || h[j] > SEA + 0.25) continue;
                seen[j] = true;
                if (h[j] >= SEA - 1.6) { water[j] = 3; h[j] = (float) (SEA + 0.4); marsh++; }
                else { water[j] = 2; est++; }
                queue.add(j);
            }
        }
        report.put("drowned_cells", est);
        report.put("marsh_cells", marsh);
    }

    /** Finger-lake troughs (about north - south, 2.4-4 km, 22-36 m, gently bent, closed 0.9 km or more from the sea) and
     *  kettles (60-240 m, 3-10 m) cut into the lake upland. */
    void excavateLakes() {
        SplittableRandom rnd = new SplittableRandom(mix(seed ^ 0x1A4E5L));
        FederalTemplate.Anchor la = anchor(FederalTemplate.Physiography.LAKE_LOWLAND);
        double ax = la.ax() * map.scale, az = la.az() * map.scale, bx = la.bx() * map.scale, bz = la.bz() * map.scale;
        int troughs = 4 + rnd.nextInt(3);
        double[][] tr = new double[troughs][];
        for (int k = 0; k < troughs; k++) {
            double t = (k + 0.5 + 0.5 * (rnd.nextDouble() - 0.5)) / troughs;
            double cx = ax + t * (bx - ax), cz = az + t * (bz - az) + (rnd.nextDouble() - 0.5) * 700;
            double len = 2400 + 1600 * rnd.nextDouble(), hw = 160 + 140 * rnd.nextDouble(), depth = 22 + 14 * rnd.nextDouble();
            double ang = Math.toRadians((rnd.nextDouble() - 0.5) * 30);     // about north - south
            double bend = (rnd.nextBoolean() ? 1 : -1) * (80 + 120 * rnd.nextDouble()), phase = rnd.nextDouble() * Math.PI;
            tr[k] = new double[]{cx, cz, len, hw, depth, Math.sin(ang), Math.cos(ang), bend, phase};
        }
        int kettles = 55 + rnd.nextInt(30);
        double[][] ke = new double[kettles][];
        for (int k = 0; k < kettles; k++) {
            double t = rnd.nextDouble(), off = (rnd.nextDouble() - 0.5) * 2 * (la.core() + la.fade() * 0.5) * map.scale;
            double cx = ax + t * (bx - ax), cz = az + t * (bz - az) + off;
            ke[k] = new double[]{cx, cz, 60 + 180 * rnd.nextDouble(), 3 + 7 * rnd.nextDouble()};
        }
        par(0, gridN, r -> { for (int c = 0; c < gridN; c++) {
            int i = cellIdx(c, r);
            if (water[i] != 0 || wLake[i] < 0.08) continue;
            double x = cellX(c), z = cellX(r), cut = 0;
            for (double[] t : tr) {
                double dx = x - t[0], dz = z - t[1];
                double s = dx * t[5] + dz * t[6], q = dx * t[6] - dz * t[5];
                double half = t[2] / 2;
                if (Math.abs(s) >= half) continue;
                q -= t[7] * Math.sin(Math.PI * s / t[2] + t[8]);              // a gently bent trough
                double hw = t[3] * (1 + 0.22 * fbm(x, z, 900, 2, 0.5, 131)) * (0.75 + 0.25 * Math.cos(Math.PI * s / t[2]));
                if (Math.abs(q) >= hw) continue;
                double along = Math.pow(1 - (s / half) * (s / half), 0.8);
                double across = 1 - Math.pow(Math.abs(q) / hw, 2.2);         // U-shaped trough
                cut = Math.max(cut, t[4] * along * across * smooth((dCoast[i] - 900) / 700));
            }
            for (double[] kk : ke) {
                double rr = Math.hypot(x - kk[0], z - kk[1]) / (kk[2] * (1 + 0.2 * fbm(x, z, 120, 2, 0.5, 132)));
                if (rr >= 1) continue;
                cut = Math.max(cut, kk[3] * Math.pow(1 - rr * rr, 1.4) * smooth((dCoast[i] - 400) / 500));
            }
            if (cut <= 0) continue;
            cut *= Math.min(1, wLake[i] * 2.2);
            h[i] -= (float) cut;
        } });
    }

    /** Closed basins of the carved surface: real lakes where the lake lowland (or a high cirque in the range) holds them,
     *  filled ground elsewhere (r1: every basin filled). A lake's water stands one block under its spill point. */
    @Override
    protected void fillPits() {
        Routing f = route(h.clone(), 0.0);
        float[] filled = f.filled();
        int[] comp = new int[n];
        Arrays.fill(comp, -1);
        int filledCells = 0, lakes = 0, cells = 0;
        int[] stack = new int[n];
        boolean[] done = new boolean[n];
        for (int s0 = 0; s0 < n; s0++) {
            if (comp[s0] >= 0 || water[s0] != 0 || filled[s0] <= h[s0] + 0.05f) continue;
            // the basin: connected cells whose filled surface stands above the ground
            int top = 0, size = 0;
            stack[top++] = s0;
            comp[s0] = s0;
            double minH = 1e9, spill = 1e9, sumLake = 0, sumMount = 0, maxH = -1e9;
            int[] members = new int[64];
            while (top > 0) {
                int i = stack[--top];
                if (size == members.length) members = Arrays.copyOf(members, size * 2);
                members[size++] = i;
                minH = Math.min(minH, h[i]);
                maxH = Math.max(maxH, h[i]);
                spill = Math.min(spill, filled[i]);
                sumLake += wLake[i];
                sumMount += wMount[i];
                int c = i % gridN, r = i / gridN;
                for (int d = 0; d < 8; d++) {
                    int cc = c + DC[d], rr = r + DR[d];
                    if (cc < 0 || rr < 0 || cc >= gridN || rr >= gridN) continue;
                    int j = rr * gridN + cc;
                    if (comp[j] >= 0 || water[j] != 0 || filled[j] <= h[j] + 0.05f) continue;
                    comp[j] = s0;
                    stack[top++] = j;
                }
            }
            double level = filled[members[0]];
            for (int k = 0; k < size; k++) level = Math.max(level, filled[members[k]]);
            double depth = level - minH;
            boolean lowland = sumLake / size >= 0.3;
            boolean tarn = sumMount / size >= 0.6 && minH >= 150 && size <= 400;
            int waterTop = (int) Math.floor(spill) - 1;
            if ((lowland || tarn) && size >= 6 && depth >= 2.0 && waterTop >= SEA + 2 && waterTop > minH + 1) {
                for (int k = 0; k < size; k++) {
                    int i = members[k];
                    if (h[i] < waterTop + 0.5) {
                        water[i] = TerrainPlanSurface.LAKE;
                        lakeTop[i] = (short) waterTop;
                        cells++;
                    }
                }
                // ground of the basin above the water keeps its shape (filling it to the spill would leave flats round
                // the lake ends); only hollows in it fill, to the level at which they drain to the lake
                FloatHeap q = new FloatHeap(64);
                for (int k = 0; k < size; k++) {
                    int i = members[k];
                    if (water[i] == TerrainPlanSurface.LAKE) { done[i] = true; q.push((float) (waterTop + 0.5), i); }
                }
                while (q.size > 0) {
                    int i = q.popId();
                    float lv = Math.max(q.lastKey, water[i] == TerrainPlanSurface.LAKE ? (float) (waterTop + 0.5) : h[i]);
                    int c = i % gridN, r = i / gridN;
                    for (int d = 0; d < 8; d++) {
                        int cc = c + DC[d], rr = r + DR[d];
                        if (cc < 0 || rr < 0 || cc >= gridN || rr >= gridN) continue;
                        int j = rr * gridN + cc;
                        // the basin's cells, and any cell at its edge still below its own spill
                        if (done[j] || water[j] != 0 || (comp[j] != s0 && !(comp[j] < 0 && filled[j] > h[j]))) continue;
                        done[j] = true;
                        if (h[j] < lv) { h[j] = lv + 0.01f; filledCells++; }
                        q.push(h[j], j);
                    }
                }
                // the outlet: the water stands a block under the spill, so the sill between the lake and the spill point
                // (and the ground past it still above the water) is notched to the lake level along the outflow path
                int[] recv = f.recv();
                int start = -1;
                for (int k = 0; k < size && start < 0; k++) if (water[members[k]] == TerrainPlanSurface.LAKE) start = members[k];
                float notch = (float) (waterTop + 0.55);
                for (int i = start, steps = 0; i >= 0 && steps < 4 * size + 64; i = recv[i], steps++) {
                    if (water[i] == 1 || water[i] == 2 || water[i] == 3) break;
                    if (water[i] == TerrainPlanSurface.LAKE) continue;
                    if (h[i] <= notch - 0.002f * steps) break;          // the path is below the lake: the outlet is open
                    h[i] = notch - 0.002f * steps;
                }
                lakes++;
            } else {
                for (int k = 0; k < size; k++) { int i = members[k]; h[i] = filled[i]; filledCells++; }
            }
        }
        lakeCount = lakes;
        lakeCells = cells;
        report.put("pit_cells_filled", filledCells);
    }

    // ------------------------------------------------------------------ runtime layers and ecology
    @Override
    protected TerrainPlanSurface.Layers layers() {
        par(0, n, i -> {
            if (water[i] == 1 || water[i] == 2) return;
            // terraces: the tableland and its escarpment (benches and cliffs); crags: steep rock in the range and on cliffs
            double tw = Math.min(1, tableland[i] * 1.3) * Math.min(1, wPlateau[i] * 2.5);
            terrace[i] = (byte) Math.round(255 * Math.max(0, Math.min(1, tw)));
            double sl = slopeAt(i);
            double rw = Math.min(1, wMount[i] * 1.6) * smooth((sl - 0.25) / 0.45) + 0.6 * tw * smooth((sl - 0.4) / 0.6);
            rough[i] = (byte) Math.round(255 * Math.max(0, Math.min(1, rw)));
            // openness (woodlots), -1..1, from the climate and the landform (both continuous across the state lines):
            // the dry plains are open farm country with few woodlots; humid country (S1, the forest state; the lake
            // upland) and the hill country are wooded, their till mostly under woodlots with farm clearings
            double dry = 1 - smooth((moist[i] - 0.40) / 0.30);
            double plainsOpen = Math.min(1, wPlain[i] * 1.35) * dry * (1 - 0.6 * Math.min(1, wBelt[i] + wFoot[i]));
            double wooded = Math.max(smooth((moist[i] - 0.45) / 0.25), Math.min(1, wBelt[i] + wFoot[i]));
            double op = plainsOpen - 0.9 * wooded * (1 - plainsOpen);
            open[i] = (byte) Math.round(127.5 * (1 + Math.max(-1, Math.min(1, op))));
        });
        short[] tops = new short[n];
        for (int i = 0; i < n; i++) tops[i] = water[i] == TerrainPlanSurface.LAKE ? lakeTop[i] : Short.MIN_VALUE;
        return new TerrainPlanSurface.Layers(tops, terrace.clone(), rough.clone(), open.clone());
    }

    @Override
    protected EcologyPlan.Zones ecology() { return FederalEcology.classify(this); }
}
