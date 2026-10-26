package com.antaurora.apofirstlight.terrainsmooth;

import com.antaurora.apofirstlight.ground.GroundClass;

/**
 * The terrain smoothing rules (docs/worldgen/terrain_smooth_v1.md), pure Java over a cell lookup, so the chunk renderer
 * (a render region), the client and the server (their levels) and the offline checks all compute the same answer from
 * the same blocks. Which cells take part, and which count as rock the soil slopes onto, is GroundClass's (terrain rework
 * R1): H is a top that slopes here, V rock or a soil wall.
 * <p>
 * A block B at (x, y, z) takes part when it is natural ground with air or a plant above and it is its column's
 * <b>natural surface</b> ({@link Cells#surface}: the first block met going down from the highest block through sky,
 * plants, leaves and tree trunks only). Cave floors, mine and basement floors, ground under a roof or an overhang are not
 * a column's natural surface and keep their cubes. Each of the 3 x 3 columns around B is read near B's level only
 * (y - 1 .. y + 2): its surface height H (top block + 1) and kind. Every block corner point gets one height from the four
 * columns around it (the same for every block touching it, so tops meet): pinned (each block keeps its own H) when one of
 * the four is not natural surface, or they differ by 2 or more; mode A the lowest of the four, mode B their mean. When the
 * four differ by at most one, every block touching the point reads all four inside its window, so the window never
 * changes the answer.
 * <p>
 * A graded_turf column (the road's earthworks, docs/worldgen/federal_highways_c3.md 10.4) has its own corner heights:
 * a corner point it touches takes the turf's height there, so the natural ground meets the earthworks without a step,
 * when the turf passes the same spread test as a natural column and every natural block there can draw that height;
 * else the point is pinned like any other (#corner; a band then closes the step down to the turf's edge).
 * <p>
 * Geometry key (long, -1 = the block keeps its vanilla shape): corner heights nw, ne, sw, se above B's floor in quarters
 * (bits 0-15, 4 each, 0..8), then for the N, S, W, E sides the neighbour's surface at the two ends of that edge (bottom of
 * the visible side band, clamped to the top; bits 16-47).
 */
public final class SmoothRules {
    public static final int MODE_OFF = 0, MODE_A = 1, MODE_B = 2;
    /** cell codes (TREE: logs and the like, solid for the slopes, see-through for the natural-surface scan) */
    /**
     * ROCK: natural rock (U6 V3, user 2026-10-24): never sloped here, its faces are RockRelief's continuous Mesh; it is still
     * its column's natural surface (the earthwork reads it), and soil above it slopes down onto its top (#corner)
     */
    public static final int AIR = 0, COVER = 1, LEAF = 2, FLUID = 3, NATURAL = 4, OTHER = 5, UNKNOWN = 6, TREE = 7, ROCK = 8;
    /**
     * WET: natural loose ground holding water (NaturalGroundBlock#WATERLOGGED; river / lake V1 R0,
     * docs/worldgen/river_lake_v1_design.md 4.9). It takes part as NATURAL does, and water over it is open to it: a column
     * whose natural surface is wet reads its water as air (#column), so a bed slopes under the water and a shore into it
     * instead of being pinned by it. Water over dry ground still pins (old worlds' rivers keep their shape).
     */
    public static final int WET = 9;
    /** Cells#surface of a column without natural surface (water, a roof, an overhang on top) or not loaded */
    public static final int NO_SURFACE = Integer.MIN_VALUE;
    /** column kinds */
    static final int K_NAT = 0, K_FIXED = 1, K_FLUID = 2, K_LOW = 3, K_HIGH = 4, K_UNKNOWN = 5, K_ROCK = 6;
    /**
     * Collision / selection boxes per side of a block. Offline (SmoothCheck, mode B): 2 x 2 leaves feet up to 0.375 in or
     * over the drawn top and 0.5 steps; 4 x 4 0.188 (p95 0.094) and 0.25 steps; 8 x 8 0.125 (p95 0.063) and 1/8 steps.
     */
    public static final int GRID = 8;

