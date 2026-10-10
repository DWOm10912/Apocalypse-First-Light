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
 * </ul>
 */
public final class TerrainPlanSurface {
    public static final int FORMAT = 2;                // 2: + rivers (Phase 2b, 2026-10-10)
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

    TerrainPlanSurface(String version, long seed, long noiseSeed, float[] h, byte[] water, float[] belt, byte[] stable, byte[] shore,
                       RiverNetwork rivers) {
        this.version = version;
        this.seed = seed;
        this.noiseSeed = noiseSeed;
        this.h = h;
        this.water = water;
        this.belt = belt;
        this.stable = stable;
        this.shore = shore;
        this.rivers = rivers;
    }

    TerrainPlanSurface withRivers(RiverNetwork r) {
        return new TerrainPlanSurface(version, seed, noiseSeed, h, water, belt, stable, shore, r);
    }

    static int idx(int col, int row) { return row * N + col; }

    static int clampI(int v) { return Math.max(0, Math.min(N - 1, v)); }

    static int cellOf(double coord) { return clampI((int) Math.floor((coord - ORIGIN) / CELL)); }

    /** The planned surface (top of the ground) at a point. */
    public double heightAt(double x, double z) {
        double gx = (x - ORIGIN) / CELL - 0.5, gz = (z - ORIGIN) / CELL - 0.5;
        double y = baseHeight(gx, gz);
        int i = idx(clampI((int) Math.round(gx)), clampI((int) Math.round(gz)));
        if (water[i] == 1 || water[i] == 2) return y;
        double steep = Math.min(1, slopeAt(i) / 0.25);
        double amp = 0.20 + 0.45 * steep + 0.20 * belt[i];
        y += amp * PlanNoise.fbm(noiseSeed, x, z, 48, 2, 0.5, 91) + 0.25 * amp * PlanNoise.fbm(noiseSeed, x, z, 11, 2, 0.5, 92)
                + 0.05 * amp * PlanNoise.fbm(noiseSeed, x, z, 4, 1, 0.5, 93);
        return y;
    }

    /** The plan grid's own surface without sub-grid detail (cheap: carver checks, spawn search). */
    public double baseHeightAt(double x, double z) {
        return baseHeight((x - ORIGIN) / CELL - 0.5, (z - ORIGIN) / CELL - 0.5);
    }

    private double baseHeight(double gx, double gz) {
        int c0 = (int) Math.floor(gx), r0 = (int) Math.floor(gz);
        double fx = gx - c0, fz = gz - r0;
        double[] col = new double[4], row = new double[4];
        for (int a = -1; a <= 2; a++) {
            for (int b = -1; b <= 2; b++) row[b + 1] = h[idx(clampI(c0 + b), clampI(r0 + a))];
            col[a + 1] = cr(row, fx);
        }
        return cr(col, fz);
    }

    public int waterClass(double x, double z) { return water[idx(cellOf(x), cellOf(z))]; }

    /** Open water at the surface: the macro sea or a drowned valley (marsh is land at Y63.4). */
    public boolean waterAt(double x, double z) {
        int w = waterClass(x, z);
        return w == 1 || w == 2;
    }

    public int stableDepth(double x, double z) { return stable[idx(cellOf(x), cellOf(z))]; }

    /**
     * Open water in the tidal marsh (Phase 2b): pools and leads at sea level (top water block Y62) on the marsh flat
     * (top block Y63), about 40 % of the marsh interior and fewer toward its edge (a smooth marsh fraction, so the
     * pools never trace the 16 m grid). RiverCarver opens a pool only where the generated top is exactly Y63.
     */
    public boolean poolAt(int x, int z) {
        double gx = (x + 0.5 - ORIGIN) / CELL - 0.5, gz = (z + 0.5 - ORIGIN) / CELL - 0.5;
        int c0 = (int) Math.floor(gx), r0 = (int) Math.floor(gz);
        double fx = gx - c0, fz = gz - r0;
        double m = 0;
        for (int b = 0; b < 2; b++) for (int a = 0; a < 2; a++)
            if (water[idx(clampI(c0 + a), clampI(r0 + b))] == 3) m += (a == 0 ? 1 - fx : fx) * (b == 0 ? 1 - fz : fz);
        if (m < POOL_MARSH) return false;
        double f = PlanNoise.fbm(noiseSeed, x, z, 30, 2, 0.5, 401) + 0.5 * PlanNoise.fbm(noiseSeed, x, z, 9, 2, 0.5, 402);
        return f < POOL_EDGE + (POOL_CORE - POOL_EDGE) * (m - POOL_MARSH) / (1 - POOL_MARSH);
    }

    static final double POOL_MARSH = 0.55, POOL_EDGE = -0.7, POOL_CORE = 0.05;

    public int shoreDistance(double x, double z) { return shore[idx(cellOf(x), cellOf(z))]; }

    /** Plan-grid slope (rise / run) at a cell. */
    double slopeAt(int i) {
        int c = i % N, r = i / N;
        if (c < 1 || r < 1 || c > N - 2 || r > N - 2) return 0;
        double gx = (h[i + 1] - h[i - 1]) / (2 * CELL), gz = (h[i + N] - h[i - N]) / (2 * CELL);
        return Math.hypot(gx, gz);
    }

    public double slopeAt(double x, double z) { return slopeAt(idx(cellOf(x), cellOf(z))); }

    public float beltWeight(double x, double z) { return belt[idx(cellOf(x), cellOf(z))]; }

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
        TerrainPlanSurface t = new TerrainPlanSurface(v, s, noiseSeed, h, water, belt, stable, shore, rivers);
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
        return crc;
    }
}
