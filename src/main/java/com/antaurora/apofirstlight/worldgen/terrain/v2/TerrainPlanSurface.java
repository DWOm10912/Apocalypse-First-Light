package com.antaurora.apofirstlight.worldgen.terrain.v2;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;

/**
 * The compact, immutable runtime form of a Terrain V2 plan (Phase 2, 2026-10-10): what world generation reads, nothing
 * else (about 20 MB for the 1408^2 grid instead of the planner's 120 MB working set). Pure Java, thread-safe.
 * <ul>
 *   <li>{@link #heightAt}: the planned surface Y at any point, the same formula as the research exports
 *   (Catmull-Rom of the 16 m plan plus 48 / 11 / 4 m detail), so the game shows the accepted maps;</li>
 *   <li>{@link #waterClass}: 0 land, 1 macro sea, 2 drowned valley (estuary), 3 tidal marsh - the unified water mask
 *   (MacroGeography's sea plus the estuaries the plan drowns); 4 lake (federal plans only, C2);</li>
 *   <li>{@link #stableDepth}: blocks below the surface kept free of cave openings and carvers (plains / coastal plain
 *   12, foothills 6, fold belt 4, water floors 6);</li>
 *   <li>{@link #shoreDistance}: signed metres to the unified water (land +, water -), clamped to +-127;</li>
 *   <li>{@link #rivers}: the river water of the r1 network (Phase 2b, RiverNetwork) and {@link #poolAt}, the open
 *   water of the tidal marsh. Both are applied by RiverCarver after the noise fill; {@link #heightAt} stays the r1
 *   surface.</li>
 *   <li>Shore warp (Phase 2b fix, 2026-10-10; open coast added in the ecology stage): within two cells of a land /
 *   water boundary (estuary, marsh, open sea) every query
 *   reads the plan through a smooth domain warp of up to about 12 m (two gradient noise octaves, 120 and 36 m, warp
 *   gradient under 0.35), so the estuary and marsh shores no longer run along the
 *   16 m cells (= chunk borders); heights, water mask, stable depth and pools all read the same warped point. The
 *   sub-grid detail fades with the bilinear share of open-water cells instead of switching off at a water cell, so
 *   there is no half-block step on a cell border. Away from estuaries and marsh nothing changes (r1 heights).</li>
 *   <li>Grid (C2, 2026-10-14; docs/worldgen/federal_six_state_c2.md): every surface carries its own grid
 *   ({@link #gridN} cells of {@link #CELL} m from {@link #gridOrigin}); the r1 surface keeps the r1 grid, the federal
 *   surface has a larger one. The static {@link #N} / {@link #ORIGIN} / {@link #idx} are the r1 grid's, kept for the
 *   offline r1 tools.</li>
 *   <li>Federal layers ({@link Layers}, C2; null on an r1 surface, so every r1 query is unchanged): lake water tops
 *   (water class 4), the plateau terrace weight (benches and cliffs at runtime) and the mountain rock detail.</li>
 * </ul>
 */
public final class TerrainPlanSurface {
    public static final int FORMAT = 4;                // 2: + rivers; 3: shore warp (Phase 2b); 4: + ecology zones (2026-10-10)
    /** Cache layout of a surface with federal layers (C2): FORMAT 4 plus its grid and the layers. r1 keeps FORMAT. */
    public static final int FORMAT_LAYERS = 5;
    public static final int N = TerrainPlanV2.N;
    public static final double CELL = TerrainPlanV2.CELL, ORIGIN = TerrainPlanV2.ORIGIN;
    /** Water class of a lake cell (federal plans only). */
    public static final int LAKE = 4;

    /**
     * The federal plan's extra per-cell layers (C2). lakeTop: the top water block of the lake a class-4 cell belongs
     * to (Short.MIN_VALUE elsewhere); terrace: plateau terrace weight 0..255; rough: mountain rock detail weight 0..255;
     * open: how open the country is, read by the ecology: 0 = wooded hill country (woodlots cover most of the till),
     * 128 = r1's woodlot share, 255 = open plains with few woodlots (value o = byte / 127.5 - 1, from -1 to 1).
     */
    public record Layers(short[] lakeTop, byte[] terrace, byte[] rough, byte[] open) {}

