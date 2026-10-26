package com.antaurora.apofirstlight.liquid;

import com.antaurora.apofirstlight.liquid.ground.GroundViewV0;
import net.minecraft.core.Direction;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Liquid package 1 offline check (docs/gameplay/water_v1_design.md 10, 15): LiquidEngine on SyntheticGround (C's
 * GroundView V0 signature), no game bootstrap. Every scene checks after every tick that no cell is negative and that each
 * chunk's cells (plus limbo) are its ledger's net to the millilitre.
 * <ul>
 *   <li>units: 5.12;</li>
 *   <li>geometry: layers cover exactly the open slabs; an edge asked from both sides is the same; the fill curve and its
 *   inverse agree; a 3 x 3 x 1 m pit holds 9,000 L to the millilitre;</li>
 *   <li>still: 1,000 L in a 2 x 2 m pit stands level at 0.25 m and goes to sleep;</li>
 *   <li>A (rain): a pit fills from rain continuously, faster with a catchment sloping to it; filled from a hose it spills
 *   only at its lowest rim and only through the notch there;</li>
 *   <li>B (trench to a river): nothing flows while the bank stands; a 0.5 m trench lets the river in until the pit stands at
 *   the river's level; a 1 m trench fills it faster;</li>
 *   <li>C (pool opened): a 4,000 L pool cut open drains to the notch's sill into a lower hollow, the total unchanged;</li>
 *   <li>D (chunks): flow across a chunk border; saving and loading every 50 ticks changes nothing, to the millilitre and the
 *   cell; a chunk unloaded mid-flow keeps its liquid while its neighbour runs on, and on reload the flow goes on;</li>
 *   <li>G (ground changes under liquid): soil into a pond, a hole dug in it, a column filled solid, a plate through the
 *   water, a change mid-flow: the liquid exact every time, levels as computed;</li>
 *   <li>V (layers): liquid off a ledge falls into the cave layer of the next columns, not onto the roof above;</li>
 *   <li>limit: two pockets of one column that meet only across a slab boundary are one layer (the accepted 1 m limit, C 6.3
 *   b): reproduced and measured;</li>
 *   <li>perf: a 62 x 62 m pond sloshing, then asleep under rain.</li>
 * </ul>
 * {@code gradlew liquidCheck}; exits 1 on a failure.
 */
