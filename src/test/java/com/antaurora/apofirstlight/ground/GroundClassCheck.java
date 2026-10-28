package com.antaurora.apofirstlight.ground;

import com.antaurora.apofirstlight.terrainsmooth.SmoothRules;
import com.antaurora.apofirstlight.terrainsmooth.SmoothRules.Cells;
import com.antaurora.apofirstlight.worldgen.underground.GeologyModel;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

/**
 * Terrain rework R1 (docs/worldgen/terrain_rework_v1_design.md 11): GroundClass took over the cell decisions of
 * SmoothRules (its natural top, soilWall, a neighbour's kind) and RockRelief (which soil is worn); behaviour unchanged
 * means every answer the game's rules give now equals the frozen copy's (SmoothRulesR0), cell by cell:
 * <ul>
 *   <li>geometry (the slope key: corner heights and side bands; the collision boxes are a pure function of it), modes A
 *   and B;</li>
 *   <li>notFree with and without walls (what the earthwork may edit), mode B;</li>
 *   <li>the class against the old decisions: V = rock, or soil that is a soil wall (what RockRelief wears); H = the old
 *   natural top (what Terrain Smooth slopes); GroundClass#surface agrees with the class; soilWall is the old one.</li>
 * </ul>
 * Dig R1 (2026-10-26, docs/worldgen/terrain_smooth_v1.md Z) changes answers on purpose, and only there: within two
 * columns of a step of two or more between neighbouring columns (the middle of a two-block spread, the deeper window,
 * the soil wall from a drop of three), and where a block's top gets a fan (a column lower or higher than both
 * neighbours along x or z). Those are counted apart ({@link #digR1}); any other difference still fails: the slopes of
 * one-block steps keep the frozen answers.
 * <p>
 * Scenes: random 3D grids of every cell kind (graded_turf among them), terrains with soil over rock, caves, water and
 * cover; and (UndergroundCheck, per seed) patches of the six seeds' planned ground: their surface, soil depth, rock and
 * caves.
 *   ./gradlew groundClassCheck
 */
public final class GroundClassCheck {
    private GroundClassCheck() {}

    /** A grid of cells (x0, y0, z0 its corner, w x hgt x w), its surfaces by the game's scan, turf corners on some tops. */
    static final class Grid implements Cells {
        final int x0, y0, z0, w, hgt;
        final byte[] c;
        final int[] surf;
        final float[][] turf;

        Grid(int x0, int y0, int z0, int w, int hgt) {
            this.x0 = x0; this.y0 = y0; this.z0 = z0; this.w = w; this.hgt = hgt;
            c = new byte[w * w * hgt];
            surf = new int[w * w];
            turf = new float[w * w * hgt][];
        }

        int i(int x, int y, int z) { return ((z - z0) * w + (x - x0)) * hgt + (y - y0); }

        boolean in(int x, int y, int z) { return x >= x0 && z >= z0 && x < x0 + w && z < z0 + w && y >= y0 && y < y0 + hgt; }

        @Override
        public int cell(int x, int y, int z) {
            if (x < x0 || z < z0 || x >= x0 + w || z >= z0 + w) return SmoothRules.UNKNOWN;
            if (y < y0) return SmoothRules.NATURAL;
            if (y >= y0 + hgt) return SmoothRules.AIR;
            return c[i(x, y, z)];
        }

        @Override
        public int surface(int x, int z) {
            if (x < x0 || z < z0 || x >= x0 + w || z >= z0 + w) return SmoothRules.NO_SURFACE;
            return surf[(z - z0) * w + (x - x0)];
        }

        @Override
        public float turf(int x, int y, int z, int k) {
            if (!in(x, y, z)) return Float.NaN;
            float[] t = turf[i(x, y, z)];
            return t == null ? Float.NaN : t[k];
        }

        void surfaces() {
            for (int z = z0; z < z0 + w; z++) for (int x = x0; x < x0 + w; x++)
                surf[(z - z0) * w + (x - x0)] = SmoothRules.surface(this, x, z, y0 + hgt - 1, y0);
        }
    }

    /** The class the old rules gave: V rock or a soil wall, H the old natural top, else C. */
    static int oldClass(Cells c, int x, int y, int z) {
        int cell = c.cell(x, y, z);
        if (cell == SmoothRules.ROCK || cell == SmoothRules.NATURAL && SmoothRulesR0.soilWall(c, x, y, z)) return GroundClass.V;
        boolean top = c.surface(x, z) == y && cell == SmoothRules.NATURAL && SmoothRules.open(c.cell(x, y + 1, z));
        return top ? GroundClass.H : GroundClass.C;
    }