    public final String version;
    public final long seed;
    final long noiseSeed;
    /** This surface's grid: gridN x gridN cells of CELL m from gridOrigin. */
    public final int gridN;
    public final double gridOrigin;
    final float[] h;
    final byte[] water;
    final float[] belt;
    final byte[] stable;
    final byte[] shore;
    public final RiverNetwork rivers;
    /** The r1 ecology zones and HAND per cell (EcologyPlan; ecology stage), null on a bare build surface. */
    public final EcologyPlan.Zones ecology;
    /** Federal layers, null on an r1 surface. */
    public final Layers layers;
    /** Shore warp weight per cell (0 / 1, read bilinearly), derived from the water mask. */
    final float[] warpW;
    /** Open-coast smoothing (ecology stage): the 5 x 5 cell mean of h near the open sea, and its weight per cell. */
    final float[] hCoast, coastW;

    TerrainPlanSurface(String version, long seed, long noiseSeed, int gridN, double gridOrigin, float[] h, byte[] water,
                       float[] belt, byte[] stable, byte[] shore, RiverNetwork rivers, EcologyPlan.Zones ecology, Layers layers) {
        this.version = version;
        this.seed = seed;
        this.noiseSeed = noiseSeed;
        this.gridN = gridN;
        this.gridOrigin = gridOrigin;
        this.h = h;
        this.water = water;
        this.belt = belt;
        this.stable = stable;
        this.shore = shore;
        this.rivers = rivers;
        this.ecology = ecology;
        this.layers = layers;
        this.warpW = warpWeights(water);
        float[][] coast = coastSmoothing(h, water);
        this.hCoast = coast[0];
        this.coastW = coast[1];
    }

    static final int WARP_REACH = 2;
    static final double WARP_A1 = 14, WARP_L1 = 120, WARP_A2 = 4, WARP_L2 = 36;

    /** 1 for cells within WARP_REACH of a land / water boundary: estuaries, marsh and (since the ecology stage) the open
     *  coast too, whose sea cells drew a 16 m staircase along every diagonal shore (the sea fill and the biomes follow the
     *  planned ground, so warping it is safe). Lakes (federal) count as water. */
    float[] warpWeights(byte[] water) {
        float[] w = new float[gridN * gridN];
        for (int r = 0; r < gridN; r++) for (int c = 0; c < gridN; c++) {
            boolean wet = false, dry = false;
            for (int dr = -WARP_REACH; dr <= WARP_REACH; dr++) for (int dc = -WARP_REACH; dc <= WARP_REACH; dc++) {
                int k = water[cellIdx(clampCell(c + dc), clampCell(r + dr))];
                if (k != 0) wet = true;
                if (k == 0 || k == 3) dry = true;
            }
            w[cellIdx(c, r)] = wet && dry ? 1 : 0;
        }
        return w;
    }

    /**
     * Open-coast smoothing (2026-10-10, ecology stage): the macro sea's 16 m cells meet the coastal land in a staircase,
     * and the planned shore traced it (steps about 50 m long on every diagonal coast, a sawtooth beach). Within two
     * cells of both open sea and land the plan surface blends to the 5 x 5 cell (80 m) mean of h, so the shore becomes a
     * gentle, smooth slope; estuaries and marsh keep their own surface (they have the shore warp), inland is unchanged.
     */
    float[][] coastSmoothing(float[] h, byte[] water) {
        int n = gridN * gridN;
        boolean[] sea = new boolean[n], land = new boolean[n];
        for (int i = 0; i < n; i++) { sea[i] = water[i] == 1; land[i] = water[i] == 0 || water[i] == 3; }
        boolean[] near2 = and(dilate(sea, 2), dilate(land, 2)), near4 = and(dilate(sea, 4), dilate(land, 4));
        float[] hs = h.clone(), w = new float[n];
        for (int r = 0; r < gridN; r++) for (int c = 0; c < gridN; c++) {
            int i = cellIdx(c, r);
            if (near2[i]) w[i] = 1;
            if (!near4[i]) continue;
            double sum = 0;
            for (int dr = -2; dr <= 2; dr++) for (int dc = -2; dc <= 2; dc++) sum += h[cellIdx(clampCell(c + dc), clampCell(r + dr))];
            hs[i] = (float) (sum / 25);
        }
        return new float[][]{hs, w};
    }

