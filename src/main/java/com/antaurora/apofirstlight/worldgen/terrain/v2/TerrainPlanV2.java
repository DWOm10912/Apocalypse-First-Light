package com.antaurora.apofirstlight.worldgen.terrain.v2;

import com.antaurora.apofirstlight.worldgen.geography.MacroGeography;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeographySample;

import java.util.Arrays;

/**
 * Terrain V2 national landform plan, research draft (2026-10-10; docs/worldgen/terrain_v2_real_terrain_research_v1.md).
 * Offline only: not compiled into the mod, not referenced by any density function. Pure seed + MacroGeography.
 *
 * Drainage first. The national surface is built in four steps on a 16 m grid over [-11264, 11264)^2:
 * 1. an initial (pre-incision) surface H0 per province: glacial till plain with end moraines, an Appalachian-style
 *    fold belt whose resistant layers outcrop as ridges (contours of a plunging fold surface: parallel ridges joined
 *    by zigzag / canoe-shaped noses), a dissected foothill zone, and coastal-plain terraces;
 * 2. flow routing on that surface (priority-flood, jittered D8) with the trunk rivers routed first on the surface
 *    WITHOUT the fold ridges (rivers older than the ridges: they keep water gaps through them);
 * 3. channel long profiles from Flint's law S = ks A^-theta upstream from base level (sea level 6 m below today's:
 *    the valleys are cut, then drowned into estuaries), ks set by rock / province; never above H0;
 * 4. hillslopes from each channel up to H0 (h = min(H0, channel + floodplain / valley-side profile)), so valleys
 *    are cut into the old surface and flat uplands (terraces between valleys) are left where no valley reached;
 *    a little diffusion rounds shoulders and crests; then the network is re-routed on the result and rebuilt once.
 * Rivers, drainage areas and heights all come from this one computation. Everything is deterministic and bounded.
 */
public final class TerrainPlanV2 {
    public static final String VERSION = "afl_terrain_plan_v2_r1";   // frozen 2026-10-10 for the user's map review
    public static final int N = 1408;
    public static final double CELL = 16, ORIGIN = -11264;
    public static final double SEA = MacroGeography.SEA_LEVEL;      // Y63: the top water block; land starts above
    public static final double LOWSTAND = SEA - 12;                    // base level while the valleys were cut
    public static final double THETA = 0.45;                           // concavity (Flint): S = ks A^-theta, A in m2

    public final long seed;
    public final MacroGeography geo;
    public final int n = N * N;
    public final byte[] water = new byte[n];          // 0 land, 1 macro sea, 2 drowned valley (estuary), 3 tidal marsh
    public final short[] landmass = new short[n];
    public final float[] dCoast = new float[n];       // m to the macro coast (land > 0, sea < 0)
    public final float[] seaFloor = new float[n];
    public final float[] wPlain = new float[n], wCoastal = new float[n], wFoot = new float[n], wBelt = new float[n];
    public final float[] resist = new float[n];       // resistant (ridge-forming) rock 0..1
    public final float[] h0 = new float[n];           // initial surface
    public final float[] h0NoRidge = new float[n];    // the same before the fold ridges stood up (trunk routing)
    public final float[] h = new float[n];            // planned surface Y
    public final float[] cap = new float[n];          // the filled initial surface: no incision rises above it
    public final int[] recv = new int[n];             // D8 receiver on the final surface (-1: sink)
    public final float[] area = new float[n];         // drainage area m2
    public final float[] chanY = new float[n];        // channel bed Y (NaN: not a channel)
    public final byte[] strahler = new byte[n];
    public final boolean[] trunk = new boolean[n];
    public double frameCx, frameCz, frameAngle, halfU, halfV;
    public int beltSide;
    public final java.util.LinkedHashMap<String, Object> report = new java.util.LinkedHashMap<>();

    private final long noiseSeed;

    public TerrainPlanV2(long seed) {
        this.seed = seed;
        this.geo = MacroGeography.forSeed(seed);
        this.noiseSeed = mix(seed ^ 0x7E44A1C3B5L);
        long t0 = System.nanoTime();
        sampleMask();
        coastDistance();
        frame();
        provinces();
        initialSurface();
        report.put("ms_setup", (System.nanoTime() - t0) / 1_000_000);
        long t1 = System.nanoTime();
        // trunk rivers on the surface before the ridges (antecedent drainage)
        Routing pre = route(h0NoRidge, 0.3);
        markTrunks(pre);
        // full network on the initial surface with the trunks burnt in; its filled surface caps all incision
        float[] surf = h0.clone();
        for (int i = 0; i < n; i++) if (trunk[i]) surf[i] = Math.min(surf[i], h0NoRidge[i] - 25);
        rows(0, n, i -> { if (water[i] == 0) surf[i] += (float) routingNoise(i); });
        Routing r = route(surf, 0.6);
        System.arraycopy(r.filled, 0, cap, 0, n);
        carve(r);
        diffuse(3);
        float[] again = h.clone();
        rows(0, n, i -> { if (water[i] == 0) again[i] += (float) (0.5 * routingNoise(i)); });
        Routing r2 = route(again, 0.4);   // rebuild once on the carved surface
        carve(r2);
        diffuse(2);
        diffuseResistant(3);
        smoothValleys(6);
        drown();
        fillPits();
        Routing fin = route(h.clone(), 0.0);
        System.arraycopy(fin.recv, 0, recv, 0, n);
        for (int i = 0; i < n; i++) area[i] = (float) fin.area[i];
        strahlerOrder(fin);
        report.put("ms_drainage", (System.nanoTime() - t1) / 1_000_000);
        validate(fin);
    }

