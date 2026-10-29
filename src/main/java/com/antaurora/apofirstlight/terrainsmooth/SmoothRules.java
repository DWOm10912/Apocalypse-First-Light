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
 * (y - 2 .. y + 3, #deep): its surface height H (top block + 1) and kind. Every block corner point gets one height from
 * the four columns around it (the same for every block touching it, so tops meet): pinned (each block keeps its own H)
 * when one of the four is not natural surface, or they differ by 3 or more; mode A the lowest of the four, mode B their
 * mean; when they differ by exactly 2, the middle (the one height every block there can draw: dig R1, a hole two deep
 * slopes in instead of standing as a shaft). When the four differ by at most two, every block touching the point reads
 * all four inside its window, so the window never changes the answer. Terrain rework R2f (#stairs): when the four are
 * plain natural columns at most one apart and the point lies on a staircase (a gentle slope's terraces), mode B puts it
 * on the line between the terrace's two steps instead of their mean, so a gentle slope tilts every block and not only
 * the two beside each step.
 * <p>
 * Dig R1 (user 2026-10-26, "挖下去就是一个很奇怪的坑"): a column lower than both its neighbours along x or along z (a dug
 * hole, a trench) or higher than both (a placed block, a ridge) has a centre point at its own top: the hole was drawn as a
 * dent a quarter deep, the corners' mean of three high columns and one low. Its top is then a fan of eight triangles
 * around the centre through the four corners and the four edge midpoints (#surface); an edge's midpoint lies on the line
 * between its corners, except between two such columns of the same height and the same kind along the edge (a trench's
 * floor, a ridge's crest), where it stands at their top too, so a trench is one trough and not a row of dents.
 * <p>
 * A graded_turf column (the road's earthworks, docs/worldgen/federal_highways_c3.md 10.4) has its own corner heights:
 * a corner point it touches takes the turf's height there, so the natural ground meets the earthworks without a step,
 * when the turf passes the same spread test as a natural column and every natural block there can draw that height;
 * else the point is pinned like any other (#corner; a band then closes the step down to the turf's edge).
 * <p>
 * A tree's column (a trunk or root, TREE, standing on its column's natural surface: K_TRUNK, terrain rework 13.13) is
 * not a wall: a corner point it touches takes the height of the ground under the trunk, on the same terms as turf, so
 * the ground rises or falls to meet the trunk's foot and the block under the trunk (a cube, not open above) never shows
 * a side (user 2026-10-28: a star of bare soil walls round a trunk on a slope, "是不是有点丑").
 * <p>
 * Geometry key (long, -1 = the block keeps its vanilla shape): corner heights nw, ne, sw, se above B's floor in quarters
 * (bits 0-15, 4 each, 0..8), then for the N, S, W, E sides the neighbour's surface at the two ends of that edge (bottom of
 * the visible side band, clamped to the top; bits 16-47); bit 48 the centre at the top (dig R1), bits 49-52 the N, S, W,
 * E edge midpoints at the top.
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
    static final int K_NAT = 0, K_FIXED = 1, K_FLUID = 2, K_LOW = 3, K_HIGH = 4, K_UNKNOWN = 5, K_ROCK = 6, K_TRUNK = 7;
    /**
     * Collision / selection boxes per side of a block. Offline (SmoothCheck, mode B): 2 x 2 leaves feet up to 0.375 in or
     * over the drawn top and 0.5 steps; 4 x 4 0.188 (p95 0.094) and 0.25 steps; 8 x 8 0.125 (p95 0.063) and 1/8 steps.
     */
    public static final int GRID = 8;

    /**
     * How many columns away a block change can re-shape a top: 2 (rework R0 10.5, 2026-10-24). A top reads its neighbour
     * columns (#column), and a neighbour's kind reads that neighbour's own neighbours: GroundClass#soilWall (U6 V3) asks whether the
     * soil beside it is open. So a dig two columns away can turn a neighbour into a soil wall and pin this top. 3 since
     * terrain rework R2b (SmoothTop): a smooth top reads its four neighbours' geometry keys and corner points two columns out.
     * TERRACE + 2 since R2f (#stairs): a corner point reads the natural surfaces along its lines up to TERRACE columns
     * out, its block's outer ring of corner points (SmoothTop#points) and its neighbours' corners one further.
     */
    public static final int REACH = 12;   // TERRACE + 2

    public interface Cells {
        int cell(int x, int y, int z);

        /** y of the column's natural surface block, or NO_SURFACE (see #surface(Cells, int, int, int, int)) */
        int surface(int x, int z);

        /** Absolute height of corner k (nw, ne, sw, se) of a graded_turf block at (x, y, z); NaN for any other block. */
        default float turf(int x, int y, int z, int k) { return Float.NaN; }

        /** SmoothRules#stairs at corner point (px, pz); the game's cells keep it per chunk (SmoothChunkData#stairs). */
        default float stairs(int px, int pz) { return SmoothRules.stairs(this, px, pz); }
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

    /**
     * Dig R1: column k (#column at level y) read a level further each way where it found nothing (K_LOW) or a wall
     * (K_HIGH): a natural top two under B's (a hole two deep beside it) or two over (its rim, seen from the hole's floor)
     * is a natural column, not a drop or a wall. Only the geometry reads this far; the earthwork's #notFree keeps the near
     * window.
     */
    static long deep(Cells c, long k, int x, int y, int z) {
        if (kind(k) == K_LOW) {
            int cell = c.cell(x, y - 2, z), above = c.cell(x, y - 1, z);
            if (solid(cell) && above != UNKNOWN && kindAt(c, cell, above, x, y - 2, z) == K_NAT) return pack(K_NAT, y - 1) | k & SOLID_HERE;
        } else if (kind(k) == K_HIGH) {
            int cell = c.cell(x, y + 2, z), above = c.cell(x, y + 3, z);
            if (solid(cell) && above != UNKNOWN && kindAt(c, cell, above, x, y + 2, z) == K_NAT) return pack(K_NAT, y + 3) | k & SOLID_HERE;
        }
        return k;
    }

    /** Column (x, z)'s natural surface is wet ground (R0): its water reads as air for the slopes. */
    static boolean wetColumn(Cells c, int x, int z) {
        int s = c.surface(x, z);
        return s != NO_SURFACE && c.cell(x, s, z) == WET;
    }

    private static long kindOf(Cells c, int c0, int x, int y, int z) {
        long trunk = trunk(c, x, y, z);
        if (trunk >= 0) return trunk;
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

    /**
     * Terrain rework 13.13: column (x, z) as a tree's column read near level y: K_TRUNK at the top of the ground under the
     * trunk (its natural surface, natural ground with a trunk or root block on it, from two below y to two above), else -1.
     */
    private static long trunk(Cells c, int x, int y, int z) {
        int s = c.surface(x, z);
        if (s == NO_SURFACE || s < y - 2 || s > y + 2 || c.cell(x, s + 1, z) != TREE || !natural(c.cell(x, s, z))) return -1;
        return pack(K_TRUNK, s + 1) | (s >= y ? SOLID_HERE : 0);
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
     * draw it (0..2 above its floor); otherwise the point is pinned for everyone. A tree's column (K_TRUNK, 13.13) gives the
     * top of the ground under its trunk on the same terms.
     */
    static float corner(Cells cells, int px, int pz, long[] col, float[][] turf, int oi, int oj, int mode) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, sum = 0, nlo = Integer.MAX_VALUE, nhi = Integer.MIN_VALUE;
        int rlo = Integer.MAX_VALUE, rhi = Integer.MIN_VALUE, rsum = 0;
        float t = Float.NaN, k = Float.NaN;
        boolean pinned = false;
        for (int j = oj; j <= oj + 1; j++) for (int i = oi; i <= oi + 1; i++) {
            long c = col[j * 3 + i];
            if (kind(c) == K_ROCK) {
                rlo = Math.min(rlo, height(c));
                rhi = Math.max(rhi, height(c));
                rsum += height(c);
                continue;
            }
            if (kind(c) == K_TRUNK) {   // the ground under a trunk: its height here (a wide trunk on a slope: the highest)
                k = Float.isNaN(k) ? height(c) : Math.max(k, height(c));
                lo = Math.min(lo, height(c));
                hi = Math.max(hi, height(c));
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
        // a tree's column (13.13): the ground meets the trunk's foot, where every natural block there can draw it
        if (!Float.isNaN(k)) {
            if (pinned || !Float.isNaN(t) || rhi != Integer.MIN_VALUE || hi - lo > 2) return Float.NaN;
            return nlo == Integer.MAX_VALUE || k >= nhi - 1 && k <= nlo + 1 ? k : Float.NaN;
        }
        // natural columns two apart (dig R1): the middle, the one height the high block (down to its floor) and the low one
        // (up to two over its floor) both draw; rock and turf keep the step of one
        boolean plain = rhi == Integer.MIN_VALUE && Float.isNaN(t);
        if (pinned || hi - lo > (plain ? 2 : 1)) return Float.NaN;
        if (!Float.isNaN(t)) return t >= nhi - 1 && t <= nlo + 1 ? t : Float.NaN;
        if (hi - lo == 2) return lo + 1;
        if (mode == MODE_A) return lo;
        if (!plain) return sum * 0.25F;
        float v = cells.stairs(px, pz);
        return v == NONE ? sum * 0.25F : Math.max(hi - 1, Math.min(lo + 1, v));
    }

    /** Terrain rework R2f: the widest terrace (columns between its two steps) a staircase reads across. */
    public static final int TERRACE = 10;
    /** #stairs: the point lies on no staircase. */
    public static final float NONE = Float.NEGATIVE_INFINITY;

    /**
     * Terrain rework R2f (docs/worldgen/terrain_rework_v1_design.md 13.10, user 2026-10-28: the far hillsides still read as
     * terraces): the height of corner point (px, pz) where it lies on a staircase, else #NONE. A gentle slope (one up every
     * three to ten columns) is rows of level terraces; the four columns at a point inside one are level, so their mean
     * left every block but the two beside each step flat. Along x through the point's two rows of columns, and along z
     * through its two columns, the terrace the point lies inside is read out to its two steps: when both go the same way
     * (up into it from one side and up out of it on the other, each one or two high, the terrace at most #TERRACE wide,
     * natural ground all along), the point lies on the line from the one step's middle to the other's. A terrace between
     * two steps up (a hole's floor, a valley) or two down (a ridge), or with one side level or built on, is no staircase:
     * the mean, as before (a dug hole stays at its true depth, a lone step keeps its 1:2 ramp). A line that is no staircase
     * gives its two columns' mean; x and z each count when a line of theirs is a staircase, averaged when both do. Only
     * natural surfaces are read, so every block touching the point, whatever its level, answers alike (SmoothTop#point
     * too); Cells#stairs may keep the answer.
     */
    public static float stairs(Cells c, int px, int pz) {
        float x0 = line(c, true, pz - 1, px), x1 = line(c, true, pz, px), z0 = line(c, false, px - 1, pz), z1 = line(c, false, px, pz);
        boolean xs = !Float.isNaN(x0) || !Float.isNaN(x1), zs = !Float.isNaN(z0) || !Float.isNaN(z1);
        if (!xs && !zs) return NONE;
        double vx = (or(x0, c, true, pz - 1, px) + or(x1, c, true, pz, px)) / 2, vz = (or(z0, c, false, px - 1, pz) + or(z1, c, false, px, pz)) / 2;
        return (float) (xs && zs ? (vx + vz) / 2 : xs ? vx : vz);
    }

    /** A line's staircase height v, or (NaN) its two columns' mean at the point. */
    private static double or(float v, Cells c, boolean alongX, int r, int p) {
        return Float.isNaN(v) ? (top(c, alongX, r, p - 1) + top(c, alongX, r, p)) / 2.0 : v;
    }

    /** Natural top (surface + 1) of the column at p along line r (x when alongX, else z), or NO_SURFACE. */
    private static int top(Cells c, boolean alongX, int r, int p) {
        int x = alongX ? p : r, z = alongX ? r : p, s = c.surface(x, z);
        return s == NO_SURFACE || !natural(c.cell(x, s, z)) ? NO_SURFACE : s + 1;
    }

    /** #stairs along one line: the staircase height at lattice position p (between columns p - 1 and p), or NaN. */
    private static float line(Cells c, boolean alongX, int r, int p) {
        int h = top(c, alongX, r, p - 1);
        if (h == NO_SURFACE || top(c, alongX, r, p) != h) return Float.NaN;   // a step at the point: its middle, the mean
        int lo = p - 1, hi = p, down, up;   // the terrace's first and last column
        while (true) {
            if (hi - lo + 1 > TERRACE) return Float.NaN;
            int n = top(c, alongX, r, lo - 1);
            if (n == NO_SURFACE) return Float.NaN;
            if (n != h) { down = h - n; break; }
            lo--;
        }
        while (true) {
            if (hi - lo + 1 > TERRACE) return Float.NaN;
            int n = top(c, alongX, r, hi + 1);
            if (n == NO_SURFACE) return Float.NaN;
            if (n != h) { up = n - h; break; }
            hi++;
        }
        if (down * up <= 0 || Math.abs(down) > 2 || Math.abs(up) > 2) return Float.NaN;
        double a = h - down / 2.0, b = h + up / 2.0;   // the steps' middles at lattice lo and hi + 1
        return (float) (a + (b - a) * (p - lo) / (hi + 1 - lo));
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
            col[j * 3 + i] = i == 1 && j == 1 ? pack(K_NAT, y + 1) | SOLID_HERE
                    : deep(c, column(c, x + i - 1, y, z + j - 1), x + i - 1, y, z + j - 1);
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
            float v = corner(c, x + CORNER[k][0], z + CORNER[k][1], col, turf, CORNER[k][0], CORNER[k][1], mode);
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
            int qa = Math.min(q(abs[SIDE[s][1]] - y), hollow ? 0 : lower(c, x, z, n, col, turf, SIDE[s][0], SIDE[s][1], mode, y));
            int qb = Math.min(q(abs[SIDE[s][2]] - y), hollow ? 0 : lower(c, x, z, n, col, turf, SIDE[s][0], SIDE[s][2], mode, y));
            g |= (long) qa << (16 + 8 * s) | (long) qb << (20 + 8 * s);
        }
        return g | fan(col, h);
    }

    /** Geometry key bits (dig R1): the centre at the top; the N, S, W, E edge midpoints at the top. */
    public static final long CENTRE = 1L << 48, MID_N = 1L << 49, MID_S = 1L << 50, MID_W = 1L << 51, MID_E = 1L << 52;
    public static final long FAN = 31L << 48;

    /**
     * Dig R1: the centre and midpoint bits of the block whose 3 x 3 columns are col, its top h. A neighbour's kind along the
     * edge is read in this block's window from the same level as its own (its top is h), so both blocks answer alike.
     */
    static long fan(long[] col, int h) {
        int sx = sense(col[3], col[4], col[5]), sz = sense(col[1], col[4], col[7]);
        if (sx == 0 && sz == 0) return 0;
        long f = CENTRE;
        // a trench or a crest running north-south: its floor goes on into the columns north and south of the same kind
        if (sx != 0 && height(col[1]) == h && sense(col[0], col[1], col[2]) == sx) f |= MID_N;
        if (sx != 0 && height(col[7]) == h && sense(col[6], col[7], col[8]) == sx) f |= MID_S;
        if (sz != 0 && height(col[3]) == h && sense(col[0], col[3], col[6]) == sz) f |= MID_W;
        if (sz != 0 && height(col[5]) == h && sense(col[2], col[5], col[8]) == sz) f |= MID_E;
        return f;
    }

    /** -1 when the middle column m is lower than both a and b, 1 when higher than both, else 0 (natural columns only). */
    private static int sense(long a, long m, long b) {
        if (kind(a) != K_NAT || kind(m) != K_NAT || kind(b) != K_NAT) return 0;
        int ha = height(a), hm = height(m), hb = height(b);
        return ha > hm && hb > hm ? -1 : ha < hm && hb < hm ? 1 : 0;
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
            "water", "two or more lower", "two or more higher (a wall)", "not loaded", "bare rock", "a tree"};
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
        return notFree(c, mode, x, y, z, walls, false);
    }

    /**
     * The same; cliffs (terrain rework R3b, docs/worldgen/terrain_rework_v1_design.md 14.4, with walls): a neighbour three
     * or more lower is a wall too, so the rim of a cliff of loose ground is free. The vertices it shares with the foot stay
     * where they are (Earthwork#cliff) and EarthSkin closes the side toward the drop.
     */
    public static String notFree(Cells c, int mode, int x, int y, int z, boolean walls, boolean cliffs) {
        return notFree(c, mode, x, y, z, walls, cliffs, false);
    }

    /**
     * The same; water (terrain rework R3a, 14.7, with walls): wet ground (a lake's, the sea's bed under vanilla water) is free
     * too, open above under its water, and a neighbour that is water (wet ground, or water over dry ground) is a wall, so the
     * shore beside the water is free. The earthwork lets the vanilla water follow the dig (Earthwork#settle).
     */
    public static String notFree(Cells c, int mode, int x, int y, int z, boolean walls, boolean cliffs, boolean water) {
        int self = c.cell(x, y, z);
        if (c.surface(x, z) != y || self != NATURAL && self != ROCK && !(water && self == WET)) return "its top is not Terrain Smooth's natural surface";
        if (!(water ? openOver(self, c.cell(x, y + 1, z)) : open(c.cell(x, y + 1, z)))) return "the block above is not open";
        long[] col = new long[9];
        boolean wall = false;
        for (int j = 0; j < 3; j++) for (int i = 0; i < 3; i++) {
            int nx = x + i - 1, nz = z + j - 1;
            col[j * 3 + i] = i == 1 && j == 1 ? pack(K_NAT, y + 1) | SOLID_HERE : column(c, nx, y, nz);
            int k = kind(col[j * 3 + i]);
            // wet ground beside (R0) is water to the earthwork: its skin must not open a shore
            if (!(i == 1 && j == 1) && wetColumn(c, nx, nz) && !water) return "neighbour " + nx + " " + nz + " is water";
            // bare rock beside (U6 V3, never sloped) is as a wall: EarthSkin closes the side toward it
            if (walls && (k == K_HIGH || k == K_ROCK || k == K_TRUNK || k == K_LOW && (cliffs || stepDown(c, nx, y - 2, nz))
                    || water && (k == K_FLUID || wetColumn(c, nx, nz)))) wall = true;
            else if (k == K_LOW && walls) return "neighbour " + nx + " " + nz + " is three or more lower (a cliff)";
            else if (k != K_NAT) return "neighbour " + nx + " " + nz + " is " + KIND_NAMES[k];
        }
        if (wall) return null;
        float[][] turf = new float[9][];
        for (int k = 0; k < 4; k++)
            if (Float.isNaN(corner(c, x + CORNER[k][0], z + CORNER[k][1], col, turf, CORNER[k][0], CORNER[k][1], mode)))
                return "its " + CORNER_NAMES[k] + " corner is pinned (the four columns there differ by two or more)";
        return null;
    }

    /** Column (x, z)'s natural surface is natural ground at y, open above: the foot of a two-cell step below a rim. */
    private static boolean stepDown(Cells c, int x, int y, int z) {
        int k = c.cell(x, y, z);
        return c.surface(x, z) == y && (k == NATURAL || k == ROCK) && open(c.cell(x, y + 1, z));
    }

    /** Bottom of B's side band at a corner, in quarters above B's floor: the neighbour column's surface there. */
    private static int lower(Cells c, int x, int z, long n, long[] col, float[][] turf, int index, int cornerIndex, int mode, int y) {
        int oi = CORNER[cornerIndex][0], oj = CORNER[cornerIndex][1];
        // a turf neighbour: rounded down, so the band starts at or below the turf's edge (turf corners are eighths, the
        // block's own corners quarters rounded half up: the block may stand 1/8 above the turf, and its band closes that)
        if (turf[index] != null)
            return Math.max(0, Math.min(8, (int) Math.floor((turf[index][(oi + 1 - index % 3) + 2 * (oj + 1 - index / 3)] - y) * 4 + 1e-4F)));
        return switch (kind(n)) {
            // a tree's column: down to the ground under its trunk at most (a wide trunk's lower foot: behind the trunk)
            case K_TRUNK -> { float v = corner(c, x + oi, z + oj, col, turf, oi, oj, mode); yield q((Float.isNaN(v) ? height(n) : Math.min(v, height(n))) - y); }
            case K_NAT -> { float v = corner(c, x + oi, z + oj, col, turf, oi, oj, mode); yield q((Float.isNaN(v) ? height(n) : v) - y); }
            case K_FIXED, K_HIGH, K_ROCK -> q(height(n) - y);
            default -> 0;   // water, a drop of two or more, not loaded: the whole side shows
        };
    }

    /**
     * Corner heights nw, ne, sw, se above the floor; with a centre (dig R1, #CENTRE) nine points: then also the centre and
     * the N, S, W, E edge midpoints (#surface).
     */
    public static float[] corners(long g) {
        boolean fan = (g & CENTRE) != 0;
        float[] c = new float[fan ? 9 : 4];
        for (int k = 0; k < 4; k++) c[k] = (g >>> (4 * k) & 15) / 4F;
        if (fan) {
            c[4] = 1;
            c[5] = (g & MID_N) != 0 ? 1 : (c[0] + c[1]) / 2;
            c[6] = (g & MID_S) != 0 ? 1 : (c[2] + c[3]) / 2;
            c[7] = (g & MID_W) != 0 ? 1 : (c[0] + c[2]) / 2;
            c[8] = (g & MID_E) != 0 ? 1 : (c[1] + c[3]) / 2;
        }
        return c;
    }

    /**
     * Whether a plain top (four corners) splits on the sw-ne diagonal instead of nw-se (dig R1): on the one whose corners
     * differ more. A lone corner off the other three (a hole's corner block, the rim of a mound) folds through it into two
     * equal slopes; split the other way it showed a flat shelf beside a steep triangle (user 2026-10-26: "坑坑洼洼").
     */
    public static boolean swNe(float[] c) { return Math.abs(c[2] - c[1]) > Math.abs(c[0] - c[3]) + 1e-6F; }

    /**
     * Height above the floor of the drawn top at (fx, fz) in 0..1: two triangles split on one diagonal (#swNe); with nine
     * points (#corners) eight, two per quarter, each from the centre to the quarter's corner and one of its edge midpoints.
     */
    public static double surface(float[] c, double fx, double fz) {
        if (c.length < 9 && swNe(c))
            return fx + fz <= 1 ? c[0] + (c[1] - c[0]) * fx + (c[2] - c[0]) * fz : c[3] + (c[2] - c[3]) * (1 - fx) + (c[1] - c[3]) * (1 - fz);
        if (c.length < 9)
            return fz >= fx ? c[0] + (c[3] - c[2]) * fx + (c[2] - c[0]) * fz : c[0] + (c[1] - c[0]) * fx + (c[3] - c[1]) * fz;
        boolean east = fx >= 0.5, south = fz >= 0.5;
        double u = Math.abs(fx - 0.5) * 2, v = Math.abs(fz - 0.5) * 2;   // from the centre (0) to the quarter's corner (1, 1)
        float k = c[south ? (east ? 3 : 2) : (east ? 1 : 0)], mx = c[east ? 8 : 7], mz = c[south ? 6 : 5], m = c[4];
        return u >= v ? m + (mx - m) * (u - v) + (k - m) * v : m + (mz - m) * (v - u) + (k - m) * u;
    }

    /**
     * The drawn top's slope {d height / dx, d height / dz} at (fx, fz) (#surface, any of its splits and fans; on a crease
     * one of the faces meeting there): the vehicles' ground normal.
     */
    public static double[] slope(float[] c, double fx, double fz) {
        double e = 1e-4, x0 = Math.min(fx, 1 - e), z0 = Math.min(fz, 1 - e), s = surface(c, x0, z0);
        return new double[]{(surface(c, x0 + e, z0) - s) / e, (surface(c, x0, z0 + e) - s) / e};
    }

    /**
     * For a quad drawn over part (x0, z0)..(x1, z1) of a top from heights read off #surface at its corners (the wet ground
     * overlays): whether it must split sw-ne to lie on the top, the diagonal whose middle meets the top there (a fan's
     * creases and a plain top's diagonal run along the halves' diagonals).
     */
    public static boolean swNe(float[] c, double x0, double z0, double x1, double z1) {
        double m = surface(c, (x0 + x1) / 2, (z0 + z1) / 2);
        double nwSe = (surface(c, x0, z0) + surface(c, x1, z1)) / 2, swNe = (surface(c, x0, z1) + surface(c, x1, z0)) / 2;
        return Math.abs(swNe - m) < Math.abs(nwSe - m) - 1e-6;
    }

    /**
     * The drawn top as quads (corner points {fx, height above the floor, fz}), each in the order nw, sw, se, ne of the
     * plain top: one quad, or with a centre one per quarter, centre first, so a quad's first diagonal (the one renderers
     * split on) runs from the centre to the corner as #surface's. A plain top split sw-ne (#swNe) starts at sw.
     */
    public static float[][][] topQuads(float[] c) {
        if (c.length < 9 && swNe(c)) return new float[][][]{{{0, c[2], 1}, {1, c[3], 1}, {1, c[1], 0}, {0, c[0], 0}}};
        if (c.length < 9) return new float[][][]{{{0, c[0], 0}, {0, c[2], 1}, {1, c[3], 1}, {1, c[1], 0}}};
        float[] m = {0.5F, c[4], 0.5F}, n = {0.5F, c[5], 0}, s = {0.5F, c[6], 1}, w = {0, c[7], 0.5F}, e = {1, c[8], 0.5F};
        float[] nw = {0, c[0], 0}, ne = {1, c[1], 0}, sw = {0, c[2], 1}, se = {1, c[3], 1};
        // the plain top's turning sense (nw, sw, se, ne), quarter by quarter
        return new float[][][]{{m, n, nw, w}, {m, w, sw, s}, {m, s, se, e}, {m, e, ne, n}};
    }

    /** Box heights in 1/16 above the floor of the GRID x GRID collision / selection boxes ([j * GRID + i], i along x). */
    public static int[] boxes(long g) { return boxes(g, GRID); }

    /** The same for any grid (the offline check compares 2, 4 and 8): each box reaches the drawn top at its centre. */
    public static int[] boxes(long g, int grid) {
        return boxes(corners(g), grid);
    }

    /** The same for a drawn top as #corners gives it (four corners, or nine points: a fan, a smooth top SmoothTop#top). */
    public static int[] boxes(float[] c, int grid) {
        int[] out = new int[grid * grid];
        for (int j = 0; j < grid; j++) for (int i = 0; i < grid; i++)
            out[j * grid + i] = (int) Math.max(0, Math.min(32, Math.round(surface(c, (i + 0.5) / grid, (j + 0.5) / grid) * 16)));
        return out;
    }

    /**
     * What a block change at (x, y, z) re-shapes: the blocks of columns within #REACH from y - 3 to y + 4 (a block at level
     * t reads its neighbours from t - 2 to t + 3, #deep, and their kinds through GroundClass#soilWall from two below their
     * tops: down to t - 4, dig R1), and around the changed column's natural surface before and after (its neighbours'
     * corners read whether it takes part): {min y, max y} of the affected blocks, the surfaces counted 3 below to 2 above.
     */
    public static int[] affected(int y, int oldSurface, int newSurface) {
        int lo = y - 3, hi = y + 4;
        for (int s : new int[]{oldSurface, newSurface}) {
            if (s == NO_SURFACE) continue;
            lo = Math.min(lo, s - 3);
            hi = Math.max(hi, s + 2);
        }
        return new int[]{lo, hi};
    }
}