    boolean[] dilate(boolean[] m, int rad) {
        boolean[] a = new boolean[m.length], b = new boolean[m.length];
        for (int r = 0; r < gridN; r++) for (int c = 0; c < gridN; c++) {
            boolean v = false;
            for (int d = -rad; d <= rad && !v; d++) v = m[cellIdx(clampCell(c + d), r)];
            a[cellIdx(c, r)] = v;
        }
        for (int r = 0; r < gridN; r++) for (int c = 0; c < gridN; c++) {
            boolean v = false;
            for (int d = -rad; d <= rad && !v; d++) v = a[cellIdx(c, clampCell(r + d))];
            b[cellIdx(c, r)] = v;
        }
        return b;
    }

    static boolean[] and(boolean[] a, boolean[] b) {
        boolean[] o = new boolean[a.length];
        for (int i = 0; i < a.length; i++) o[i] = a[i] && b[i];
        return o;
    }

    /** The plan grid's surface at grid coordinates, the open-coast smoothing blended in. */
    private double gridHeight(double gx, double gz) {
        double y = baseHeight(h, gx, gz);
        int c0 = (int) Math.floor(gx), r0 = (int) Math.floor(gz);
        double fx = gx - c0, fz = gz - r0, cw = 0;
        for (int b = 0; b < 2; b++) for (int a = 0; a < 2; a++)
            cw += coastW[cellIdx(clampCell(c0 + a), clampCell(r0 + b))] * (a == 0 ? 1 - fx : fx) * (b == 0 ? 1 - fz : fz);
        if (cw <= 0) return y;
        cw = cw * cw * (3 - 2 * cw);
        return y + (baseHeight(hCoast, gx, gz) - y) * cw;
    }

    /** The shore-warp offset of a block column (cached per worker thread), {dx, dz} in m. */
    private float[] warp(double x, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        WarpCache c = WARP.get();
        int i = (bx & 15) | ((bz & 15) << 4);
        long key = ((long) bx << 32) ^ (bz & 0xffffffffL);
        if (c.owner[i] != this || c.key[i] != key) {
            c.owner[i] = this;
            c.key[i] = key;
            double gx = (bx + 0.5 - gridOrigin) / CELL - 0.5, gz = (bz + 0.5 - gridOrigin) / CELL - 0.5;
            int c0 = (int) Math.floor(gx), r0 = (int) Math.floor(gz);
            double fx = gx - c0, fz = gz - r0, wt = 0;
            for (int b = 0; b < 2; b++) for (int a = 0; a < 2; a++)
                wt += warpW[cellIdx(clampCell(c0 + a), clampCell(r0 + b))] * (a == 0 ? 1 - fx : fx) * (b == 0 ? 1 - fz : fz);
            if (wt <= 0) { c.dx[i] = 0; c.dz[i] = 0; }
            else {
                wt = wt * wt * (3 - 2 * wt);
                double px = bx + 0.5, pz = bz + 0.5;
                c.dx[i] = (float) (wt * (WARP_A1 * PlanNoise.noise(noiseSeed, px / WARP_L1, pz / WARP_L1, 501)
                        + WARP_A2 * PlanNoise.noise(noiseSeed, px / WARP_L2, pz / WARP_L2, 503)));
                c.dz[i] = (float) (wt * (WARP_A1 * PlanNoise.noise(noiseSeed, px / WARP_L1, pz / WARP_L1, 502)
                        + WARP_A2 * PlanNoise.noise(noiseSeed, px / WARP_L2, pz / WARP_L2, 504)));
            }
        }
        c.out[0] = c.dx[i];
        c.out[1] = c.dz[i];
        return c.out;
    }

    private static final ThreadLocal<WarpCache> WARP = ThreadLocal.withInitial(WarpCache::new);

    private static final class WarpCache {
        final long[] key = new long[256];
        final float[] dx = new float[256], dz = new float[256], out = new float[2];
        final TerrainPlanSurface[] owner = new TerrainPlanSurface[256];
    }

