package com.antaurora.apofirstlight.vehicle;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;

import java.util.Locale;

/**
 * Offline check of the T15 headlamp beams (HeadlightBeam, docs/vehicles/first_pickup_v0_design.md 8.10) on made-up
 * cells: a flat field, a road_surface layer (partial solid) on it, a wall ahead, the high beam. The truck at the origin
 * faces -Z (north), its two headlamps 2.32 m ahead, 0.66 m either side of the middle and 0.81 m up from a ground top at
 * y = 64, each cast on its own as the client does (HeadlightField). Prints the light the ground takes (the cell above a
 * full block, or the layer cell itself) along the middle and across, a map of it, the reach, the cells and the time; checks
 * that the pool is smooth (no pits, soft edges: user 2026-10-17) and that a reverse lamp lights the ground behind.
 * Run with ./gradlew headlightCheck.
 */
public final class HeadlightBeamCheck {
    private static int failures;
    private static final double[] FWD = {0, 0, -1}, UP = {0, 1, 0};
    private static final double OX = 0.5, OY = 64.81, OZ = -2.32, LAMP_HALF = 0.66;

    public static void main(String[] args) {
        HeadlightBeam.World field = (x, y, z) -> y < 64 ? HeadlightBeam.Cell.OPAQUE : HeadlightBeam.Cell.OPEN;
        // a thin road_surface layer (3 / 16) the truck stands on: the lamp is in the layer cell, above its solid part
        HeadlightBeam.World road = layer(3 / 16.0);
        // a near full layer (15 / 16) under the truck: the lamp is a cell higher
        HeadlightBeam.World deep = layer(15 / 16.0);
        HeadlightBeam.World wall = (x, y, z) -> y < 64 || z <= -15 ? HeadlightBeam.Cell.OPAQUE : HeadlightBeam.Cell.OPEN;

        Long2ByteOpenHashMap low = cast(field, false, "field, low beam");
        profile(low, 64);
        map(low, 64, 40, 20);
        int lowPits = pits(low, 64), lowEdges = hardEdges(low, 64);
        System.out.println("  pits (ground cells darker than both neighbours): " + lowPits + "; hard edges (>= 4 beside unlit, 6 m on): " + lowEdges);
        check("field: no pits in the pool", lowPits == 0);
        check("field: soft edges", lowEdges == 0);
        // a metre past the bumper the ground is under the beam's lower edge: well lit, not the brightest
        check("field: well lit in front of the bumper", get(low, 0, 64, -5) >= 9);
        check("field: lit ground reaches 30 m", get(low, 0, 64, -30) > 0);
        check("field: nothing lit behind the lamp", get(low, 0, 64, 1) == 0);
        check("field: the right edge reaches farther than the left", reach(low, 64, 8) > reach(low, 64, -8));

        Long2ByteOpenHashMap layer = cast(road, false, "road layer, low beam");
        profile(layer, 64);
        check("road layer: the layer cells take the light", get(layer, 0, 64, -12) >= 8 && get(layer, 0, 64, -30) > 0);

        Long2ByteOpenHashMap deepLow = cast(deep, false, "deep road layer (15/16), lamp 0.81 m over its top, low beam", 64.94 + 0.81);
        profile(deepLow, 64);
        check("deep layer: the layer cells take the light", get(deepLow, 0, 64, -12) >= 8 && get(deepLow, 0, 64, -30) > 0);

        Long2ByteOpenHashMap walled = cast(wall, false, "wall at 15 m, low beam");
        check("wall: the cell in front of the wall is lit", get(walled, 0, 64, -14) > 0);
        check("wall: nothing beyond the wall", get(walled, 0, 64, -17) == 0 && get(walled, 0, 65, -20) == 0);

        Long2ByteOpenHashMap high = cast(field, true, "field, low + high beam");
        profile(high, 64);
        map(high, 64, 72, 20);
        int highPits = pits(high, 64), highEdges = hardEdges(high, 64);
        System.out.println("  pits: " + highPits + "; hard edges: " + highEdges);
        check("high beam: no pits, soft edges", highPits == 0 && highEdges == 0);

        // a reverse lamp pair 2.36 m behind the origin, 0.75 m up, facing +Z
        Long2FloatOpenHashMap backLight = new Long2FloatOpenHashMap();
        Long2ObjectOpenHashMap<HeadlightBeam.Cell> cache = new Long2ObjectOpenHashMap<>();
        for (double side : new double[]{-LAMP_HALF, LAMP_HALF})
            HeadlightBeam.cast(OX + side, 64.75, 2.36, new double[]{0, 0, 1}, UP, HeadlightBeam.REVERSE, field, backLight, cache);
        Long2ByteOpenHashMap back = HeadlightBeam.levels(backLight);
        System.out.printf(Locale.ROOT, "%n== reverse lamps: %d cells; behind at 2 / 5 / 9 m: %d / %d / %d%n", back.size(),
                get(back, 0, 64, 4), get(back, 0, 64, 7), get(back, 0, 64, 11));
        check("reverse: the ground behind is lit, a few metres", get(back, 0, 64, 4) >= 5 && get(back, 0, 64, 7) > 0 && get(back, 0, 64, 14) == 0);
        check("reverse: nothing ahead", get(back, 0, 64, -1) == 0);
        check("high beam reaches farther", reach(high, 64, 0) > reach(low, 64, 0) + 15);

        System.out.println(failures == 0 ? "[AFL HEADLIGHT CHECK] PASS" : "[AFL HEADLIGHT CHECK] FAIL (" + failures + ")");
        if (failures > 0) System.exit(1);
    }

