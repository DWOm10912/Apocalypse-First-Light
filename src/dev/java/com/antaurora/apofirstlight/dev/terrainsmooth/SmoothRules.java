package com.antaurora.apofirstlight.dev.terrainsmooth;

/**
 * The terrain smoothing rules (docs/worldgen/terrain_smooth_visual_prototype_p0.md, P1), pure Java over a cell lookup,
 * so the chunk renderer (a render region), the client and the server (their levels) and the offline check all compute
 * the same answer from the same blocks.
 * <p>
 * A block B at (x, y, z) takes part when it is natural ground, inside the test area, with air or a plant above. Each of
 * the 3 x 3 columns around it is read near B's level only (y - 1 .. y + 2): its surface height H (top block + 1) and kind.
 * Every block corner point gets one height from the four columns around it (the same for every block touching it, so tops
 * meet): pinned (each block keeps its own H) when one of the four is not taking part or they differ by 2 or more; mode A
 * the lowest of the four, mode B their mean. When the four differ by at most one, every block touching the point reads
 * all four inside its window, so the window never changes the answer.
 * <p>
 * Geometry key (long, -1 = the block keeps its vanilla shape): corner heights nw, ne, sw, se above B's floor in quarters
 * (bits 0-15, 4 each, 0..8), then for the N, S, W, E sides the neighbour's surface at the two ends of that edge (bottom of
 * the visible side band, clamped to the top; bits 16-47).
 */
public final class SmoothRules {
    public static final int MODE_OFF = 0, MODE_A = 1, MODE_B = 2;
    /** cell codes */
    public static final int AIR = 0, COVER = 1, LEAF = 2, FLUID = 3, NATURAL = 4, OTHER = 5, UNKNOWN = 6;
    /** column kinds */
    static final int K_NAT = 0, K_FIXED = 1, K_FLUID = 2, K_LOW = 3, K_HIGH = 4, K_UNKNOWN = 5;
    /**
     * Collision / selection boxes per side of a block. Offline (SmoothCheck, mode B): 2 x 2 leaves feet up to 0.375 in or
     * over the drawn top and 0.5 steps; 4 x 4 0.188 (p95 0.094) and 0.25 steps; 8 x 8 0.125 (p95 0.063) and 1/8 steps.
     */
    public static final int GRID = 8;

    public interface Cells {
        int cell(int x, int y, int z);
    }

    private SmoothRules() {}

    static boolean solid(int c) { return c == NATURAL || c == OTHER; }

    static boolean open(int c) { return c == AIR || c == COVER; }

    static long pack(int kind, int h) { return (long) kind << 32 | (h & 0xFFFFFFFFL); }

    static int kind(long c) { return (int) (c >>> 32); }

    static int height(long c) { return (int) c; }

    static boolean inArea(int minX, int minZ, int size, int x, int z) { return x >= minX && x < minX + size && z >= minZ && z < minZ + size; }

    /** Column (x, z) read near level y. */
    static long column(Cells c, int minX, int minZ, int size, int x, int y, int z) {
        int c2 = c.cell(x, y + 2, z), c1 = c.cell(x, y + 1, z), c0 = c.cell(x, y, z), cm = c.cell(x, y - 1, z);
        if (c1 == UNKNOWN || c0 == UNKNOWN || cm == UNKNOWN) return pack(K_UNKNOWN, 0);
        boolean in = inArea(minX, minZ, size, x, z);
        if (c1 == FLUID) return pack(K_FLUID, y + 2);
        if (solid(c1)) {
            if (solid(c2) || c2 == UNKNOWN) return pack(K_HIGH, y + 3);
            return pack(c1 == NATURAL && open(c2) && in ? K_NAT : K_FIXED, y + 2);
        }
        if (c0 == FLUID) return pack(K_FLUID, y + 1);
        if (solid(c0)) return pack(c0 == NATURAL && open(c1) && in ? K_NAT : K_FIXED, y + 1);
        if (cm == FLUID) return pack(K_FLUID, y);
        if (solid(cm)) return pack(cm == NATURAL && open(c0) && in ? K_NAT : K_FIXED, y);
        return pack(K_LOW, y - 1);
    }

