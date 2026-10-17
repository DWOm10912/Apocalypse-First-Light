package com.antaurora.apofirstlight.vehicle;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;

import java.util.Locale;

/**
 * Offline check of the T15 headlamp beams (HeadlightBeam, docs/vehicles/first_pickup_v0_design.md 8.10) on made-up
 * cells: a flat field, a road_surface layer (partial solid) on it, a wall ahead, the high beam. The truck at the origin
 * faces -Z (north), the lamp 2.32 m ahead and 0.81 m up from a ground top at y = 64. Prints the light the ground takes
 * (the cell above a full block, or the layer cell itself) along the middle and across, the reach, the cells and the time.
 * Run with ./gradlew headlightCheck.
 */
public final class HeadlightBeamCheck {
    private static int failures;
    private static final double[] FWD = {0, 0, -1}, UP = {0, 1, 0};
    private static final double OX = 0.5, OY = 64.81, OZ = -2.32;

    public static void main(String[] args) {
        HeadlightBeam.World field = (x, y, z) -> y < 64 ? HeadlightBeam.Cell.OPAQUE : HeadlightBeam.Cell.OPEN;
        // a thin road_surface layer (3 / 16) the truck stands on: the lamp is in the layer cell, above its solid part
        HeadlightBeam.World road = layer(3 / 16.0);
        // a near full layer (15 / 16) under the truck: the lamp is a cell higher
        HeadlightBeam.World deep = layer(15 / 16.0);
        HeadlightBeam.World wall = (x, y, z) -> y < 64 || z <= -15 ? HeadlightBeam.Cell.OPAQUE : HeadlightBeam.Cell.OPEN;

        Long2ByteOpenHashMap low = cast(field, false, "field, low beam");
        profile(low, 64);
        check("field: full light in front of the bumper", get(low, 0, 64, -4) >= 13);
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
        Long2ByteOpenHashMap out = new Long2ByteOpenHashMap();
        // warm up, then time one cast as the client does it (fresh maps)
        for (int i = 0; i < 20; i++) once(w, high, new Long2ByteOpenHashMap(), oy);
        long t0 = System.nanoTime();
        once(w, high, out, oy);
        long us = (System.nanoTime() - t0) / 1000;
        System.out.printf(Locale.ROOT, "%n== %s: %d cells, %d us%n", name, out.size(), us);
        return out;
    }

    private static void once(HeadlightBeam.World w, boolean high, Long2ByteOpenHashMap out, double oy) {
        Long2ObjectOpenHashMap<HeadlightBeam.Cell> cache = new Long2ObjectOpenHashMap<>();
        HeadlightBeam.cast(OX, oy, OZ, FWD, UP, HeadlightBeam.LOW, w, out, cache);
        if (high) HeadlightBeam.cast(OX, oy, OZ, FWD, UP, HeadlightBeam.HIGH, w, out, cache);
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

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "  PASS " : "  FAIL ") + what);
        if (!ok) failures++;
    }
}