    /** The (shore-warped) plan cell index of a point, for tools outside the package. */
    public static int cellIndex(TerrainPlanSurface s, double x, double z) { return s.cellAt(x, z); }

    /** The plan cell index a point reads (shore-warped). */
    int cellAt(double x, double z) {
        float[] w = warp(x, z);
        return cellIdx(cellOfCoord(x + w[0]), cellOfCoord(z + w[1]));
    }

    TerrainPlanSurface withRivers(RiverNetwork r, EcologyPlan.Zones e) {
        return new TerrainPlanSurface(version, seed, noiseSeed, gridN, gridOrigin, h, water, belt, stable, shore, r, e, layers);
    }

    /** r1 grid helpers (static, for the offline r1 tools); the queries use this surface's own grid. */
    static int idx(int col, int row) { return row * N + col; }

    static int clampI(int v) { return Math.max(0, Math.min(N - 1, v)); }

    static int cellOf(double coord) { return clampI((int) Math.floor((coord - ORIGIN) / CELL)); }

    public int cellIdx(int col, int row) { return row * gridN + col; }

    public int clampCell(int v) { return Math.max(0, Math.min(gridN - 1, v)); }

    /** The (clamped) column or row of this grid that holds a coordinate. */
    public int cellOfCoord(double coord) { return clampCell((int) Math.floor((coord - gridOrigin) / CELL)); }

    /** Centre coordinate (x or z) of a column or row of this grid. */
    public double cellCentre(int col) { return gridOrigin + (col + 0.5) * CELL; }

    /** True for a surface with federal layers (C2). */
    public boolean federal() { return layers != null; }

    /** The planned surface (top of the ground) at a point. */
    public double heightAt(double x, double z) {
        float[] w = warp(x, z);
        double gx = (x + w[0] - gridOrigin) / CELL - 0.5, gz = (z + w[1] - gridOrigin) / CELL - 0.5;
        double y = gridHeight(gx, gz);
        if (layers != null) y = terraced(y, gx, gz, x, z);
        int i = cellIdx(clampCell((int) Math.round(gx)), clampCell((int) Math.round(gz)));
        // the detail fades with the bilinear share of open-water cells (r1: switched off inside a water cell, which left
        // a step of up to a block on the cell border); identical to r1 wherever no open water is among the 4 cells
        int c0 = (int) Math.floor(gx), r0 = (int) Math.floor(gz);
        double fx = gx - c0, fz = gz - r0, wet = 0;
        for (int b = 0; b < 2; b++) for (int a = 0; a < 2; a++) {
            int k = water[cellIdx(clampCell(c0 + a), clampCell(r0 + b))];
            if (k == 1 || k == 2 || k == LAKE) wet += (a == 0 ? 1 - fx : fx) * (b == 0 ? 1 - fz : fz);
        }
        if (wet >= 0.999) return y;
        double steep = Math.min(1, slopeAt(i) / 0.25);
        double amp = (0.20 + 0.45 * steep + 0.20 * belt[i]) * (1 - wet);
        y += amp * PlanNoise.fbm(noiseSeed, x, z, 48, 2, 0.5, 91) + 0.25 * amp * PlanNoise.fbm(noiseSeed, x, z, 11, 2, 0.5, 92)
                + 0.05 * amp * PlanNoise.fbm(noiseSeed, x, z, 4, 1, 0.5, 93);
        if (layers != null && layers.rough != null) {
            // federal mountains (C2): crags and ledges on the rock, ridged noise (sharp crests, rounded hollows)
            double rw = bilinear(layers.rough, gx, gz) / 255.0;
            if (rw > 0.004) {
                double crag = 1 - Math.abs(PlanNoise.noise(noiseSeed, x / 38, z / 38, 95) * 1.6);
                y += rw * (1 - wet) * (2.6 * (crag * crag - 0.45) + 0.9 * PlanNoise.fbm(noiseSeed, x, z, 14, 2, 0.5, 96));
            }
        }
        return y;
    }

    /** The plan grid's own surface without sub-grid detail (cheap: carver checks, spawn search). */
    public double baseHeightAt(double x, double z) {
        float[] w = warp(x, z);
        double gx = (x + w[0] - gridOrigin) / CELL - 0.5, gz = (z + w[1] - gridOrigin) / CELL - 0.5;
        double y = gridHeight(gx, gz);
        return layers == null ? y : terraced(y, gx, gz, x, z);
    }