    // ------------------------------------------------------------------ grid
    /** Row-parallel loop: every body writes only its own row's cells and reads shared, finished arrays, so the result
     *  does not depend on the thread count (same bytes as a sequential run). */
    private static void rows(int from, int to, java.util.function.IntConsumer body) {
        java.util.stream.IntStream.range(from, to).parallel().forEach(body);
    }

    public static double cx(int col) { return ORIGIN + (col + 0.5) * CELL; }
    public static int idx(int col, int row) { return row * N + col; }

    private void sampleMask() {
        rows(0, N, r -> {
            for (int c = 0; c < N; c++) {
                MacroGeographySample s = geo.sample((int) Math.floor(cx(c)), (int) Math.floor(cx(r)));
                int i = idx(c, r);
                landmass[i] = (short) s.landmassId();
                water[i] = (byte) (s.isLand() ? 0 : 1);
                seaFloor[i] = (float) s.surfaceHeight();
            }
        });
    }

    /** Exact Euclidean distance to the other class (Felzenszwalb & Huttenlocher 2012), in metres. */
    private void coastDistance() {
        float[] toWater = edt(i -> water[i] != 0), toLand = edt(i -> water[i] == 0);
        for (int i = 0; i < n; i++) dCoast[i] = water[i] == 0 ? toWater[i] : -toLand[i];
    }

    private interface Pred { boolean at(int i); }

    private float[] edt(Pred target) {
        double inf = 1e20;
        double[] g = new double[n];
        for (int i = 0; i < n; i++) g[i] = target.at(i) ? 0 : inf;
        rows(0, N, c -> {                            // columns
            double[] f = new double[N], d = new double[N], zz = new double[N + 1];
            int[] v = new int[N];
            for (int r = 0; r < N; r++) f[r] = g[idx(c, r)];
            dt1(f, d, v, zz);
            for (int r = 0; r < N; r++) g[idx(c, r)] = d[r];
        });
        float[] out = new float[n];
        rows(0, N, r -> {                            // rows
            double[] f = new double[N], d = new double[N], zz = new double[N + 1];
            int[] v = new int[N];
            for (int c = 0; c < N; c++) f[c] = g[idx(c, r)];
            dt1(f, d, v, zz);
            for (int c = 0; c < N; c++) out[idx(c, r)] = (float) (Math.sqrt(d[c]) * CELL);
        });
        return out;
    }

    private static void dt1(double[] f, double[] d, int[] v, double[] z) {
        int k = 0;
        v[0] = 0;
        z[0] = -1e30;
        z[1] = 1e30;
        for (int q = 1; q < f.length; q++) {
            double s;
            while (true) {
                s = ((f[q] + q * (double) q) - (f[v[k]] + v[k] * (double) v[k])) / (2.0 * q - 2.0 * v[k]);
                if (s <= z[k] && k > 0) k--; else break;
            }
            if (s <= z[k]) { v[k] = q; z[k + 1] = 1e30; continue; }
            k++;
            v[k] = q;
            z[k] = s;
            z[k + 1] = 1e30;
        }
        k = 0;
        for (int q = 0; q < f.length; q++) {
            while (z[k + 1] < q) k++;
            d[q] = (q - v[k]) * (double) (q - v[k]) + f[v[k]];
        }
    }

