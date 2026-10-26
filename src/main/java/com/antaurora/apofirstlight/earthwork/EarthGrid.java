package com.antaurora.apofirstlight.earthwork;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Earthwork A1 (docs/worldgen/earthwork_a1.md, docs/gameplay/sandbox_v1_design.md 3): the editable height field's grid
 * and its pure math, with no game classes, so the offline check (EarthworkCheck) runs exactly what the game runs.
 * <ul>
 *   <li>Vertices every 1 / {@link #PER_BLOCK} block (0.25 m; a parameter still to be verified in game, user 2026-10-21),
 *   {@link #CHUNK} x {@link #CHUNK} owned by each chunk. A block column reads (PER_BLOCK + 1)^2 of them; its east and south
 *   row belong to the next column (and at a chunk's edge to the next chunk).</li>
 *   <li>Heights are integer quanta, {@link #Q} per block. Every grid cell is split on the nw-se diagonal (the
 *   diagonal Terrain Smooth draws its tops on), so every vertex is in six triangles and one quantum of one vertex is
 *   exactly cell area / Q of volume: volumes are sums of integers.</li>
 *   <li>Block cells of a column (heights h of its vertices): all h &gt;= top of the cell, FULL (a vanilla cube); all h &lt;=
 *   floor, AIR; else SURFACE, drawn and collided as the part of the skin inside it. A FULL cell and an AIR cell at the same
 *   level never touch sideways (they would share an edge vertex both above and below it), so only the skin is drawn.</li>
 * </ul>
 */
public final class EarthGrid {
    /** vertices per block along x and z (0.25 m spacing) */
    public static final int PER_BLOCK = 4;
    /** vertices of a column along x and z */
    public static final int SIDE = PER_BLOCK + 1;
    /** vertices owned by a chunk along x and z */
    public static final int CHUNK = 16 * PER_BLOCK;
    /** height quanta per block (1/256 m) */
    public static final int Q = 256;
    /** a vertex without a height (not part of an edited column yet) */
    public static final int NONE = Integer.MIN_VALUE;
    /** litres of one quantum of one vertex: one grid cell's area times 1/Q m */
    public static final double LITRES_PER_QUANTUM = 1000.0 / (PER_BLOCK * PER_BLOCK * Q);
    /**
     * A vertex less than this far from its first height (1/8 block) still has its turf (A1.1: the brush's edge moves
     * vertices by a few quanta, which shows nowhere and must not bare a ring of ground).
     */
    public static final int TURF = Q / 8;
    /** Rounds of slumping per edit at most (docs/worldgen/earthwork_a1.md 2.4). */
    public static final int SLUMP_ROUNDS = 64;

    public static final int AIR = 0, SURFACE = 1, FULL = 2;

    private EarthGrid() {}

    /** chunk-local vertex index */
    public static int vertex(int lvx, int lvz) { return lvz * CHUNK + lvx; }

    /** column vertex index, i along x, j along z (0..PER_BLOCK) */
    public static int at(int i, int j) { return j * SIDE + i; }

    /** chunk-local column index (as the chunk's 256 columns) */
    public static int column(int x, int z) { return (z & 15) << 4 | (x & 15); }

    /** a global vertex as one long (x high, z low) */
    public static long key(int vx, int vz) { return (long) vx << 32 | (vz & 0xFFFFFFFFL); }

    public static int keyX(long key) { return (int) (key >> 32); }

    public static int keyZ(long key) { return (int) key; }

    public static int min(int[] h) {
        int m = Integer.MAX_VALUE;
        for (int v : h) m = Math.min(m, v);
        return m;
    }

    public static int max(int[] h) {
        int m = Integer.MIN_VALUE;
        for (int v : h) m = Math.max(m, v);
        return m;
    }

    /** A vertex at h whose first height was base still shows its first top (turf, sand...). */
    public static boolean undisturbed(int h, int base) { return Math.abs(h - base) < TURF; }

    /** The largest height difference (quanta) between two neighbouring vertices on a slope of the given angle. */
    public static int step(double degrees) {
        return (int) Math.round(Math.tan(Math.toRadians(degrees)) * Q / PER_BLOCK);
    }

    /** Kind of block cell y of a column whose vertex heights span hmin..hmax. */
    public static int kind(int hmin, int hmax, int y) {
        if (hmin >= (y + 1) * Q) return FULL;
        if (hmax <= y * Q) return AIR;
        return SURFACE;
    }

    /** The highest cell of a column that is not AIR (the cells from the lowest SURFACE one up to it hold ground). */
    public static int top(int hmax) { return Math.floorDiv(hmax - 1, Q); }

    /**
     * Skin height at (fx, fz) in 0..1 over a column's (relative, any unit) vertex heights r[at(i, j)]: the grid cell's two
     * triangles nw-sw-se (fz &gt;= fx inside the cell) and nw-se-ne, as Terrain Smooth's SmoothRules#surface over a block.
     */
    public static double skin(double[] r, double fx, double fz) {
        double gx = Math.max(0, Math.min(PER_BLOCK, fx * PER_BLOCK)), gz = Math.max(0, Math.min(PER_BLOCK, fz * PER_BLOCK));
        int i = Math.min(PER_BLOCK - 1, (int) gx), j = Math.min(PER_BLOCK - 1, (int) gz);
        double u = gx - i, v = gz - j;
        double nw = r[at(i, j)], ne = r[at(i + 1, j)], sw = r[at(i, j + 1)], se = r[at(i + 1, j + 1)];
        return v >= u ? nw + (se - sw) * u + (sw - nw) * v : nw + (ne - nw) * u + (se - ne) * v;
    }

    /**
     * The skin of a column's absolute vertex heights h (quanta) at (fx, fz) in 0..1 for a vehicle's tyre (A3): out[0] its
     * height in blocks (#skin), out[1..3] the upward unit normal of the triangle there. The nearest vertex's (i, j) in
     * out[4], out[5].
     */
    public static void ground(int[] h, double fx, double fz, double[] out) {
        double gx = Math.max(0, Math.min(PER_BLOCK, fx * PER_BLOCK)), gz = Math.max(0, Math.min(PER_BLOCK, fz * PER_BLOCK));
        int i = Math.min(PER_BLOCK - 1, (int) gx), j = Math.min(PER_BLOCK - 1, (int) gz);
        double u = gx - i, v = gz - j;
        double nw = h[at(i, j)] / (double) Q, ne = h[at(i + 1, j)] / (double) Q, sw = h[at(i, j + 1)] / (double) Q, se = h[at(i + 1, j + 1)] / (double) Q;
        // the triangle's slopes per block (#skin: split on the nw-se diagonal)
        double dx = (v >= u ? se - sw : ne - nw) * PER_BLOCK, dz = (v >= u ? sw - nw : se - ne) * PER_BLOCK;
        double len = Math.sqrt(dx * dx + 1 + dz * dz);
        out[0] = v >= u ? nw + (se - sw) * u + (sw - nw) * v : nw + (ne - nw) * u + (se - ne) * v;
        out[1] = -dx / len;
        out[2] = 1 / len;
        out[3] = -dz / len;
        out[4] = Math.round(gx);
        out[5] = Math.round(gz);
    }

    /** A column's heights relative to the floor of cell y, in blocks. */
    public static double[] relative(int[] h, int y) {
        double[] r = new double[h.length];
        for (int k = 0; k < h.length; k++) r[k] = (h[k] - (double) y * Q) / Q;
        return r;
    }

    /**
     * Collision (grid 8) or selection (grid 4) box heights of SURFACE cell y in sixteenths, [j * grid + i]: each box up to
     * the skin at its centre, rounded, within the cell (the cell above, if it holds ground, has its own boxes), minus sink
     * sixteenths (mud). Terrain Smooth's rule (SmoothRules#boxes), so walking feels the same on edited ground.
     */
    public static int[] boxes(int[] h, int y, int grid, int sink) {
        double[] r = relative(h, y);
        int[] out = new int[grid * grid];
        for (int j = 0; j < grid; j++) for (int i = 0; i < grid; i++) {
            long top = Math.round(skin(r, (i + 0.5) / grid, (j + 0.5) / grid) * 16);
            out[j * grid + i] = (int) Math.max(0, Math.min(16, top) - sink);
        }
        return out;
    }

    // ----------------------------------------------------------------------------------------------- the drawn skin

    /**
     * One polygon of skin inside a cell: points {x, y, z} in block units relative to the cell, x / z in 0..1; loose: the
     * material of the loose earth on it (the highest of its vertices', A2), -1 none.
     */
    public record Piece(List<double[]> points, boolean disturbed, int loose) {}

    /**
     * The skin pieces SURFACE cell y draws: every triangle of the column clipped to the cell's height range, keeping a
     * piece that lies flat on the floor (the cell below is FULL, whose top face this cell's block hides, or SURFACE, which
     * leaves its own ceiling to this cell) and dropping one flat on the ceiling when the cell above holds ground (it draws
     * it as its floor). So every point of the skin is drawn by exactly one cell (EarthworkCheck#partition).
     * undisturbed[k]: vertex k still at its first height (Terrain Smooth's original top, drawn with the original texture);
     * a triangle with any moved vertex is disturbed (bare ground).
     */
    public static List<Piece> pieces(int[] h, boolean[] undisturbed, int y) {
        return pieces(h, undisturbed, null, y);
    }

    /** The same with the material of each vertex's loose earth (-1 none; null: none anywhere). */
    public static List<Piece> pieces(int[] h, boolean[] undisturbed, int[] loose, int y) {
        double[] r = relative(h, y);
        boolean dropCeiling = max(h) > (y + 1) * Q;
        List<Piece> out = new ArrayList<>();
        double s = 1.0 / PER_BLOCK;
        for (int j = 0; j < PER_BLOCK; j++) for (int i = 0; i < PER_BLOCK; i++) {
            int nw = at(i, j), ne = at(i + 1, j), sw = at(i, j + 1), se = at(i + 1, j + 1);
            double[] pnw = {i * s, r[nw], j * s}, pne = {(i + 1) * s, r[ne], j * s}, psw = {i * s, r[sw], (j + 1) * s}, pse = {(i + 1) * s, r[se], (j + 1) * s};
            // the two triangles in Terrain Smooth's quad order (nw, sw, se, ne): (nw, sw, se) and (nw, se, ne)
            add(out, clip(List.of(pnw, psw, pse)), dropCeiling, !(undisturbed[nw] && undisturbed[sw] && undisturbed[se]), loose(loose, nw, sw, se));
            add(out, clip(List.of(pnw, pse, pne)), dropCeiling, !(undisturbed[nw] && undisturbed[se] && undisturbed[ne]), loose(loose, nw, se, ne));
        }
        return out;
    }

    private static final double EPS = 1e-9;

    private static int loose(int[] loose, int a, int b, int c) {
        return loose == null ? -1 : Math.max(loose[a], Math.max(loose[b], loose[c]));
    }

    private static void add(List<Piece> out, List<double[]> poly, boolean dropCeiling, boolean disturbed, int loose) {
        if (poly.size() < 3) return;
        boolean onCeiling = true;
        for (double[] p : poly) if (p[1] < 1 - EPS) onCeiling = false;
        if (onCeiling && dropCeiling) return;
        if (Math.abs(projectedArea(poly)) < 1e-12) return;   // vertical: no area seen from above (a wall's edge)
        out.add(new Piece(poly, disturbed || loose >= 0, loose));
    }

    /** A polygon clipped to 0 &lt;= y &lt;= 1 (Sutherland-Hodgman on y), winding kept. */
    static List<double[]> clip(List<double[]> poly) {
        return clipBelow(clipAbove(poly, 0), 1);
    }

    private static List<double[]> clipAbove(List<double[]> in, double lo) {
        List<double[]> out = new ArrayList<>(in.size() + 2);
        for (int k = 0; k < in.size(); k++) {
            double[] a = in.get(k), b = in.get((k + 1) % in.size());
            boolean ia = a[1] >= lo, ib = b[1] >= lo;
            if (ia) out.add(a);
            if (ia != ib) out.add(cut(a, b, lo));
        }
        return out;
    }

    private static List<double[]> clipBelow(List<double[]> in, double hi) {
        List<double[]> out = new ArrayList<>(in.size() + 2);
        for (int k = 0; k < in.size(); k++) {
            double[] a = in.get(k), b = in.get((k + 1) % in.size());
            boolean ia = a[1] <= hi, ib = b[1] <= hi;
            if (ia) out.add(a);
            if (ia != ib) out.add(cut(a, b, hi));
        }
        return out;
    }

    private static double[] cut(double[] a, double[] b, double y) {
        double t = (y - a[1]) / (b[1] - a[1]);
        return new double[]{a[0] + (b[0] - a[0]) * t, y, a[2] + (b[2] - a[2]) * t};
    }

    /** Signed area of a polygon seen from above (x, z): positive when it faces up (the vanilla UP order nw, sw, se, ne). */
    public static double projectedArea(List<double[]> poly) {
        double a = 0;
        for (int k = 0; k < poly.size(); k++) {
            double[] p = poly.get(k), q = poly.get((k + 1) % poly.size());
            a += q[0] * p[2] - p[0] * q[2];
        }
        return a / 2;
    }

    // ----------------------------------------------------------------------------------------------- brushes

    /**
     * A round brush: for every vertex within radius (blocks) of (cx, cz), depth * w(d) in quanta, w = (1 + cos(pi d / r)) / 2,
     * rounded to whole quanta; sign -1 lowers, +1 raises. Global vertex keys to deltas (no zero entries).
     */
    public static it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap brush(double cx, double cz, double radius, double depth, int sign) {
        var out = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
        int x0 = (int) Math.floor((cx - radius) * PER_BLOCK), x1 = (int) Math.ceil((cx + radius) * PER_BLOCK);
        int z0 = (int) Math.floor((cz - radius) * PER_BLOCK), z1 = (int) Math.ceil((cz + radius) * PER_BLOCK);
        for (int vz = z0; vz <= z1; vz++) for (int vx = x0; vx <= x1; vx++) {
            double dx = (double) vx / PER_BLOCK - cx, dz = (double) vz / PER_BLOCK - cz, d = Math.sqrt(dx * dx + dz * dz);
            if (d >= radius) continue;
            int q = (int) Math.round(depth * Q * 0.5 * (1 + Math.cos(Math.PI * d / radius)));
            if (q != 0) out.put(key(vx, vz), sign * q);
        }
        return out;
    }

    /**
     * A cone brush (pouring, docs/gameplay/sandbox_v1_design.md 5.3): every vertex within radius (blocks) of (cx, cz)
     * weighted 1 - d / r in thousandths (no zero entries); the earth is shared out by #distribute.
     */
    public static Long2IntOpenHashMap cone(double cx, double cz, double radius) {
        var out = new Long2IntOpenHashMap();
        int x0 = (int) Math.floor((cx - radius) * PER_BLOCK), x1 = (int) Math.ceil((cx + radius) * PER_BLOCK);
        int z0 = (int) Math.floor((cz - radius) * PER_BLOCK), z1 = (int) Math.ceil((cz + radius) * PER_BLOCK);
        for (int vz = z0; vz <= z1; vz++) for (int vx = x0; vx <= x1; vx++) {
            double dx = (double) vx / PER_BLOCK - cx, dz = (double) vz / PER_BLOCK - cz, d = Math.sqrt(dx * dx + dz * dz);
            int w = (int) Math.round(1000 * (1 - d / radius));
            if (w > 0) out.put(key(vx, vz), w);
        }
        return out;
    }

    /**
     * total quanta split over weights (all &gt; 0) in proportion, the largest remainders (then the lowest keys) taking the
     * leftover quanta, so the parts sum to exactly total: earth put down is exactly the earth carried.
     */
    public static it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap distribute(long total, it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap weights) {
        var out = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
        long sum = 0;
        for (int w : weights.values()) sum += w;
        if (sum <= 0 || total <= 0) return out;
        long[] keys = weights.keySet().toLongArray();
        java.util.Arrays.sort(keys);
        long[] rem = new long[keys.length];
        long given = 0;
        for (int k = 0; k < keys.length; k++) {
            long num = total * weights.get(keys[k]);
            out.put(keys[k], (int) (num / sum));
            rem[k] = num % sum;
            given += num / sum;
        }
        Integer[] order = new Integer[keys.length];
        for (int k = 0; k < order.length; k++) order[k] = k;
        java.util.Arrays.sort(order, (a, b) -> rem[a] != rem[b] ? Long.compare(rem[b], rem[a]) : Integer.compare(a, b));
        for (int k = 0; given < total; k++, given++) out.addTo(keys[order[k]], 1);
        for (long k : keys) if (out.get(k) == 0) out.remove(k);
        return out;
    }

    /**
     * A vertex moved by delta, kept between lo and hi (the column floors and ceilings around it); a vertex already outside
     * that range only moves back towards it. The new height.
     */
    public static int clamp(int h, int delta, int lo, int hi) {
        int t = h + delta;
        if (delta < 0) return Math.min(h, Math.max(t, lo));
        if (delta > 0) return Math.max(h, Math.min(t, hi));
        return h;
    }

    /**
     * Rock R2: the lowest a shovel may take a vertex at h with l loose earth on it: its hard floor f where natural ground
     * still lies over that, else the rock's surface (only the loose earth comes off rock), never below r (the deepest the
     * rock may be cut; f where none may).
     */
    public static int shovelFloor(int f, int r, int h, int l) { return Math.max(r, Math.min(f, h - l)); }

    /**
     * Rock R2 round 3 (docs/gameplay/rock_r2_rock_face_mining_design.md 12): the lowest a pick's strike may cut a vertex at
     * h with l loose earth on it: down to r (the rock's depth limit) where the rock lies bare (r below the hard floor f, and
     * the ground there rock from the top: bare, else nothing but loose earth over f), else nowhere (h: natural ground over
     * the rock is the shovel's).
     */
    public static int pickFloor(int f, int r, int h, int l, boolean bare) { return r < f && (bare || h - l <= f) ? r : h; }

    /**
     * Rock R2 round 4 (docs/gameplay/rock_r2_rock_face_mining_design.md 13): how one segment of a skin cell's side closes
     * against an unedited neighbour. e0, e1: the skin's height at the segment's ends, t0, t1: the neighbour's ground there
     * (cell heights, unclamped, both linear along the segment). Pieces {u0, u1, bottom0, bottom1, top0, top1, inward}, u in
     * fractions of the segment, heights within the cell (0..1), each piece linear (split where either line crosses the
     * other or the cell's floor or top): inward (1) where the neighbour's ground stands higher (its side, facing into this
     * cell), outward (0) where the skin does (this column's side, facing the neighbour).
     */
    public static List<float[]> sideGaps(float e0, float e1, float t0, float t1) {
        float[] cuts = new float[7];
        int n = 0;
        cuts[n++] = 0;
        for (float[] l : new float[][]{{e0, e1}, {t0, t1}})
            for (float c : new float[]{0, 1}) n = cut(cuts, n, l[0] - c, l[1] - c);
        n = cut(cuts, n, t0 - e0, t1 - e1);
        cuts[n++] = 1;
        Arrays.sort(cuts, 0, n);
        List<float[]> out = new ArrayList<>(2);
        for (int k = 0; k + 1 < n; k++) {
            float p = cuts[k], q = cuts[k + 1];
            if (q - p < 1e-5F) continue;
            float m = (p + q) / 2, em = e0 + (e1 - e0) * m, tm = t0 + (t1 - t0) * m;
            if (Math.abs(tm - em) < 1e-5F) continue;
            boolean in = tm > em;
            float ep = clamp01(e0 + (e1 - e0) * p), eq = clamp01(e0 + (e1 - e0) * q);
            float tp = clamp01(t0 + (t1 - t0) * p), tq = clamp01(t0 + (t1 - t0) * q);
            float b0 = in ? ep : tp, b1 = in ? eq : tq, u0 = in ? tp : ep, u1 = in ? tq : eq;
            if (u0 - b0 < 1e-5F && u1 - b1 < 1e-5F) continue;
            out.add(new float[]{p, q, b0, b1, u0, u1, in ? 1 : 0});
        }
        return out;
    }

    /** Adds where a line from a to b (along 0..1) crosses zero strictly inside. */
    private static int cut(float[] cuts, int n, float a, float b) {
        if ((a < 0) == (b < 0) || a == b) return n;
        float u = a / (a - b);
        if (u > 0 && u < 1) cuts[n++] = u;
        return n;
    }

    private static float clamp01(float v) { return Math.max(0, Math.min(1, v)); }

    // ----------------------------------------------------------------------------------------------- slopes

    /** Slope a vertex next to locked ground may take against it at most: 1:1 (docs/gameplay/sandbox_v1_design.md 3.4). */
    public static final int GUARD_STEP = Q / PER_BLOCK;

    /**
     * The vertices one edit works on (docs/worldgen/earthwork_a1.md 2.4, docs/worldgen/earthwork_a2.md 2). Movable vertices
     * (keys of {@link #step}) have a range (their columns' hard floors and ceilings, {@link #lo} / {@link #hi}) and a step: the
     * largest height difference to a neighbour their natural ground stands at (its excavation angle). Loose earth on a
     * vertex ({@link #loose}, of material {@link #mat}) stands at its material's angle of repose instead ({@link #pile}).
     * Every other vertex with a height in {@link #h} is a wall: it never moves, and earth never moves into or out of it; a
     * wall in {@link #guard} is locked ground (a road, a house, a tree), which nothing next to it may undercut steeper than
     * 1:1. Without materials (pile null) every vertex is natural ground at its step (A1).
     */
    public static final class Field {
        public final Long2IntOpenHashMap h = new Long2IntOpenHashMap(), base = new Long2IntOpenHashMap();
        public final Long2IntOpenHashMap lo = new Long2IntOpenHashMap(), hi = new Long2IntOpenHashMap(), step = new Long2IntOpenHashMap();
        /** loose earth on a vertex (quanta), its material, the material of the natural ground under it */
        public final Long2IntOpenHashMap loose = new Long2IntOpenHashMap(), mat = new Long2IntOpenHashMap(), nat = new Long2IntOpenHashMap();
        public final LongOpenHashSet guard = new LongOpenHashSet();
        /** step of loose earth by material (its angle of repose), null: no materials; the material mixed earth becomes */
        public int[] pile;
        public int backfill;
        /** quanta taken out by material (#move), signed: earth put down is negative */
        public final Int2LongOpenHashMap taken = new Int2LongOpenHashMap();
        /** the bounds the walls set (#bound) */
        final Long2IntOpenHashMap low = new Long2IntOpenHashMap(), high = new Long2IntOpenHashMap();
        private long[] order;

        public Field() {
            h.defaultReturnValue(NONE);
        }

        public boolean movable(long v) { return step.containsKey(v); }

        long[] order() {
            if (order == null) {
                order = step.keySet().toLongArray();
                Arrays.sort(order);
            }
            return order;
        }

        /** The four neighbours of v along x and z. */
        static long[] around(long v) {
            int x = keyX(v), z = keyZ(v);
            return new long[]{key(x - 1, z), key(x + 1, z), key(x, z - 1), key(x, z + 1)};
        }

        /** The step of the earth on v's surface: its loose earth's repose, else its natural ground's excavation angle. */
        public int stepOf(long v) {
            return pile != null && loose.get(v) > 0 ? pile[mat.get(v)] : step.get(v);
        }

        /** The step earth put on v stands at against what is around it (the repose of what lies on it). */
        int pileOf(long v) {
            return pile == null ? step.get(v) : pile[loose.get(v) > 0 ? mat.get(v) : nat.get(v)];
        }

        /**
         * The range every movable vertex may take so that no slope against a wall gets steeper than it can stand: at least
         * the height of each wall minus the excavation step per vertex of distance, and minus 1:1 from locked ground; at
         * most plus the repose step. Computed before an edit; a vertex already outside it (natural ground steeper than
         * that) is never pushed.
         */
        public void bound() {
            long[] vs = order();
            Long2IntOpenHashMap guarded = new Long2IntOpenHashMap();
            guarded.defaultReturnValue(Integer.MIN_VALUE);
            for (long v : vs) {
                int l = lo.get(v), u = hi.get(v), s = step.get(v), p = pileOf(v), g = Integer.MIN_VALUE;
                for (long n : around(v)) {
                    if (movable(n) || h.get(n) == NONE) continue;
                    l = Math.max(l, h.get(n) - s);
                    u = Math.min(u, h.get(n) + p);
                    if (guard.contains(n)) g = Math.max(g, h.get(n) - GUARD_STEP);
                }
                low.put(v, l);
                high.put(v, u);
                if (g != Integer.MIN_VALUE) guarded.put(v, g);
            }
            for (int pass = 0, changed = 1; changed > 0 && pass < vs.length; pass++) {
                changed = 0;
                for (long v : vs) for (long n : around(v)) {
                    if (!movable(n)) continue;
                    int s = Math.min(step.get(v), step.get(n)), p = Math.min(pileOf(v), pileOf(n));
                    if (low.get(n) - s > low.get(v)) {
                        low.put(v, low.get(n) - s);
                        changed++;
                    }
                    if (high.get(n) + p < high.get(v)) {
                        high.put(v, high.get(n) + p);
                        changed++;
                    }
                    int g = guarded.get(n);
                    if (g != Integer.MIN_VALUE && g - GUARD_STEP > guarded.get(v)) {
                        guarded.put(v, g - GUARD_STEP);
                        changed++;
                    }
                }
            }
            for (long v : vs) {
                int g = guarded.get(v);
                if (g != Integer.MIN_VALUE) low.put(v, Math.min(Math.max(low.get(v), g), high.get(v)));
            }
        }

        /** No slopes: every movable vertex only within its own range (putting ground back: reset, reconcile). */
        public void unbound() {
            for (long v : order()) {
                low.put(v, lo.get(v));
                high.put(v, hi.get(v));
            }
        }

        /** Vertex v rises no higher than maxH in this edit (under an entity: docs/gameplay/sandbox_v1_design.md 5.3). */
        public void cap(long v, int maxH) { high.put(v, Math.min(high.get(v), Math.max(maxH, h.get(v)))); }

        /** How far movable vertex v would move by delta within its range and the walls' bounds (#move without moving). */
        public int allowed(long v, int delta) {
            int old = h.get(v);
            return clamp(old, delta, low.get(v), high.get(v)) - old;
        }

        /** Moves movable vertex v by delta within its range and the walls' bounds; the quanta it moved. */
        public int move(long v, int delta) {
            return move(v, delta, -1);
        }

        /**
         * The same with materials: earth taken comes off v's loose earth first, then its natural ground (#taken); earth put
         * down (of material put, -1 none) becomes loose earth on v, mixed with what lies there (#mix).
         */
        public int move(long v, int delta, int put) {
            int old = h.get(v), now = clamp(old, delta, low.get(v), high.get(v));
            h.put(v, now);
            int moved = now - old;
            if (moved < 0) take(v, -moved);
            else if (moved > 0 && put >= 0) lay(v, moved, put);
            return moved;
        }

        /** amount quanta off v's surface, loose earth first, counted in #taken; the material of its first part. */
        private int take(long v, int amount) {
            int l = loose.get(v), fromLoose = Math.min(l, amount);
            if (fromLoose > 0) {
                taken.addTo(mat.get(v), fromLoose);
                loose.put(v, l - fromLoose);
            }
            if (amount > fromLoose && pile != null) taken.addTo(nat.get(v), amount - fromLoose);
            return l > 0 ? mat.get(v) : nat.get(v);
        }

        /** amount quanta of material m laid on v as loose earth, counted in #taken (negative). */
        private void lay(long v, int amount, int m) {
            int l = loose.get(v);
            loose.put(v, l + amount);
            mat.put(v, l > 0 ? mix(mat.get(v), m) : m);
            taken.addTo(m, -amount);
        }

        /**
         * The moves of one edit: each vertex of wanted by its wanted delta, as far as its range and the walls' bounds let it
         * (#allowed); more than limit quanta in all is shared out over what each may move (#distribute), so a shovel never
         * takes more than its room nor puts down more than its load. Earth put down is of material put (-1 none). The quanta
         * moved, signed (earth taken out negative).
         */
        public long apply(Long2IntOpenHashMap wanted, int put, long limit) {
            Long2IntOpenHashMap moves = new Long2IntOpenHashMap();
            long want = 0;
            for (var en : wanted.long2IntEntrySet()) {
                long v = en.getLongKey();
                if (!movable(v)) continue;
                int a = allowed(v, en.getIntValue());
                if (a == 0) continue;
                moves.put(v, a);
                want += Math.abs(a);
            }
            if (want > limit) {
                Long2IntOpenHashMap w = new Long2IntOpenHashMap();
                for (var en : moves.long2IntEntrySet()) w.put(en.getLongKey(), Math.abs(en.getIntValue()));
                Long2IntOpenHashMap share = distribute(limit, w);
                for (long v : moves.keySet().toLongArray()) moves.put(v, Integer.signum(moves.get(v)) * share.get(v));
            }
            long moved = 0;
            long[] keys = moves.keySet().toLongArray();
            Arrays.sort(keys);
            for (long v : keys) if (moves.get(v) != 0) moved += move(v, moves.get(v), put);
            return moved;
        }

        /** Two materials laid together (docs/gameplay/sandbox_v1_design.md 4.3): the same stays, different is backfill. */
        public int mix(int a, int b) { return a == b ? a : backfill; }

        /**
         * Slumping: where a neighbour pair is steeper than the higher vertex's earth stands (#stepOf), whole quanta go from
         * the higher vertex to the lower one, half the excess at a time, in a fixed order (deterministic), at most
         * SLUMP_ROUNDS rounds; the total never changes. Loose earth slides first; natural ground only once the loose earth
         * on it is gone, at its excavation angle. What lands on a vertex is loose earth there. A pair still at its first
         * heights (natural ground, undisturbed) stands as it is, and no vertex leaves the walls' bounds, so slumping never
         * cuts a cliff into them. The quanta moved.
         */
        public long slump() {
            long[] vs = order();
            long moved = 0;
            for (int round = 0; round < SLUMP_ROUNDS; round++) {
                long now = 0;
                for (long v : vs) for (long n : around(v)) {
                    if (!movable(n)) continue;
                    int hv = h.get(v), hn = h.get(n);
                    if (hv <= hn || undisturbed(hv, base.get(v)) && undisturbed(hn, base.get(n))) continue;
                    int excess = hv - hn - stepOf(v);
                    if (excess <= 0) continue;
                    int amount = Math.min((excess + 1) / 2, Math.min(hv - low.get(v), high.get(n) - hn));
                    int l = loose.get(v);
                    if (pile != null && l > 0) amount = Math.min(amount, l);   // one material per move: the loose earth first
                    if (amount <= 0) continue;
                    h.put(v, hv - amount);
                    h.put(n, hn + amount);
                    if (pile != null) lay(n, amount, take(v, amount));   // moved, not taken out: #taken nets to zero
                    now += amount;
                }
                moved += now;
                if (now == 0) break;
            }
            return moved;
        }
    }
}