    /**
     * Federal plateau terraces (C2): the smooth planned surface mapped onto benches and cliffs where the terrace weight
     * is set. Layers 7.5..12.5 m thick (a 900 m noise), each a gentle bench rising 12 % of the layer and a cliff over the
     * last 26 % of the ramp; monotone in y, so drainage and river levels keep their direction. Applied before the
     * detail, so the detail never flips a column between two benches.
     */
    private double terraced(double y, double gx, double gz, double x, double z) {
        if (layers.terrace == null) return y;
        double tw = bilinear(layers.terrace, gx, gz) / 255.0;
        if (tw <= 0.004) return y;
        double thick = 10 + 2.5 * Math.max(-1, Math.min(1, 1.5 * PlanNoise.noise(noiseSeed, x / 900, z / 900, 97)));
        double phase = 3 * PlanNoise.noise(noiseSeed, x / 1400, z / 1400, 98);
        double u = (y - 64 - phase) / thick, k = Math.floor(u), f = u - k;
        final double cliff = 0.26, bench = 0.12;
        double g = f < 1 - cliff ? bench * f / (1 - cliff) : bench + (1 - bench) * smooth01((f - (1 - cliff)) / cliff);
        double yt = 64 + phase + thick * (k + g);
        tw = tw * tw * (3 - 2 * tw);
        return y + (yt - y) * tw;
    }

    private static double smooth01(double t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }

    /** Bilinear read of an unsigned byte layer at grid coordinates. */
    double bilinear(byte[] layer, double gx, double gz) {
        int c0 = (int) Math.floor(gx), r0 = (int) Math.floor(gz);
        double fx = gx - c0, fz = gz - r0, v = 0;
        for (int b = 0; b < 2; b++) for (int a = 0; a < 2; a++)
            v += (layer[cellIdx(clampCell(c0 + a), clampCell(r0 + b))] & 0xff) * (a == 0 ? 1 - fx : fx) * (b == 0 ? 1 - fz : fz);
        return v;
    }

    private static double baseHeight(float[] h, int gridN, double gx, double gz) {
        int c0 = (int) Math.floor(gx), r0 = (int) Math.floor(gz);
        double fx = gx - c0, fz = gz - r0;
        double[] col = new double[4], row = new double[4];
        for (int a = -1; a <= 2; a++) {
            for (int b = -1; b <= 2; b++) {
                int cc = Math.max(0, Math.min(gridN - 1, c0 + b)), rr = Math.max(0, Math.min(gridN - 1, r0 + a));
                row[b + 1] = h[rr * gridN + cc];
            }
            col[a + 1] = cr(row, fx);
        }
        return cr(col, fz);
    }

    private double baseHeight(float[] h, double gx, double gz) { return baseHeight(h, gridN, gx, gz); }

    public int waterClass(double x, double z) { return water[cellAt(x, z)]; }

    /**
     * Whether open air under Y63 over the planned ground fills to sea level here (the one sea-fill rule: the generator's
     * water, the biome, the maps and the audits all ask this): sea and drowned-valley cells, and land or marsh within
     * 48 m of open water whose planned ground lies under Y63 (so the shore follows the ground's contour; 2b.1: the low
     * marsh edge next to an estuary used to stay dry at Y61 and left a water face).
     */
    public boolean seaFloodAt(double x, double z, double h) {
        int wc = waterClass(x, z);
        if (wc == 1 || wc == 2) return true;
        return h < 63 && shoreDistance(x, z) <= 48;
    }

    /** Open water at the surface: the macro sea or a drowned valley (marsh is land at Y63.4; lakes: {@link #lakeTopAt}). */
    public boolean waterAt(double x, double z) {
        int w = waterClass(x, z);
        return w == 1 || w == 2;
    }