    /** Full blocks below y = 64, a layer of this top in y = 64. */
    private static HeadlightBeam.World layer(double top) {
        return new HeadlightBeam.World() {
            @Override
            public HeadlightBeam.Cell cell(int x, int y, int z) {
                return y < 64 ? HeadlightBeam.Cell.OPAQUE : y == 64 ? HeadlightBeam.Cell.SURFACE : HeadlightBeam.Cell.OPEN;
            }

            @Override
            public double surfaceTop(int x, int y, int z) { return top; }
        };
    }

    private static Long2ByteOpenHashMap cast(HeadlightBeam.World w, boolean high, String name) {
        return cast(w, high, name, OY);
    }

    private static Long2ByteOpenHashMap cast(HeadlightBeam.World w, boolean high, String name, double oy) {
        // warm up, then time one cast as the client does it (fresh maps, rounded to levels)
        for (int i = 0; i < 20; i++) HeadlightBeam.levels(once(w, high, oy));
        long t0 = System.nanoTime();
        Long2ByteOpenHashMap out = HeadlightBeam.levels(once(w, high, oy));
        long us = (System.nanoTime() - t0) / 1000;
        System.out.printf(Locale.ROOT, "%n== %s: %d cells, %d us%n", name, out.size(), us);
        return out;
    }

    private static Long2FloatOpenHashMap once(HeadlightBeam.World w, boolean high, double oy) {
        Long2FloatOpenHashMap out = new Long2FloatOpenHashMap();
        Long2ObjectOpenHashMap<HeadlightBeam.Cell> cache = new Long2ObjectOpenHashMap<>();
        for (double side : new double[]{-LAMP_HALF, LAMP_HALF}) {
            HeadlightBeam.cast(OX + side, oy, OZ, FWD, UP, HeadlightBeam.LOW, w, out, cache);
            if (high) HeadlightBeam.cast(OX + side, oy, OZ, FWD, UP, HeadlightBeam.HIGH, w, out, cache);
        }
        return out;
    }

    private static int get(Long2ByteOpenHashMap m, int x, int y, int z) { return m.get(BlockPos.asLong(x, y, z)); }

