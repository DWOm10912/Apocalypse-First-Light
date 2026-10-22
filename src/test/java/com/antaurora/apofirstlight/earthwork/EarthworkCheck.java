package com.antaurora.apofirstlight.earthwork;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.Random;

import static com.antaurora.apofirstlight.earthwork.EarthGrid.*;

/**
 * Earthwork offline check (docs/worldgen/earthwork_a1.md 7, earthwork_a2.md 7): the grid math the game runs (EarthGrid), no
 * game bootstrap.
 * <ul>
 *   <li>partition: over random columns, the skin pieces of all SURFACE cells cover the column exactly once seen from above
 *   (projected areas sum to 1), inside their cells; a column without SURFACE cells is flat at a block boundary;</li>
 *   <li>cell kinds run FULL, SURFACE, AIR upwards; collision boxes stay within their cell;</li>
 *   <li>distribute: the parts sum to exactly the total;</li>
 *   <li>volume: random digs and fills on a field keep dug = carried + put down to the quantum, and the field's volume (its
 *   triangles) changes by exactly the vertices' quanta times the cell area;</li>
 *   <li>shovel (A2): scoops and pours with materials (Field#apply, the brushes Earthwork uses) keep the ground plus the
 *   blade's load to the quantum; a scoop takes no more than the room left, a pour puts down no more than the load and only
 *   what went down leaves the blade; a full blade takes nothing, ground that holds nothing more takes nothing; two
 *   materials laid together are backfill; no vertex next to a wall ends below the wall's excavation step, none next to
 *   locked ground steeper than 1:1;</li>
 *   <li>vehicle ground (A3): EarthGrid#ground's height is the skin's, its normal is a unit vector up and agrees with the
 *   skin's slope measured a step away inside the same triangle.</li>
 * </ul>
 * {@code gradlew earthworkCheck}; exits 1 on a failure.
 */