    /**
     * The lake a column belongs to (federal plans, C2): the top water block of the lake cell with the largest bilinear
     * share among the four cells round the (shore-warped) point, or Integer.MIN_VALUE when none of them is a lake cell.
     * share[0] receives that share (0..1). RiverCarver fills the column's air up to it.
     */
    public int lakeTopAt(double x, double z, double[] share) {
        if (share != null) share[0] = 0;
        if (layers == null || layers.lakeTop == null) return Integer.MIN_VALUE;
        float[] w = warp(x, z);
        double gx = (x + w[0] - gridOrigin) / CELL - 0.5, gz = (z + w[1] - gridOrigin) / CELL - 0.5;
        int c0 = (int) Math.floor(gx), r0 = (int) Math.floor(gz);
        double fx = gx - c0, fz = gz - r0, total = 0, best = 0;
        int top = Integer.MIN_VALUE;
        for (int b = 0; b < 2; b++) for (int a = 0; a < 2; a++) {
            int i = cellIdx(clampCell(c0 + a), clampCell(r0 + b));
            if (water[i] != LAKE) continue;
            double wt = (a == 0 ? 1 - fx : fx) * (b == 0 ? 1 - fz : fz);
            total += wt;
            if (wt > best || top == Integer.MIN_VALUE) { best = wt; top = layers.lakeTop[i]; }
        }
        if (share != null) share[0] = total;
        return top;
    }

    /** The lake a point lies near (within two cells): its top water block, else Integer.MIN_VALUE (the rim rule). */
    public int lakeNear(double x, double z) {
        if (layers == null || layers.lakeTop == null) return Integer.MIN_VALUE;
        float[] w = warp(x, z);
        int c = cellOfCoord(x + w[0]), r = cellOfCoord(z + w[1]), top = Integer.MIN_VALUE;
        for (int dr = -2; dr <= 2; dr++) for (int dc = -2; dc <= 2; dc++) {
            int i = cellIdx(clampCell(c + dc), clampCell(r + dr));
            if (water[i] == LAKE) top = Math.max(top, layers.lakeTop[i]);
        }
        return top;
    }

    /**
     * The lowest planned lake bed (cell height) within two cells of a point (federal lakes, C2), +infinity when no lake
     * cell is near: carvers stay under it (RiverCarver.carveCeiling).
     */
    public double lakeFloorNear(double x, double z) {
        if (layers == null || layers.lakeTop == null) return Double.POSITIVE_INFINITY;
        float[] w = warp(x, z);
        int c = cellOfCoord(x + w[0]), r = cellOfCoord(z + w[1]);
        double low = Double.POSITIVE_INFINITY;
        for (int dr = -2; dr <= 2; dr++) for (int dc = -2; dc <= 2; dc++) {
            int i = cellIdx(clampCell(c + dc), clampCell(r + dr));
            if (water[i] == LAKE) low = Math.min(low, h[i]);
        }
        return low;
    }

    public int stableDepth(double x, double z) { return stable[cellAt(x, z)]; }

    /** Blocks below the planned surface that the density's hard stable floor keeps solid (PlanStabilityDensity, "hard"). */
    public static int hardDepth(int stable) { return Math.max(0, Math.min(stable, 2 * (stable - 6))); }

    /**
     * Open water in the tidal marsh (Phase 2b): pools and leads at sea level (top water block Y62) on the marsh flat
     * (top block Y63), about 40 % of the marsh interior and fewer toward its edge: the noise threshold rises smoothly
     * with the bilinear marsh share (shore-warped), so the pools never trace the 16 m grid. RiverCarver opens a pool
     * only where the generated top is exactly Y63.
     */
    public boolean poolAt(int x, int z) {
        float[] w = warp(x, z);
        double gx = (x + 0.5 + w[0] - gridOrigin) / CELL - 0.5, gz = (z + 0.5 + w[1] - gridOrigin) / CELL - 0.5;
        int c0 = (int) Math.floor(gx), r0 = (int) Math.floor(gz);
        double fx = gx - c0, fz = gz - r0;
        double m = 0;
        for (int b = 0; b < 2; b++) for (int a = 0; a < 2; a++)
            if (water[cellIdx(clampCell(c0 + a), clampCell(r0 + b))] == 3) m += (a == 0 ? 1 - fx : fx) * (b == 0 ? 1 - fz : fz);
        if (m < POOL_MARSH) return false;
        // the threshold rises smoothly with the marsh share (no hard m cut: inside a cell the bilinear share's
        // iso-lines are straight, and a hard cut drew straight pool edges; Phase 2b fix)
        double t = (m - POOL_MARSH) / (1 - POOL_MARSH);
        t = t * t * (3 - 2 * t);
        double f = PlanNoise.fbm(noiseSeed, x, z, 30, 2, 0.5, 401) + 0.5 * PlanNoise.fbm(noiseSeed, x, z, 9, 2, 0.5, 402);
        return f < POOL_EDGE + (POOL_CORE - POOL_EDGE) * t;
    }