    /** The farthest lit cell of row y at x (m ahead of the lamp). */
    private static int reach(Long2ByteOpenHashMap m, int y, int x) {
        int far = 0;
        for (int z = -1; z > -120; z--) if (get(m, x, y, z) > 0) far = z;
        return (int) Math.round(-far + OZ);
    }

    private static void profile(Long2ByteOpenHashMap m, int y) {
        StringBuilder s = new StringBuilder("ahead (m): light at x = 0 | lit width\n");
        for (int d : new int[]{2, 5, 10, 15, 20, 30, 40, 50, 60}) {
            int z = (int) Math.floor(OZ - d), lo = 0, hi = 0;
            for (int x = -40; x <= 40; x++) if (get(m, x, y, z) > 0) { lo = Math.min(lo, x); hi = Math.max(hi, x); }
            int w = get(m, 0, y, z) > 0 ? hi - lo + 1 : 0;
            s.append(String.format(Locale.ROOT, "  %3d: %2d | %2d m%n", d, get(m, 0, y, z), w));
        }
        s.append(String.format(Locale.ROOT, "  reach: middle %d m, left 8 m %d m, right 8 m %d m; head height (y + 1) at 10 m: %d%n",
                reach(m, y, 0), reach(m, y, -8), reach(m, y, 8), get(m, 0, y + 1, (int) Math.floor(OZ - 10))));
        System.out.print(s);
    }

    /** The ground row's light as a map (rows ahead of the lamp, columns across; '.' unlit, 1-9, a-f). */
    private static void map(Long2ByteOpenHashMap m, int y, int ahead, int half) {
        StringBuilder s = new StringBuilder("ground light map (top far, left -x):\n");
        for (int z = (int) Math.floor(OZ - ahead); z <= (int) Math.floor(OZ); z++) {
            s.append(String.format(Locale.ROOT, "  %3d ", (int) Math.round(OZ - z)));
            for (int x = -half; x <= half; x++) {
                int v = get(m, x, y, z);
                s.append(v <= 0 ? '.' : Character.forDigit(v, 16));
            }
            s.append('\n');
        }
        System.out.print(s);
    }

    /**
     * Pits in the ground row 12 m or more ahead: lit cells darker than both neighbours across or both along (a ray gap
     * or a streak). A smooth pool has none (nearer, the two lamps' own peaks may leave their middle a level lower);
     * returns how many.
     */
    private static int pits(Long2ByteOpenHashMap m, int y) {
        int n = 0;
        for (long key : m.keySet()) {
            if (BlockPos.getY(key) != y) continue;
            int x = BlockPos.getX(key), z = BlockPos.getZ(key), v = m.get(key);
            if (OZ - z < 12) continue;
            int l = get(m, x - 1, y, z), r = get(m, x + 1, y, z), f = get(m, x, y, z - 1), b = get(m, x, y, z + 1);
            if (l > v && r > v || f > v && b > v) {
                n++;
                System.out.printf(Locale.ROOT, "    pit at x %d, %d m ahead: %d (across %d %d, along %d %d)%n", x, (int) Math.round(OZ - z), v, l, r, f, b);
            }
        }
        return n;
    }

    /** Ground cells 6 m or more ahead lit 4 or more beside an unlit one across or along: a hard edge. */
    private static int hardEdges(Long2ByteOpenHashMap m, int y) {
        int n = 0;
        for (long key : m.keySet()) {
            if (BlockPos.getY(key) != y || m.get(key) < 4) continue;
            int x = BlockPos.getX(key), z = BlockPos.getZ(key);
            if (OZ - z < 6) continue;
            if (get(m, x - 1, y, z) == 0 || get(m, x + 1, y, z) == 0 || get(m, x, y, z - 1) == 0 || get(m, x, y, z + 1) == 0) n++;
        }
        return n;
    }

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "  PASS " : "  FAIL ") + what);
        if (!ok) failures++;
    }
}
