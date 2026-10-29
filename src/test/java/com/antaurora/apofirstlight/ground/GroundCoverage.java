package com.antaurora.apofirstlight.ground;

import com.antaurora.apofirstlight.terrainsmooth.SmoothRules;
import com.antaurora.apofirstlight.worldgen.underground.GeologyModel;
import com.antaurora.apofirstlight.worldgen.underground.UndergroundPlan;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.BiPredicate;

import static com.antaurora.apofirstlight.terrainsmooth.SmoothRules.*;

/**
 * Terrain rework R3 survey (docs/worldgen/terrain_rework_v1_design.md 14; user 2026-10-28: "先做 R3 的离线统计"): how
 * much of the natural ground a player can see is still drawn as block faces, and how many columns the shovel cannot
 * edit and why. Read only; no pass or fail.
 * <ul>
 *   <li>Every open face of a natural cell (loose ground or rock) is put where the game draws it: GroundClass and
 *   SmoothRules#geometry for the ground, RockRelief (#rockOpen) for the rock, else a plain cube face. Faces toward water
 *   are counted apart, and faces whose open side lies under a roof (a cave, an overhang) apart from those under the sky.</li>
 *   <li>Every column's shovel answer as Earthwork#why gives it (offline: no carvings, nothing edited, no chunk edges), and
 *   the free columns sharing a vertex with a fixed one (the shovel cannot move that vertex).</li>
 *   <li>Steps between neighbouring natural columns: one block high with bare rock on either side (a jump, no slope).</li>
 * </ul>
 * Two sources: each seed's planned ground (patches as GroundClassCheck#seedPatches, larger, run from GroundCoverageCheck)
 * and dumps of a test world (tools/terrain-smooth/dump_world.py), which carry trees, plants, roads and real rivers:
 * <pre>
 *   gradlew groundCoverage -PprobeIn=a.txt;b.txt
 * </pre>
 */
public final class GroundCoverage {
    private GroundCoverage() {}

    static final String[] CATS = {
            "H smooth top (mesh)", "H side band (mesh, vertical)", "H flat top (cube face, level ground)",
            "H cube side (flat top beside a drop)", "V rock worn (mesh)", "V rock not worn (beside a plant or leaves, at the water's surface)",
            "V soil wall (cube)", "C natural top (cube: cave floor, ground under an overhang)", "C natural side (cube)",
            "C natural bottom (cube)"};
    /** the categories that are block faces a player sees on natural ground (R3's target 0); the flat top is level anyway */
    static final int[] CUBES = {3, 5, 6, 7, 8, 9};
    static final String[] LOCS = {"sky", "under a roof", "water, open sky", "water under a roof"};
    static final int R = 12;   // SmoothRules.REACH: cells counted this far in from a patch's edge

    public static final class Tally {
        final double[][] area = new double[CATS.length][LOCS.length];
        final Map<String, Integer> why = new TreeMap<>();
        final Map<String, Integer> names = new TreeMap<>();
        final Map<String, Integer> other = new TreeMap<>();
        long columns, surfaces, free, freeNearFixed, rockTops, rockStep1, rockStep2, soilStep1, patches;

        public void add(Tally t) {
            for (int i = 0; i < CATS.length; i++) for (int j = 0; j < LOCS.length; j++) area[i][j] += t.area[i][j];
            t.why.forEach((k, v) -> why.merge(k, v, Integer::sum));
            t.names.forEach((k, v) -> names.merge(k, v, Integer::sum));
            t.other.forEach((k, v) -> other.merge(k, v, Integer::sum));
            columns += t.columns; surfaces += t.surfaces; free += t.free; freeNearFixed += t.freeNearFixed;
            rockTops += t.rockTops; rockStep1 += t.rockStep1; rockStep2 += t.rockStep2; soilStep1 += t.soilStep1; patches += t.patches;
        }

        double cubes(int loc) {
            double s = 0;
            for (int c : CUBES) s += area[c][loc];
            return s;
        }