    static final double POOL_MARSH = 0.2, POOL_EDGE = -2.2, POOL_CORE = 0.1;

    public int shoreDistance(double x, double z) { return shore[cellAt(x, z)]; }

    /** Plan-grid slope (rise / run) at a cell. */
    double slopeAt(int i) {
        int c = i % gridN, r = i / gridN;
        if (c < 1 || r < 1 || c > gridN - 2 || r > gridN - 2) return 0;
        double gx = (h[i + 1] - h[i - 1]) / (2 * CELL), gz = (h[i + gridN] - h[i - gridN]) / (2 * CELL);
        return Math.hypot(gx, gz);
    }

    public double slopeAt(double x, double z) { return slopeAt(cellAt(x, z)); }

    public float beltWeight(double x, double z) { return belt[cellAt(x, z)]; }

    /**
     * The natural-land spawn (TerrainV2SpawnEvents): the plan cell nearest the origin, ring by ring on the 16 m grid
     * out to maxRadius, that is dry plain / coastal plain (stable depth 12), at least 64 m from water, grade at most
     * 1/32, not marsh, not on a river or its graded banks, Y >= 65, with level dry ground for 32 m round it.
     * Deterministic; null when nothing fits.
     */
    public int[] naturalSpawn(int maxRadius) {
        return naturalSpawn(0, 0, maxRadius, 12);
    }

    /**
     * The same search round any centre (C2: the federal start region), with the stable depth the spawn needs (r1 12;
     * the federal start lies in the forested hills, where the hard floor is shallower).
     */
    public int[] naturalSpawn(int centreX, int centreZ, int maxRadius, int minStable) {
        int step = (int) CELL;
        for (int ring = 0; ring * step <= maxRadius; ring++) {
            int[] best = null;
            double bestD = Double.MAX_VALUE;
            for (int dx = -ring; dx <= ring; dx++) for (int dz = -ring; dz <= ring; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                int x = centreX + dx * step + step / 2, z = centreZ + dz * step + step / 2;
                if (!spawnSuitable(x, z, minStable)) continue;
                double d = (double) (x - centreX) * (x - centreX) + (double) (z - centreZ) * (z - centreZ);
                if (d < bestD) { bestD = d; best = new int[]{x, z}; }
            }
            if (best != null) return best;
        }
        return null;
    }

    boolean spawnSuitable(int x, int z) { return spawnSuitable(x, z, 12); }

    boolean spawnSuitable(int x, int z, int minStable) {
        if (waterClass(x, z) != 0 || stableDepth(x, z) < minStable || shoreDistance(x, z) < 64 || slopeAt(x, z) > 1.0 / 32) return false;
        if (rivers != null && rivers.edgeDistance(x, z) < RiverNetwork.BANK_REACH + 2) return false;
        if (lakeNear(x, z) != Integer.MIN_VALUE) return false;
        double h0 = baseHeightAt(x, z);
        for (int k = -2; k <= 2; k++) for (int j = -2; j <= 2; j++) {
            int xx = x + k * 16, zz = z + j * 16;
            if (waterClass(xx, zz) != 0 || Math.abs(baseHeightAt(xx, zz) - h0) > 2.5) return false;
        }
        return h0 >= 65;
    }

    static double cr(double[] p, double t) {
        return p[1] + 0.5 * t * (p[2] - p[0] + t * (2 * p[0] - 5 * p[1] + 4 * p[2] - p[3] + t * (3 * (p[1] - p[2]) + p[3] - p[0])));
    }

    // ------------------------------------------------------------------ persistence (the world-load cache)
    private static final int MAGIC = 0x41464C54;   // "AFLT"

