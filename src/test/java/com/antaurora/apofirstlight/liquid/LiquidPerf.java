package com.antaurora.apofirstlight.liquid;

import java.util.Arrays;

/**
 * The engine's cost offline (river plan docs/gameplay/water_river_v1_design.md 9, step 1): the scenes the game is slow in,
 * timed by phase, so a change to the engine can be measured before it goes in. Not a pass / fail check (liquidCheck is);
 * run it alone ({@code gradlew liquidPerf}), under JFR for where the time goes.
 * <ul>
 *   <li>hill: rain on 96 x 96 m of rolling smoothed ground, films on and soaking in, as {@code /afl dev water rain} in game;</li>
 *   <li>pond: a 62 x 62 m pond sloshing (liquidCheck's perf scene);</li>
 *   <li>channel: an 8 m wide, 200 m long channel fed at one end and drained at the other, the active window of a river.</li>
 * </ul>
 */
public final class LiquidPerf {
    private LiquidPerf() {
    }

    public static void main(String[] args) {
        String only = args.length > 0 ? args[0] : "";
        if (only.isEmpty() || only.equals("hill")) hill();
        if (only.isEmpty() || only.equals("pond")) pond();
        if (only.isEmpty() || only.equals("channel")) channel();
    }

    private static LiquidEngine engine(SyntheticGround g, int y0, int y1, boolean storage) {
        LiquidEngine e = new LiquidEngine(g, y0, y1);
        e.setSurfaceStorage(storage);
        for (int cx = g.minX >> 4; cx <= (g.minX + g.sizeX - 1) >> 4; cx++)
            for (int cz = g.minZ >> 4; cz <= (g.minZ + g.sizeZ - 1) >> 4; cz++) e.loadChunk(cx, cz, null);
        return e;
    }

    private record Rain(double mm) implements LiquidEngine.RainField {
        @Override
        public double mmPerHour(int x, int z) {
            return mm;
        }

        @Override
        public int minX() {
            return 0;
        }

        @Override
        public int minZ() {
            return 0;
        }

        @Override
        public int maxX() {
            return 95;
        }

        @Override
        public int maxZ() {
            return 95;
        }
    }

    /** Times n ticks, best of rounds: ms a tick by phase, and ns an awake cell a substep. */
    private static void time(LiquidEngine e, String scene, int rounds, int n) {
        double bestMs = Double.MAX_VALUE;
        long[] best = new long[4];
        long bestCellSteps = 0;
        for (int r = 0; r < rounds; r++) {
            Arrays.fill(e.spent, 0);
            long cellSteps = 0;
            long t0 = System.nanoTime();
            for (int t = 0; t < n; t++) {
                e.tick();
                cellSteps += (long) e.awakeWetCells() * e.substeps();
            }
            double ms = (System.nanoTime() - t0) / 1e6 / n;
            if (ms < bestMs) {
                bestMs = ms;
                System.arraycopy(e.spent, 0, best, 0, 4);
                bestCellSteps = cellSteps;
            }
        }
        System.out.printf("%s: %.3f ms a tick (rain + levels %.3f, wake %.3f, flow %.3f, quiet + sleep + soak %.3f); %.0f ns an awake wet cell a substep%n",
                scene, bestMs, best[0] / 1e6 / n, best[1] / 1e6 / n, best[2] / 1e6 / n, best[3] / 1e6 / n,
                bestCellSteps == 0 ? 0 : (best[0] + best[1] + best[2] + best[3]) / (double) bestCellSteps);
        System.out.println("  " + e.stats());
    }

    private static void hill() {
        // rolling ground, slopes to about 0.45, with hollows that fill: the hillside the game's rain readout came from
        SyntheticGround g = new SyntheticGround(0, 0, 96, 96, (x, z) ->
                40 + 4 * Math.sin(x / 13.0) * Math.cos(z / 17.0) + 2 * Math.sin((x + 2 * z) / 9.0) + 0.05 * x);
        LiquidEngine e = engine(g, 25, 55, true);
        e.setRain(new Rain(20));
        long t0 = System.nanoTime();
        for (int t = 0; t < 600; t++) e.tick();
        System.out.printf("hill warm-up: 600 ticks of 20 mm/h in %.0f ms%n", (System.nanoTime() - t0) / 1e6);
        time(e, "hill rain", 3, 200);
        // the rain stops: what is left runs into the hollows, the films soak in; the columns it wetted stay loaded
        e.setRain(null);
        for (int t = 0; t < 20000 && e.awakeWetCells() > 0; t++) e.tick();
        time(e, "hill after the rain", 3, 200);
        if (e.awakeWetCells() > 0) awake(e);
    }

    private static void pond() {
        SyntheticGround g = new SyntheticGround(0, 0, 64, 64, (x, z) -> 21);
        g.dig(1, 1, 63, 63, 19);
        LiquidEngine e = engine(g, 10, 30, false);
        for (int x = 1; x < 63; x++) for (int z = 1; z < 63; z++) e.add(x, 19.2, z, x < 11 && z < 11 ? 800_000 : 500_000);
        for (int t = 0; t < 200; t++) e.tick();
        time(e, "pond slosh", 5, 100);
    }

    private static void channel() {
        // a 200 m channel, 8 m wide, falling 1 m in 100 m, 1 m deep: fed at the top, open at the bottom
        SyntheticGround g = new SyntheticGround(0, 0, 208, 16, (x, z) -> {
            double bed = 30 - 0.01 * x;
            return z >= 4 && z < 12 ? bed : bed + 2;
        });
        LiquidEngine e = engine(g, 15, 45, false);
        for (int z = 4; z < 12; z++) {
            e.setBoundary(0, 30.5, z, 31.0);
            e.setBoundary(207, 28.5, z, 28.9);
        }
        long t0 = System.nanoTime();
        for (int t = 0; t < 1200; t++) e.tick();
        System.out.printf("channel warm-up: 1,200 ticks in %.0f ms%n", (System.nanoTime() - t0) / 1e6);
        time(e, "channel", 3, 200);
    }

    /** Why water stays awake: a sample of awake wet cells, the least quiet first. */
    static void awake(LiquidEngine e) {
        java.util.List<LiquidCell> awake = new java.util.ArrayList<>();
        for (LiquidCell c : e.cells()) if (!c.sleeping() && !c.boundary() && c.volume() > 0) awake.add(c);
        int films = 0;
        for (LiquidCell c : awake) if (c.volume() <= c.storage) films++;
        System.out.printf("  %d awake wet cells, %d of them films the floor holds%n", awake.size(), films);
        awake.sort(java.util.Comparator.comparingInt(c -> c.quiet));
        for (int i = 0; i < Math.min(8, awake.size()); i++) {
            LiquidCell c = awake.get(i * Math.max(1, awake.size() / 8));
            double q = 0;
            for (LiquidFace f : c.faces) q = Math.max(q, Math.abs(f.q));
            System.out.printf("  (%d %d) floor %.4f level %.6f last %.6f vol %d storage %d quiet %d max q %.2e%n",
                    c.x(), c.z(), c.floor(), c.level(), c.lastLevel, c.volume(), c.storage, c.quiet, q);
        }
    }
}