        public List<String> report(String title) {
            List<String> out = new ArrayList<>();
            double k = 1000.0 / Math.max(1, columns);
            out.add(String.format(Locale.ROOT, "%s: %d patches, %d columns counted, %d with a natural surface (%d bare rock)",
                    title, patches, columns, surfaces, rockTops));
            out.add("  visible natural faces, m2 per 1000 columns (sky / under a roof / toward water under the sky / toward water under a roof):");
            for (int i = 0; i < CATS.length; i++)
                out.add(String.format(Locale.ROOT, "    %-62s %8.1f %8.1f %8.1f %8.1f", CATS[i], area[i][0] * k, area[i][1] * k, area[i][2] * k, area[i][3] * k));
            out.add(String.format(Locale.ROOT, "    %-62s %8.1f %8.1f %8.1f %8.1f", "= block faces (R3 target 0)", cubes(0) * k, cubes(1) * k, cubes(2) * k, cubes(3) * k));
            if (!names.isEmpty()) {
                out.add("  block faces by block (m2 per 1000 columns, top 16):");
                names.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(16)
                        .forEach(e -> out.add(String.format(Locale.ROOT, "    %-70s %8.1f", e.getKey(), e.getValue() * k)));
            }
            if (!other.isEmpty()) {
                out.add("  open faces of other blocks under the sky or in caves, not natural ground here (m2 per 1000 columns, top 16):");
                other.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(16)
                        .forEach(e -> out.add(String.format(Locale.ROOT, "    %-70s %8.1f", e.getKey(), e.getValue() * k)));
            }
            long fixed = why.values().stream().mapToLong(Integer::longValue).sum();
            out.add(String.format(Locale.ROOT, "  shovel: %d free (%.1f %%), %d fixed (%.1f %%); %d free columns share a vertex with a fixed one (%.1f %% of free)",
                    free, 100.0 * free / Math.max(1, columns), fixed, 100.0 * fixed / Math.max(1, columns), freeNearFixed, 100.0 * freeNearFixed / Math.max(1, free)));
            why.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue())
                    .forEach(e -> out.add(String.format(Locale.ROOT, "    %6.2f %%  %s", 100.0 * e.getValue() / Math.max(1, columns), e.getKey())));
            out.add(String.format(Locale.ROOT, "  steps between natural columns, per 1000 columns: one high with bare rock %.1f, two high with bare rock %.1f, one high soil only (sloped) %.1f",
                    rockStep1 * k, rockStep2 * k, soilStep1 * k));
            return out;
        }
    }

    /** Counts columns x in [xa, xb), z in [za, zb) of g (every cell they read lies inside g); names: per cell index or null. */
    static void count(GroundClassCheck.Grid g, int xa, int za, int xb, int zb, String[] names, Tally t) {
        t.patches++;
        int[] sky = new int[g.w * g.w];
        for (int z = g.z0; z < g.z0 + g.w; z++) for (int x = g.x0; x < g.x0 + g.w; x++) {
            int y = g.y0 + g.hgt - 1;
            while (y >= g.y0) {
                int k = g.cell(x, y, z);
                if (!skyward(k) && k != FLUID) break;
                y--;
            }
            sky[(z - g.z0) * g.w + (x - g.x0)] = y;
        }
        for (int z = za; z < zb; z++) for (int x = xa; x < xb; x++) {
            t.columns++;
            for (int y = g.y0 + 1; y < g.y0 + g.hgt - 1; y++) {
                int k = g.cell(x, y, z);
                boolean nat = natural(k), rock = k == ROCK;
                if (!nat && !rock) {
                    if (k == OTHER && names != null) for (int d = 0; d < 6; d++) {
                        int n = neighbour(g, x, y, z, d);
                        if (open(n)) t.other.merge(base(names[g.i(x, y, z)]), 1, Integer::sum);
                    }
                    continue;
                }
                int cls = GroundClass.of(g, x, y, z);
                long geo = cls == GroundClass.H ? SmoothRules.geometry(g, MODE_B, x, y, z) : -1;
                if (geo >= 0) t.area[1][0] += bands(geo);
                for (int d = 0; d < 6; d++) {
                    int nx = x + DX[d], ny = y + DY[d], nz = z + DZ[d], n = g.cell(nx, ny, nz);
                    boolean water = n == FLUID;
                    if (!water && !open(n) && n != LEAF) continue;
                    if (water && k == WET && d == 1) water = false;   // a wet bed's top under its water: the ground's own
                    boolean open = ny > sky[(nz - g.z0) * g.w + (nx - g.x0)];
                    int loc = water ? (open ? 2 : 3) : open ? 0 : 1;
                    int cat;
                    if (rock) cat = (rockOpen(g, x, y, z) & 1 << DIR[d]) != 0 ? 4 : 5;
                    else if (cls == GroundClass.H) {
                        if (d == 1) cat = geo >= 0 ? 0 : 2;
                        else if (geo >= 0) continue;   // its side band (counted above) draws what shows
                        else cat = 3;
                    } else if (cls == GroundClass.V) cat = 6;
                    else if (d == 1) cat = 7;
                    else if (d == 0) cat = 9;
                    else {
                        // a buried cell with an open side is a soil wall unless the neighbour's slope covers that side
                        // (GroundClass#covered): a C one never shows it
                        if (!openOver(k, g.cell(x, y + 1, z))) continue;
                        cat = 8;
                    }
                    t.area[cat][loc] += 1;
                    if (names != null && isCube(cat)) t.names.merge(CATS[cat].split(" \\(")[0] + ": " + base(names[g.i(x, y, z)]), 1, Integer::sum);
                }
            }
            shovel(g, x, z, t);
        }
        // free columns beside a fixed one, and steps
        for (int z = za; z < zb; z++) for (int x = xa; x < xb; x++) {
            if (why(g, x, z) != null) continue;
            boolean near = false;
            for (int j = -1; j <= 1 && !near; j++) for (int i = -1; i <= 1 && !near; i++) if ((i | j) != 0 && why(g, x + i, z + j) != null) near = true;
            if (near) t.freeNearFixed++;
        }
        for (int z = za; z < zb; z++) for (int x = xa; x < xb; x++) {
            int s = g.surface(x, z);
            if (s == NO_SURFACE) continue;
            for (int e = 0; e < 2; e++) {
                int ox = x + (e == 0 ? 1 : 0), oz = z + (e == 1 ? 1 : 0);
                if (ox >= xb || oz >= zb) continue;
                int so = g.surface(ox, oz);
                if (so == NO_SURFACE) continue;
                boolean anyRock = g.cell(x, s, z) == ROCK || g.cell(ox, so, oz) == ROCK;
                int dh = Math.abs(s - so);
                if (dh == 1 && anyRock) t.rockStep1++;
                else if (dh == 2 && anyRock) t.rockStep2++;
                else if (dh == 1) t.soilStep1++;
            }
        }
    }

    static boolean isCube(int cat) {
        for (int c : CUBES) if (c == cat) return true;
        return false;
    }

    static final int[] DX = {0, 0, 0, 0, -1, 1}, DY = {-1, 1, 0, 0, 0, 0}, DZ = {0, 0, -1, 1, 0, 0};
    /** Direction ordinals of the six steps above: down, up, north, south, west, east (the same order) */
    static final int[] DIR = {0, 1, 2, 3, 4, 5};

    /** The sides RockRelief wears on a rock cell (RockRelief#openSides: air, and deep water). */
    static int rockOpen(GroundClassCheck.Grid g, int x, int y, int z) {
        int air = 0, water = 0;
        for (int d = 0; d < 6; d++) {
            int n = neighbour(g, x, y, z, d);
            if (n == AIR) air |= 1 << DIR[d];
            else if (n == FLUID) {
                int over = g.cell(x + DX[d], y + DY[d] + 1, z + DZ[d]);
                if (d <= 1 || over == FLUID || over == NATURAL || over == WET || over == ROCK || over == OTHER || over == TREE) water |= 1 << DIR[d];   // a wall: deep water only
            }
        }
        return com.antaurora.apofirstlight.earthwork.RockRelief.open(air, water);
    }

    static int neighbour(GroundClassCheck.Grid g, int x, int y, int z, int d) { return g.cell(x + DX[d], y + DY[d], z + DZ[d]); }

    static String base(String n) {
        if (n == null) return "?";
        return n.contains("[") ? n.substring(0, n.indexOf('[')) : n;
    }

    /** Area of a smoothed top's four side bands (m2): from the neighbour's surface up to the top's corners. */
    static double bands(long g) {
        int[][] side = {{1, 0}, {2, 3}, {0, 2}, {3, 1}};   // N, S, W, E: corners at the band's ends (SmoothRules#SIDE)
        double a = 0;
        for (int s = 0; s < 4; s++) {
            int ca = (int) (g >>> (4 * side[s][0]) & 15), cb = (int) (g >>> (4 * side[s][1]) & 15);
            int qa = (int) (g >>> (16 + 8 * s) & 15), qb = (int) (g >>> (20 + 8 * s) & 15);
            a += (Math.max(0, ca - qa) + Math.max(0, cb - qb)) / 2.0 / 4.0;
        }
        return a;
    }

    /** Earthwork#why offline: null when the column can be edited, else why not (coordinates left out). */
    static String why(GroundClassCheck.Grid g, int x, int z) {
        int s = g.surface(x, z);
        if (s == NO_SURFACE) {
            for (int y = g.y0 + g.hgt - 1; y >= g.y0; y--) {
                int k = g.cell(x, y, z);
                if (skyward(k)) continue;
                return k == FLUID ? "no natural surface: water on top" : k == UNKNOWN ? "outside" : "no natural surface: built over";
            }
            return "no natural surface: nothing";
        }
        int k = g.cell(x, s, z);
        if (k == ROCK) {
            for (int y = s + 1; y <= s + 2; y++) if (!SmoothRules.open(g.cell(x, y, z))) return onTop(g.cell(x, y, z));
        } else {
            int u1 = g.cell(x, s - 1, z);
            if (!natural(u1) && u1 != ROCK) return "under its top is not ground (an overhang, a cave roof, built)";
            if (natural(u1)) {
                int u2 = g.cell(x, s - 2, z);
                if (u2 == AIR || u2 == COVER || u2 == LEAF || u2 == FLUID) return "thin soil over a hollow (two below is open)";
            }
            // R3a: water over a wet bed is open to the shovel (Earthwork#why)
            for (int y = s + 1; y <= s + 2; y++) if (!SmoothRules.open(g.cell(x, y, z)) && g.cell(x, y, z) != FLUID) return onTop(g.cell(x, y, z));
        }
        // R3b: a loose ground's cliff rim is free; R3a: a wet bed and the shore beside water too
        String w = SmoothRules.notFree(g, MODE_B, x, s, z, true, k != ROCK, k != ROCK);
        return w == null ? null : w.replaceAll("neighbour -?\\d+ -?\\d+", "a neighbour");
    }

    static String onTop(int k) {
        return k == TREE ? "a tree trunk on top" : k == LEAF ? "leaves on top" : k == FLUID ? "water on top" : "a block on top (built, not a plant)";
    }

    static void shovel(GroundClassCheck.Grid g, int x, int z, Tally t) {
        int s = g.surface(x, z);
        if (s != NO_SURFACE) {
            t.surfaces++;
            if (g.cell(x, s, z) == ROCK) t.rockTops++;
        }
        String w = why(g, x, z);
        if (w == null) t.free++;
        else t.why.merge(w, 1, Integer::sum);
    }

    /**
     * Patches of a seed's planned ground (GroundClassCheck#seedPatches, w = 56): at the first 8 cave entrances, at 8 shores and at
     * random land points; wet columns get a wet top under two blocks of water. Ground under the sky is the plan's surface
     * height, loose ground its soil depth over rock; caves from the underground plan. No roads, trees or plants.
     */
    public static void seedPatches(long seed, GeologyModel geo, UndergroundPlan plan, BiPredicate<Double, Double> surfaceWater,
                                   BiPredicate<Double, Double> land, double[] area, int random, Tally t) {
        Random r = new Random(seed ^ 0x6C1A56L);
        List<double[]> at = new ArrayList<>();
        for (UndergroundPlan.Entrance e : plan.entrances) if (at.size() < 8) at.add(new double[]{e.x(), e.z()});
        int tries = 0;
        while (at.size() < 8 + random && tries++ < 10_000) {
            double x = area[0] + r.nextDouble() * (area[1] - area[0]), z = area[0] + r.nextDouble() * (area[1] - area[0]);
            if (land.test(x, z) && !surfaceWater.test(x, z)) at.add(new double[]{x, z});
        }
        // shores: surface water with dry land 16 m away (rivers, lakes)
        int shores = 0;
        tries = 0;
        while (shores < 8 && tries++ < 200_000) {
            double x = area[0] + r.nextDouble() * (area[1] - area[0]), z = area[0] + r.nextDouble() * (area[1] - area[0]);
            if (!land.test(x, z) || !surfaceWater.test(x, z)) continue;
            if (!surfaceWater.test(x + 16, z) || !surfaceWater.test(x - 16, z) || !surfaceWater.test(x, z + 16) || !surfaceWater.test(x, z - 16)) {
                at.add(new double[]{x, z});
                shores++;
            }
        }
        int w = 56;
        for (double[] p : at) {
            int cx = (int) Math.floor(p[0]), cz = (int) Math.floor(p[1]);
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
            int y0 = Math.max(geo.minY, lo - 24);
            GroundClassCheck.Grid g = new GroundClassCheck.Grid(cx - w / 2, y0, cz - w / 2, w, Math.min(hi + 6, lo + 160) - y0);
            for (int dz = 0; dz < w; dz++) for (int dx = 0; dx < w; dx++) {
                int x = g.x0 + dx, z = g.z0 + dz;
                for (int y = g.y0; y < g.y0 + g.hgt; y++) {
                    int k;
                    if (y > top[dx][dz]) k = wet[dx][dz] && y <= top[dx][dz] + 2 ? FLUID : AIR;
                    else if (plan.waterAt(x + 0.5, y + 0.5, z + 0.5)) k = FLUID;
                    else if (plan.airAt(x + 0.5, y + 0.5, z + 0.5)) k = AIR;
                    else if (y > top[dx][dz] - soil[dx][dz]) k = wet[dx][dz] && y == top[dx][dz] ? WET : NATURAL;
                    else k = ROCK;
                    g.c[g.i(x, y, z)] = (byte) k;
                }
            }
            g.surfaces();
            count(g, g.x0 + R, g.z0 + R, g.x0 + w - R, g.z0 + w - R, null, t);
        }
    }

    /** WorldDumpProbe#code, with every NaturalRockBlock and OreRockBlock (AflBlocks) as rock, as Earthwork#rock has them. */
    static int code(String n) {
        int k = WorldDumpProbe.code(n);
        if (k != OTHER) return k;
        String p = base(n).substring(base(n).indexOf(':') + 1);
        return switch (p) {
            case "schist", "marl", "rock_salt", "coal_seam", "banded_iron_formation", "quartz_vein", "lead_zinc_dolomite",
                 "copper_breccia", "spodumene_ore" -> ROCK;
            default -> k;
        };
    }

    /** A test world's dump (tools/terrain-smooth/dump_world.py), counted R in from its edges. */
    static void dump(Path file, Tally t) throws Exception {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        String[] ar = lines.get(1).split(" ");
        int x0 = Integer.parseInt(ar[1]), z0 = Integer.parseInt(ar[2]), w = Integer.parseInt(ar[3]), ylo = Integer.parseInt(ar[4]), yhi = Integer.parseInt(ar[5]);
        int nn = Integer.parseInt(lines.get(2).split(" ")[1]);
        String[] pal = new String[nn];
        for (int i = 0; i < nn; i++) {
            String l = lines.get(3 + i);
            pal[i] = l.substring(l.indexOf(' ') + 1);
        }
        int hgt = yhi - ylo;
        GroundClassCheck.Grid g = new GroundClassCheck.Grid(x0, ylo, z0, w, hgt);
        String[] names = new String[w * w * hgt];
        for (int z = 0; z < w; z++) for (int x = 0; x < w; x++) {
            String[] v = lines.get(3 + nn + z * w + x).trim().split(" ");
            for (int y = 0; y < hgt; y++) {
                String n = pal[Integer.parseInt(v[y])];
                int i = g.i(x0 + x, ylo + y, z0 + z);
                g.c[i] = (byte) code(n);
                names[i] = n;
                if (n.startsWith("apocalypse_firstlight:graded_turf")) {
                    float b = n.contains("shift=true") ? 0.5F : 0;
                    g.turf[i] = new float[]{ylo + y + b + WorldDumpProbe.prop(n, "nw") / 8F, ylo + y + b + WorldDumpProbe.prop(n, "ne") / 8F,
                            ylo + y + b + WorldDumpProbe.prop(n, "sw") / 8F, ylo + y + b + WorldDumpProbe.prop(n, "se") / 8F};
                }
            }
        }
        g.surfaces();
        count(g, x0 + R, z0 + R, x0 + w - R, z0 + w - R, names, t);
    }

    public static void main(String[] args) throws Exception {
        Tally all = new Tally();
        for (String a : args[0].split(";")) {
            Tally t = new Tally();
            dump(Path.of(a), t);
            for (String l : t.report(Path.of(a).getFileName().toString())) System.out.println(l);
            all.add(t);
        }
        if (args[0].contains(";")) for (String l : all.report("all dumps")) System.out.println(l);
    }
}
