package com.antaurora.apofirstlight.worldgen.terrain.v2;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * What a Terrain V2 plan produces, for the world generation version lock (docs/worldgen/worldgen_version_lock_v1.md).
 * Everything here is computed from the plan's VALUES, never from the cache file, so a new cache layout
 * ({@link TerrainPlanSurface#FORMAT}) that decodes to the same plan gives the same fingerprint:
 * <ul>
 *   <li>{@link Result#planDigest}: SHA-256 (first 16 hex digits) of the stored plan: heights, water classes, fold-belt
 *   weights, stable depths, shore distances, the river network (vertices, widths, depths, areas, levels, line
 *   topology) and the ecology zones / HAND, plus the grid size and the noise seed;</li>
 *   <li>{@link Result#queryDigest}: the runtime queries world generation actually calls ({@link TerrainPlanSurface#heightAt},
 *   water class, stable depth, shore distance, marsh pools, river columns, the ecology biome index) at a fixed lattice
 *   of 32 x 32 columns over the plan grid plus every k-th river vertex, quantised; catches a changed query formula
 *   (detail noise, shore warp, carving widths) that leaves the stored arrays as they were;</li>
 *   <li>{@link Result#tiles}: a coarse summary per 512 m tile (mean height in cm, water cells, river vertices, a zone
 *   sum), kept in the world record so a digest mismatch can be told apart from floating-point noise (a plan built on
 *   another CPU architecture) and located.</li>
 * </ul>
 * Pure Java, deterministic, about 0.1 to 0.3 s for the r1 grid; memoised per surface instance.
 */
public final class TerrainPlanFingerprint {
    /** Cells per tile side: 32 x 16 m = 512 m. */
    public static final int TILE = 32;
    /** Lattice of query probes per side. */
    public static final int PROBES = 32;

    /** Per-tile summary, row-major (cols x rows). */
    public record Tiles(int cols, int rows, int[] meanHeightCm, int[] wetCells, int[] riverVertices, int[] zoneSum) {
        public int size() { return cols * rows; }
    }

    public record Result(String planDigest, String queryDigest, Tiles tiles) {}

    /** How two tile summaries differ (the same grid, else {@link #comparable} is false). */
    public record TileComparison(boolean comparable, int changedTiles, int largeTiles, double maxHeightShift,
                                 int minCol, int minRow, int maxCol, int maxRow) {
        /** Only floating-point level differences: no tile moved more than 2 cm in mean height, gained or lost more than
         *  two water cells or four river vertices, and at most 1 % of the tiles differ at all. */
        public boolean noiseOnly(int tileCount) {
            return comparable && largeTiles == 0 && changedTiles <= Math.max(1, tileCount / 100);
        }
    }

    private static final Map<TerrainPlanSurface, Result> MEMO = Collections.synchronizedMap(new WeakHashMap<>());

    private TerrainPlanFingerprint() {
    }

    /** The fingerprint of a finished (rivers + ecology) surface. */
    public static Result of(TerrainPlanSurface s) {
        Result r = MEMO.get(s);
        if (r == null) {
            r = compute(s);
            MEMO.put(s, r);
        }
        return r;
    }

    static Result compute(TerrainPlanSurface s) {
        if (s.rivers == null || s.ecology == null) throw new IllegalArgumentException("fingerprint needs a finished plan surface");
        return new Result(planDigest(s), queryDigest(s), tiles(s));
    }

    // ------------------------------------------------------------------ stored values
    static String planDigest(TerrainPlanSurface s) {
        Sha d = new Sha();
        d.text("afl-plan-values-1");
        d.i(TerrainPlanSurface.N);
        d.d(TerrainPlanSurface.CELL);
        d.d(TerrainPlanSurface.ORIGIN);
        d.l(s.noiseSeed);
        d.floats(s.h);
        d.bytes(s.water);
        d.floats(s.belt);
        d.bytes(s.stable);
        d.bytes(s.shore);
        RiverNetwork r = s.rivers;
        d.ints(r.start);
        d.ints(r.parent);
        d.floats(r.vx);
        d.floats(r.vz);
        d.floats(r.vw);
        d.floats(r.vd);
        d.floats(r.va);
        d.i(r.vl.length);
        for (short v : r.vl) d.i(v);
        d.bytes(s.ecology.zone());
        d.bytes(s.ecology.hand());
        return d.hex();
    }

    // ------------------------------------------------------------------ runtime queries
    static String queryDigest(TerrainPlanSurface s) {
        Sha d = new Sha();
        d.text("afl-plan-queries-1");
        RiverNetwork.Column col = new RiverNetwork.Column();
        double span = TerrainPlanSurface.N * TerrainPlanSurface.CELL;
        for (int j = 0; j < PROBES; j++) for (int i = 0; i < PROBES; i++) {
            // off the cell centres and the chunk grid: a constant irrational offset per lattice point
            double fx = (i + 0.5 + 0.37 * frac(i * 0.6180339887 + j * 0.4142135623)) / PROBES;
            double fz = (j + 0.5 + 0.37 * frac(i * 0.7320508075 + j * 0.2360679774)) / PROBES;
            int x = (int) Math.floor(TerrainPlanSurface.ORIGIN + fx * span), z = (int) Math.floor(TerrainPlanSurface.ORIGIN + fz * span);
            probe(d, s, col, x, z);
        }
        RiverNetwork r = s.rivers;
        int step = Math.max(1, r.vertices() / 512);
        for (int v = 0; v < r.vertices(); v += step) probe(d, s, col, (int) Math.floor(r.vx[v]), (int) Math.floor(r.vz[v]));
        return d.hex();
    }

    private static void probe(Sha d, TerrainPlanSurface s, RiverNetwork.Column col, int x, int z) {
        double px = x + 0.5, pz = z + 0.5;
        d.i(x);
        d.i(z);
        d.l(Math.round(s.heightAt(px, pz) * 64));
        d.l(Math.round(s.baseHeightAt(px, pz) * 64));
        d.i(s.waterClass(px, pz));
        d.i(s.stableDepth(px, pz));
        d.i(s.shoreDistance(px, pz));
        d.i(s.poolAt(x, z) ? 1 : 0);
        d.l(Math.round(s.beltWeight(px, pz) * 1024));
        d.i(EcologyBiomes.biomeAt(s, px, pz));
        s.rivers.column(x, z, col);
        d.i(col.kind);
        d.i(col.level);
        d.i(col.depth);
        d.i(col.maxTop);
        d.i(col.lowLevel);
        d.l(col.edge > 1e6f ? Long.MAX_VALUE : Math.round(col.edge * 16));
    }

    private static double frac(double v) { return v - Math.floor(v); }

    // ------------------------------------------------------------------ tiles
    static Tiles tiles(TerrainPlanSurface s) {
        int n = TerrainPlanSurface.N, cols = (n + TILE - 1) / TILE, rows = cols;
        double[] sum = new double[cols * rows];
        int[] count = new int[cols * rows], wet = new int[cols * rows], river = new int[cols * rows], zone = new int[cols * rows];
        for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) {
            int i = r * n + c, t = (r / TILE) * cols + c / TILE;
            sum[t] += s.h[i];
            count[t]++;
            if (s.water[i] != 0) wet[t]++;
            zone[t] += s.ecology.zone()[i] + 1;
        }
        RiverNetwork rv = s.rivers;
        for (int v = 0; v < rv.vertices(); v++) {
            int c = TerrainPlanSurface.cellOf(rv.vx[v]) / TILE, r = TerrainPlanSurface.cellOf(rv.vz[v]) / TILE;
            river[r * cols + c]++;
        }
        int[] mean = new int[cols * rows];
        for (int t = 0; t < mean.length; t++) mean[t] = (int) Math.round(sum[t] / Math.max(1, count[t]) * 100);
        return new Tiles(cols, rows, mean, wet, river, zone);
    }

    /** Compares the tiles stored with a world to the tiles of the plan this build makes. */
    public static TileComparison compare(Tiles stored, Tiles current) {
        if (stored == null || current == null || stored.cols() != current.cols() || stored.rows() != current.rows()
                || stored.meanHeightCm().length != stored.size() || current.meanHeightCm().length != current.size())
            return new TileComparison(false, -1, -1, Double.NaN, 0, 0, 0, 0);
        int changed = 0, large = 0, minC = Integer.MAX_VALUE, minR = Integer.MAX_VALUE, maxC = -1, maxR = -1;
        double maxShift = 0;
        for (int t = 0; t < stored.size(); t++) {
            int dh = Math.abs(stored.meanHeightCm()[t] - current.meanHeightCm()[t]);
            int dw = Math.abs(stored.wetCells()[t] - current.wetCells()[t]);
            int dr = Math.abs(stored.riverVertices()[t] - current.riverVertices()[t]);
            int dz = Math.abs(stored.zoneSum()[t] - current.zoneSum()[t]);
            if (dh == 0 && dw == 0 && dr == 0 && dz == 0) continue;
            changed++;
            maxShift = Math.max(maxShift, dh / 100.0);
            if (dh > 2 || dw > 2 || dr > 4 || dz > 4) large++;
            int c = t % stored.cols(), r = t / stored.cols();
            minC = Math.min(minC, c); maxC = Math.max(maxC, c);
            minR = Math.min(minR, r); maxR = Math.max(maxR, r);
        }
        if (changed == 0) return new TileComparison(true, 0, 0, 0, 0, 0, 0, 0);
        return new TileComparison(true, changed, large, maxShift, minC, minR, maxC, maxR);
    }

    /** World X / Z of a tile corner (for messages). */
    public static int tileOrigin(int tileIndex) {
        return (int) (TerrainPlanSurface.ORIGIN + (double) tileIndex * TILE * TerrainPlanSurface.CELL);
    }

    // ------------------------------------------------------------------ digest helper
    /** SHA-256 over typed values (big-endian), reported as the first 16 hex digits. */
    static final class Sha {
        private final MessageDigest md;
        private final ByteBuffer buf = ByteBuffer.allocate(1 << 16);

        Sha() {
            try {
                md = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }

        private void room(int bytes) {
            if (buf.remaining() < bytes) flush();
        }

        private void flush() {
            md.update(buf.array(), 0, buf.position());
            buf.clear();
        }

        void i(int v) { room(4); buf.putInt(v); }

        void l(long v) { room(8); buf.putLong(v); }

        void d(double v) { room(8); buf.putLong(Double.doubleToLongBits(v)); }

        void text(String s) {
            byte[] b = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            i(b.length);
            bytes0(b);
        }

        void bytes(byte[] a) {
            i(a.length);
            bytes0(a);
        }

        private void bytes0(byte[] a) {
            flush();
            md.update(a);
        }

        void floats(float[] a) {
            i(a.length);
            for (float v : a) { room(4); buf.putInt(Float.floatToIntBits(v)); }
        }

        void ints(int[] a) {
            i(a.length);
            for (int v : a) { room(4); buf.putInt(v); }
        }

        String hex() {
            flush();
            byte[] h = md.digest();
            StringBuilder sb = new StringBuilder(16);
            for (int k = 0; k < 8; k++) sb.append(String.format("%02x", h[k] & 0xff));
            return sb.toString();
        }
    }
}