    /**
     * How many columns away a block change can re-shape a top: 2 (rework R0 10.5, 2026-10-24). A top reads its neighbour
     * columns (#column), and a neighbour's kind reads that neighbour's own neighbours: GroundClass#soilWall (U6 V3) asks whether the
     * soil beside it is open. So a dig two columns away can turn a neighbour into a soil wall and pin this top.
     */
    public static final int REACH = 2;

    public interface Cells {
        int cell(int x, int y, int z);

        /** y of the column's natural surface block, or NO_SURFACE (see #surface(Cells, int, int, int, int)) */
        int surface(int x, int z);

        /** Absolute height of corner k (nw, ne, sw, se) of a graded_turf block at (x, y, z); NaN for any other block. */
        default float turf(int x, int y, int z, int k) { return Float.NaN; }
    }

    private SmoothRules() {}

    static boolean solid(int c) { return c == NATURAL || c == OTHER || c == TREE || c == ROCK || c == WET; }

    public static boolean open(int c) { return c == AIR || c == COVER; }

    /** Natural loose ground, dry or wet. */
    public static boolean natural(int c) { return c == NATURAL || c == WET; }

    /** Open over a natural cell: air or a cover, or water over wet ground. */
    public static boolean openOver(int cell, int above) { return open(above) || cell == WET && above == FLUID; }

    /** Passed over by the natural-surface scan: sky, plants and snow, tree crowns and trunks. */
    public static boolean skyward(int c) { return c == AIR || c == COVER || c == LEAF || c == TREE; }

    /**
     * A column's natural surface: going down from {@code top} (its highest block), past sky, plants, leaves and trunks,
     * the first other cell if it is natural ground, else NO_SURFACE (water, anything built or a rock overhang on top).
     * Past water only wet ground counts (a river's wet bed, R0): dry ground or rock under water is no natural surface.
     */
    public static int surface(Cells c, int x, int z, int top, int bottom) {
        boolean water = false;
        for (int y = top; y >= bottom; y--) {
            int k = c.cell(x, y, z);
            if (skyward(k)) continue;
            if (k == FLUID) { water = true; continue; }
            return k == WET || !water && (k == NATURAL || k == ROCK) ? y : NO_SURFACE;
        }
        return NO_SURFACE;
    }

    static long pack(int kind, int h) { return (long) kind << 32 | (h & 0xFFFFFFFFL); }

    /** column flag: the cell at the reading block's own level is solid */
    static final long SOLID_HERE = 1L << 40;

    static int kind(long c) { return (int) (c >>> 32) & 0xFF; }

    static int height(long c) { return (int) c; }

    /** Column (x, z) read near level y: kind and surface height, flagged SOLID_HERE when its cell at level y is solid. */
    static long column(Cells c, int x, int y, int z) {
        int c0 = c.cell(x, y, z);
        long k = kindOf(c, c0, x, y, z);
        return solid(c0) ? k | SOLID_HERE : k;
    }

    /** Column (x, z)'s natural surface is wet ground (R0): its water reads as air for the slopes. */
    static boolean wetColumn(Cells c, int x, int z) {
        int s = c.surface(x, z);
        return s != NO_SURFACE && c.cell(x, s, z) == WET;
    }

    private static long kindOf(Cells c, int c0, int x, int y, int z) {
        int c2 = c.cell(x, y + 2, z), c1 = c.cell(x, y + 1, z), cm = c.cell(x, y - 1, z);
        if ((c2 == FLUID || c1 == FLUID || c0 == FLUID || cm == FLUID) && wetColumn(c, x, z)) {
            if (c2 == FLUID) c2 = AIR;
            if (c1 == FLUID) c1 = AIR;
            if (c0 == FLUID) c0 = AIR;
            if (cm == FLUID) cm = AIR;
        }
        if (c1 == UNKNOWN || c0 == UNKNOWN || cm == UNKNOWN) return pack(K_UNKNOWN, 0);
        if (c1 == FLUID) return pack(K_FLUID, y + 2);
        if (solid(c1)) {
            if (solid(c2) || c2 == UNKNOWN) return pack(K_HIGH, y + 3);
            return pack(kindAt(c, c1, c2, x, y + 1, z), y + 2);
        }
        if (c0 == FLUID) return pack(K_FLUID, y + 1);
        if (solid(c0)) return pack(kindAt(c, c0, c1, x, y, z), y + 1);
        if (cm == FLUID) return pack(K_FLUID, y);
        if (solid(cm)) return pack(kindAt(c, cm, c0, x, y - 1, z), y);
        return pack(K_LOW, y - 1);
    }