    public void write(DataOutputStream out) throws IOException {
        CRC32 crc = checksum();
        out.writeInt(MAGIC);
        out.writeInt(layers == null ? FORMAT : FORMAT_LAYERS);
        out.writeUTF(version);
        out.writeLong(seed);
        out.writeLong(noiseSeed);
        out.writeInt(gridN);
        if (layers != null) out.writeDouble(gridOrigin);
        out.writeLong(crc.getValue());
        for (float v : h) out.writeFloat(v);
        out.write(water);
        for (float v : belt) out.writeFloat(v);
        out.write(stable);
        out.write(shore);
        rivers.write(out);
        out.write(ecology.zone());
        out.write(ecology.hand());
        if (layers != null) {
            for (short v : layers.lakeTop) out.writeShort(v);
            out.write(layers.terrace);
            out.write(layers.rough);
            out.write(layers.open);
        }
    }

    /** Reads a cached r1 surface; null when it is for another seed / version / format or does not check out. */
    public static TerrainPlanSurface read(DataInputStream in, long seed, String version) throws IOException {
        return read(in, seed, version, N, ORIGIN);
    }

    /** Reads a cached surface on the given grid (r1: FORMAT, federal: FORMAT_LAYERS); null when anything differs. */
    public static TerrainPlanSurface read(DataInputStream in, long seed, String version, int gridN, double gridOrigin) throws IOException {
        if (in.readInt() != MAGIC) return null;
        int format = in.readInt();
        if (format != FORMAT && format != FORMAT_LAYERS) return null;
        String v = in.readUTF();
        long s = in.readLong(), noiseSeed = in.readLong();
        if (!v.equals(version) || s != seed || in.readInt() != gridN) return null;
        if (format == FORMAT_LAYERS && in.readDouble() != gridOrigin) return null;
        if (format == FORMAT && gridOrigin != ORIGIN) return null;
        long want = in.readLong();
        int n = gridN * gridN;
        float[] h = new float[n], belt = new float[n];
        byte[] water = new byte[n], stable = new byte[n], shore = new byte[n];
        for (int i = 0; i < n; i++) h[i] = in.readFloat();
        in.readFully(water);
        for (int i = 0; i < n; i++) belt[i] = in.readFloat();
        in.readFully(stable);
        in.readFully(shore);
        RiverNetwork rivers = RiverNetwork.read(in);
        byte[] zone = new byte[n], hand = new byte[n];
        in.readFully(zone);
        in.readFully(hand);
        Layers layers = null;
        if (format == FORMAT_LAYERS) {
            short[] lakeTop = new short[n];
            for (int i = 0; i < n; i++) lakeTop[i] = in.readShort();
            byte[] terrace = new byte[n], rough = new byte[n], open = new byte[n];
            in.readFully(terrace);
            in.readFully(rough);
            in.readFully(open);
            layers = new Layers(lakeTop, terrace, rough, open);
        }
        TerrainPlanSurface t = new TerrainPlanSurface(v, s, noiseSeed, gridN, gridOrigin, h, water, belt, stable, shore, rivers,
                new EcologyPlan.Zones(zone, hand), layers);
        return t.checksum().getValue() == want ? t : null;
    }

    private CRC32 checksum() {
        CRC32 crc = new CRC32();
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(8 * gridN);
        for (int r = 0; r < gridN; r++) {
            b.clear();
            for (int c = 0; c < gridN; c++) { b.putFloat(h[r * gridN + c]); b.putFloat(belt[r * gridN + c]); }
            crc.update(b.array(), 0, b.position());
        }
        crc.update(water);
        crc.update(stable);
        crc.update(shore);
        if (rivers != null) rivers.checksum(crc);
        if (ecology != null) { crc.update(ecology.zone()); crc.update(ecology.hand()); }
        if (layers != null) {
            java.nio.ByteBuffer s = java.nio.ByteBuffer.allocate(2 * gridN);
            for (int r = 0; r < gridN; r++) {
                s.clear();
                for (int c = 0; c < gridN; c++) s.putShort(layers.lakeTop[r * gridN + c]);
                crc.update(s.array(), 0, s.position());
            }
            crc.update(layers.terrace);
            crc.update(layers.rough);
            crc.update(layers.open);
        }
        return crc;
    }
}