public final class LiquidCheck {
    private static int failures, checks;

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures++;
            if (failures <= 30) System.out.println("FAIL " + what);
        }
    }

    public static void main(String[] args) {
        units();
        geometry();
        still();
        rainCatchment();
        overflow();
        trench();
        pool();
        chunks();
        groundChanges();
        layers();
        falls();
        slope();
        crest();
        reaching();
        soak();
        rain3a();
        sky();
        limit();
        performance();
        System.out.printf("Liquid check: %d checks, %d failures%n", checks, failures);
        if (failures > 0) System.exit(1);
    }

    // ---- helpers ------------------------------------------------------------------------------------------------------

    private static LiquidEngine engine(SyntheticGround g, int y0, int y1) {
        LiquidEngine e = new LiquidEngine(g, y0, y1);
        // the flow scenes test the flow alone; films and soaking have their own (soak)
        e.setSurfaceStorage(false);
        for (int cx = g.minX >> 4; cx <= (g.minX + g.sizeX - 1) >> 4; cx++)
            for (int cz = g.minZ >> 4; cz <= (g.minZ + g.sizeZ - 1) >> 4; cz++) e.loadChunk(cx, cz, null);
        return e;
    }

    /** One tick, then the ledger check. */
    private static void tick(LiquidEngine e, String scene) {
        e.tick();
        List<String> p = e.verify();
        if (!p.isEmpty()) check(false, scene + " tick " + e.tickCount() + ": " + p.get(0));
    }

    private static int settle(LiquidEngine e, String scene, int max) {
        for (int t = 0; t < max; t++) {
            tick(e, scene);
            if (e.awakeWetCells() == 0) return t + 1;
        }
        List<LiquidCell> awake = new java.util.ArrayList<>();
        for (LiquidCell c : e.cells()) if (!c.sleeping() && !c.boundary() && c.volume() > 0) awake.add(c);
        awake.sort(java.util.Comparator.comparingInt(c -> c.quiet));
        System.out.printf("  %s: %d awake wet cells%n", scene, awake.size());
        for (LiquidCell c : awake.subList(0, Math.min(6, awake.size()))) {
            double q = 0;
            for (LiquidFace f : c.faces) q = Math.max(q, Math.abs(f.q));
            System.out.printf("  awake in %s: (%d %d) floor %.4f level %.6f last %.6f vol %d quiet %d max q %.2e%n",
                    scene, c.x(), c.z(), c.floor(), c.level(), c.lastLevel, c.volume(), c.quiet, q);
        }
        return -1;
    }

    private static double levelAt(LiquidEngine e, int x, double y, int z) {
        LiquidCell c = e.cellAt(x, y, z);
        return c == null ? Double.NaN : c.level();
    }

    private static long volumeIn(LiquidEngine e, int x0, int z0, int x1, int z1) {
        long v = 0;
        for (LiquidCell c : e.cells()) if (c.x() >= x0 && c.x() < x1 && c.z() >= z0 && c.z() < z1) v += c.volume();
        return v;
    }

    private static void allChunks(SyntheticGround g, LiquidEngine e, int minY, int maxY) {
        for (int cx = g.minX >> 4; cx <= (g.minX + g.sizeX - 1) >> 4; cx++)
            for (int cz = g.minZ >> 4; cz <= (g.minZ + g.sizeZ - 1) >> 4; cz++) e.geometryChanged(cx, cz, minY, maxY);
    }

    private record Rain(double mm, int minX, int minZ, int maxX, int maxZ, java.util.function.BiPredicate<Integer, Integer> where)
            implements LiquidEngine.RainField {
        @Override
        public double mmPerHour(int x, int z) {
            return where.test(x, z) ? mm : 0;
        }
    }

    // ---- units ----------------------------------------------------------------------------------------------------------

    private static void units() {
        check(LiquidUnits.ML_PER_GROUND_UNIT == 244.140625, "a ground unit is 244.140625 mL exactly");
        check(LiquidUnits.floorLitres(1999) == 1 && LiquidUnits.floorLitres(999) == 0 && LiquidUnits.floorLitres(0) == 0,
                "only whole litres go to a Forge handler, rounded down");
        check(LiquidUnits.litresToMl(1_000) == 1_000_000, "1,000 L = 1,000,000 mL");
        check(LiquidUnits.forgeAmount(5_000_000_000_000L) == Integer.MAX_VALUE, "a Forge amount is capped, not wrapped");
        boolean threw = false;
        try {
            LiquidUnits.litresToMl(Long.MAX_VALUE / 10);
        } catch (ArithmeticException ex) {
            threw = true;
        }
        check(threw, "litres to mL throws rather than overflow");
        long pond = 100L * 100 * 3 * LiquidUnits.ML_PER_M3;
        check(pond == 30_000_000_000L && pond > Integer.MAX_VALUE, "a 100 x 100 x 3 m pond needs 64 bits (3e10 mL)");
        System.out.println("Units: 1 L = 1 mB = 1,000 mL, ground unit 244.140625 mL, Forge drain floors to whole litres");
    }

    // ---- geometry -------------------------------------------------------------------------------------------------------

    private static void geometry() {
        Random r = new Random(20261024L);
        SyntheticGround g = new SyntheticGround(0, 0, 32, 32,
                (x, z) -> 20 + 1.2 * Math.sin(x * 0.37) * Math.cos(z * 0.29) + 0.4 * Math.sin(x * 1.7 + z * 2.3));
        g.box(5, 22, 5, 9, 22.5, 7);     // an overhang plate
        g.box(12.5, 19, 12, 13, 26, 15); // a half-column wall
        g.box(20, 21, 20, 20.75, 21.3, 20.6);
        LiquidEngine e = engine(g, 10, 34);
        short[] area = new short[24 * 16];
        byte[] ea = new byte[24 * 16], eb = new byte[24 * 16];
        int edgeMismatch = 0, layerMismatch = 0;
        for (int t = 0; t < 400; t++) {
            int x = r.nextInt(31), z = r.nextInt(31);
            g.openArea(x, z, 10, 34, area);
            long open = 0;
            for (short a : area) open += a;
            LiquidColumn col = e.column(x, z);
            long layered = 0;
            // an open layer stops 4 m above the last solid: the air above it is whole slabs
            for (LiquidCell c : col.cells()) layered += c.prefix[c.area.length] + (c.openTop() ? (34L * 16 - c.ceilSlab) * 256 : 0);
            if (layered != open) layerMismatch++;
            Direction d = r.nextBoolean() ? Direction.EAST : Direction.SOUTH;
            g.openEdge(x, z, d, 10, 34, ea);
            g.openEdge(d == Direction.EAST ? x + 1 : x, d == Direction.EAST ? z : z + 1, d.getOpposite(), 10, 34, eb);
            if (!Arrays.equals(ea, eb)) edgeMismatch++;
        }
        check(layerMismatch == 0, "layers cover exactly the open slabs (" + layerMismatch + " columns off)");
        check(edgeMismatch == 0, "an edge asked from both sides gives the same answer (" + edgeMismatch + " off)");
        // the fill curve and its inverse
        double worst = 0;
        boolean monotone = true;
        for (LiquidCell c : e.cells()) {
            double prev = -1;
            for (int k = 0; k <= 40; k++) {
                double h = c.floor() + (Math.min(c.ceiling(), c.floor() + 6) - c.floor()) * k / 40.0;
                long v = Math.round(c.volumeBelow(h));
                double back = c.levelOf(v);
                if (v > 0) worst = Math.max(worst, Math.abs(back - h));
                if (back < prev) monotone = false;
                prev = back;
            }
        }
        check(worst < 3e-4, "the level of a fill curve's volume is its height (worst " + worst + " m)");
        check(monotone, "the level rises with the volume");
        // a 3 x 3 x 1 m pit with straight walls holds 9,000 L
        SyntheticGround pit = new SyntheticGround(0, 0, 8, 8, (x, z) -> 21);
        pit.dig(2, 2, 5, 5, 20);
        LiquidEngine pe = engine(pit, 10, 30);
        double held = 0;
        for (int x = 2; x < 5; x++) for (int z = 2; z < 5; z++) held += pe.column(x, z).cellAt(20.5).volumeBelow(21);
        check(Math.abs(held - 9_000_000) < 1e-6, "a 3 x 3 x 1 m pit holds 9,000 L (" + held + " mL)");
        int[] grid = new int[65 * 65];
        g.surfaceGrid(0, 0, 40, grid);
        check(Math.abs(grid[0] / 256.0 - g.surfaceBelow(0, 40, 0)) < 1e-9 && grid[64 * 65 + 64] != GroundViewV0.UNKNOWN_HEIGHT,
                "surfaceGrid reads the surface, the far rows from the neighbour");
        System.out.printf("Geometry: 400 columns, layer and edge mismatches %d / %d, fill curve error %.1e m, pit %.0f L%n",
                layerMismatch, edgeMismatch, worst, held / 1000);
    }

    // ---- still water ----------------------------------------------------------------------------------------------------

    private static void still() {
        SyntheticGround g = new SyntheticGround(0, 0, 12, 12, (x, z) -> 21);
        g.dig(5, 5, 7, 7, 18);
        LiquidEngine e = engine(g, 10, 30);
        e.add(5, 18.5, 5, 1_000_000);
        int t = settle(e, "still", 3000);
        double worst = 0;
        for (int x = 5; x < 7; x++) for (int z = 5; z < 7; z++) worst = Math.max(worst, Math.abs(levelAt(e, x, 18.5, z) - 18.25));
        check(worst < 0.001, "1,000 L in a 2 x 2 m pit stands at 18.25 (worst " + worst + ")");
        check(t > 0, "the pit's water goes to sleep");
        check(e.total() == 1_000_000, "still: 1,000,000 mL kept (" + e.total() + ")");
        List<LiquidBasin> b = e.basins();
        check(b.size() == 1 && Math.abs(b.get(0).spill() - 21) < 1e-9, "one basin, spilling at the ground (21)");
        System.out.printf("Still: level error %.1e m, asleep after %d ticks, spill %.3f%n", worst, t, b.isEmpty() ? Double.NaN : b.get(0).spill());
        sides();
    }

    /** Joined sides (what the client's surface smooths across): within a pit, not across the wall between two pits. */
    private static void sides() {
        SyntheticGround g = new SyntheticGround(0, 0, 12, 12, (x, z) -> 21);
        g.dig(2, 5, 4, 7, 18);
        g.dig(5, 5, 7, 7, 18);
        LiquidEngine e = engine(g, 10, 30);
        e.add(2, 18.5, 5, 1_000_000);
        e.add(5, 18.5, 5, 1_000_000);
        settle(e, "sides", 3000);
        int west = e.cellAt(3, 18.5, 5).joinedSides(), east = e.cellAt(5, 18.5, 5).joinedSides();
        check((west & LiquidCell.EAST) == 0 && (east & LiquidCell.WEST) == 0, "no joined side across the wall between two pits at one level");
        check(west == (LiquidCell.WEST | LiquidCell.SOUTH) && east == (LiquidCell.EAST | LiquidCell.SOUTH),
                "the pit's cells join their wet neighbours in it (" + west + " " + east + ")");
        check(e.cellAt(4, 21.5, 5) == null || e.cellAt(4, 21.5, 5).joinedSides() == 0, "a dry cell joins nothing");
    }

    // ---- A: rain ---------------------------------------------------------------------------------------------------------

    /** A cone-shaped catchment draining to a pit, closed by a berm. */
    private static SyntheticGround catchment() {
        return new SyntheticGround(0, 0, 25, 25, (x, z) -> {
            double r = Math.hypot(x - 12.5, z - 12.5);
            if (r >= 10) return 21.6;
            double cone = 20 + 0.08 * r;
            return Math.min(cone, 19.0 + Math.max(0, r - 1.5));
        });
    }

    private static void rainCatchment() {
        int[] ticks = new int[2];
        for (int run = 0; run < 2; run++) {
            SyntheticGround g = catchment();
            LiquidEngine e = engine(g, 10, 30);
            double reach = run == 0 ? 9.5 : 2.6;   // the whole catchment, or only the pit
            e.setRain(new Rain(30, 0, 0, 24, 24, (x, z) -> Math.hypot(x + 0.5 - 12.5, z + 0.5 - 12.5) < reach));
            double last = 19.0, worstDrop = 0, worstJump = 0;
            int t = 0;
            for (; t < 20000; t++) {
                tick(e, "rain " + run);
                if (t % 20 == 19) {
                    double l = levelAt(e, 12, 19.2, 12);
                    if (!Double.isNaN(l)) {
                        worstDrop = Math.max(worstDrop, last - l);
                        worstJump = Math.max(worstJump, l - last);
                        last = l;
                        if (l >= 19.5) break;
                    }
                }
            }
            ticks[run] = t + 1;
            long rained = 0;
            for (int cx = 0; cx < 2; cx++) for (int cz = 0; cz < 2; cz++) rained += e.ledger(cx, cz).rain();
            check(e.total() == rained, "rain " + run + ": every millilitre in the ground came as rain (" + e.total() + " / " + rained + ")");
            check(worstDrop < 0.001, "rain " + run + ": the pit's level never falls while it rains (" + worstDrop + ")");
            check(worstJump < 0.05, "rain " + run + ": the pit's level rises smoothly, no steps (" + worstJump + " m per second)");
            System.out.printf("A rain %s: 30 mm/h, pit to half depth in %d ticks (%.1f game hours), rain %.0f L%n",
                    run == 0 ? "with catchment" : "on the pit only", ticks[run], ticks[run] / 1000.0, rained / 1000.0);
        }
        check(ticks[1] > 2 * ticks[0], "a catchment sloping to the pit fills it more than twice as fast (" + ticks[0] + " vs " + ticks[1] + ")");
    }

    private static void overflow() {
        SyntheticGround g = new SyntheticGround(0, 0, 20, 20, (x, z) -> 20);
        g.dig(6, 6, 9, 9, 19);
        // the notch: 0.5 m wide, its sill 19.6875 for the first metre, then falling to a hollow
        double rim = 19.6875;
        g.shape(9, 7.25, 14, 7.75, (x, z, old) -> Math.min(old, x < 10 ? rim : rim - 0.14 * (x - 10)));
        g.dig(14, 6, 17, 9, 18.5);
        LiquidEngine e = engine(g, 10, 30);
        double firstOut = Double.NaN;
        int firstX = -1, firstZ = -1;
        for (int t = 0; t < 1200; t++) {
            if (t < 900) e.add(7, 19.5, 7, 10_000);
            tick(e, "overflow");
            if (Double.isNaN(firstOut)) {
                for (LiquidCell c : e.cells()) {
                    boolean inPit = c.x() >= 6 && c.x() < 9 && c.z() >= 6 && c.z() < 9;
                    if (!inPit && c.volume() > 0) {
                        firstOut = 19 + volumeIn(e, 6, 6, 9, 9) / 9e6;   // the pit's mean level
                        firstX = c.x();
                        firstZ = c.z();
                        break;
                    }
                }
            }
        }
        check(!Double.isNaN(firstOut) && firstOut >= rim - 0.01, "the pit holds everything until it reaches its lowest rim (" + rim + "; first out at a mean " + firstOut + ")");
        check(firstX == 9 && firstZ == 7, "the first water out of the pit is in the notch (" + firstX + " " + firstZ + ")");
        int t = settle(e, "overflow", 40000);
        double pit = levelAt(e, 7, 19.5, 7);
        check(Math.abs(pit - rim) < 0.003, "after the hose stops the pit drains to its rim, " + rim + " (" + pit + ")");
        check(e.total() == 9_000L * 1000, "overflow: 9,000 L kept (" + e.total() + ")");
        long outside = e.total() - volumeIn(e, 6, 6, 9, 9);
        System.out.printf("A overflow: first out at %.4f through (%d, %d); pit settles at %.4f after %d more ticks; %.0f L went out%n",
                firstOut, firstX, firstZ, pit, t, outside / 1000.0);
    }

    // ---- B: trench to a river ---------------------------------------------------------------------------------------------

    private static void trench() {
        int[] ticks = new int[2];
        for (int run = 0; run < 2; run++) {
            double half = run == 0 ? 0.25 : 0.5;
            SyntheticGround g = new SyntheticGround(0, 0, 24, 9, (x, z) -> {
                if (x < 3) return 19.0;
                double r = Math.hypot(x - 14.5, z - 4.5);
                return Math.min(21.5, 19.5 + Math.max(0, r - 1));
            });
            LiquidEngine e = engine(g, 10, 30);
            for (int x = 0; x < 3; x++) for (int z = 0; z < 9; z++) e.setBoundary(x, 20, z, 21.0);
            for (int t = 0; t < 200; t++) tick(e, "trench closed");
            check(e.total() == 0, "trench " + run + ": nothing flows while the bank stands (" + e.total() + ")");
            g.shape(3, 4.5 - half, 13.5, 4.5 + half, (x, z, old) -> Math.min(old, 20.5));
            e.geometryChanged(0, 0, 18, 23);
            e.geometryChanged(1, 0, 18, 23);
            double fastest = 0;
            int t = 0;
            for (; t < 60000; t++) {
                tick(e, "trench " + run);
                if (t % 10 == 0) {
                    for (LiquidFace f : e.faces()) {
                        double hi = Math.max(f.a.level(), f.b.level()), area = f.crossSection(hi);
                        if (area > 0 && f.a.z() == 4 && f.b.z() == 4) fastest = Math.max(fastest, Math.abs(f.flow()) / area);
                    }
                }
                if (levelAt(e, 14, 19.7, 4) >= 20.99) break;
            }
            ticks[run] = t + 1;
            int s = settle(e, "trench " + run, 60000);
            double pit = levelAt(e, 14, 19.7, 4);
            LiquidLedger l0 = e.ledger(0, 0), l1 = e.ledger(1, 0);
            long fromRiver = l0.boundaryIn() + l1.boundaryIn() - l0.boundaryOut() - l1.boundaryOut();
            check(Math.abs(pit - 21.0) < 0.003, "trench " + run + ": the pit stands at the river's level, 21 (" + pit + ")");
            check(fromRiver == e.total(), "trench " + run + ": all the pit's water came from the river (" + fromRiver + " / " + e.total() + ")");
            check(fastest > 0.05, "trench " + run + ": water runs in the trench (" + fastest + " m/s)");
            check(s > 0, "trench " + run + ": settles");
            System.out.printf("B trench %.1f m: filled to 20.99 in %d ticks, fastest %.2f m/s in the trench, %.0f L from the river, pit %.4f%n",
                    half * 2, ticks[run], fastest, fromRiver / 1000.0, pit);
        }
        check(ticks[1] < 0.8 * ticks[0], "a 1 m trench fills the pit faster than a 0.5 m one (" + ticks[1] + " vs " + ticks[0] + ")");
    }

    // ---- C: a pool cut open ------------------------------------------------------------------------------------------------

    private static void pool() {
        SyntheticGround g = new SyntheticGround(0, 0, 20, 10, (x, z) -> 22 - 0.15 * x);
        g.set(3, 3, 7, 6, 20.0);
        LiquidEngine e = engine(g, 10, 30);
        e.add(4, 20.1, 4, 4_000_000);
        int t0 = settle(e, "pool fill", 6000);
        double before = levelAt(e, 4, 20.1, 4);
        long poolBefore = volumeIn(e, 3, 3, 7, 6);
        check(t0 > 0 && poolBefore == 4_000_000, "the pool holds its 4,000 L (" + poolBefore + ")");
        // the cut: 0.5 m wide, sill 20.125 for its first metre, then running down to a hollow
        double sill = 20.125;
        g.shape(7, 4.25, 14.5, 4.75, (x, z, old) -> Math.min(old, x < 8 ? sill : sill - 0.04 * (x - 8)));
        g.dig(14, 3, 18, 6, 18.8);
        allChunks(g, e, 18, 23);
        int t = settle(e, "pool cut", 60000);
        double after = levelAt(e, 4, 20.1, 4);
        long poolAfter = volumeIn(e, 3, 3, 7, 6);
        double expected = 0;
        for (int x = 3; x < 7; x++) for (int z = 3; z < 6; z++) expected += e.cellAt(x, 20.1, z).volumeBelow(sill);
        check(e.total() == 4_000_000, "pool: 4,000 L kept (" + e.total() + ")");
        check(Math.abs(after - sill) < 0.003, "the pool drains to the cut's sill, " + sill + " (" + after + ")");
        check(Math.abs(poolAfter - expected) < 0.02 * expected,"what stays is the pool below the sill (" + poolAfter + " vs " + Math.round(expected) + ")");
        check(t > 0, "pool: settles");
        System.out.printf("C pool: %.4f -> %.4f, %.0f L stayed, %.0f L went down the cut and the slope, %d ticks%n",
                before, after, poolAfter / 1000.0, (e.total() - poolAfter) / 1000.0, t);
    }

    // ---- D: chunks ---------------------------------------------------------------------------------------------------------

    private static SyntheticGround trough() {
        SyntheticGround g = new SyntheticGround(0, 0, 32, 8, (x, z) -> 21);
        g.dig(2, 2, 30, 6, 19);
        return g;
    }

    private static Map<String, Long> volumes(LiquidEngine e) {
        Map<String, Long> m = new HashMap<>();
        for (LiquidCell c : e.cells()) if (c.volume() > 0) m.put(c.x() + " " + c.floor() + " " + c.z(), c.volume());
        return m;
    }

    private static void chunks() {
        // continuous
        SyntheticGround g = trough();
        LiquidEngine e = engine(g, 10, 30);
        for (int z = 2; z < 6; z++) e.add(3, 19.5, z, 2_000_000);
        for (int t = 0; t < 600; t++) tick(e, "chunks continuous");
        Map<String, Long> continuous = volumes(e);
        long crossed = e.ledger(1, 0).crossIn();
        check(crossed > 0, "water crossed the chunk border (" + crossed + " mL)");
        // saved and loaded every 50 ticks, east chunk first
        SyntheticGround g2 = trough();
        LiquidEngine s = engine(g2, 10, 30);
        for (int z = 2; z < 6; z++) s.add(3, 19.5, z, 2_000_000);
        for (int t = 0; t < 600; t++) {
            tick(s, "chunks saved");
            if (t % 50 == 49) {
                byte[] west = s.saveChunk(0, 0), east = s.saveChunk(1, 0);
                s = new LiquidEngine(g2, 10, 30);
                s.setSurfaceStorage(false);
                s.loadChunk(1, 0, east);
                s.loadChunk(0, 0, west);
            }
        }
        Map<String, Long> saved = volumes(s);
        check(saved.equals(continuous), "saving and loading every 50 ticks changes no cell's volume (" + diff(continuous, saved) + ")");
        // the east chunk unloaded mid-flow
        SyntheticGround g3 = trough();
        LiquidEngine u = engine(g3, 10, 30);
        for (int z = 2; z < 6; z++) u.add(3, 19.5, z, 2_000_000);
        for (int t = 0; t < 400; t++) tick(u, "chunks before unload");
        long east = volumeIn(u, 16, 0, 32, 8), all = u.total();
        byte[] data = u.unloadChunk(1, 0);
        g3.unload(1, 0);
        for (int t = 0; t < 200; t++) tick(u, "chunks east away");
        check(u.total() == all - east, "while the east chunk is away the west keeps its own, exactly (" + u.total() + " vs " + (all - east) + ")");
        g3.load(1, 0);
        u.loadChunk(1, 0, data);
        check(volumeIn(u, 16, 0, 32, 8) == east, "the east chunk comes back with what it had (" + volumeIn(u, 16, 0, 32, 8) + " vs " + east + ")");
        int t = settle(u, "chunks reloaded", 40000);
        double wl = levelAt(u, 4, 19.2, 3), el = levelAt(u, 28, 19.2, 3);
        check(u.total() == 8_000_000, "chunks: 8,000 L kept through unload and reload (" + u.total() + ")");
        check(t > 0 && Math.abs(wl - el) < 0.003, "after reload the trough levels out across the border (" + wl + " / " + el + ")");
        System.out.printf("D chunks: %.0f L crossed in 600 ticks; save/load every 50 ticks identical to the cell: %s; east held %.0f L while away; level %.4f / %.4f%n",
                crossed / 1000.0, saved.equals(continuous), east / 1000.0, wl, el);
    }

    private static String diff(Map<String, Long> a, Map<String, Long> b) {
        int n = 0;
        long worst = 0;
        for (String k : a.keySet()) {
            long d = Math.abs(a.get(k) - b.getOrDefault(k, 0L));
            if (d != 0) n++;
            worst = Math.max(worst, d);
        }
        for (String k : b.keySet()) if (!a.containsKey(k)) n++;
        return n + " cells differ, worst " + worst + " mL";
    }

    // ---- G: the ground changes under the liquid -----------------------------------------------------------------------

    private static void groundChanges() {
        SyntheticGround g = new SyntheticGround(0, 0, 12, 12, (x, z) -> 21);
        g.dig(3, 3, 8, 8, 19);
        LiquidEngine e = engine(g, 10, 30);
        for (int x = 3; x < 8; x++) for (int z = 3; z < 8; z++) e.add(x, 19.1, z, 250_000);
        settle(e, "pond", 6000);
        double l0 = levelAt(e, 3, 19.1, 3);
        check(Math.abs(l0 - 19.25) < 0.001, "pond: 6,250 L over 25 m2 stands at 19.25 (" + l0 + ")");
        // G1: soil pushed into the pond, 2 x 2 m x 0.1875 m
        g.set(4, 4, 6, 6, 19.1875);
        e.geometryChanged(0, 0, 18, 22);
        check(e.total() == 6_250_000, "G1 soil in: kept to the millilitre at once (" + e.total() + ")");
        settle(e, "G1", 20000);
        double l1 = levelAt(e, 3, 19.1, 3);
        check(Math.abs(l1 - (19 + 7.0 / 25)) < 0.002, "G1: the level rises by what the soil displaced, to 19.28 (" + l1 + ")");
        // G2: a hole dug a metre deeper in the pond
        g.set(6, 6, 7, 7, 18.0);
        e.geometryChanged(0, 0, 17, 22);
        check(e.total() == 6_250_000, "G2 hole: kept (" + e.total() + ")");
        settle(e, "G2", 20000);
        double l2 = levelAt(e, 3, 19.1, 3);
        check(Math.abs(l2 - (19 + 6.0 / 25)) < 0.002, "G2: the level falls by the hole, to 19.24 (" + l2 + ")");
        // G3: a column under water filled solid: its water goes next door
        g.set(7, 3, 8, 4, 21);
        e.geometryChanged(0, 0, 18, 22);
        check(e.total() == 6_250_000 && e.ledger(0, 0).limbo() == 0, "G3 column filled: kept, nothing in limbo (" + e.total() + ")");
        settle(e, "G3", 20000);
        double l3 = levelAt(e, 3, 19.1, 3);
        check(l3 > l2, "G3: the level rises (" + l3 + ")");
        // G4: a plate through the water splits a column's layer
        g.box(5, 19.10, 3, 6, 19.15, 4);
        e.geometryChanged(0, 0, 18, 22);
        check(e.total() == 6_250_000 && e.ledger(0, 0).limbo() == 0, "G4 plate: kept, nothing in limbo (" + e.total() + ")");
        settle(e, "G4", 20000);
        // G5: the ground changes while water still runs
        e.add(3, 19.1, 7, 500_000);
        for (int t = 0; t < 5; t++) tick(e, "G5 flowing");
        g.set(3, 7, 4, 8, 19.4);
        e.geometryChanged(0, 0, 18, 22);
        check(e.total() == 6_750_000, "G5 change mid-flow: kept (" + e.total() + ")");
        int t5 = settle(e, "G5", 20000);
        check(t5 > 0, "G5: settles");
        // G6: one block's change rebuilds only the columns around it (the game's block changes, 16.3)
        double before6 = levelAt(e, 3, 19.1, 3);
        g.set(5, 6, 6, 7, 18.5);
        e.groundChanged(4, 5, 6, 7);
        check(e.total() == 6_750_000 && e.ledger(0, 0).limbo() == 0, "G6 one column dug: kept (" + e.total() + ")");
        settle(e, "G6", 20000);
        double l6 = levelAt(e, 3, 19.1, 3);
        check(l6 < before6, "G6: the level falls into the new hole (" + before6 + " -> " + l6 + ")");
        System.out.printf("G ground changes: 19.2500 -> soil %.4f -> hole %.4f -> filled column %.4f; plate and mid-flow change exact; limbo %d mL%n",
                l1, l2, l3, e.ledger(0, 0).limbo());
    }

    // ---- V: layers --------------------------------------------------------------------------------------------------------

    private static void layers() {
        // a ledge at 22 west of x = 5; east of it a cave from 18 under a roof at 24..25, open again above
        SyntheticGround g = new SyntheticGround(0, 0, 12, 6, (x, z) -> x < 5 ? 22 : 18);
        g.box(5, 24, 0, 9, 25, 6);
        g.set(9, 0, 12, 6, 26);
        g.set(0, 0, 12, 1, 26);
        g.set(0, 5, 12, 6, 26);
        LiquidEngine e = engine(g, 10, 32);
        check(e.column(6, 2).cells().size() == 2, "a column under the roof has two layers, the cave and above the roof");
        e.add(2, 22.2, 2, 3_000_000);
        int t = settle(e, "layers", 40000);
        long cave = 0, roof = 0;
        for (LiquidCell c : e.cells()) {
            if (c.x() < 5 || c.x() >= 9) continue;
            if (c.floor() < 20) cave += c.volume();
            else roof += c.volume();
        }
        check(e.total() == 3_000_000, "layers: kept (" + e.total() + ")");
        check(roof == 0 && cave > 2_000_000, "water off the ledge falls into the cave layer, none onto the roof (cave " + cave + ", roof " + roof + ")");
        check(t > 0, "layers: settles");
        System.out.printf("V layers: %.0f L fell from the ledge into the cave, %d mL on the roof, settled in %d ticks%n", cave / 1000.0, roof, t);
    }

    // ---- F: falling water (docs/gameplay/water_falling_v1_design.md) -----------------------------------------------------------

    private static void falls() {
        // a terrace at 23 west of x = 6, ground 21 east of it; a 3 x 3 pit in the terrace to 22 with a notch at (5, 5) to
        // 22.3 (22.3125 on the 1/16 m grid), and a pit below the terrace's edge to 19.5: the overflow goes over the notch's lip and falls 2.8 m
        SyntheticGround g = new SyntheticGround(0, 0, 12, 12, (x, z) -> x < 6 ? 23 : 21);
        g.dig(2, 4, 5, 7, 22);
        g.dig(5, 5, 6, 6, 22.3);
        g.dig(6, 3, 9, 8, 19.5);
        LiquidEngine e = engine(g, 10, 32);
        e.add(3, 22.5, 5, 6_000_000);
        long firstAir = -1, firstBelow = -1;
        double lip = Double.NaN, bottom = Double.NaN, flow = 0;
        int sign = 0;
        boolean alongX = true, saved = false;
        for (int t = 0; t < 6000; t++) {
            tick(e, "falls");
            if (firstAir < 0 && e.inFlight() > 0) firstAir = e.tickCount();
            if (firstBelow < 0 && volumeIn(e, 6, 3, 9, 8) > 0) firstBelow = e.tickCount();
            List<LiquidFall> fl = e.fallsIn(0, 0);
            LiquidFall notch = null;
            for (LiquidFall x : fl) if (x.x() == 6 && x.z() == 5) notch = x;
            if (notch != null && notch.flow() > flow) {
                LiquidFall f = notch;
                flow = f.flow();
                lip = f.lip();
                bottom = f.bottom();
                sign = f.sign();
                alongX = f.alongX();
            }
            // a save mid-fall lands what is in the air into the chunk first, and nothing is lost
            if (!saved && e.inFlight() > 0 && t > 200) {
                e.saveChunk(0, 0);
                saved = true;
                check(e.inFlight() == 0 && e.verify().isEmpty(), "a save lands the liquid in the air first, the ledger still holds");
            }
        }
        int settled = settle(e, "falls", 20000);
        long expected = (long) Math.ceil(Math.sqrt(2 * 2.8 / LiquidEngine.G) / LiquidEngine.DT);
        check(firstAir > 0 && firstBelow - firstAir >= expected - 1 && firstBelow - firstAir <= expected + 1,
                "the overflow lands below after its fall time, " + expected + " ticks (" + (firstBelow - firstAir) + ")");
        check(flow > 0 && Math.abs(lip - 22.3125) < 1e-9 && bottom < 22.3 - LiquidEngine.FALL_MIN && sign == 1 && !alongX,
                "the fall is found at the notch's lip, going east (lip " + lip + ", bottom " + bottom + ", sign " + sign + ")");
        check(e.total() == 6_000_000 && e.inFlight() == 0, "falls: kept, none left in the air (" + e.total() + ")");
        check(settled > 0 && e.fallsIn(0, 0).isEmpty(), "falls: settles, and the fall ends");
        System.out.printf("F falls: lip %.2f, fell to %.2f, %.2f L/s at most, landed %d ticks after leaving (free fall %d)%n",
                lip, bottom, flow, firstBelow - firstAir, expected);
    }

    // ---- slope: rain running down a smoothed hillside (falling water 18) ------------------------------------------------

    private static void slope() {
        // a hillside falling 0.4 m a metre to the east, as the smoothing draws one (a block every 2.5 m), its ground under
        // every 1/16 m point as drawn; at x = 12 a 1.2 m step down into a basin
        SyntheticGround g = new SyntheticGround(0, 0, 16, 6, (x, z) -> x < 12 ? 24 - 0.4 * x : 18);
        LiquidEngine e = engine(g, 10, 30);
        e.setRain(new Rain(20, 0, 0, 16, 6, (x, z) -> x < 12));
        int fallsOnSlope = 0, fallsAtStep = 0;
        double deepest = 0;
        for (int t = 0; t < 3000; t++) {
            tick(e, "slope");
            for (LiquidFall f : e.fallsIn(0, 0)) {
                if (f.x() < 12) fallsOnSlope++;
                else fallsAtStep++;
            }
            if (t >= 1000) {
                for (LiquidCell c : e.cells()) {
                    if (c.x() >= 1 && c.x() < 12 && !c.boundary()) deepest = Math.max(deepest, c.level() - c.floor());
                }
            }
        }
        e.setRain(null);
        settle(e, "slope", 30000);
        long left = volumeIn(e, 0, 0, 12, 6);
        check(fallsOnSlope == 0, "rain on a smooth slope runs down it: no falls on it (" + fallsOnSlope + ")");
        check(fallsAtStep > 0, "it falls where the ground steps down 1.2 m into the basin");
        check(deepest < 0.02, "it runs down as a sheet, nowhere on the slope deeper than 2 cm in the rain (" + deepest + " m)");
        check(left * 100 < e.total(), "after the rain the slope drains into the basin (" + left + " of " + e.total() + " mL left on it)");
        System.out.printf("S slope: 20 mm/h on a 0.4 slope, deepest %.1f mm in the rain, %.2f %% left on it after%n",
                deepest * 1000, 100.0 * left / Math.max(1, e.total()));
    }

    // ---- crest: a pond spilling over the top of a slope; where its drawn surface would hang (falling water 21) ----------

    private static void crest() {
        // a pond floored at 23.8 between x = 1 and 4.5, a bank to the west; from its crest at 4.5, inside a cell, the ground
        // falls 0.5 m a metre (as the smoothing draws it) to the flat at 20; water poured into the pond goes over the crest
        // and runs down (a crest inside a cell holds no water back: the cell is one level; it drains the pond to a film)
        SyntheticGround.Height h = (x, z) -> x < 1 ? 24.3 : x < 4.5 ? 23.8 : x < 12.5 ? 24.0 - 0.5 * (x - 4.5) : 20;
        SyntheticGround g = new SyntheticGround(0, 0, 16, 6, h);
        LiquidEngine e = engine(g, 10, 30);
        // the pond filled to its brim cell by cell (fill rises as one pool from the lowest water it reaches: the basin)
        for (int x = 1; x < 5; x++) for (int z = 0; z < 6; z++) e.add(x, 23.9, z, 190_000);
        for (int t = 0; t < 1200; t++) {
            for (int z = 0; z < 6; z++) e.add(2, 23.9, z, 20);
            tick(e, "crest");
        }
        // the client draws cells 8 mm deep or more (LiquidPackets.Surface.SHOWN); a drawn cell's surface reaches out to
        // its edges at its level. An edge where the water goes on (joined) into a column with nothing drawn within
        // ClientLiquid's JOIN of that level, its ground there lower: the surface hangs over that ground
        double shown = 0.008, join = 0.6;
        int hanging = 0;
        double worst = 0, worstAfter = 0;
        for (LiquidCell c : e.cells()) {
            if (c.boundary() || c.level() - c.floor() < shown) continue;
            int sides = c.joinedSides();
            int[][] dirs = {{LiquidCell.EAST, 1, 0}, {LiquidCell.WEST, -1, 0}, {LiquidCell.SOUTH, 0, 1}, {LiquidCell.NORTH, 0, -1}};
            for (int[] d : dirs) {
                if ((sides & d[0]) == 0) continue;
                LiquidColumn n = e.column(c.x() + d[1], c.z() + d[2]);
                boolean drawn = false;
                if (n != null) for (LiquidCell o : n.cells()) {
                    if (o.level() - o.floor() >= shown && Math.abs(o.level() - c.level()) < join) drawn = true;
                }
                if (drawn) continue;
                // the ground just inside the neighbour, at the middle of the shared edge
                double gx = d[1] == 0 ? c.x() + 0.5 : d[1] > 0 ? c.x() + 1.02 : c.x() - 0.02;
                double gz = d[2] == 0 ? c.z() + 0.5 : d[2] > 0 ? c.z() + 1.02 : c.z() - 0.02;
                double ground = h.at(gx, gz);
                double gap = c.level() - ground;
                if (gap > 0.02) hanging++;
                worst = Math.max(worst, gap);
                float after = LiquidSurfaceEdge.runoff((float) c.level(), (float) c.floor(), ground);
                // no lower than the cell's own floor (1/16 m slabs: 23.8125 over ground drawn at 23.8)
                worstAfter = Math.max(worstAfter, after - Math.max(ground, c.floor()));
            }
        }
        check(hanging > 0, "a pond spilling over a crest: its drawn surface at its level reaches out over the slope (" + hanging + " edges, up to " + worst + " m)");
        check(worstAfter <= LiquidSurfaceEdge.LIP + 1e-4, "brought down to the ground there it meets the slope (" + worstAfter + " m above it at most)");
        System.out.printf("S crest: %d hanging edges, the surface up to %.3f m over the slope; brought down, %.4f m%n", hanging, worst, worstAfter);
    }

    // ---- reaching: shapes reaching above their own block (falling water 18) ---------------------------------------------

    private static void reaching() {
        var tall = List.of(new net.minecraft.world.phys.AABB(0, 0, 0, 1, 1.5, 1));
        check(com.antaurora.apofirstlight.liquid.ground.BlockGroundView.count(com.antaurora.apofirstlight.liquid.ground.BlockGroundView.rasterise(tall, 0)) == 4096
                        && com.antaurora.apofirstlight.liquid.ground.BlockGroundView.count(com.antaurora.apofirstlight.liquid.ground.BlockGroundView.rasterise(tall, 1)) == 2048,
                "a 1.5 m tall shape fills its block and half the block above");
        // a smoothed top's 8 x 8 collision boxes, its drawn top rising from 0.5 to 1.75 above its floor: its block and the
        // one above together hold as many 1/16 m cells as lie under the boxes (cut at 1 m, the upper part was a hollow)
        List<net.minecraft.world.phys.AABB> boxes = new java.util.ArrayList<>();
        int expected = 0;
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                int top = (int) Math.round((0.5 + 1.25 * (i + 0.5) / 8) * 16);
                boxes.add(new net.minecraft.world.phys.AABB(i / 8.0, 0, j / 8.0, (i + 1) / 8.0, top / 16.0, (j + 1) / 8.0));
                expected += 4 * top;
            }
        }
        int own = com.antaurora.apofirstlight.liquid.ground.BlockGroundView.count(com.antaurora.apofirstlight.liquid.ground.BlockGroundView.rasterise(boxes, 0));
        int up = com.antaurora.apofirstlight.liquid.ground.BlockGroundView.count(com.antaurora.apofirstlight.liquid.ground.BlockGroundView.rasterise(boxes, 1));
        check(own + up == expected && up > 0, "a smoothed top reaching into the block above is solid there too (" + own + " + " + up + " of " + expected + ")");
    }

    // ---- soak: films the floor holds, soaking in and drying; a fill rising as one pool (falling water 12) ---------------

    private static void soak() {
        // flat soil at 20 with a 3 x 3 pit to 19 at (2..4, 2..4)
        SyntheticGround g = new SyntheticGround(0, 0, 32, 32, (x, z) -> 20);
        g.dig(2, 2, 5, 5, 19);
        LiquidEngine e = new LiquidEngine(g, 10, 32);
        e.loadChunk(0, 0, null);
        e.loadChunk(1, 0, null);
        e.loadChunk(0, 1, null);
        e.loadChunk(1, 1, null);
        // a fill rises over the whole pit at once: 3,000 L on 9 m2 is 0.333 m
        int cells = e.fill(3, 19.5, 3, 3_000_000);
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (LiquidCell c : e.cells()) {
            if (c.volume() <= 0) continue;
            lo = Math.min(lo, c.level());
            hi = Math.max(hi, c.level());
        }
        check(cells == 9 && hi - lo < 1e-3 && Math.abs(hi - 19.3333) < 2e-3 && e.verify().isEmpty(),
                "a fill rises over the whole pit at once (" + cells + " cells, " + lo + " to " + hi + ")");
        // 200 L poured on the flat: it spreads until each cell holds no more than its floor keeps (4 mm on soil), then stops
        // spreading (it soaks in meanwhile, so the wet patch is at its widest early)
        e.add(20, 20.01, 20, 200_000);
        long storage = e.cellAt(20, 20.01, 20).storage();
        int wetMost = 0, wetAt = 0;
        for (int t = 0; t < 1200; t++) {
            tick(e, "soak spread");
            int wet = 0;
            for (LiquidCell c : e.cells()) if (c.x() >= 8 && c.volume() > 0) wet++;
            if (wet > wetMost) {
                wetMost = wet;
                wetAt = t;
            }
        }
        check(storage == 4000, "soil keeps a 4 mm film (" + storage + " mL a cell)");
        check(wetMost >= 20 && wetMost <= 60 && wetAt < 600,
                "a film stops spreading where the floor holds it (at most " + wetMost + " wet cells, tick " + wetAt + ")");
        int spreadTicks = wetAt;
        int wet = wetMost;
        // the film soaks in (10 mm/h) and dries; the pit, deeper than a film, keeps its water
        long before = e.total();
        for (int t = 0; t < 6000; t++) tick(e, "soak");
        long pit = volumeIn(e, 2, 2, 5, 5), flat = e.total() - pit;
        LiquidLedger l = e.ledger(1, 1);
        long soaked = 0, dried = 0;
        for (int cx = 0; cx < 2; cx++) for (int cz = 0; cz < 2; cz++) {
            soaked += e.ledger(cx, cz).infiltration();
            dried += e.ledger(cx, cz).evaporation();
        }
        check(pit == 3_000_000, "the pit keeps its water (" + pit + ")");
        check(flat == 0 && soaked + dried == 200_000 && soaked > dried * 20 && l != null,
                "the film is gone into the ground and the air (" + flat + " left, soaked " + soaked + ", dried " + dried + ")");
        System.out.printf("S soak: fill over %d cells to %.4f; 200 L spread over %d cells in %d ticks and stopped; soaked %.1f L, dried %.1f L in 6000 ticks; the pit kept %.0f L%n",
                cells, hi, wet, spreadTicks, soaked / 1000.0, dried / 1000.0, pit / 1000.0);
    }

    // ---- package 3a: natural rain on the ground (docs/gameplay/water_rain_v1_design.md 4.1) ------------------------------

    /** Rain at mm/h over hours, settled a second (20 ticks) at a time like LiquidRain. */
    private static void rainOn(SurfaceWater w, int i, double mm, double hours, double canopy, int sink, com.antaurora.apofirstlight.liquid.ground.GroundMaterialV0.Kind kind) {
        int passes = (int) Math.round(hours * LiquidUnits.TICKS_PER_GAME_HOUR / 20);
        for (int k = 0; k < passes; k++) w.settle(i, mm, 20, canopy, sink, kind);
    }

    private static void rain3a() {
        var SOIL = com.antaurora.apofirstlight.liquid.ground.GroundMaterialV0.Kind.SOIL;
        var ROCK = com.antaurora.apofirstlight.liquid.ground.GroundMaterialV0.Kind.ROCK;
        int G = SurfaceWater.GROUND;
        // 1. moderate rain on dry loam soaks in: no water stands, the account balances
        SurfaceWater w = new SurfaceWater();
        rainOn(w, 0, 5, 1, 0, G, SOIL);
        check(w.rain() == 5000 && w.surface(0) == 0 && w.infiltration() == 5000 && w.verify() == 0 && w.dark(0) > 0,
                "5 mm/h for an hour soaks into dry loam (rain " + w.rain() + ", standing " + w.surface(0) + ", in " + w.infiltration() + ", dark " + w.dark(0) + ")");
        // 2. a 60 mm/h storm: soaking in slows (Horton) to the steady 10 mm/h; the rest stands as runoff waiting for 3b.
        //    Horton's integral, f = 10 + 65 e^(-4t): 10.95 mm in the first 0.2 h, 15.0 mm in the next 0.8 h
        SurfaceWater st = new SurfaceWater();
        rainOn(st, 1, 60, 0.2, 0, G, SOIL);
        long firstQuarter = st.infiltration();
        rainOn(st, 1, 60, 0.8, 0, G, SOIL);
        long lastThree = st.infiltration() - firstQuarter;
        double fNow = Math.max(10, 75 - LiquidSoak.HORTON_K * st.soaked(1));
        check(st.rain() == 60000 && Math.abs(firstQuarter - 10950) < 600 && Math.abs(lastThree - 15000) < 600 && fNow < 12 && st.verify() == 0,
                "a storm on loam: " + firstQuarter / 1000.0 + " mm in the first 0.2 h, " + lastThree / 1000.0 + " in the next 0.8 h, capacity now " + fNow + " mm/h");
        check(SurfaceWater.runoff(st.surface(1), SOIL) > 30000,
                "the storm leaves runoff over the storage (" + st.surface(1) / 1000.0 + " mm standing, runoff " + SurfaceWater.runoff(st.surface(1), SOIL) / 1000.0 + " mm)");
        // after it, the soil recovers and soaks up the standing water; the account still balances
        rainOn(st, 1, 0, 6, 0, G, SOIL);
        check(st.surface(1) == 0 && st.verify() == 0 && st.soaked(1) < 1, "after the storm the water soaks in and the soil recovers (F " + st.soaked(1) + ")");
        // 3. rock: nothing soaks in; the water stays dark while it dries at 0.2 mm/h
        SurfaceWater r = new SurfaceWater();
        rainOn(r, 2, 5, 1, 0, G, ROCK);
        long onRock = r.surface(2);
        rainOn(r, 2, 0, 10, 0, G, ROCK);
        check(onRock == 5000 && Math.abs(r.surface(2) - 3000) <= 20 && r.dark(2) == 1 && r.infiltration() == 0 && r.verify() == 0,
                "rain on rock stands and dries slowly (" + onRock + " then " + r.surface(2) + " mL after 10 h, dark " + r.dark(2) + ")");
        rainOn(r, 2, 0, 20, 0, G, ROCK);
        check(r.surface(2) == 0 && r.dark(2) == 0, "rock is dry once its water is gone (" + r.surface(2) + ", dark " + r.dark(2) + ")");
        // 4. a broadleaf canopy: a light shower mostly stays on the leaves; a long rain fills them and drips through
        SurfaceWater c = new SurfaceWater();
        rainOn(c, 3, 1, 0.5, 1, G, SOIL);
        long leaves = c.canopy(3), ground = c.infiltration() + c.surface(3);
        check(leaves == 375 && ground == 125, "a 0.5 mm shower under broadleaves: " + leaves + " mL on the leaves, " + ground + " on the ground");
        rainOn(c, 3, 10, 1, 1, G, SOIL);
        check(c.canopy(3) == 1000 && c.verify() == 0, "the leaves hold 1 mm at most (" + c.canopy(3) + ")");
        rainOn(c, 3, 0, 4, 1, G, SOIL);
        check(c.canopy(3) == 0 && c.canopyEvaporation() == 1000 && c.verify() == 0, "the leaves dry after the rain (" + c.canopy(3) + " left, " + c.canopyEvaporation() + " dried)");
        // a tree felled: what its leaves held falls to the ground
        rainOn(c, 4, 1, 1, 2, G, SOIL);
        long held = c.canopy(4);
        c.settle(4, 0, 20, 0, G, SOIL);
        check(held > 0 && c.canopy(4) == 0 && c.verify() == 0, "leaves gone shed their water onto the ground (" + held + " mL)");
        // 5. onto water: everything is handed over; refused, it stays on the ground
        SurfaceWater o = new SurfaceWater();
        rainOn(o, 5, 3, 0.2, 0, G, ROCK);
        long standing = o.surface(5);
        o.settle(5, 3, 20, 0, SurfaceWater.WATER, SOIL);
        long handed = o.handed;
        check(handed == standing + 60 && o.surface(5) == 0 && o.toWater() == handed && o.verify() == 0,
                "rain onto water hands it the rain and what stood there (" + handed + " mL)");
        o.keep(5);
        check(o.surface(5) == handed && o.toWater() == 0 && o.verify() == 0, "refused, it stays on the ground");
        o.settle(5, 3, 20, 0, SurfaceWater.BODY, SOIL);
        check(o.surface(5) == 0 && o.toBodies() == handed + 60 && o.verify() == 0, "rain onto a water body is gone to it (" + o.toBodies() + ")");
        // 6. the rain is the same however the ticks are cut (one long settle or many short ones): no millilitre twice or lost
        SurfaceWater a1 = new SurfaceWater(), a2 = new SurfaceWater();
        a1.settle(0, 7.3, 200, 0, G, ROCK);
        for (int k = 0; k < 10; k++) a2.settle(0, 7.3, 20, 0, G, ROCK);
        check(a1.rain() == 1460 && a2.rain() == 1460, "rain is the same in one settle or ten (" + a1.rain() + ", " + a2.rain() + ")");
        // 7. a save gives the same back
        SurfaceWater back = SurfaceWater.load(st.save());
        SurfaceWater back2 = SurfaceWater.load(c.save());
        check(back.rain() == st.rain() && back.held() == st.held() && back.verify() == 0 && back2.canopyEvaporation() == c.canopyEvaporation()
                && back2.verify() == 0, "a save of the surface water reads back the same");
        // 8. the engine takes rain onto standing water, written apart from test rain, and its accounts stay exact
        SyntheticGround g = new SyntheticGround(0, 0, 16, 16, (x, z) -> 20);
        g.dig(2, 2, 5, 5, 19);
        LiquidEngine e = new LiquidEngine(g, 10, 32);
        e.loadChunk(0, 0, null);
        e.fill(3, 19.5, 3, 900_000);
        for (int t = 0; t < 40; t++) tick(e, "rain3a settle");
        boolean overWater = e.surfaceWet(3, 3), overDry = e.surfaceWet(10, 10);
        boolean took = e.fromSurface(3, 3, 5_000), refused = !e.fromSurface(10, 10, 5_000);
        for (int t = 0; t < 200; t++) tick(e, "rain3a flow");
        LiquidLedger l = e.ledger(0, 0);
        check(overWater && !overDry && took && refused && l.fromSurface() == 5_000 && l.rain() == 0 && e.verify().isEmpty()
                        && volumeIn(e, 2, 2, 5, 5) == 905_000,
                "the engine takes natural rain onto its water only (" + l.fromSurface() + " mL, pit " + volumeIn(e, 2, 2, 5, 5) + ")");
        // and its save (version 2) keeps the account
        byte[] saved = e.unloadChunk(0, 0);
        e.loadChunk(0, 0, saved);
        check(e.ledger(0, 0).fromSurface() == 5_000 && e.verify().isEmpty(), "a liquid save keeps the natural rain's column of the account");
        System.out.printf("R3a: loam storm %.1f mm in 0.2 h then %.1f mm in 0.8 h, capacity %.1f mm/h; rock 5 mm dries to %.2f mm in 10 h; leaves %d mL of a 0.5 mm shower%n",
                firstQuarter / 1000.0, lastThree / 1000.0, fNow, 3.0, leaves);
    }

    // ---- sky: water higher than an open layer's kept slabs ----------------------------------------------------------------------

    private static void sky() {
        // a 2 x 1 m shaft, floor 18, in walls to 30; a thin ledge high in the east column keeps its slabs to 29.5, the west
        // column's stop at 22: 12,000 L stands 6 m deep, above both, and joins over the west column's top
        SyntheticGround g = new SyntheticGround(0, 0, 8, 8, (x, z) -> 30);
        g.set(3, 3, 5, 4, 18);
        g.box(4.75, 25, 3, 5, 25.5, 4);
        LiquidEngine e = engine(g, 10, 40);
        LiquidCell w = e.column(3, 3).cellAt(18.5), east = e.column(4, 3).cellAt(18.5);
        check(w.openTop() && w.ceiling() == 22 && east.ceiling() == 29.5, "open layers keep 4 m above their last solid (" + w.ceiling() + ", " + east.ceiling() + ")");
        e.add(3, 18.5, 3, 12_000_000);
        int t = settle(e, "sky", 20000);
        double lw = levelAt(e, 3, 18.5, 3), le = levelAt(e, 4, 18.5, 3), expected = 18 + 12 / 2.0;   // the ledge is above it
        check(e.total() == 12_000_000, "sky: kept (" + e.total() + ")");
        check(t > 0 && Math.abs(lw - le) < 0.003, "sky: the two columns join above the west one's kept slabs (" + lw + " / " + le + ")");
        check(Math.abs(lw - expected) < 0.003, "sky: 12,000 L over 2 m2 stands at " + expected + " (" + lw + ")");
        System.out.printf("Sky: open layers kept to %.2f / %.2f; 12,000 L stands at %.4f / %.4f (expected %.4f)%n", w.ceiling(), east.ceiling(), lw, le, expected);
    }

    // ---- the accepted limit --------------------------------------------------------------------------------------------------

    private static void limit() {
        // one column walled in at 25: west half open 19-20 under a block (20-22), east half solid to 20 and open 20-22
        SyntheticGround g = new SyntheticGround(0, 0, 9, 9, (x, z) -> 25);
        g.set(4, 4, 5, 5, 19);
        g.box(4, 20, 4, 4.5, 22, 5);
        g.box(4.5, 19, 4, 5, 20, 5);
        LiquidEngine e = engine(g, 10, 30);
        e.add(4, 23, 4, 100_000);
        settle(e, "limit", 4000);
        double model = levelAt(e, 4, 19.5, 4), real = 20.2;
        check(e.total() == 100_000, "limit: kept (" + e.total() + ")");
        check(e.column(4, 4).cells().size() == 1, "limit: the two pockets make one layer (the accepted 1 m limit)");
        check(Math.abs(model - 19.2) < 0.001, "limit: 100 L poured on the east pocket ends in the west one, at 19.2 (" + model + ")");
        System.out.printf("Limit (accepted, C 6.3 b): a pocket that only meets another across a slab boundary is one layer; 100 L stands at %.3f, really %.3f (%.2f m off)%n",
                model, real, real - model);
    }

    // ---- performance ---------------------------------------------------------------------------------------------------------

    private static void performance() {
        SyntheticGround g = new SyntheticGround(0, 0, 64, 64, (x, z) -> 21);
        g.dig(1, 1, 63, 63, 19);
        LiquidEngine e = engine(g, 10, 30);
        for (int x = 1; x < 63; x++) for (int z = 1; z < 63; z++) e.add(x, 19.2, z, x < 11 && z < 11 ? 800_000 : 500_000);
        for (int t = 0; t < 200; t++) e.tick();
        // best of 5 rounds: the machine is shared
        double ns = Double.MAX_VALUE;
        long[] best = new long[4];
        long bestSteps = 1;
        for (int round = 0; round < 5; round++) {
            long cellSteps = 0;
            Arrays.fill(e.spent, 0);
            long start = System.nanoTime();
            for (int t = 0; t < 100; t++) {
                e.tick();
                cellSteps += (long) e.awakeWetCells() * e.substeps();
            }
            double r = (e.spent[0] + e.spent[1] + e.spent[2] + e.spent[3]) / (double) cellSteps;
            if (cellSteps > 0 && r < ns) {
                ns = r;
                System.arraycopy(e.spent, 0, best, 0, 4);
                bestSteps = cellSteps;
            }
        }
        System.out.printf("  perf phases, ns a cell a substep: rain + levels %.0f, wake %.0f, flow %.0f, quiet + sleep %.0f%n",
                best[0] / (double) bestSteps, best[1] / (double) bestSteps, best[2] / (double) bestSteps, best[3] / (double) bestSteps);
        int sleep = settle(e, "perf", 40000);
        e.setRain(new Rain(10, 1, 1, 62, 62, (x, z) -> true));
        for (int t = 0; t < 100; t++) tick(e, "perf rain");
        Arrays.fill(e.spent, 0);
        long rs = System.nanoTime();
        for (int t = 0; t < 200; t++) e.tick();
        double rainMs = (System.nanoTime() - rs) / 1e6 / 200;
        System.out.printf("  rain on the sleeping pond, ms a tick: rain + levels %.3f, wake %.3f, flow %.3f, quiet + sleep %.3f%n",
                e.spent[0] / 2e8, e.spent[1] / 2e8, e.spent[2] / 2e8, e.spent[3] / 2e8);
        for (int t = 0; t < 20; t++) tick(e, "perf rain");
        check(e.awakeWetCells() == 0, "perf: an even rain on a sleeping pond does not wake it");
        check(sleep > 0, "perf: the pond goes to sleep");
        System.out.printf("Perf: 3,844 cells sloshing %.0f ns a cell a substep (best of 5; design budget 80-120 ns); asleep after %d ticks; package 1's per-column rain on it asleep %.2f ms a tick%n",
                ns, sleep, rainMs);
    }
}