    /** A solid column top's kind, open above: GroundClass H natural (K_NAT), V rock or a soil wall (K_ROCK: soil slopes onto it), else fixed. */
    private static int kindAt(Cells c, int cell, int above, int x, int top, int z) {
        if (!openOver(cell, above)) return K_FIXED;
        return switch (GroundClass.of(c, cell, above, x, top, z)) {
            case GroundClass.V -> K_ROCK;
            case GroundClass.H -> K_NAT;
            default -> K_FIXED;
        };
    }

    /**
     * Height of the corner whose four columns are col[(oj + b) * 3 + oi + a], a, b in {0, 1}; NaN when pinned. A turf
     * column among them (turf[index], its four absolute corner heights) gives its height at this point, on the same terms
     * as the natural columns (2026-10-15, the see-through seams of the C3.3a check): it counts with its block top in the
     * spread test (so every block at the point reads all four columns inside its window and answers alike), all turf
     * columns at the point must give the same height, and that height must lie where every natural block at the point can
     * draw it (0..2 above its floor); otherwise the point is pinned for everyone.
     */
    static float corner(long[] col, float[][] turf, int oi, int oj, int mode) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, sum = 0, nlo = Integer.MAX_VALUE, nhi = Integer.MIN_VALUE;
        int rlo = Integer.MAX_VALUE, rhi = Integer.MIN_VALUE, rsum = 0;
        float t = Float.NaN;
        boolean pinned = false;
        for (int j = oj; j <= oj + 1; j++) for (int i = oi; i <= oi + 1; i++) {
            long c = col[j * 3 + i];
            if (kind(c) == K_ROCK) {
                rlo = Math.min(rlo, height(c));
                rhi = Math.max(rhi, height(c));
                rsum += height(c);
                continue;
            }
            if (turf[j * 3 + i] != null) {
                float v = turf[j * 3 + i][(oi + 1 - i) + 2 * (oj + 1 - j)];
                if (Float.isNaN(t)) t = v;
                else if (Math.abs(t - v) > 1e-4F) pinned = true;
                lo = Math.min(lo, height(c));
                hi = Math.max(hi, height(c));
                continue;
            }
            if (kind(c) != K_NAT) { pinned = true; continue; }
            int h = height(c);
            lo = Math.min(lo, h);
            hi = Math.max(hi, h);
            nlo = Math.min(nlo, h);
            nhi = Math.max(nhi, h);
            sum += h;
        }
        // bare rock (U6 V3) is never sloped: soil lower than it or level with it would bare the rock's side under the
        // corner, so the point is pinned; rock lower than every other column there takes part, the soil sloping down onto
        // its top (a corner above a rock top by at least 3/4: the rock's side is never bared)
        if (rhi != Integer.MIN_VALUE) {
            if (rhi >= lo) pinned = true;
            lo = Math.min(lo, rlo);
            sum += rsum;
        }
        if (pinned || hi - lo > 1) return Float.NaN;
        if (!Float.isNaN(t)) return t >= nhi - 1 && t <= nlo + 1 ? t : Float.NaN;
        return mode == MODE_A ? lo : sum * 0.25F;
    }

    private static int q(float relative) { return Math.max(0, Math.min(8, Math.round(relative * 4))); }

    /** corner offsets (oi, oj) of nw, ne, sw, se */
    private static final int[][] CORNER = {{0, 0}, {1, 0}, {0, 1}, {1, 1}};
    /** per side N, S, W, E: neighbour column index, corner indices at the band's ends a and b */
    private static final int[][] SIDE = {{1, 1, 0}, {7, 2, 3}, {3, 0, 2}, {5, 3, 1}};

    /** The geometry key of block (x, y, z), or -1 when it keeps its vanilla shape. */
    public static long geometry(Cells c, int mode, int x, int y, int z) {
        if (mode == MODE_OFF || !GroundClass.surface(c, x, y, z)) return -1;
        long[] col = new long[9];
        for (int j = 0; j < 3; j++) for (int i = 0; i < 3; i++)
            col[j * 3 + i] = i == 1 && j == 1 ? pack(K_NAT, y + 1) | SOLID_HERE : column(c, x + i - 1, y, z + j - 1);
        // graded_turf columns read at this level: their corner heights
        float[][] turf = new float[9][];
        for (int j = 0; j < 3; j++) for (int i = 0; i < 3; i++) {
            long n = col[j * 3 + i];
            if (kind(n) != K_FIXED) continue;
            int tx = x + i - 1, ty = height(n) - 1, tz = z + j - 1;
            if (Float.isNaN(c.turf(tx, ty, tz, 0))) continue;
            turf[j * 3 + i] = new float[]{c.turf(tx, ty, tz, 0), c.turf(tx, ty, tz, 1), c.turf(tx, ty, tz, 2), c.turf(tx, ty, tz, 3)};
        }
        int h = y + 1;
        float[] abs = new float[4];
        boolean flat = true;
        for (int k = 0; k < 4; k++) {
            float v = corner(col, turf, CORNER[k][0], CORNER[k][1], mode);
            abs[k] = Float.isNaN(v) ? h : v;
            if (abs[k] != h) flat = false;
        }
        if (flat) return -1;
        long g = 0;
        for (int k = 0; k < 4; k++) g |= (long) q(abs[k] - y) << (4 * k);
        for (int s = 0; s < 4; s++) {
            long n = col[SIDE[s][0]];
            // a neighbour whose top is above this block's cell is counted on to hide this side up to its surface; with a cavity
            // at this level (the ground under an overhang, blasted out) it does not: draw the whole side
            boolean hollow = height(n) > y + 1 && (n & SOLID_HERE) == 0;
            int qa = Math.min(q(abs[SIDE[s][1]] - y), hollow ? 0 : lower(n, col, turf, SIDE[s][0], SIDE[s][1], mode, y));
            int qb = Math.min(q(abs[SIDE[s][2]] - y), hollow ? 0 : lower(n, col, turf, SIDE[s][0], SIDE[s][2], mode, y));
            g |= (long) qa << (16 + 8 * s) | (long) qb << (20 + 8 * s);
        }
        return g;
    }

    /**
     * Earthwork (docs/worldgen/earthwork_a1.md 2.1): block (x, y, z) is its column's natural surface top, open above, all
     * 3 x 3 columns around it are natural at this level and none of its four corners is pinned. Then its top is continuous
     * with every neighbour's (they share the corner heights) and has no side band, so a height field can take it over
     * exactly, and its neighbours, reading it as it was, never part from it.
     */
    public static boolean freeCorners(Cells c, int mode, int x, int y, int z) {
        return notFree(c, mode, x, y, z) == null;
    }

    private static final String[] KIND_NAMES = {"natural", "not natural ground or not open above (built, a path, a plant block...)",
            "water", "two or more lower", "two or more higher (a wall)", "not loaded", "bare rock"};
    private static final String[] CORNER_NAMES = {"north-west", "north-east", "south-west", "south-east"};

    /** Why #freeCorners fails (development output, English), or null when the corners are free. */
    public static String notFree(Cells c, int mode, int x, int y, int z) {
        return notFree(c, mode, x, y, z, false);
    }

    /**
     * The same; walls: a neighbour two or more higher is allowed too (Rock R2 round 2, Earthwork: the foot of a natural step),
     * and one whose natural surface lies exactly two lower (R2 round 4, 2026-10-23: the rim of a two-cell step; deeper is
     * a cliff and stays locked), and then the corners are not asked to be free. The vertices a step's two columns share go
     * to the one edited first (Earthwork#convert); EarthSkin closes the side between the skin and an unedited neighbour.
     */
    public static String notFree(Cells c, int mode, int x, int y, int z, boolean walls) {
        int self = c.cell(x, y, z);
        if (c.surface(x, z) != y || self != NATURAL && self != ROCK) return "its top is not Terrain Smooth's natural surface";
        if (!open(c.cell(x, y + 1, z))) return "the block above is not open";
        long[] col = new long[9];
        boolean wall = false;
        for (int j = 0; j < 3; j++) for (int i = 0; i < 3; i++) {
            int nx = x + i - 1, nz = z + j - 1;
            col[j * 3 + i] = i == 1 && j == 1 ? pack(K_NAT, y + 1) | SOLID_HERE : column(c, nx, y, nz);
            int k = kind(col[j * 3 + i]);
            // wet ground beside (R0) is water to the earthwork: its skin must not open a shore
            if (!(i == 1 && j == 1) && wetColumn(c, nx, nz)) return "neighbour " + nx + " " + nz + " is water";
            // bare rock beside (U6 V3, never sloped) is as a wall: EarthSkin closes the side toward it
            if (walls && (k == K_HIGH || k == K_ROCK || k == K_LOW && stepDown(c, nx, y - 2, nz))) wall = true;
            else if (k == K_LOW && walls) return "neighbour " + nx + " " + nz + " is three or more lower (a cliff)";
            else if (k != K_NAT) return "neighbour " + nx + " " + nz + " is " + KIND_NAMES[k];
        }
        if (wall) return null;
        float[][] turf = new float[9][];
        for (int k = 0; k < 4; k++)
            if (Float.isNaN(corner(col, turf, CORNER[k][0], CORNER[k][1], mode)))
                return "its " + CORNER_NAMES[k] + " corner is pinned (the four columns there differ by two or more)";
        return null;
    }

    /** Column (x, z)'s natural surface is natural ground at y, open above: the foot of a two-cell step below a rim. */
    private static boolean stepDown(Cells c, int x, int y, int z) {
        int k = c.cell(x, y, z);
        return c.surface(x, z) == y && (k == NATURAL || k == ROCK) && open(c.cell(x, y + 1, z));
    }

    /** Bottom of B's side band at a corner, in quarters above B's floor: the neighbour column's surface there. */
    private static int lower(long n, long[] col, float[][] turf, int index, int cornerIndex, int mode, int y) {
        int oi = CORNER[cornerIndex][0], oj = CORNER[cornerIndex][1];
        // a turf neighbour: rounded down, so the band starts at or below the turf's edge (turf corners are eighths, the
        // block's own corners quarters rounded half up: the block may stand 1/8 above the turf, and its band closes that)
        if (turf[index] != null)
            return Math.max(0, Math.min(8, (int) Math.floor((turf[index][(oi + 1 - index % 3) + 2 * (oj + 1 - index / 3)] - y) * 4 + 1e-4F)));
        return switch (kind(n)) {
            case K_NAT -> { float v = corner(col, turf, oi, oj, mode); yield q((Float.isNaN(v) ? height(n) : v) - y); }
            case K_FIXED, K_HIGH, K_ROCK -> q(height(n) - y);
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

    /**
     * What a block change at (x, y, z) re-shapes: the blocks of columns within #REACH from y - 2 to y + 2 (the
     * neighbours read it from y - 2 to y + 1; the columns two away through a neighbour's GroundClass#soilWall, which reads the changed
     * cell from one level below the neighbour's top: tops from y - 1 to y + 2), and around the changed column's natural
     * surface before and after (its neighbours' corners read whether it takes part): {min y, max y} of the affected blocks,
     * the surfaces counted 2 below to 1 above.
     */
    public static int[] affected(int y, int oldSurface, int newSurface) {
        int lo = y - 2, hi = y + 2;
        for (int s : new int[]{oldSurface, newSurface}) {
            if (s == NO_SURFACE) continue;
            lo = Math.min(lo, s - 2);
            hi = Math.max(hi, s + 1);
        }
        return new int[]{lo, hi};
    }
}