public final class EarthworkCheck {
    private static int failures, checks;

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures++;
            if (failures <= 20) System.out.println("FAIL " + what);
        }
    }

    public static void main(String[] args) {
        Random r = new Random(20261021L);
        partition(r);
        distribution(r);
        volume(r);
        shovel(r);
        vehicleGround(r);
        System.out.printf("Earthwork check: %d checks, %d failures (grid %d per block, %d quanta per block, %.4f L per quantum)%n",
                checks, failures, PER_BLOCK, Q, LITRES_PER_QUANTUM);
        if (failures > 0) System.exit(1);
    }

    /** EarthGrid#ground against #skin: height, and the normal against the slope a small step away in the same triangle. */
    private static void vehicleGround(Random r) {
        int[] h = new int[SIDE * SIDE];
        double[] out = new double[6];
        double worstHeight = 0, worstNormal = 0, worstLength = 0;
        for (int t = 0; t < 20000; t++) {
            int y0 = 64 * Q + r.nextInt(Q);
            for (int k = 0; k < h.length; k++) h[k] = y0 + r.nextInt(Q * 2) - Q;
            double fx = r.nextDouble(), fz = r.nextDouble();
            ground(h, fx, fz, out);
            worstHeight = Math.max(worstHeight, Math.abs(out[0] - skin(relative(h, 0), fx, fz)));
            worstLength = Math.max(worstLength, Math.abs(out[1] * out[1] + out[2] * out[2] + out[3] * out[3] - 1));
            // a step that stays in the same triangle: away from the cell's edges and its diagonal
            double gx = fx * PER_BLOCK, gz = fz * PER_BLOCK, u = gx - Math.floor(gx), v = gz - Math.floor(gz), e = 1e-4;
            if (u < 0.01 || u > 0.99 || v < 0.01 || v > 0.99 || Math.abs(u - v) < 0.01 || fx > 0.99 || fz > 0.99) continue;
            double[] rel = relative(h, 0);
            double hx = (skin(rel, fx + e, fz) - out[0]) / e, hz = (skin(rel, fx, fz + e) - out[0]) / e;
            double len = Math.sqrt(hx * hx + 1 + hz * hz);
            worstNormal = Math.max(worstNormal, Math.max(Math.abs(out[1] + hx / len), Math.max(Math.abs(out[2] - 1 / len), Math.abs(out[3] + hz / len))));
            check(out[2] > 0, "vehicle ground normal points up");
        }
        check(worstHeight < 1e-9, "vehicle ground height is the skin's (worst " + worstHeight + ")");
        check(worstLength < 1e-9, "vehicle ground normal is a unit vector (worst " + worstLength + ")");
        check(worstNormal < 1e-6, "vehicle ground normal follows the skin's slope (worst " + worstNormal + ")");
        System.out.printf("Vehicle ground: 20000 points, height error %.1e, normal error %.1e%n", worstHeight, worstNormal);
    }

    private static void partition(Random r) {
        int n = SIDE * SIDE;
        for (int t = 0; t < 20000; t++) {
            int[] h = new int[n];
            boolean[] u = new boolean[n];
            int base = (r.nextInt(7) - 3) * Q, spread = t % 4 == 0 ? Q / 4 : (1 + r.nextInt(3)) * Q;
            for (int k = 0; k < n; k++) {
                // some fields snap to block boundaries (flat pieces on cell floors and ceilings)
                int v = base + r.nextInt(spread + 1);
                h[k] = t % 3 == 0 ? Math.round(v / (float) (Q / 4)) * (Q / 4) : v;
                u[k] = r.nextBoolean();
            }
            int lo = Math.floorDiv(min(h), Q) - 1, hi = top(max(h)) + 1;
            double area = 0;
            int surface = 0, last = FULL;
            boolean inside = true;
            for (int y = lo; y <= hi; y++) {
                int k = kind(min(h), max(h), y);
                check(k <= last, "kinds run FULL, SURFACE, AIR upwards");
                last = k;
                if (k != SURFACE) continue;
                surface++;
                for (Piece p : pieces(h, u, y)) {
                    area += projectedArea(p.points());
                    for (double[] q : p.points()) inside &= q[0] >= -1e-9 && q[0] <= 1 + 1e-9 && q[1] >= -1e-9 && q[1] <= 1 + 1e-9 && q[2] >= -1e-9 && q[2] <= 1 + 1e-9;
                }
                for (int b : boxes(h, y, 8, 0)) check(b >= 0 && b <= 16, "collision box within its cell");            }
            check(inside, "pieces inside their cells");
            if (surface == 0) {
                check(min(h) == max(h) && Math.floorMod(min(h), Q) == 0, "no skin cell only when flat on a block boundary");
            } else {
                check(Math.abs(area - 1) < 1e-9, "skin covers the column once (area " + area + ", field " + java.util.Arrays.toString(h) + ")");
            }
        }
    }

    private static void distribution(Random r) {
        for (int t = 0; t < 2000; t++) {
            Long2IntOpenHashMap w = new Long2IntOpenHashMap();
            int n = 1 + r.nextInt(200);
            for (int k = 0; k < n; k++) w.put(key(r.nextInt(100), r.nextInt(100)), 1 + r.nextInt(600));
            long total = r.nextInt(50000);
            long sum = 0;
            boolean positive = true;
            for (int v : distribute(total, w).values()) {
                sum += v;
                positive &= v > 0;
            }
            check(sum == total, "distribute sums to the total");
            check(positive, "distribute gives no negative or empty part");
        }
    }

    /**
     * A field of N x N vertices (border fixed, as vertices shared with unedited ground: walls), digs and fills as the game
     * does them (Field: bounds against the walls, the moves, slumping); every step keeps the volume, and no slope against
     * a wall gets steeper than the repose.
     */
    private static void volume(Random r) {
        int size = 48, step = step(35);
        int[][] h = new int[size][size];
        for (int z = 0; z < size; z++) for (int x = 0; x < size; x++) h[z][x] = 64 * Q + (int) Math.round(Q * 0.5 * Math.sin(x * 0.3) * Math.cos(z * 0.2));
        int[][] base = new int[size][];
        for (int z = 0; z < size; z++) base[z] = h[z].clone();
        double startVolume = volume(h);
        long startSum = sum(h), carried = 0, slumped = 0;
        int lo = 63 * Q, hi = 67 * Q, worst = 0;
        for (int t = 0; t < 400; t++) {
            double cx = 2 + r.nextDouble() * (size / (double) PER_BLOCK - 4), cz = 2 + r.nextDouble() * (size / (double) PER_BLOCK - 4);
            double radius = 0.3 + r.nextDouble() * 2.5, depth = 0.05 + r.nextDouble() * 1.5;
            boolean dig = carried == 0 || r.nextBoolean();
            Long2IntOpenHashMap deltas = brush(cx, cz, radius, depth, dig ? -1 : 1);
            if (!dig) {
                long want = 0;
                Long2IntOpenHashMap movable = new Long2IntOpenHashMap();
                for (var e : deltas.long2IntEntrySet()) if (interior(e.getLongKey(), size)) {
                    movable.put(e.getLongKey(), e.getIntValue());
                    want += e.getIntValue();
                }
                deltas = distribute(Math.min(want, carried), movable);
            }
            Field f = new Field();
            for (int z = 0; z < size; z++) for (int x = 0; x < size; x++) {
                long v = key(x, z);
                f.h.put(v, h[z][x]);
                f.base.put(v, base[z][x]);
                if (!interior(v, size)) continue;
                f.lo.put(v, lo);
                f.hi.put(v, hi);
                f.step.put(v, step);
            }
            f.bound();
            long moved = 0;
            for (var e : deltas.long2IntEntrySet()) if (f.movable(e.getLongKey())) moved += f.move(e.getLongKey(), e.getIntValue());
            long before = 0, after = 0;
            for (int v : f.h.values()) before += v;
            slumped += f.slump();
            for (int v : f.h.values()) after += v;
            check(before == after, "slumping keeps the volume");
            for (int z = 0; z < size; z++) for (int x = 0; x < size; x++) h[z][x] = f.h.get(key(x, z));
            carried -= moved;
            check(carried >= 0, "never more put down than carried");
            // against the walls (the border): no edited slope steeper than the repose
            for (int k = 1; k < size - 1; k++) {
                int[][] pairs = {{k, 1, k, 0}, {k, size - 2, k, size - 1}, {1, k, 0, k}, {size - 2, k, size - 1, k}};
                for (int[] p : pairs) {
                    int a = h[p[1]][p[0]], b = h[p[3]][p[2]];
                    if (undisturbed(a, base[p[1]][p[0]])) continue;
                    worst = Math.max(worst, Math.abs(a - b) - step);
                }
            }
        }
        check(worst <= 0, "no slope against a wall steeper than the repose (worst excess " + worst + " quanta)");
        check(sum(h) + carried == startSum, "earth dug = carried + put down (quanta)");
        double cellArea = 1.0 / (PER_BLOCK * PER_BLOCK);
        check(Math.abs((volume(h) - startVolume) - (sum(h) - startSum) * cellArea / Q) < 1e-6,
                "field volume changes by the vertices' quanta times the cell area");
        // inside: what slumping left steeper than the repose (reported, A1.1: 64 rounds)
        int steep = 0, pairs = 0, maxExcess = 0;
        for (int z = 1; z < size - 1; z++) for (int x = 1; x < size - 2; x++) for (int[] d : new int[][]{{1, 0}, {0, 1}}) {
            int a = h[z][x], b = h[z + d[1]][x + d[0]];
            if (undisturbed(a, base[z][x]) && undisturbed(b, base[z + d[1]][x + d[0]])) continue;
            pairs++;
            int e = Math.abs(a - b) - step;
            if (e > 0) steep++;
            maxExcess = Math.max(maxExcess, e);
        }
        System.out.printf("Slopes: %d quanta slumped over 400 edits; %d of %d disturbed pairs inside steeper than %d quanta (worst +%d)%n",
                slumped, steep, pairs, step, maxExcess);
    }

    /** Materials as data/apocalypse_firstlight/bulk_materials: topsoil, sand, gravel, clay, mud, backfill (repose, cut). */
    private static final int TOPSOIL = 0, SAND = 1, BACKFILL = 5;
    private static final int[] PILE = {step(35), step(34), step(38), step(40), step(20), step(36)};
    private static final int[] CUT = {step(63), step(34), step(45), step(70), step(30), step(36)};

    /**
     * Shovel strokes on a field of topsoil (west) and sand (east), its border walls, the north border locked ground, as
     * Earthwork scoop / pour do them: a brush r 0.30 m 0.15 m deep into the room left, a cone r 0.35 m of the load shared
     * out, then slumping at each material's repose. The blade holds ShovelItem.CAPACITY (11.72 L, docs/worldgen/earthwork_a2.md 4.1).
     */
    private static void shovel(Random r) {
        int size = 40, room = com.antaurora.apofirstlight.item.ShovelItem.CAPACITY;   // a constant: no game class loads
        int[][] h = new int[size][size], base = new int[size][], loose = new int[size][size], mat = new int[size][size];
        for (int z = 0; z < size; z++) for (int x = 0; x < size; x++) h[z][x] = 64 * Q + (int) Math.round(Q * 0.3 * Math.sin(x * 0.4 + z * 0.1));
        for (int z = 0; z < size; z++) base[z] = h[z].clone();
        long start = sum(h);
        int load = 0, held = -1, mixed = 0, worstWall = 0, worstGuard = 0;
        boolean overRoom = false, overLoad = false, unpaid = false, fullTook = false, nowhereTook = false;
        for (int t = 0; t < 600; t++) {
            double cx = 1.5 + r.nextDouble() * (size / (double) PER_BLOCK - 3), cz = 1.5 + r.nextDouble() * (size / (double) PER_BLOCK - 3);
            boolean dig = load == 0 || load < room && r.nextBoolean();
            boolean full = t % 50 == 7;   // now and then: a full blade digs, or the ground under a pour can take nothing
            Field f = shovelField(h, base, loose, mat, size, full && !dig);
            f.bound();
            long moved;
            if (dig) {
                int left = full ? 0 : room - load;
                moved = left <= 0 ? 0 : f.apply(brush(cx, cz, Earthwork.DIG_RADIUS, Earthwork.DIG_DEPTH, -1), -1, left);
                if (full) fullTook |= moved != 0;
                overRoom |= -moved > room - load;
                int got = -1;
                long in = 0;
                for (var e : f.taken.int2LongEntrySet()) if (e.getLongValue() > 0) {
                    in += e.getLongValue();
                    got = got < 0 ? e.getIntKey() : f.mix(got, e.getIntKey());
                }
                f.slump();
                long net = 0;
                for (long q : f.taken.values()) net += q;
                unpaid |= net != -moved || in != -moved;
                if (moved < 0) {
                    held = load > 0 ? f.mix(held, got) : got;
                    load -= (int) moved;
                }
            } else {
                Long2IntOpenHashMap cone = cone(cx, cz, Earthwork.POUR_RADIUS), movable = new Long2IntOpenHashMap();
                for (var e : cone.long2IntEntrySet()) if (f.movable(e.getLongKey())) movable.put(e.getLongKey(), e.getIntValue());
                moved = f.apply(distribute(load, movable), held, load);
                if (full) nowhereTook |= moved != 0;
                overLoad |= moved < 0 || moved > load;
                f.slump();
                long net = 0;
                for (long q : f.taken.values()) net += q;
                unpaid |= net != -moved;
                load -= (int) moved;
                if (load == 0) held = -1;
            }
            for (int z = 0; z < size; z++) for (int x = 0; x < size; x++) {
                long v = key(x, z);
                h[z][x] = f.h.get(v);
                loose[z][x] = f.loose.get(v);
                mat[z][x] = loose[z][x] > 0 ? f.mat.get(v) : 0;
                if (loose[z][x] > 0 && mat[z][x] == BACKFILL) mixed++;
            }
            check(sum(h) + load == start, "shovel: ground + blade = start (stroke " + t + ")");
            for (int k = 1; k < size - 1; k++) {
                // a wall (the border) never undercut past its excavation step; the north border (locked) never past 1:1
                int[][] pairs = {{k, size - 2, k, size - 1}, {1, k, 0, k}, {size - 2, k, size - 1, k}};
                for (int[] p : pairs) {
                    int a = h[p[1]][p[0]], b = h[p[3]][p[2]];
                    if (!undisturbed(a, base[p[1]][p[0]])) worstWall = Math.max(worstWall, b - a - CUT[natural(p[0], size)]);
                }
                int a = h[1][k], b = h[0][k];
                if (!undisturbed(a, base[1][k])) worstGuard = Math.max(worstGuard, b - a - GUARD_STEP);
            }
        }
        check(!overRoom, "a scoop takes no more than the room left");
        check(!overLoad, "a pour puts down no more than the load, never takes");
        check(!unpaid, "what a stroke moves is what Field#taken counts (slumping nets to zero)");
        check(!fullTook, "a full blade takes nothing");
        check(!nowhereTook, "ground that holds nothing more takes nothing");
        check(worstWall <= 0, "no cut below a wall steeper than its excavation step (worst excess " + worstWall + ")");
        check(worstGuard <= 0, "no cut next to locked ground steeper than 1:1 (worst excess " + worstGuard + ")");
        Field m = new Field();
        m.pile = PILE;
        m.backfill = BACKFILL;
        long v = key(1, 1);
        m.h.put(v, 0);
        m.lo.put(v, -Q);
        m.hi.put(v, Q);
        m.step.put(v, CUT[TOPSOIL]);
        m.nat.put(v, TOPSOIL);
        m.unbound();
        m.move(v, 10, SAND);
        check(m.mat.get(v) == SAND && m.loose.get(v) == 10, "earth poured on natural ground keeps its material");
        m.move(v, 10, TOPSOIL);
        check(m.mat.get(v) == BACKFILL && m.loose.get(v) == 20, "two materials laid together are backfill");
        m.move(v, -25, -1);
        check(m.loose.get(v) == 0 && m.taken.get(BACKFILL) == 20 && m.taken.get(TOPSOIL) == -5 && m.taken.get(SAND) == -10,
                "a scoop takes the loose earth first, then the natural ground (taken " + m.taken + ")");
        System.out.printf("Shovel: 600 strokes, blade %d quanta (%.2f L); %d loose backfill vertex-strokes%n", room, room * LITRES_PER_QUANTUM, mixed);
    }

    /** The natural material of vertex column x: topsoil west, sand east. */
    private static int natural(int x, int size) { return x < size / 2 ? TOPSOIL : SAND; }

    /** One stroke's field: interior vertices movable, the border walls, the north border locked; full: nothing rises. */
    private static Field shovelField(int[][] h, int[][] base, int[][] loose, int[][] mat, int size, boolean full) {
        Field f = new Field();
        f.pile = PILE;
        f.backfill = BACKFILL;
        for (int z = 0; z < size; z++) for (int x = 0; x < size; x++) {
            long v = key(x, z);
            f.h.put(v, h[z][x]);
            f.base.put(v, base[z][x]);
            if (z == 0) f.guard.add(v);
            if (!interior(v, size)) continue;
            f.lo.put(v, 60 * Q);
            f.hi.put(v, full ? h[z][x] : 68 * Q);
            f.nat.put(v, natural(x, size));
            f.step.put(v, CUT[natural(x, size)]);
            if (loose[z][x] > 0) {
                f.loose.put(v, loose[z][x]);
                f.mat.put(v, mat[z][x]);
            }
        }
        return f;
    }

    private static boolean interior(long key, int size) {
        int x = keyX(key), z = keyZ(key);
        return x > 0 && z > 0 && x < size - 1 && z < size - 1;
    }

    private static long sum(int[][] h) {
        long s = 0;
        for (int[] row : h) for (int v : row) s += v;
        return s;
    }

    /** Volume under the field (blocks^3) over its triangles, nw-se split as the game draws them. */
    private static double volume(int[][] h) {
        double v = 0, a = 1.0 / (PER_BLOCK * PER_BLOCK) / 2;
        for (int z = 0; z + 1 < h.length; z++) for (int x = 0; x + 1 < h.length; x++) {
            double nw = h[z][x] / (double) Q, ne = h[z][x + 1] / (double) Q, sw = h[z + 1][x] / (double) Q, se = h[z + 1][x + 1] / (double) Q;
            v += a * (nw + sw + se) / 3 + a * (nw + se + ne) / 3;
        }
        return v;
    }
}