    /**
     * Dig R1: whether cell (x, y, z) may answer differently from the frozen rules on purpose: two columns or less from a
     * step of two or more between neighbouring columns, or its new geometry has a fan (mode A or B).
     */
    static boolean digR1(Cells c, int x, int y, int z) {
        for (int mode : new int[]{SmoothRules.MODE_A, SmoothRules.MODE_B}) {
            long g = SmoothRules.geometry(c, mode, x, y, z);
            if (g >= 0 && (g & SmoothRules.FAN) != 0) return true;
        }
        for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) {
            int s = c.surface(x + dx, z + dz);
            if (s == SmoothRules.NO_SURFACE) continue;
            for (int[] n : new int[][]{{1, 0}, {0, 1}, {1, 1}, {1, -1}}) {
                int t = c.surface(x + dx + n[0], z + dz + n[1]);
                if (t != SmoothRules.NO_SURFACE && Math.abs(t - s) >= 2) return true;
            }
        }
        return false;
    }

    /** Totals over every scene: cells compared, answers compared, differences; and how many of each class (coverage). */
    static final class Tally {
        long cells, answers, diffs, dig;
        final long[] cls = new long[3];
        long sloped, free;
        final List<String> first = new ArrayList<>();

        void diff(String what) {
            diffs++;
            if (first.size() < 8) first.add(what);
        }

        String line() {
            return String.format(Locale.ROOT, "%d cells, %d answers, %d different, %d changed by dig R1 (H %d, V %d, C %d; %d sloped tops, %d free columns)",
                    cells, answers, diffs, dig, cls[GroundClass.H], cls[GroundClass.V], cls[GroundClass.C], sloped, free);
        }
    }

    /** Compares every cell of columns x0 .. x0 + w - 1, z0 .. z0 + w - 1, levels y0 .. y1. */
    public static void compare(Cells c, int x0, int z0, int w, int y0, int y1, Tally t) {
        for (int z = z0; z < z0 + w; z++) for (int x = x0; x < x0 + w; x++) for (int y = y0; y <= y1; y++) {
            t.cells++;
            int cls = GroundClass.of(c, x, y, z), old = oldClass(c, x, y, z);
            t.cls[cls]++;
            t.answers += 8;
            if (digR1(c, x, y, z)) {   // the frozen answers do not hold here on purpose; the classes still agree with themselves
                if (GroundClass.surface(c, x, y, z) != (cls == GroundClass.H)) t.diff(at("surface", x, y, z));
                long g = SmoothRules.geometry(c, SmoothRules.MODE_B, x, y, z);
                if (g >= 0) t.sloped++;
                if (SmoothRules.notFree(c, SmoothRules.MODE_B, x, y, z, true) == null) t.free++;
                t.dig++;
                continue;
            }
            if (cls != old) t.diff(at("class", x, y, z) + " " + cls + " was " + old);
            if (GroundClass.surface(c, x, y, z) != (cls == GroundClass.H)) t.diff(at("surface", x, y, z));
            if (GroundClass.soilWall(c, x, y, z) != SmoothRulesR0.soilWall(c, x, y, z)) t.diff(at("soilWall", x, y, z));
            for (int mode : new int[]{SmoothRules.MODE_A, SmoothRules.MODE_B}) {
                long g = SmoothRules.geometry(c, mode, x, y, z), r = SmoothRulesR0.geometry(c, mode, x, y, z);
                if (g != r) t.diff(at("geometry mode " + mode, x, y, z) + String.format(Locale.ROOT, " %x was %x", g, r));
                if (mode == SmoothRules.MODE_B && g >= 0) t.sloped++;
            }
            for (boolean walls : new boolean[]{false, true}) {
                String n = SmoothRules.notFree(c, SmoothRules.MODE_B, x, y, z, walls), r = SmoothRulesR0.notFree(c, SmoothRules.MODE_B, x, y, z, walls);
                if (!Objects.equals(n, r)) t.diff(at("notFree walls " + walls, x, y, z) + " '" + n + "' was '" + r + "'");
                if (walls && n == null) t.free++;
            }
            if (SmoothRules.freeCorners(c, SmoothRules.MODE_B, x, y, z) != SmoothRulesR0.freeCorners(c, SmoothRules.MODE_B, x, y, z))
                t.diff(at("freeCorners", x, y, z));
        }
    }

    static String at(String what, int x, int y, int z) { return what + " at " + x + " " + y + " " + z; }

    private static final int[] KINDS = {SmoothRules.AIR, SmoothRules.COVER, SmoothRules.LEAF, SmoothRules.FLUID, SmoothRules.NATURAL,
            SmoothRules.OTHER, SmoothRules.UNKNOWN, SmoothRules.TREE, SmoothRules.ROCK};

    /** Random grids: each level more solid lower down, every kind, a turf top on some OTHER cells. */
    static void randomGrids(Tally t) {
        Random r = new Random(11);
        for (int n = 0; n < 300; n++) {
            Grid g = new Grid(-10, 60, -10, 20, 12);
            for (int z = g.z0; z < g.z0 + g.w; z++) for (int x = g.x0; x < g.x0 + g.w; x++) for (int y = g.y0; y < g.y0 + g.hgt; y++) {
                double solid = 1 - (y - g.y0) / (double) g.hgt;
                int k = r.nextDouble() < solid * 0.9 ? (r.nextDouble() < 0.6 ? SmoothRules.NATURAL : r.nextDouble() < 0.6 ? SmoothRules.ROCK : KINDS[r.nextInt(KINDS.length)])
                        : (r.nextDouble() < 0.85 ? SmoothRules.AIR : KINDS[r.nextInt(KINDS.length)]);
                g.c[g.i(x, y, z)] = (byte) k;
                if (k == SmoothRules.OTHER && r.nextInt(4) == 0)
                    g.turf[g.i(x, y, z)] = new float[]{y + 0.5f + r.nextInt(3) * 0.25f, y + 0.5f + r.nextInt(3) * 0.25f, y + 0.75f + r.nextInt(2) * 0.25f, y + 1f};
            }
            g.surfaces();
            compare(g, g.x0 + 1, g.z0 + 1, g.w - 2, g.y0 + 1, g.y0 + g.hgt - 2, t);
        }
    }

    /** Terrains: soil over rock, rock outcrops and steps, a cave or two under the ground, ponds, cover and trees. */
    static void terrains(Tally t) {
        Random r = new Random(23);
        for (int n = 0; n < 120; n++) {
            Grid g = new Grid(-16, 48, -16, 32, 32);
            double ph = r.nextDouble() * 6, amp = 1 + r.nextDouble() * 4;
            int water = 60 + r.nextInt(3);
            double[][] caves = new double[1 + r.nextInt(3)][];
            for (int k = 0; k < caves.length; k++) caves[k] = new double[]{r.nextInt(24) - 12, 52 + r.nextInt(10), r.nextInt(24) - 12, 2 + r.nextDouble() * 4};
            for (int z = g.z0; z < g.z0 + g.w; z++) for (int x = g.x0; x < g.x0 + g.w; x++) {
                int top = 62 + (int) Math.floor(amp * Math.sin(x * 0.23 + ph) + amp * Math.cos(z * 0.19 - ph) + r.nextDouble() * 1.4);
                int soil = (x + z + n) % 7 == 0 ? 0 : 1 + (int) Math.floor(2.5 + 2 * Math.sin(x * 0.4 + z * 0.3 + ph));
                for (int y = g.y0; y < g.y0 + g.hgt; y++) {
                    int k;
                    if (y <= top) k = y > top - soil ? SmoothRules.NATURAL : SmoothRules.ROCK;
                    else if (y <= water) k = SmoothRules.FLUID;
                    else k = SmoothRules.AIR;
                    for (double[] cv : caves) {
                        double d = Math.hypot(Math.hypot(x + 0.5 - cv[0], (z + 0.5 - cv[2]) * 0.7), (y + 0.5 - cv[1]) * 1.6);
                        if (y <= top && d < cv[3]) k = SmoothRules.AIR;
                    }
                    g.c[g.i(x, y, z)] = (byte) k;
                }
                if (top + 1 < g.y0 + g.hgt && g.c[g.i(x, top + 1, z)] == SmoothRules.AIR) {
                    int roll = r.nextInt(20);
                    if (roll < 3) g.c[g.i(x, top + 1, z)] = SmoothRules.COVER;
                    else if (roll == 3 && top + 4 < g.y0 + g.hgt) {   // a tree: trunk and a leaf on top
                        for (int y = top + 1; y <= top + 3; y++) g.c[g.i(x, y, z)] = SmoothRules.TREE;
                        g.c[g.i(x, top + 4, z)] = SmoothRules.LEAF;
                    } else if (roll == 4) g.c[g.i(x, top + 1, z)] = SmoothRules.OTHER;   // something built on the ground
                }
            }
            g.surfaces();
            compare(g, g.x0 + 2, g.z0 + 2, g.w - 4, g.y0 + 1, g.y0 + g.hgt - 2, t);
        }
    }

    /**
     * A seed's planned ground (UndergroundCheck): patches of 24 x 24 columns at its entrances and at random land points,
     * cells from the plan (the surface height, the soil depth, rock under it, planned cave air and water, water above
     * the ground where the terrain plan has it). Returns the patch report line; differences go to bad.
     */
    public static String seedPatches(List<String> bad, long seed, GeologyModel geo, UndergroundPlan plan,
                                     java.util.function.BiPredicate<Double, Double> surfaceWater, double[] area) {
        Tally t = new Tally();
        Random r = new Random(seed ^ 0x6C1A55L);
        List<double[]> at = new ArrayList<>();
        for (UndergroundPlan.Entrance e : plan.entrances) if (at.size() < 8) at.add(new double[]{e.x(), e.z()});
        while (at.size() < 16) at.add(new double[]{area[0] + r.nextDouble() * (area[1] - area[0]), area[0] + r.nextDouble() * (area[1] - area[0])});
        for (double[] p : at) {
            int cx = (int) Math.floor(p[0]), cz = (int) Math.floor(p[1]), w = 24;
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            int[][] top = new int[w][w];
            double[][] soil = new double[w][w];
            boolean[][] wet = new boolean[w][w];
            for (int dz = 0; dz < w; dz++) for (int dx = 0; dx < w; dx++) {
                double x = cx - w / 2 + dx + 0.5, z = cz - w / 2 + dz + 0.5;
                GeologyModel.Column c = geo.column(x, z);
                top[dx][dz] = (int) Math.floor(c.h);
                soil[dx][dz] = c.soil;
                wet[dx][dz] = surfaceWater.test(x, z);
                lo = Math.min(lo, top[dx][dz]);
                hi = Math.max(hi, top[dx][dz]);
            }
            Grid g = new Grid(cx - w / 2, Math.max(geo.minY, lo - 24), cz - w / 2, w, Math.min(hi + 6, lo + 120) - Math.max(geo.minY, lo - 24));
            for (int dz = 0; dz < w; dz++) for (int dx = 0; dx < w; dx++) {
                int x = g.x0 + dx, z = g.z0 + dz;
                for (int y = g.y0; y < g.y0 + g.hgt; y++) {
                    int k;
                    if (y > top[dx][dz]) k = wet[dx][dz] && y <= top[dx][dz] + 2 ? SmoothRules.FLUID : SmoothRules.AIR;
                    else if (plan.waterAt(x + 0.5, y + 0.5, z + 0.5)) k = SmoothRules.FLUID;
                    else if (plan.airAt(x + 0.5, y + 0.5, z + 0.5)) k = SmoothRules.AIR;
                    else k = y > top[dx][dz] - soil[dx][dz] ? SmoothRules.NATURAL : SmoothRules.ROCK;
                    g.c[g.i(x, y, z)] = (byte) k;
                }
            }
            g.surfaces();
            compare(g, g.x0 + 2, g.z0 + 2, w - 4, g.y0 + 1, g.y0 + g.hgt - 2, t);
        }
        for (String f : t.first) bad.add("ground class R1: " + f);
        if (t.diffs > 0) bad.add("ground class R1: " + t.diffs + " answers differ from the frozen rules");
        return "ground class R1 (" + at.size() + " patches): " + t.line();
    }

    public static void main(String[] args) {
        Tally grids = new Tally(), lands = new Tally();
        long t0 = System.nanoTime();
        randomGrids(grids);
        terrains(lands);
        System.out.println("Random grids: " + grids.line());
        System.out.println("Terrains: " + lands.line());
        for (String f : grids.first) System.out.println("  DIFF " + f);
        for (String f : lands.first) System.out.println("  DIFF " + f);
        long diffs = grids.diffs + lands.diffs;
        System.out.printf(Locale.ROOT, "Ground class check (R1): %s, %.1f s%n", diffs == 0 ? "0 differences, behaviour unchanged" : diffs + " DIFFERENCES", (System.nanoTime() - t0) / 1e9);
        if (diffs > 0) System.exit(1);
    }
}
