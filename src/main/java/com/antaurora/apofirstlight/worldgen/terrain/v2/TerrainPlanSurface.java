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
 *   (MacroGeography's sea plus the estuaries the plan drowns);</li>
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
 * </ul>
 */
public final class TerrainPlanSurface {
    public static final int FORMAT = 4;                // 2: + rivers; 3: shore warp (Phase 2b); 4: + ecology zones (2026-10-10)
    public static final int N = TerrainPlanV2.N;
    public static final double CELL = TerrainPlanV2.CELL, ORIGIN = TerrainPlanV2.ORIGIN;

    public final String version;
    public final long seed;
    final long noiseSeed;
    final float[] h;
    final byte[] water;
    final float[] belt;
    final byte[] stable;
    final byte[] shore;
    public final RiverNetwork rivers;
    /** The r1 ecology zones and HAND per cell (EcologyPlan; ecology stage), null on a bare build surface. */
    public final EcologyPlan.Zones ecology;
    /** Shore warp weight per cell (0 / 1, read bilinearly), derived from the water mask. */
    final float[] warpW;
    /** Open-coast smoothing (ecology stage): the 5 x 5 cell mean of h near the open sea, and its weight per cell. */
    final float[] hCoast, coastW;

    TerrainPlanSurface(String version, long seed, long noiseSeed, float[] h, byte[] water, float[] belt, byte[] stable, byte[] shore,
                       RiverNetwork rivers, EcologyPlan.Zones ecology) {
        this.version = version;
        this.seed = seed;
        this.noiseSeed = noiseSeed;
        this.h = h;
        this.water = water;
        this.belt = belt;
        this.stable = stable;
        this.shore = shore;
        this.rivers = rivers;
        this.ecology = ecology;
        this.warpW = warpWeights(water);
        float[][] coast = coastSmoothing(h, water);
        this.hCoast = coast[0];
        this.coastW = coast[1];
    }

    static final int WARP_REACH = 2;
    static final double WARP_A1 = 14, WARP_L1 = 120, WARP_A2 = 4, WARP_L2 = 36;

    /** 1 for cells within WARP_REACH of a land / water boundary: estuaries, marsh and (since the ecology stage) the open
     *  coast too, whose sea cells drew a 16 m staircase along every diagonal shore (the sea fill and the biomes follow the
     *  planned ground, so warping it is safe). */
    static float[] warpWeights(byte[] water) {
        float[] w = new float[N * N];
        for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) {
            boolean wet = false, dry = false;
            for (int dr = -WARP_REACH; dr <= WARP_REACH; dr++) for (int dc = -WARP_REACH; dc <= WARP_REACH; dc++) {
                int k = water[idx(clampI(c + dc), clampI(r + dr))];
                if (k != 0) wet = true;
                if (k == 0 || k == 3) dry = true;
            }
            w[idx(c, r)] = wet && dry ? 1 : 0;
        }
        return w;
    }

    /**
     * Open-coast smoothing (2026-10-10, ecology stage): the macro sea's 16 m cells meet the coastal land in a staircase,
     * and the planned shore traced it (steps about 50 m long on every diagonal coast, a sawtooth beach). Within two
     * cells of both open sea and land the plan surface blends to the 5 x 5 cell (80 m) mean of h, so the shore becomes a
     * gentle, smooth slope; estuaries and marsh keep their own surface (they have the shore warp), inland is unchanged.
     */
    static float[][] coastSmoothing(float[] h, byte[] water) {
        int n = N * N;
        boolean[] sea = new boolean[n], land = new boolean[n];
        for (int i = 0; i < n; i++) { sea[i] = water[i] == 1; land[i] = water[i] == 0 || water[i] == 3; }
        boolean[] near2 = and(dilate(sea, 2), dilate(land, 2)), near4 = and(dilate(sea, 4), dilate(land, 4));
        float[] hs = h.clone(), w = new float[n];
        for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) {
            int i = idx(c, r);
            if (near2[i]) w[i] = 1;
            if (!near4[i]) continue;
            double sum = 0;
            for (int dr = -2; dr <= 2; dr++) for (int dc = -2; dc <= 2; dc++) sum += h[idx(clampI(c + dc), clampI(r + dr))];
            hs[i] = (float) (sum / 25);
        }
        return new float[][]{hs, w};
    }

    static boolean[] dilate(boolean[] m, int rad) {
        boolean[] a = new boolean[m.length], b = new boolean[m.length];
        for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) {
            boolean v = false;
            for (int d = -rad; d <= rad && !v; d++) v = m[idx(clampI(c + d), r)];
            a[idx(c, r)] = v;
        }
        for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) {
            boolean v = false;
            for (int d = -rad; d <= rad && !v; d++) v = a[idx(c, clampI(r + d))];
            b[idx(c, r)] = v;
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
            cw += coastW[idx(clampI(c0 + a), clampI(r0 + b))] * (a == 0 ? 1 - fx : fx) * (b == 0 ? 1 - fz : fz);
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
            double gx = (bx + 0.5 - ORIGIN) / CELL - 0.5, gz = (bz + 0.5 - ORIGIN) / CELL - 0.5;
            int c0 = (int) Math.floor(gx), r0 = (int) Math.floor(gz);
            double fx = gx - c0, fz = gz - r0, wt = 0;
            for (int b = 0; b < 2; b++) for (int a = 0; a < 2; a++)
                wt += warpW[idx(clampI(c0 + a), clampI(r0 + b))] * (a == 0 ? 1 - fx : fx) * (b == 0 ? 1 - fz : fz);
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
        return idx(cellOf(x + w[0]), cellOf(z + w[1]));
    }

    TerrainPlanSurface withRivers(RiverNetwork r, EcologyPlan.Zones e) {
        return new TerrainPlanSurface(version, seed, noiseSeed, h, water, belt, stable, shore, r, e);
    }

    static int idx(int col, int row) { return row * N + col; }

    static int clampI(int v) { return Math.max(0, Math.min(N - 1, v)); }

    static int cellOf(double coord) { return clampI((int) Math.floor((coord - ORIGIN) / CELL)); }

    /** The planned surface (top of the ground) at a point. */
    public double heightAt(double x, double z) {
        float[] w = warp(x, z);
        double gx = (x + w[0] - ORIGIN) / CELL - 0.5, gz = (z + w[1] - ORIGIN) / CELL - 0.5;
        double y = gridHeight(gx, gz);
        int i = idx(clampI((int) Math.round(gx)), clampI((int) Math.round(gz)));
        // the detail fades with the bilinear share of open-water cells (r1: switched off inside a water cell, which left
        // a step of up to a block on the cell border); identical to r1 wherever no open water is among the 4 cells
        int c0 = (int) Math.floor(gx), r0 = (int) Math.floor(gz);
        double fx = gx - c0, fz = gz - r0, wet = 0;
        for (int b = 0; b < 2; b++) for (int a = 0; a < 2; a++) {
            int k = water[idx(clampI(c0 + a), clampI(r0 + b))];
            if (k == 1 || k == 2) wet += (a == 0 ? 1 - fx : fx) * (b == 0 ? 1 - fz : fz);
        }
        if (wet >= 0.999) return y;
        double steep = Math.min(1, slopeAt(i) / 0.25);
        double amp = (0.20 + 0.45 * steep + 0.20 * belt[i]) * (1 - wet);
        y += amp * PlanNoise.fbm(noiseSeed, x, z, 48, 2, 0.5, 91) + 0.25 * amp * PlanNoise.fbm(noiseSeed, x, z, 11, 2, 0.5, 92)
                + 0.05 * amp * PlanNoise.fbm(noiseSeed, x, z, 4, 1, 0.5, 93);
        return y;
    }

    /** The plan grid's own surface without sub-grid detail (cheap: carver checks, spawn search). */
    public double baseHeightAt(double x, double z) {
        float[] w = warp(x, z);
        return gridHeight((x + w[0] - ORIGIN) / CELL - 0.5, (z + w[1] - ORIGIN) / CELL - 0.5);
    }

    private static double baseHeight(float[] h, double gx, double gz) {
        int c0 = (int) Math.floor(gx), r0 = (int) Math.floor(gz);
        double fx = gx - c0, fz = gz - r0;
        double[] col = new double[4], row = new double[4];
        for (int a = -1; a <= 2; a++) {
            for (int b = -1; b <= 2; b++) row[b + 1] = h[idx(clampI(c0 + b), clampI(r0 + a))];
            col[a + 1] = cr(row, fx);
        }
        return cr(col, fz);
    }

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

    /** Open water at the surface: the macro sea or a drowned valley (marsh is land at Y63.4). */
    public boolean waterAt(double x, double z) {
        int w = waterClass(x, z);
        return w == 1 || w == 2;
    }

    public int stableDepth(double x, double z) { return stable[cellAt(x, z)]; }

    /**
     * Open water in the tidal marsh (Phase 2b): pools and leads at sea level (top water block Y62) on the marsh flat
     * (top block Y63), about 40 % of the marsh interior and fewer toward its edge: the noise threshold rises smoothly
     * with the bilinear marsh share (shore-warped), so the pools never trace the 16 m grid. RiverCarver opens a pool
     * only where the generated top is exactly Y63.
     */
    public boolean poolAt(int x, int z) {
        float[] w = warp(x, z);
        double gx = (x + 0.5 + w[0] - ORIGIN) / CELL - 0.5, gz = (z + 0.5 + w[1] - ORIGIN) / CELL - 0.5;
        int c0 = (int) Math.floor(gx), r0 = (int) Math.floor(gz);
        double fx = gx - c0, fz = gz - r0;
        double m = 0;
        for (int b = 0; b < 2; b++) for (int a = 0; a < 2; a++)
            if (water[idx(clampI(c0 + a), clampI(r0 + b))] == 3) m += (a == 0 ? 1 - fx : fx) * (b == 0 ? 1 - fz : fz);
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
        int c = i % N, r = i / N;
        if (c < 1 || r < 1 || c > N - 2 || r > N - 2) return 0;
        double gx = (h[i + 1] - h[i - 1]) / (2 * CELL), gz = (h[i + N] - h[i - N]) / (2 * CELL);
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
        int step = (int) CELL;
        for (int ring = 0; ring * step <= maxRadius; ring++) {
            int[] best = null;
            double bestD = Double.MAX_VALUE;
            for (int dx = -ring; dx <= ring; dx++) for (int dz = -ring; dz <= ring; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                int x = dx * step + step / 2, z = dz * step + step / 2;
                if (!spawnSuitable(x, z)) continue;
                double d = (double) x * x + (double) z * z;
                if (d < bestD) { bestD = d; best = new int[]{x, z}; }
            }
            if (best != null) return best;
        }
        return null;
    }

    boolean spawnSuitable(int x, int z) {
        if (waterClass(x, z) != 0 || stableDepth(x, z) < 12 || shoreDistance(x, z) < 64 || slopeAt(x, z) > 1.0 / 32) return false;
        if (rivers != null && rivers.edgeDistance(x, z) < RiverNetwork.BANK_REACH + 2) return false;
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
        out.writeInt(FORMAT);
        out.writeUTF(version);
        out.writeLong(seed);
        out.writeLong(noiseSeed);
        out.writeInt(N);
        out.writeLong(crc.getValue());
        for (float v : h) out.writeFloat(v);
        out.write(water);
        for (float v : belt) out.writeFloat(v);
        out.write(stable);
        out.write(shore);
        rivers.write(out);
        out.write(ecology.zone());
        out.write(ecology.hand());
    }

    /** Reads a cached surface; null when it is for another seed / version / format or does not check out. */
    public static TerrainPlanSurface read(DataInputStream in, long seed, String version) throws IOException {
        if (in.readInt() != MAGIC || in.readInt() != FORMAT) return null;
        String v = in.readUTF();
        long s = in.readLong(), noiseSeed = in.readLong();
        if (!v.equals(version) || s != seed || in.readInt() != N) return null;
        long want = in.readLong();
        int n = N * N;
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
        TerrainPlanSurface t = new TerrainPlanSurface(v, s, noiseSeed, h, water, belt, stable, shore, rivers, new EcologyPlan.Zones(zone, hand));
        return t.checksum().getValue() == want ? t : null;
    }

    private CRC32 checksum() {
        CRC32 crc = new CRC32();
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(8 * N);
        for (int r = 0; r < N; r++) {
            b.clear();
            for (int c = 0; c < N; c++) { b.putFloat(h[r * N + c]); b.putFloat(belt[r * N + c]); }
            crc.update(b.array(), 0, b.position());
        }
        crc.update(water);
        crc.update(stable);
        crc.update(shore);
        if (rivers != null) rivers.checksum(crc);
        if (ecology != null) { crc.update(ecology.zone()); crc.update(ecology.hand()); }
        return crc;
    }
}