    /** Height of the corner whose four columns are col[(oj + b) * 3 + oi + a], a, b in {0, 1}; NaN when pinned. */
    static float corner(long[] col, int oi, int oj, int mode) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, sum = 0;
        for (int j = oj; j <= oj + 1; j++) for (int i = oi; i <= oi + 1; i++) {
            long c = col[j * 3 + i];
            if (kind(c) != K_NAT) return Float.NaN;
            int h = height(c);
            lo = Math.min(lo, h);
            hi = Math.max(hi, h);
            sum += h;
        }
        if (hi - lo > 1) return Float.NaN;
        return mode == MODE_A ? lo : sum * 0.25F;
    }

    private static int q(float relative) { return Math.max(0, Math.min(8, Math.round(relative * 4))); }

    /** corner offsets (oi, oj) of nw, ne, sw, se */
    private static final int[][] CORNER = {{0, 0}, {1, 0}, {0, 1}, {1, 1}};
    /** per side N, S, W, E: neighbour column index, corner indices at the band's ends a and b */
    private static final int[][] SIDE = {{1, 1, 0}, {7, 2, 3}, {3, 0, 2}, {5, 3, 1}};

    /** The geometry key of block (x, y, z), or -1 when it keeps its vanilla shape. */
    public static long geometry(Cells c, int mode, int minX, int minZ, int size, int x, int y, int z) {
        if (mode == MODE_OFF || !inArea(minX, minZ, size, x, z)) return -1;
        if (c.cell(x, y, z) != NATURAL || !open(c.cell(x, y + 1, z))) return -1;
        long[] col = new long[9];
        for (int j = 0; j < 3; j++) for (int i = 0; i < 3; i++)
            col[j * 3 + i] = i == 1 && j == 1 ? pack(K_NAT, y + 1) : column(c, minX, minZ, size, x + i - 1, y, z + j - 1);
        int h = y + 1;
        float[] abs = new float[4];
        boolean flat = true;
        for (int k = 0; k < 4; k++) {
            float v = corner(col, CORNER[k][0], CORNER[k][1], mode);
            abs[k] = Float.isNaN(v) ? h : v;
            if (abs[k] != h) flat = false;
        }
        if (flat) return -1;
        long g = 0;
        for (int k = 0; k < 4; k++) g |= (long) q(abs[k] - y) << (4 * k);
        for (int s = 0; s < 4; s++) {
            long n = col[SIDE[s][0]];
            int qa = Math.min(q(abs[SIDE[s][1]] - y), lower(n, col, SIDE[s][1], mode, y));
            int qb = Math.min(q(abs[SIDE[s][2]] - y), lower(n, col, SIDE[s][2], mode, y));
            g |= (long) qa << (16 + 8 * s) | (long) qb << (20 + 8 * s);
        }
        return g;
    }

    /** Bottom of B's side band at a corner, in quarters above B's floor: the neighbour column's surface there. */
    private static int lower(long n, long[] col, int cornerIndex, int mode, int y) {
        return switch (kind(n)) {
            case K_NAT -> { float v = corner(col, CORNER[cornerIndex][0], CORNER[cornerIndex][1], mode); yield q((Float.isNaN(v) ? height(n) : v) - y); }
            case K_FIXED, K_HIGH -> q(height(n) - y);
            default -> 0;   // water, a drop of two or more, not loaded: the whole side shows
        };
    }

    /** Corner heights nw, ne, sw, se above the floor. */
    public static float[] corners(long g) {
        float[] c = new float[4];
        for (int k = 0; k < 4; k++) c[k] = (g >>> (4 * k) & 15) / 4F;
        return c;
    }

    /** Height above the floor of the drawn top at (fx, fz) in 0..1: two triangles split on the nw-se diagonal. */
    public static double surface(float[] c, double fx, double fz) {
        return fz >= fx ? c[0] + (c[3] - c[2]) * fx + (c[2] - c[0]) * fz : c[0] + (c[1] - c[0]) * fx + (c[3] - c[1]) * fz;
    }

    /** Box heights in 1/16 above the floor of the GRID x GRID collision / selection boxes ([j * GRID + i], i along x). */
    public static int[] boxes(long g) { return boxes(g, GRID); }

    /** The same for any grid (the offline check compares 2, 4 and 8): each box reaches the drawn top at its centre. */
    public static int[] boxes(long g, int grid) {
        float[] c = corners(g);
        int[] out = new int[grid * grid];
        for (int j = 0; j < grid; j++) for (int i = 0; i < grid; i++)
            out[j * grid + i] = (int) Math.max(0, Math.min(32, Math.round(surface(c, (i + 0.5) / grid, (j + 0.5) / grid) * 16)));
        return out;
    }
}
