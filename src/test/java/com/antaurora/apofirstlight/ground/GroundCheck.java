package com.antaurora.apofirstlight.ground;

import com.antaurora.apofirstlight.earthwork.EarthGrid;
import com.antaurora.apofirstlight.terrainsmooth.SmoothRules;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static com.antaurora.apofirstlight.earthwork.EarthGrid.*;

/**
 * Terrain rework R0 (docs/worldgen/terrain_rework_v1_design.md 5, 6): GroundGeometry over a synthetic world, no game.
 * <ul>
 *   <li>volume: an edited column's solid is the skin's exact volume, one quantum on a vertex is one unit (0.244 L);
 *   sloped tops, carved sub-cells and boxes likewise;</li>
 *   <li>rounding: every run of slabs within one unit of the exact sum, every slab 0..256 (0..16 for edges);</li>
 *   <li>edges: the same numbers from both sides, the open area of a skin edge as its exact integral;</li>
 *   <li>points: surfaceBelow on skins, slopes, under a ledge; surfaceGrid against it and across a chunk border;</li>
 *   <li>speed: one 32-block column (synthetic, so a floor, not the game's cost).</li>
 * </ul>
 * {@code gradlew groundCheck}; exits 1 on a failure.
 */
public final class GroundCheck {
    private static int failures, checks;

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures++;
            if (failures <= 20) System.out.println("FAIL " + what);
        }
    }

    public static void main(String[] args) {
        Random r = new Random(20261024L);
        earthVolume(r);
        smoothVolume(r);
        carveAndBoxes(r);
        rounding(r);
        edges(r);
        points(r);
        grid(r);
        drawnTops(r);
        speed(r);
        System.out.printf("Ground check: %d checks, %d failures%n", checks, failures);
        if (failures > 0) System.exit(1);
    }

    // ------------------------------------------------------------------------------------------------- the world

    /** A synthetic world: columns with a solid floor up to `floor`, optional skin, slopes, carvings and boxes. */
    static final class World implements GroundSource {
        final Map<Long, int[]> earth = new HashMap<>();
        final Map<Long, Integer> floor = new HashMap<>();
        final Map<Long, Long> smooth = new HashMap<>();
        final Map<Long, long[]> gone = new HashMap<>();
        final Map<Long, double[]> boxes = new HashMap<>();
        /** drawn road tops (road drainage D1): 25 vertex heights above the cell's floor */
        final Map<Long, double[]> drawn = new HashMap<>();
        int range = 64;

        static long col(int x, int z) { return (long) x << 32 | (z & 0xFFFFFFFFL); }

        static long cell(int x, int y, int z) { return ((long) x & 0x3FFFFF) << 42 | ((long) z & 0x3FFFFF) << 20 | (y & 0xFFFFF); }

        @Override
        public boolean loaded(int x, int z) { return Math.abs(x) < range && Math.abs(z) < range; }

        @Override
        public int minY() { return 0; }

        @Override
        public int top(int x, int z) { return 70; }   // above everything the scenes put down (rounding goes to 72: open above)

        @Override
        public boolean earth(int x, int z, int[] h) {
            int[] e = earth.get(col(x, z));
            if (e == null) return false;
            System.arraycopy(e, 0, h, 0, e.length);
            return true;
        }

        @Override
        public long[] gone(int x, int y, int z) { return gone.get(cell(x, y, z)); }

        @Override
        public long smooth(int x, int y, int z) { return smooth.getOrDefault(cell(x, y, z), -1L); }

        @Override
        public boolean drawn(int x, int y, int z, double[] rel) {
            double[] d = drawn.get(cell(x, y, z));
            if (d == null) return false;
            System.arraycopy(d, 0, rel, 0, d.length);
            return true;
        }

        @Override
        public double[] boxes(int x, int y, int z) {
            double[] b = boxes.get(cell(x, y, z));
            if (b != null) return b;
            int[] e = earth.get(col(x, z));
            if (e != null) return kind(EarthGrid.min(e), EarthGrid.max(e), y) == FULL ? new double[]{0, 0, 0, 1, 1, 1} : new double[0];
            return y < floor.getOrDefault(col(x, z), 0) ? new double[]{0, 0, 0, 1, 1, 1} : new double[0];
        }
    }

    /** Random skin heights (quanta) round block y + 0.5, spanning up to `spread` blocks. */
    private static int[] skin(Random r, int y, double spread) {
        int[] h = new int[SIDE * SIDE];
        for (int k = 0; k < h.length; k++) h[k] = (int) Math.round((y + 0.5 + (r.nextDouble() - 0.5) * spread) * Q);
        return h;
    }

    /** The exact volume (m3) under a column's skin above y0 (every vertex above y0): the 32 triangles' area x mean height. */
    private static double under(int[] h, int y0) {
        double v = 0;
        for (int j = 0; j < PER_BLOCK; j++) for (int i = 0; i < PER_BLOCK; i++) {
            double nw = h[at(i, j)] / (double) Q - y0, ne = h[at(i + 1, j)] / (double) Q - y0;
            double sw = h[at(i, j + 1)] / (double) Q - y0, se = h[at(i + 1, j + 1)] / (double) Q - y0;
            v += (nw + sw + se) / 3 / 32 + (nw + se + ne) / 3 / 32;
        }
        return v;
    }

    private static double sum(double[] a) {
        double s = 0;
        for (double v : a) s += v;
        return s;
    }

    // ------------------------------------------------------------------------------------------------- volume

    private static void earthVolume(Random r) {
        double worst = 0, worstQuantum = 0;
        for (int t = 0; t < 2000; t++) {
            World w = new World();
            int[] h = skin(r, 64, 0.2 + r.nextDouble() * 2.5);
            w.earth.put(World.col(0, 0), h);
            GroundGeometry g = new GroundGeometry(w);
            int y0 = 60, y1 = 70;
            double got = sum(g.solidExact(0, 0, y0, y1)), want = under(h, y0) * GroundGeometry.UNITS;
            worst = Math.max(worst, Math.abs(got - want));
            // one quantum on an inner vertex: one unit, exactly
            int k = at(1 + r.nextInt(3), 1 + r.nextInt(3));
            h[k]++;
            double up = sum(g.solidExact(0, 0, y0, y1)) - got;
            worstQuantum = Math.max(worstQuantum, Math.abs(up - 1));
        }
        check(worst < 1e-6, "skin volume off by " + worst + " units");
        check(worstQuantum < 1e-6, "one quantum moved the volume by 1 +- " + worstQuantum + " units");
        System.out.printf("Earth volume: 2000 skins, worst %.1e units; one quantum = 1 unit +- %.1e (%.4f L)%n", worst, worstQuantum, 1000 / GroundGeometry.UNITS);
    }

    private static long pack(int[] q) { return q[0] | (long) q[1] << 4 | (long) q[2] << 8 | (long) q[3] << 12; }

    private static void smoothVolume(Random r) {
        double worst = 0;
        for (int t = 0; t < 2000; t++) {
            World w = new World();
            w.floor.put(World.col(0, 0), 64);
            int[] q = {r.nextInt(9), r.nextInt(9), r.nextInt(9), r.nextInt(9)};
            w.smooth.put(World.cell(0, 64, 0), pack(q));
            double c0 = q[0] / 4.0, c1 = q[1] / 4.0, c2 = q[2] / 4.0, c3 = q[3] / 4.0;
            double want = (4 + (c0 + c2 + c3) / 6 + (c0 + c3 + c1) / 6) * GroundGeometry.UNITS;   // 4 full blocks from 60, then the top
            double got = sum(new GroundGeometry(w).solidExact(0, 0, 60, 68));
            worst = Math.max(worst, Math.abs(got - want));
        }
        check(worst < 1e-6, "sloped top volume off by " + worst + " units");
        System.out.printf("Sloped tops: 2000 corner sets, worst %.1e units%n", worst);
    }

    private static void carveAndBoxes(Random r) {
        int bad = 0;
        for (int t = 0; t < 500; t++) {
            World w = new World();
            long[] bits = new long[8];
            for (int sy = 0; sy < 8; sy++) bits[sy] = r.nextLong() & r.nextLong();
            w.gone.put(World.cell(0, 64, 0), bits);
            int left = 512;
            for (long b : bits) left -= Long.bitCount(b);
            if (Math.abs(sum(new GroundGeometry(w).solidExact(0, 0, 64, 65)) - left * GroundGeometry.UNITS / 512) > 1e-9) bad++;
        }
        check(bad == 0, bad + " carved blocks off");
        World w = new World();
        w.boxes.put(World.cell(0, 64, 0), new double[]{0, 0, 0, 1, 0.5, 1, 0.25, 0.5, 0.25, 0.75, 0.875, 0.75});
        double got = sum(new GroundGeometry(w).solidExact(0, 0, 64, 65)), want = (0.5 + 0.25 * 0.375) * GroundGeometry.UNITS;
        check(Math.abs(got - want) < 1e-9, "boxes " + got + " want " + want);
        System.out.printf("Carved and boxes: 500 carvings exact, %d off; a slab and a post %.1f units%n", bad, got);
    }

    // ------------------------------------------------------------------------------------------------- rounding

    private static void rounding(Random r) {
        int bad = 0, range = 0;
        for (int t = 0; t < 1000; t++) {
            World w = world(r);
            GroundGeometry g = new GroundGeometry(w);
            int x = r.nextInt(4), z = r.nextInt(4), y0 = 58, y1 = 72, n = (y1 - y0) * 16;
            double[] solid = g.solidExact(x, z, y0, y1);
            short[] out = new short[n];
            g.openArea(x, z, y0, y1, out);
            for (int s = 0; s < n; s++) if (out[s] < 0 || out[s] > 256) range++;
            for (int k = 0; k < 40; k++) {
                int a = r.nextInt(n), b = a + 1 + r.nextInt(n - a);
                double exact = 0;
                long got = 0;
                for (int s = a; s < b; s++) {
                    exact += 256 - Math.min(256, solid[s]);
                    got += out[s];
                }
                if (Math.abs(got - exact) > 1 + 1e-9) bad++;
            }
        }
        check(bad == 0, bad + " slab runs more than one unit off");
        check(range == 0, range + " slabs outside 0..256");
        System.out.printf("Rounding: 40000 slab runs, %d more than one unit off, %d slabs out of range%n", bad, range);
    }

    /** 4 x 4 columns of mixed ground round y 64: skins, slopes, carvings, boxes, floors. */
    private static World world(Random r) {
        World w = new World();
        for (int z = -1; z <= 4; z++) for (int x = -1; x <= 4; x++) {
            switch (r.nextInt(4)) {
                case 0 -> w.earth.put(World.col(x, z), skin(r, 64, 2));
                case 1 -> {
                    w.floor.put(World.col(x, z), 64);
                    w.smooth.put(World.cell(x, 64, z), pack(new int[]{r.nextInt(9), r.nextInt(9), r.nextInt(9), r.nextInt(9)}));
                }
                case 2 -> {
                    w.floor.put(World.col(x, z), 64);
                    long[] bits = new long[8];
                    for (int sy = 4; sy < 8; sy++) bits[sy] = r.nextLong() | r.nextLong();
                    w.gone.put(World.cell(x, 64, z), bits);
                }
                default -> {
                    w.floor.put(World.col(x, z), 63 + r.nextInt(3));
                    if (r.nextBoolean()) w.boxes.put(World.cell(x, 66, z), new double[]{0, 0, 0, 1, r.nextInt(16) / 16.0, 1});
                }
            }
        }
        return w;
    }

    // ------------------------------------------------------------------------------------------------- edges

    private static void edges(Random r) {
        int asym = 0, range = 0;
        for (int t = 0; t < 500; t++) {
            World w = world(r);
            GroundGeometry g = new GroundGeometry(w);
            int x = r.nextInt(3), z = r.nextInt(3), n = 14 * 16;
            byte[] a = new byte[n], b = new byte[n];
            g.openEdge(x, z, GroundGeometry.EAST, 58, 72, a);
            g.openEdge(x + 1, z, GroundGeometry.WEST, 58, 72, b);
            if (!Arrays.equals(a, b)) asym++;
            g.openEdge(x, z, GroundGeometry.SOUTH, 58, 72, a);
            g.openEdge(x, z + 1, GroundGeometry.NORTH, 58, 72, b);
            if (!Arrays.equals(a, b)) asym++;
            for (byte v : a) if (v < 0 || v > 16) range++;
        }
        check(asym == 0, asym + " edges differ between their two sides");
        check(range == 0, range + " edge slabs outside 0..16");
        // a skin column beside low ground: the east edge's open area is the integral of (y1 - skin) along it
        double worst = 0;
        for (int t = 0; t < 500; t++) {
            World w = new World();
            int[] h = skin(r, 64, 1.5);
            w.earth.put(World.col(0, 0), h);
            w.floor.put(World.col(1, 0), 60);
            byte[] out = new byte[10 * 16];
            new GroundGeometry(w).openEdge(0, 0, GroundGeometry.EAST, 60, 70, out);
            double want = 0;
            for (int j = 0; j < PER_BLOCK; j++) want += (70 - (h[at(4, j)] + h[at(4, j + 1)]) / 2.0 / Q) * 0.25;   // m2 open above the skin
            long got = 0;
            for (byte v : out) got += v;
            worst = Math.max(worst, Math.abs(got - want * 256));
        }
        check(worst <= 1 + 1e-9, "skin edge open area off by " + worst + " units");
        System.out.printf("Edges: 1000 pairs alike from both sides (%d not), skin edge area within %.3f units%n", asym, worst);
    }

    // ------------------------------------------------------------------------------------------------- points

    private static void points(Random r) {
        double worst = 0;
        for (int t = 0; t < 2000; t++) {
            World w = new World();
            int[] h = skin(r, 64, 2);
            w.earth.put(World.col(0, 0), h);
            GroundGeometry g = new GroundGeometry(w);
            double fx = r.nextDouble(), fz = r.nextDouble();
            double got = g.surfaceBelow(fx, 100, fz), want = EarthGrid.skin(EarthGrid.relative(h, 0), fx, fz);
            worst = Math.max(worst, Math.abs(got - want));
        }
        check(worst < 1e-9, "surfaceBelow on a skin off by " + worst);
        // a ledge: block 66 solid in its upper half only, ground up to 64 under it
        World w = new World();
        w.floor.put(World.col(0, 0), 64);
        long[] bits = new long[8];
        for (int sy = 0; sy < 4; sy++) bits[sy] = -1L;
        w.gone.put(World.cell(0, 66, 0), bits);
        GroundGeometry g = new GroundGeometry(w);
        check(g.surfaceBelow(0.5, 80, 0.5) == 67, "on the ledge " + g.surfaceBelow(0.5, 80, 0.5));
        check(g.surfaceBelow(0.5, 66.2, 0.5) == 64, "under the ledge " + g.surfaceBelow(0.5, 66.2, 0.5));
        check(g.surfaceBelow(0.5, 63.5, 0.5) == 64, "inside the ground " + g.surfaceBelow(0.5, 63.5, 0.5));
        // a slope rising into the cell above
        w = new World();
        w.floor.put(World.col(0, 0), 64);
        w.smooth.put(World.cell(0, 64, 0), pack(new int[]{7, 7, 7, 7}));
        check(Math.abs(new GroundGeometry(w).surfaceBelow(0.3, 65.9, 0.6) - 65.75) < 1e-9, "slope into the cell above");
        w.range = 0;
        check(Double.isNaN(new GroundGeometry(w).surfaceBelow(0.5, 70, 0.5)), "not loaded is NaN");
        System.out.printf("Points: 2000 skin points within %.1e; ledge, under it, inside, slope above the cell, not loaded%n", worst);
    }

    private static void grid(Random r) {
        World w = world(r);
        for (int z = -1; z <= 20; z++) for (int x = -1; x <= 36; x++) if (!w.earth.containsKey(World.col(x, z)) && !w.floor.containsKey(World.col(x, z)))
            w.earth.put(World.col(x, z), skin(r, 64, 1));
        GroundGeometry g = new GroundGeometry(w);
        int side = 65;
        int[] a = new int[side * side], b = new int[side * side];
        g.surfaceGrid(0, 0, 80, a);
        g.surfaceGrid(1, 0, 80, b);
        int off = 0, border = 0;
        for (int vz = 0; vz < side; vz++) for (int vx = 0; vx < side; vx++) {
            double h = g.surfaceBelow(vx / 4.0, 81 - 1e-9, vz / 4.0);
            if (a[vz * side + vx] != (int) Math.round(h * Q)) off++;
        }
        for (int vz = 0; vz < side; vz++) if (a[vz * side + 64] != b[vz * side]) border++;
        check(off == 0, off + " grid vertices differ from surfaceBelow");
        check(border == 0, border + " border vertices differ between two chunks");
        System.out.printf("Grid: 65 x 65 vertices as surfaceBelow (%d off), chunk border alike (%d off)%n", off, border);
    }

    /**
     * Road drainage D1 (docs/worldgen/road_drainage_v1_design.md 3): a crowned, superelevated, graded pavement drawn over
     * blocks: every grid vertex is the design's height (one quantum), volumes are the 32 triangles exactly, points the
     * skin's interpolation, a top rising into the cell above counts there, and the crowned band has no low vertex.
     */
    private static void drawnTops(Random r) {
        World w = new World();
        // a crown at x 6.13 (2 % each way, the right side 4 % past x 9.8: a shoulder), a 0.5 % grade along z
        java.util.function.DoubleBinaryOperator pave = (x, z) -> {
            double v = x - 6.13;
            double cross = v < 0 ? 0.02 * v : v < 3.67 ? -0.02 * v : -0.02 * 3.67 - 0.04 * (v - 3.67);
            return 64.4 + cross + 0.005 * z;
        };
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            w.floor.put(World.col(x, z), 64);
            double[] rel = new double[SIDE * SIDE];
            for (int j = 0; j < SIDE; j++) for (int i = 0; i < SIDE; i++) rel[at(i, j)] = pave.applyAsDouble(x + i / 4.0, z + j / 4.0) - 64;
            w.drawn.put(World.cell(x, 64, z), rel);
        }
        GroundGeometry g = new GroundGeometry(w);
        int[] grid = new int[65 * 65];
        g.surfaceGrid(0, 0, 70, grid);
        int off = 0, low = 0;
        for (int vz = 0; vz < 64; vz++) for (int vx = 0; vx < 64; vx++)
            if (grid[vz * 65 + vx] != (int) Math.round(pave.applyAsDouble(vx / 4.0, vz / 4.0) * Q)) off++;
        for (int vz = 1; vz < 63; vz++) for (int vx = 1; vx < 63; vx++) {
            int h = grid[vz * 65 + vx];
            boolean lowest = true;
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++)
                if ((dx != 0 || dz != 0) && grid[(vz + dz) * 65 + vx + dx] <= h) lowest = false;
            if (lowest) low++;
        }
        check(off == 0, off + " pavement vertices off the design");
        check(low == 0, low + " low vertices on a crowned, graded pavement");
        double worst = 0, worstPoint = 0;
        for (int t = 0; t < 200; t++) {
            int x = r.nextInt(16), z = r.nextInt(16);
            double[] rel = w.drawn.get(World.cell(x, 64, z));
            double want = 0;
            for (int j = 0; j < PER_BLOCK; j++) for (int i = 0; i < PER_BLOCK; i++)
                want += (rel[at(i, j)] + rel[at(i, j + 1)] + rel[at(i + 1, j + 1)]) / 3 / 32 + (rel[at(i, j)] + rel[at(i + 1, j + 1)] + rel[at(i + 1, j)]) / 3 / 32;
            want = (4 + want) * GroundGeometry.UNITS;   // four full blocks from 60, then the drawn top
            worst = Math.max(worst, Math.abs(sum(g.solidExact(x, z, 60, 68)) - want));
            double fx = r.nextDouble(), fz = r.nextDouble();
            worstPoint = Math.max(worstPoint, Math.abs(g.surfaceBelow(x + fx, 69, z + fz) - (64 + EarthGrid.skin(rel, fx, fz))));
        }
        check(worst < 1e-6, "drawn top volume off by " + worst + " units");
        check(worstPoint < 1e-9, "drawn top point off by " + worstPoint);
        // a top 1.3 above its floor: into the open cell above, as a sloped top may
        World u = new World();
        u.floor.put(World.col(0, 0), 64);
        double[] high = new double[SIDE * SIDE];
        java.util.Arrays.fill(high, 1.3);
        u.drawn.put(World.cell(0, 64, 0), high);
        GroundGeometry gu = new GroundGeometry(u);
        check(Math.abs(gu.surfaceBelow(0.5, 69, 0.5) - 65.3) < 1e-9, "drawn top into the cell above");
        check(Math.abs(sum(gu.solidExact(0, 0, 64, 66)) - 1.3 * GroundGeometry.UNITS) < 1e-6, "drawn top volume into the cell above");
        long t0 = System.nanoTime();
        for (int i = 0; i < 20; i++) g.surfaceGrid(0, 0, 70, grid);
        double ms = (System.nanoTime() - t0) / 1e6 / 20;
        System.out.printf("Drawn road tops: %d of 4096 vertices off the design, %d low vertices, volume within %.1e units, points within %.1e; a top above its cell; surfaceGrid %.2f ms (synthetic source)%n",
                off, low, worst, worstPoint, ms);
    }

    private static void speed(Random r) {
        World w = world(r);
        GroundGeometry g = new GroundGeometry(w);
        short[] out = new short[32 * 16];
        for (int i = 0; i < 2000; i++) g.openArea(i & 3, i >> 2 & 3, 48, 80, out);   // warm up
        long t0 = System.nanoTime();
        int n = 20000;
        for (int i = 0; i < n; i++) g.openArea(i & 3, i >> 2 & 3, 48, 80, out);
        double us = (System.nanoTime() - t0) / 1000.0 / n;
        int[] grid = new int[65 * 65];
        long t1 = System.nanoTime();
        for (int i = 0; i < 20; i++) g.surfaceGrid(0, 0, 80, grid);
        double ms = (System.nanoTime() - t1) / 1e6 / 20;
        System.out.printf("Speed (synthetic world, a floor for the game's): openArea 32 blocks %.1f us, surfaceGrid %.2f ms a chunk%n", us, ms);
    }
}