    /** The mainland's own principal axes (from its cells), independent of MacroGeography internals. */
    private void frame() {
        double sx = 0, sz = 0, cnt = 0;
        for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) if (landmass[idx(c, r)] == 0) { sx += cx(c); sz += cx(r); cnt++; }
        frameCx = sx / cnt;
        frameCz = sz / cnt;
        double xx = 0, xz = 0, zz = 0;
        for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) if (landmass[idx(c, r)] == 0) {
            double dx = cx(c) - frameCx, dz = cx(r) - frameCz;
            xx += dx * dx; xz += dx * dz; zz += dz * dz;
        }
        frameAngle = 0.5 * Math.atan2(2 * xz, xx - zz);
        double[] us = new double[(int) cnt], vs = new double[(int) cnt];
        int k = 0;
        for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) if (landmass[idx(c, r)] == 0) { us[k] = u(cx(c), cx(r)); vs[k] = v(cx(c), cx(r)); k++; }
        Arrays.sort(us);
        Arrays.sort(vs);
        halfU = (us[(int) (cnt * .995)] - us[(int) (cnt * .005)]) / 2;
        halfV = (vs[(int) (cnt * .995)] - vs[(int) (cnt * .005)]) / 2;
        report.put("frame", new double[]{frameCx, frameCz, Math.toDegrees(frameAngle), halfU, halfV});
    }

    public double u(double x, double z) { return (x - frameCx) * Math.cos(frameAngle) + (z - frameCz) * Math.sin(frameAngle); }
    public double v(double x, double z) { return -(x - frameCx) * Math.sin(frameAngle) + (z - frameCz) * Math.cos(frameAngle); }

    // ------------------------------------------------------------------ provinces
    // Fold belt: a lens along the long axis on one side (seed), its centre line BELT_INSET from that side's coast,
    // bowed like the Pennsylvania salient, tapering to both ends, its margins irregular. Between it and that coast lie
    // the foothills (piedmont) and the coastal plain (the Mid-Atlantic order: mountains, piedmont, coastal plain,
    // estuaries). The main divide lies DIVIDE_OFFSET inland of the belt, so the rivers of the strip between rise on the
    // plain and cross the belt in water gaps (as the Susquehanna crosses the Pennsylvania ridges).
    static final double BELT_INSET = 3900, BELT_HALF = 1450, BELT_EDGE = 600, BELT_LENGTH = 0.64;
    static final double DIVIDE_OFFSET = 3600, DIVIDE_RISE = 10;
    static final double FOLD_WAVE = 3200;            // across strike: limb ridges every half wave, 1.6 km (0.55 x PA)
    double beltPhase, beltBend, beltPlunge, beltShift, dividePhase;

    private void provinces() {
        java.util.SplittableRandom rnd = new java.util.SplittableRandom(mix(seed ^ 0x51DE5EEDL));
        beltSide = rnd.nextBoolean() ? 1 : -1;
        beltPhase = rnd.nextDouble() * Math.PI * 2;
        beltBend = (0.6 + 0.4 * rnd.nextDouble()) * 600;
        beltPlunge = rnd.nextDouble() * Math.PI * 2;
        beltShift = (rnd.nextDouble() - 0.5) * 0.24;
        dividePhase = rnd.nextDouble() * Math.PI * 2;
        rows(0, N, r -> { for (int c = 0; c < N; c++) {
            int i = idx(c, r);
            if (water[i] != 0) continue;
            double x = cx(c), z = cx(r), uu = u(x, z), vv = v(x, z);
            double dc = dCoast[i];
            if (landmass[i] != 0) {                  // satellite islands: coastal plain and low till plain
                double cw = 1 - smooth((dc - 250) / 500);
                wCoastal[i] = (float) cw;
                wPlain[i] = (float) (1 - cw);
                continue;
            }
            double belt = beltEnvelope(uu, vv, x, z);
            double q = beltSide * (vv - beltCentre(uu));
            double p = uu - beltShift * halfU, lb = BELT_LENGTH * halfU;
            double hw = beltHalfWidth(p, x, z);
            double foot = (1 - smooth((Math.abs(q) - hw - BELT_EDGE - 650) / 600))
                    * (1 - smooth((Math.abs(p) - lb - 900) / 700)) * (1 - belt);
            boolean seaward = q > 0;
            // the coastal plain widens and narrows along the shore (no ring of one width round the island)
            double coastalWidth = (seaward ? 1800 : 750) * (1 + 0.38 * fbm(x, z, 3200, 2, 0.5, 6));
            double coastal = (1 - smooth((dc - coastalWidth * 0.55) / (coastalWidth * 0.6))) * (1 - belt);
            foot *= 1 - coastal;
            double plain = Math.max(0, 1 - belt - foot - coastal);
            double sum = belt + foot + coastal + plain;
            wBelt[i] = (float) (belt / sum);
            wFoot[i] = (float) (foot / sum);
            wCoastal[i] = (float) (coastal / sum);
            wPlain[i] = (float) (plain / sum);
        } });
    }

    double beltCentre(double uu) {
        double edge = beltSide > 0 ? halfV : -halfV;
        return edge - beltSide * BELT_INSET + beltSide * beltBend * (1 - (uu / halfU) * (uu / halfU))
                + 160 * Math.sin(uu / 1900 + beltPhase);
    }

    /** Lens: full width in the middle, tapering to the plunging ends, margins wandering by about 15 %. */
    double beltHalfWidth(double p, double x, double z) {
        double lb = BELT_LENGTH * halfU, t = Math.min(1, Math.abs(p) / lb);
        return BELT_HALF * Math.sqrt(Math.max(0, 1 - t * t * t)) * (1 + 0.15 * fbm(x, z, 2500, 2, 0.5, 5));
    }

    double beltEnvelope(double uu, double vv, double x, double z) {
        double q = beltSide * (vv - beltCentre(uu)), p = uu - beltShift * halfU, lb = BELT_LENGTH * halfU;
        return (1 - smooth((Math.abs(q) - beltHalfWidth(p, x, z)) / BELT_EDGE)) * (1 - smooth((Math.abs(p) - lb) / 500));
    }

    /** The regional surface: 0 at the coast rising to the main divide (DIVIDE_OFFSET inland of the belt). */
    double regional(double x, double z, double uu, double vv, double dc, double tilt) {
        double vDiv = beltCentre(uu) - beltSide * (DIVIDE_OFFSET + 450 * Math.sin(uu / 3100 + dividePhase));
        double dDiv = Math.abs(vv - vDiv);
        double t = dc / (dc + dDiv + 1);
        double s = Math.pow(smooth(Math.min(1, 1.15 * t)), 1.1);
        // a low terrace behind every shore (monotone inland, so it never dams the plain behind it)
        return SEA + 2.5 + 4 * smooth(dc / 700) + DIVIDE_RISE * s + s * (tilt * uu / halfU + 3 * fbm(x, z, 7000, 2, 0.5, 10));
    }

    // ------------------------------------------------------------------ initial surface
    private void initialSurface() {
        java.util.SplittableRandom rnd = new java.util.SplittableRandom(mix(seed ^ 0x40AA1E5L));
        // end moraines: 1-2 broad arcs across the till plain, 8-14 m high, 700-1200 m wide (Bloomington / Darby type)
        int moraines = 1 + rnd.nextInt(2);
        double[][] arcs = new double[moraines][];
        for (int k = 0; k < moraines; k++) {
            double uc = (rnd.nextDouble() - 0.5) * halfU * 0.9, vc = -beltSide * halfV * (1.6 + rnd.nextDouble());
            double rad = Math.abs(vc) + (0.15 + 0.35 * rnd.nextDouble()) * halfV * (k + 1);
            arcs[k] = new double[]{uc, vc, rad, 8 + 6 * rnd.nextDouble(), 350 + 250 * rnd.nextDouble()};
        }
        // one regional surface for every province (the old erosion surface the rivers first ran on): rising inland
        // with the distance to the coast, tilted along the long axis (seed) and warped at 5-8 km, so the basins differ
        // in size and the main divide is not the island's centre line; the provinces add their own relief to it
        double tilt = (rnd.nextBoolean() ? 1 : -1) * (2 + 2 * rnd.nextDouble());
        rows(0, N, r -> { for (int c = 0; c < N; c++) {
            int i = idx(c, r);
            if (water[i] != 0) { h0[i] = h0NoRidge[i] = seaFloor[i]; continue; }
            double x = cx(c), z = cx(r), uu = u(x, z), vv = v(x, z), dc = dCoast[i];
            double regional = landmass[i] == 0 ? regional(x, z, uu, vv, dc, tilt)
                    : SEA + 2.5 + 14 * smooth(dc / 900) + 2 * fbm(x, z, 1200, 2, 0.5, 9);
            // till plain: broad swells (km scale), hummocky ground moraine (100-300 m), end moraines
            // amplitudes from the Ohio / Illinois spectra (octave-band RMS 80-320 m about 0.8-1.0 m, 320-1280 m 1.4-2.5 m)
            // the till surface itself is smooth: in the Ohio / Illinois tiles the 80-1280 m relief sits in the valleys
            double plain = 1.5 * fbm(x, z, 3600, 2, 0.55, 11) + 1.3 * fbm(x, z, 800, 3, 0.5, 12)
                    + 0.6 * fbm(x, z, 300, 2, 0.5, 13);
            for (double[] a : arcs) {
                double du = uu - a[0], dv = vv - a[1];
                double rr = Math.hypot(du, dv) - a[2] - 220 * fbm(x, z, 2600, 2, 0.5, 14);
                double crest = a[3] * (0.75 + 0.35 * fbm(x, z, 1800, 2, 0.5, 15));
                plain += crest * Math.exp(-(rr / a[4]) * (rr / a[4])) * smooth(dc / 1500);
            }
            // coastal plain: low terraces (the regional surface already drops toward the shore)
            // coastal plain: terraces rising behind the shore (the Chesapeake uplands stand 10-30 m above the water)
            double coastal = 1.4 * fbm(x, z, 1500, 3, 0.5, 21) + 0.5 * fbm(x, z, 300, 2, 0.5, 22);
            // foothills / piedmont: a rolling upland on weak rock, dissected by the network later
            double foot = 22 + 6 * fbm(x, z, 1300, 3, 0.55, 31) + 0.8 * fbm(x, z, 300, 2, 0.5, 32);
            // fold belt: a weak-rock upland (dissected into the valley hills) with resistant-layer outcrops above it
            double q = beltSide * (vv - beltCentre(uu));
            double rg = landmass[i] == 0 ? foldRidges(uu - beltShift * halfU, q, x, z) * beltEnvelope(uu, vv, x, z) : 0;
            resist[i] = (float) rg;
            double crestVar = 1 + 0.18 * fbm(x, z, 1400, 3, 0.55, 42)
                    - 0.30 * Math.max(0, fbm(x, z, 950, 2, 0.5, 44) - 0.9);     // crests vary; occasional wind gaps
            double beltBase = 24 + 5 * fbm(x, z, 2000, 2, 0.5, 41) + 1.0 * fbm(x, z, 300, 2, 0.5, 45);
            double base = regional + wPlain[i] * plain + wCoastal[i] * coastal + wFoot[i] * foot + wBelt[i] * beltBase;
            h0NoRidge[i] = (float) base;
            // a rough ridge surface (talus, ledges): flank flow converges into hollows instead of running in parallel
            double rough = rg * (4.5 * fbm(x, z, 230, 2, 0.5, 47) + 0.6 * fbm(x, z, 90, 2, 0.5, 48));
            h0[i] = (float) (base + 105 * rg * crestVar + rough);
        } });
    }

    /**
     * Resistant layers of a plunging fold train: the fold surface L(p, q) = cos(2 pi q / FOLD_WAVE + drift(p)) *
     * plunge(p, q); a layer at level l outcrops along the contour L = l, so each layer gives two parallel ridges that
     * meet at the fold's plunging nose (canoe / zigzag shapes). The fold axes rise and fall along strike (culminations
     * and depressions, each fold at its own phase), so ridges split, end and reappear. The scarp side is steeper than
     * the dip slope; a broad apron gives the ridge its foot. Strength 0..1.
     */
    double foldRidges(double p, double q, double x, double z) {
        double drift = 0.85 * Math.sin(p / 3100 + beltPhase) + 0.40 * Math.sin(p / 1350 + 2.3 * beltPhase)
                + 0.18 * fbm(x, z, 1800, 2, 0.5, 46);
        double phase = 2 * Math.PI * (q + 400) / FOLD_WAVE + drift;
        // structural elevation of the fold axes along strike: culminations close the limbs into noses; toward the
        // belt's ends the folds plunge out (alternate folds up and down, so the noses point both ways)
        double lb = BELT_LENGTH * halfU, end = smooth((Math.abs(p) / lb - 0.55) / 0.45);
        double fold = Math.floor((q + 400) / FOLD_WAVE + drift / (2 * Math.PI) + 0.5);
        double culm = 0.50 * Math.sin(p / 2100 + beltPlunge + 1.7 * fold) + 0.28 * Math.sin(p / 870 + 2.9 * fold + beltPhase)
                + end * 1.25 * (((long) fold & 1) == 0 ? 1 : -1);
        double L = Math.cos(phase) + culm;
        double best = 0;
        double[] levels = {0.05, 0.85};
        double[] widths = {0.36, 0.20};
        double[] tops = {1.0, 0.55};
        for (int k = 0; k < levels.length; k++) {
            double d = (L - levels[k]) / widths[k];
            double ad = Math.abs(d);
            double core = d > 0 ? Math.exp(-Math.pow(ad, 1.9)) : Math.exp(-Math.pow(ad * 1.3, 1.9));   // dip slope / scarp
            double apron = Math.exp(-(d / 2.4) * (d / 2.4));
            best = Math.max(best, (0.80 * core + 0.20 * apron) * tops[k]);
        }
        return best * (0.85 + 0.15 * fbm(x, z, 600, 2, 0.5, 43));
    }

    // ------------------------------------------------------------------ routing
    static final class Routing {
        int[] recv; double[] area; int[] order;   // order: downstream first (pop order)
        float[] filled;
    }

    /** Priority-flood (+epsilon) from the sea, jittered D8 on the filled surface, accumulation. */
    Routing route(float[] surf, double jitter) {
        Routing R = new Routing();
        float[] zf = surf.clone();
        boolean[] done = new boolean[n];
        FloatHeap heap = new FloatHeap(n / 4);
        int[] order = new int[n];
        int k = 0;
        for (int i = 0; i < n; i++) {
            int c = i % N, r = i / N;
            if (water[i] == 1 || water[i] == 2 || c == 0 || r == 0 || c == N - 1 || r == N - 1) { heap.push(zf[i], i); done[i] = true; }
        }
        while (heap.size > 0) {
            int i = heap.popId();
            order[k++] = i;
            int c = i % N, r = i / N;
            for (int d = 0; d < 8; d++) {
                int cc = c + DC[d], rr = r + DR[d];
                if (cc < 0 || rr < 0 || cc >= N || rr >= N) continue;
                int j = rr * N + cc;
                if (done[j]) continue;
                done[j] = true;
                if (zf[j] <= zf[i]) zf[j] = Math.nextUp(zf[i]) + 1e-4f;
                heap.push(zf[j], j);
            }
        }
        int[] rv = new int[n];
        rows(0, n, i -> {
            int c = i % N, r = i / N;
            if (water[i] == 1 || water[i] == 2 || c == 0 || r == 0 || c == N - 1 || r == N - 1) { rv[i] = -1; return; }
            double best = 0;
            int bj = -1;
            for (int d = 0; d < 8; d++) {
                int j = (r + DR[d]) * N + c + DC[d];
                double drop = (zf[i] - zf[j]) / DIST[d];
                if (drop <= 0) continue;
                if (jitter > 0) drop *= 1 + jitter * (hash01(i * 8L + d) - 0.5);
                if (drop > best) { best = drop; bj = j; }
            }
            rv[i] = bj;
        });
        double[] a = new double[n];
        Arrays.fill(a, CELL * CELL);
        for (int t = n - 1; t >= 0; t--) { int i = order[t]; if (rv[i] >= 0) a[rv[i]] += a[i]; }
        R.recv = rv; R.area = a; R.order = order; R.filled = zf;
        return R;
    }

    /** Routing-only irregularity on the flat provinces (60-160 m), so channels wander and converge instead of running
     *  straight down a planar slope along a grid direction; the carved surface follows the network it produces. */
    double routingNoise(int i) {
        double flat = wPlain[i] + wCoastal[i] + 0.6 * wFoot[i] + 0.8 * wBelt[i] * (1 - Math.min(1, resist[i] * 2));
        if (flat < 0.01) return 0;
        double x = cx(i % N), z = cx(i / N);
        return flat * (1.1 * fbm(x, z, 170, 2, 0.5, 71) + 0.45 * fbm(x, z, 60, 2, 0.5, 72));
    }

    private void markTrunks(Routing pre) {
        for (int i = 0; i < n; i++) trunk[i] = water[i] == 0 && pre.area[i] >= 6e6;   // >= 6 km2 before the ridges
    }

    // ------------------------------------------------------------------ carving
    double ks(int i) {      // channel steepness index by rock / province (S = ks A^-0.45, A in m2)
        return wPlain[i] * 3.0 + wCoastal[i] * 4.0 + wFoot[i] * 4 + wBelt[i] * 3.6 + 36 * resist[i];
    }

    /** Deepest incision below the old surface: the till plain and coastal plain are young (post-glacial / post-
     *  lowstand), so valleys deepen only with the log of the area; the belt and foothills are not limited. */
    double depthCap(int i, double a) {
        double young = wPlain[i] + wCoastal[i];
        if (young < 0.02) return 1e9;
        double lg = Math.log10(Math.max(1, a / 5e4));
        double d = (wPlain[i] * (0.5 + 2.6 * lg) + wCoastal[i] * (0.8 + 3.6 * lg)) / young;
        return young > 0.98 ? d : d / young;     // blends out toward the old landscapes
    }

    double channelThreshold(int i) { return 5e4 + (3e4 - 5e4) * (wBelt[i] + wFoot[i]); }

    double sideSlope(int i, double aChan, double incision) {
        double lg = Math.log10(Math.max(1e4, aChan) / 1e4);
        double plain = Math.min(0.36, 0.035 + 0.016 * incision + 0.01 * lg);
        double coastal = Math.min(0.40, 0.05 + 0.018 * incision + 0.01 * lg);
        double belt = 0.22 + (0.55 - 0.22) * Math.min(1, resist[i] * 1.6);
        double rk = Math.min(1, resist[i] * 1.6);
        return (1 - rk) * (wPlain[i] * plain + wCoastal[i] * coastal + wFoot[i] * 0.18 + wBelt[i] * belt) + rk * belt;
    }

    private void carve(Routing R) {
        Arrays.fill(chanY, Float.NaN);
        boolean[] isChan = new boolean[n];
        for (int i = 0; i < n; i++) isChan[i] = water[i] == 0 && R.area[i] >= channelThreshold(i);
        for (int t = n - 1; t >= 0; t--) {           // everything below a channel is channel (thresholds vary)
            int i = R.order[t], j = R.recv[i];
            if (isChan[i] && j >= 0 && water[j] == 0) isChan[j] = true;
        }
        // channel profiles, downstream first
        for (int t = 0; t < n; t++) {
            int i = R.order[t];
            if (!isChan[i]) continue;
            int j = R.recv[i];
            double step = stepLen(i, j);
            double below = j < 0 || water[j] != 0 ? LOWSTAND : chanY[j];
            if (j >= 0 && water[j] == 0 && !isChan[j]) below = h[j];
            double s = Math.min(0.55, ks(i) * Math.pow(R.area[i], -THETA));
            double y = below + step * s;
            // young glacial plain / coastal plain: incision limited by the log of the area, except near a deeper valley
            // or the sea, from where it works headward (knickpoints): the allowed depth decays upstream over Lk(A),
            // so ravines cut back into the bluffs of the main valleys and the uplands beyond stay flat
            double cap = depthCap(i, R.area[i]);
            if (cap < 1e8) {
                double dj = j < 0 || water[j] != 0 ? 1e9 : Math.max(0, h0[j] - below);
                double lk = 120 * Math.pow(R.area[i] / 1e5, 0.35);
                double allowed = Math.max(cap, dj * Math.exp(-step / lk));
                y = Math.max(y, h0[i] - allowed);
            }
            y = Math.min(y, h0[i]);
            if (y < below + 1e-3) y = below + 1e-3;
            chanY[i] = (float) y;
        }
        // hillslopes: the lower envelope of the valley sides growing out from every channel (and from the shore), a
        // weighted shortest-path spread on a 16-neighbour stencil: each cell takes the lowest channel + side profile
        // that reaches it, so valley walls follow true distance from the channel (not a D8 flow path, which would leave
        // grid staircases) and divides form where two valleys' sides meet. A floodplain of half-width 35 sqrt(A km2)
        // rises gently first. The result never rises above the old surface; a hollow below it fills to the channel.
        if (shoreSlope == null) {
            shoreSlope = new float[n];
            float[] ss = shoreSlope;
            rows(0, n, i -> { if (water[i] == 0 && dCoast[i] < 900)
                ss[i] = (float) (0.05 + 0.20 * smooth(0.5 + 0.6 * fbm(cx(i % N), cx(i / N), 900, 2, 0.5, 61))); });
        }
        float[] val = new float[n], fpd = new float[n];
        int[] src = new int[n];
        Arrays.fill(val, Float.POSITIVE_INFINITY);
        FloatHeap heap = new FloatHeap(n / 4);
        for (int i = 0; i < n; i++) {
            if (water[i] != 0) continue;
            if (isChan[i]) { val[i] = chanY[i]; src[i] = i; heap.push(val[i], i); continue; }
            int c = i % N, r = i / N;
            for (int d = 0; d < 8; d++) {
                int cc = c + DC[d], rr = r + DR[d];
                if (cc >= 0 && rr >= 0 && cc < N && rr < N && water[rr * N + cc] == 1) {
                    val[i] = (float) (SEA + 0.5); src[i] = -2; heap.push(val[i], i); break;
                }
            }
        }
        float[] sideOf = new float[n];
        while (heap.size > 0) {
            int i = heap.popId();
            if (heap.lastKey > val[i]) continue;          // superseded entry
            double vi = val[i];
            int c = i % N, r = i / N, sc = src[i];
            double fp = 0, side;
            if (sc >= 0) { double a = R.area[sc]; fp = a >= 1e6 ? 35 * Math.sqrt(a / 1e6) : 0; }
            for (int d = 0; d < 16; d++) {
                int cc = c + NC[d], rr = r + NR[d];
                if (cc < 0 || rr < 0 || cc >= N || rr >= N) continue;
                int k = rr * N + cc;
                if (water[k] != 0 || isChan[k]) continue;
                double step = ND[d] * CELL, dist = fpd[i] + step;
                double sl;
                if (sc == -2) sl = shoreSlope[k];                       // shore: marshy edges / bluffs alternate
                else if (dist <= fp) sl = 0.012;
                else sl = sideSlope(k, R.area[sc], Math.max(0, h0[sc] - chanY[sc]));
                double nv = vi + sl * step;
                if (nv < val[k]) { val[k] = (float) nv; src[k] = sc; fpd[k] = (float) dist; heap.push(val[k], k); }
            }
        }
        rows(0, n, i -> {
            if (water[i] != 0) { h[i] = h0[i]; return; }
            if (isChan[i]) { h[i] = chanY[i]; return; }
            int sc = src[i];
            double floor = sc == -2 ? SEA + 0.5 : sc >= 0 ? chanY[sc] + (R.area[sc] >= 1e6 ? 0.6 : 0.3) : h0[i];
            double y = Float.isInfinite(val[i]) ? h0[i] : val[i] + (sc >= 0 && R.area[sc] >= 1e6 ? 0.6 : 0.3);
            h[i] = (float) Math.min(Math.max(h0[i], floor), y);
        });
    }

    private float[] shoreSlope;

    /** Rounds the carved valleys (floor, walls and the channel line in them): a D8 channel runs in straight runs with
     *  sharp bends, and the valley drawn around it would keep those corners; a few passes over the carved cells only
     *  (h below the old surface) round them off. Drainage is restored by the pit fill afterwards. */
    private void smoothValleys(int iterations) {
        float[] t = new float[n];
        for (int it = 0; it < iterations; it++) {
            System.arraycopy(h, 0, t, 0, n);
            rows(1, N - 1, r -> { for (int c = 1; c < N - 1; c++) {
                int i = r * N + c;
                if (water[i] != 0 || h0[i] - t[i] < 0.4 || resist[i] > 0.15) continue;
                double s = 0;
                int m = 0;
                for (int d = 0; d < 8; d++) { int j = (r + DR[d]) * N + c + DC[d]; if (water[j] == 0) { s += t[j]; m++; } }
                if (m > 0) h[i] = (float) Math.min(h0[i], t[i] + 0.3 * (s / m - t[i]));
            } });
        }
    }

    private void diffuse(int iterations) {
        float[] t = new float[n];
        for (int it = 0; it < iterations; it++) {
            System.arraycopy(h, 0, t, 0, n);
            rows(1, N - 1, r -> { for (int c = 1; c < N - 1; c++) {
                int i = r * N + c;
                if (water[i] != 0 || !Float.isNaN(chanY[i])) continue;
                double s = 0;
                int m = 0;
                for (int d = 0; d < 8; d++) { int j = (r + DR[d]) * N + c + DC[d]; if (water[j] == 0) { s += t[j]; m++; } }
                if (m == 0) continue;
                double nh = t[i] + 0.25 * (s / m - t[i]);
                h[i] = (float) nh;
            } });
        }
    }

    /** Fill the remaining closed hollows (ponds the carving left) so every land cell drains to the sea. */
    private void fillPits() {
        Routing f = route(h.clone(), 0.0);
        int filled = 0;
        for (int i = 0; i < n; i++) if (water[i] == 0 && f.filled[i] > h[i] + 1e-3f) { h[i] = f.filled[i]; filled++; }
        report.put("pit_cells_filled", filled);
    }

    /** Extra rounding on resistant rock only (crests and flanks; the 16 m grid must not carry notches it cannot draw). */
    private void diffuseResistant(int iterations) {
        float[] t = new float[n];
        for (int it = 0; it < iterations; it++) {
            System.arraycopy(h, 0, t, 0, n);
            rows(1, N - 1, r -> { for (int c = 1; c < N - 1; c++) {
                int i = r * N + c;
                if (water[i] != 0 || resist[i] < 0.15 || !Float.isNaN(chanY[i])) continue;
                double s = 0;
                for (int d = 0; d < 8; d++) s += t[(r + DR[d]) * N + c + DC[d]];
                h[i] = (float) (t[i] + 0.3 * Math.min(1, resist[i] * 2) * (s / 8 - t[i]));
            } });
        }
    }

    /** Sea level back to Y63: the lowest valley floors drown (estuaries); shallow, sheltered ones silt up into marsh. */
    private void drown() {
        int est = 0, marsh = 0;
        for (int i = 0; i < n; i++) {
            if (water[i] != 0 || h[i] > SEA + 0.25) continue;
            if (h[i] >= SEA - 1.6) { water[i] = 3; h[i] = (float) (SEA + 0.4); marsh++; }
            else { water[i] = 2; est++; }
        }
        report.put("drowned_cells", est);
        report.put("marsh_cells", marsh);
    }

    private void strahlerOrder(Routing R) {
        int[] mx = new int[n], cnt = new int[n];
        for (int t = n - 1; t >= 0; t--) {
            int i = R.order[t];
            if (water[i] != 0 || R.area[i] < 5e4) continue;
            int o = mx[i] == 0 ? 1 : (cnt[i] >= 2 ? mx[i] + 1 : mx[i]);
            strahler[i] = (byte) o;
            int j = R.recv[i];
            if (j >= 0) { if (o > mx[j]) { mx[j] = o; cnt[j] = 1; } else if (o == mx[j]) cnt[j]++; }
        }
    }

    private void validate(Routing R) {
        // planned channels (A >= 1 km2) must descend along their receivers; count reversals and closed pits
        int uphill = 0, chan = 0, pits = 0;
        for (int i = 0; i < n; i++) {
            if (water[i] != 0) continue;
            int j = R.recv[i];
            if (R.area[i] >= 1e6 && water[i] == 0) { chan++; if (j >= 0 && water[j] == 0 && h[j] > h[i] + 0.05) uphill++; }
            int c = i % N, r = i / N;
            if (c > 0 && r > 0 && c < N - 1 && r < N - 1) {
                boolean low = true;
                for (int d = 0; d < 8 && low; d++) { int k = (r + DR[d]) * N + c + DC[d]; if (h[k] <= h[i] || water[k] != 0) low = false; }
                if (low) pits++;
            }
        }
        report.put("channel_cells_A1km2", chan);
        report.put("uphill_steps_A1km2", uphill);
        report.put("closed_pits_16m", pits);
    }

    // ------------------------------------------------------------------ runtime surface and point queries
    private volatile TerrainPlanSurface surface;

    /** The compact runtime form (TerrainPlanSurface): planned heights, the unified water mask (macro sea + drowned
     *  valleys + marsh), the fold-belt weight for the detail, the stable near-surface depth and the shore distance. */
    public TerrainPlanSurface toSurface() {
        TerrainPlanSurface s = surface;
        if (s != null) return s;
        float[] toWater = edt(i -> water[i] == 1 || water[i] == 2), toLand = edt(i -> water[i] == 0 || water[i] == 3);
        byte[] stable = new byte[n], shore = new byte[n];
        rows(0, n, i -> {
            boolean wet = water[i] == 1 || water[i] == 2;
            double d = wet ? -toLand[i] : toWater[i];
            shore[i] = (byte) Math.max(-127, Math.min(127, Math.round(d)));
            double depth = wet ? 6 : water[i] == 3 ? 12 : 12 * (wPlain[i] + wCoastal[i]) + 6 * wFoot[i] + 4 * wBelt[i];
            stable[i] = (byte) Math.round(depth);
        });
        TerrainPlanSurface bare = new TerrainPlanSurface(VERSION, seed, noiseSeed, h.clone(), water.clone(), wBelt.clone(), stable, shore, null);
        long t0 = System.nanoTime();
        RiverNetwork rivers = RiverNetwork.build(this, bare);      // Phase 2b: river water on the frozen r1 network
        report.put("ms_rivers", (System.nanoTime() - t0) / 1_000_000);
        report.put("river_lines", rivers.lines());
        report.put("river_vertices", rivers.vertices());
        s = bare.withRivers(rivers);
        surface = s;
        return s;
    }

    /** Planned surface at any point (delegates to the runtime surface: one formula for maps and the game). */
    public double heightAt(double x, double z) { return toSurface().heightAt(x, z); }

    public boolean waterAt(double x, double z) { return toSurface().waterAt(x, z); }

    double slopeAt(int i) {
        int c = i % N, r = i / N;
        if (c < 1 || r < 1 || c > N - 2 || r > N - 2) return 0;
        double gx = (h[i + 1] - h[i - 1]) / (2 * CELL), gz = (h[i + N] - h[i - N]) / (2 * CELL);
        return Math.hypot(gx, gz);
    }

    static double cr(double[] p, double t) {
        return p[1] + 0.5 * t * (p[2] - p[0] + t * (2 * p[0] - 5 * p[1] + 4 * p[2] - p[3] + t * (3 * (p[1] - p[2]) + p[3] - p[0])));
    }

    static int clampI(int v) { return Math.max(0, Math.min(N - 1, v)); }

    // ------------------------------------------------------------------ helpers
    static final int[] DC = {-1, 0, 1, -1, 1, -1, 0, 1}, DR = {-1, -1, -1, 0, 0, 1, 1, 1};
    // 16-neighbour stencil (8 + knight moves) for the valley-side spread: near-circular distance, no octagons
    static final int[] NC = {-1, 0, 1, -1, 1, -1, 0, 1, -2, -1, 1, 2, -2, -1, 1, 2}, NR = {-1, -1, -1, 0, 0, 1, 1, 1, -1, -2, -2, -1, 1, 2, 2, 1};
    static final double[] ND = {Math.sqrt(2), 1, Math.sqrt(2), 1, 1, Math.sqrt(2), 1, Math.sqrt(2),
            Math.sqrt(5), Math.sqrt(5), Math.sqrt(5), Math.sqrt(5), Math.sqrt(5), Math.sqrt(5), Math.sqrt(5), Math.sqrt(5)};
    static final double[] DIST = {Math.sqrt(2) * CELL, CELL, Math.sqrt(2) * CELL, CELL, CELL, Math.sqrt(2) * CELL, CELL, Math.sqrt(2) * CELL};

    static double stepLen(int i, int j) {
        if (j < 0) return CELL;
        int d = Math.abs(j - i);
        return d == 1 || d == N ? CELL : Math.sqrt(2) * CELL;
    }

    static double smooth(double t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }

    static long mix(long z) { return PlanNoise.mix(z); }

    double hash01(long k) { return PlanNoise.hash01(noiseSeed, k); }

    double noise(double x, double z, int salt) { return PlanNoise.noise(noiseSeed, x, z, salt); }

    double fbm(double x, double z, double wavelength, int octaves, double gain, int salt) {
        return PlanNoise.fbm(noiseSeed, x, z, wavelength, octaves, gain, salt);
    }

    /** Min-heap of (float key, int id) for the priority flood. */
    static final class FloatHeap {
        float[] key; int[] id; int size;
        FloatHeap(int cap) { key = new float[cap]; id = new int[cap]; }
        void push(float k, int v) {
            if (size == key.length) { key = Arrays.copyOf(key, size * 2); id = Arrays.copyOf(id, size * 2); }
            int i = size++;
            while (i > 0) { int p = (i - 1) >> 1; if (key[p] <= k) break; key[i] = key[p]; id[i] = id[p]; i = p; }
            key[i] = k; id[i] = v;
        }
        float lastKey;
        int popId() {
            int top = id[0];
            lastKey = key[0];
            float k = key[--size];
            int v = id[size];
            int i = 0;
            while (true) {
                int l = 2 * i + 1;
                if (l >= size) break;
                int m = l + 1 < size && key[l + 1] < key[l] ? l + 1 : l;
                if (key[m] >= k) break;
                key[i] = key[m]; id[i] = id[m]; i = m;
            }
            key[i] = k; id[i] = v;
            return top;
        }
    }
}
