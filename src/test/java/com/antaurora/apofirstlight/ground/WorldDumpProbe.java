package com.antaurora.apofirstlight.ground;

import com.antaurora.apofirstlight.terrainsmooth.SmoothRules;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Diagnostics (terrain rework R2b follow-up, docs/worldgen/terrain_rework_v1_design.md 13.10): a test world's columns
 * dumped offline (tools/terrain-smooth/dump_world.py: block names per column round the player) run through the game's
 * Terrain Smooth rules, cell codes mapped from the names as TerrainSmooth#cell does. Prints what each kind of top is
 * and why natural tops stay cubes; writes a top-down map (4 px a column): green smooth, dark green flat natural, red a
 * natural top kept a cube beside a step, yellow road turf, grey pavement, brown rock, blue anything built, cyan water.
 * <pre>
 *   WorldDumpProbe dump.txt out.png
 * </pre>
 */
public final class WorldDumpProbe {
    private WorldDumpProbe() {}

    static int code(String n) {
        String b = n.contains("[") ? n.substring(0, n.indexOf('[')) : n;
        boolean wet = n.contains("waterlogged=true");
        if (b.equals("minecraft:air") || b.equals("minecraft:cave_air") || b.equals("?")) return SmoothRules.AIR;
        if (b.equals("minecraft:water")) return SmoothRules.FLUID;
        if (n.contains("fx=") || b.endsWith("_cattail") || b.contains("loose_stone") || b.equals("minecraft:snow")) return SmoothRules.COVER;
        if (b.endsWith("_canopy") || b.endsWith("_foliage") || b.endsWith("leaves")) return SmoothRules.LEAF;
        if (b.endsWith("tree_wood") || b.endsWith("tree_root") || b.endsWith("_log")) return SmoothRules.TREE;
        String p = b.substring(b.indexOf(':') + 1);
        if (p.equals("topsoil") || p.equals("gravel") || p.equals("sand") || p.equals("red_sand") || p.equals("mud") || p.equals("clay")
                || p.equals("snow_block") || p.equals("dirt") || p.equals("grass_block") || p.equals("coarse_dirt") || p.equals("podzol"))
            return wet ? SmoothRules.WET : SmoothRules.NATURAL;
        if (p.equals("limestone") || p.equals("shale") || p.equals("granite") || p.equals("sandstone") || p.equals("red_sandstone")
                || p.equals("stone") || p.equals("basalt") || p.equals("slate") || p.equals("gneiss") || p.equals("dolomite"))
            return SmoothRules.ROCK;
        return SmoothRules.OTHER;
    }

    public static void main(String[] args) throws Exception {
        List<String> lines = Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8);
        String[] pl = lines.get(0).split(" ");
        int px = Integer.parseInt(pl[1]), pz = Integer.parseInt(pl[3]);
        double yaw = Double.parseDouble(pl[5]);
        String[] ar = lines.get(1).split(" ");
        int x0 = Integer.parseInt(ar[1]), z0 = Integer.parseInt(ar[2]), w = Integer.parseInt(ar[3]), ylo = Integer.parseInt(ar[4]), yhi = Integer.parseInt(ar[5]);
        int nn = Integer.parseInt(lines.get(2).split(" ")[1]);
        String[] names = new String[nn];
        for (int i = 0; i < nn; i++) {
            String l = lines.get(3 + i);
            names[i] = l.substring(l.indexOf(' ') + 1);
        }
        int hgt = yhi - ylo;
        GroundClassCheck.Grid c = new GroundClassCheck.Grid(x0, ylo, z0, w, hgt);
        String[][] top = new String[w][w];
        for (int z = 0; z < w; z++)
            for (int x = 0; x < w; x++) {
                String[] v = lines.get(3 + nn + z * w + x).trim().split(" ");
                for (int y = 0; y < hgt; y++) {
                    String n = names[Integer.parseInt(v[y])];
                    int k = code(n);
                    c.c[c.i(x0 + x, ylo + y, z0 + z)] = (byte) k;
                    if (n.startsWith("apocalypse_firstlight:graded_turf")) {
                        float base = n.contains("shift=true") ? 0.5F : 0;
                        c.turf[c.i(x0 + x, ylo + y, z0 + z)] = new float[]{ylo + y + base + prop(n, "nw") / 8F, ylo + y + base + prop(n, "ne") / 8F,
                                ylo + y + base + prop(n, "sw") / 8F, ylo + y + base + prop(n, "se") / 8F};
                    }
                    if (k != SmoothRules.AIR && k != SmoothRules.COVER && k != SmoothRules.LEAF) top[x][z] = n;
                }
            }
        c.surfaces();
        int[] count = new int[8];
        String[] kinds = {"smooth top", "flat natural (cube, all level)", "natural kept a cube beside a step", "road turf", "pavement", "rock", "built / other", "water / none"};
        int[] colour = {0x4caf50, 0x1b5e20, 0xe53935, 0xfdd835, 0x9e9e9e, 0x795548, 0x1e88e5, 0x00bcd4};
        int S = 4;
        BufferedImage img = new BufferedImage(w * S, w * S, BufferedImage.TYPE_INT_RGB);
        int[][] reasons = new int[8][];
        java.util.Map<String, Integer> why = new java.util.TreeMap<>();
        int[][] kindAt = new int[w][w];
        int tiltedLevel = 0;
        int[][] hAt = new int[w][w];
        for (int z = 1; z < w - 1; z++)
            for (int x = 1; x < w - 1; x++) {
                int wx = x0 + x, wz = z0 + z, s = c.surface(wx, wz), k;
                String t = top[x][z] == null ? "" : top[x][z];
                if (t.contains("graded_turf")) k = 3;
                else if (t.contains("road_surface")) k = 4;
                else if (s == SmoothRules.NO_SURFACE) k = t.contains("water") ? 7 : 6;
                else if (c.cell(wx, s, wz) == SmoothRules.ROCK) k = 5;
                else {
                    long g = SmoothRules.geometry(c, SmoothRules.MODE_B, wx, s, wz);
                    if (g >= 0) {
                        k = 0;
                        boolean level = true;
                        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) if (c.surface(wx + dx, wz + dz) != s) level = false;
                        if (level) tiltedLevel++;   // R2f: a block of a terrace that now follows the staircase
                    }
                    else {
                        boolean level = true;
                        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) if (c.surface(wx + dx, wz + dz) != s) level = false;
                        k = level ? 1 : 2;
                        if (!level) {
                            String r = SmoothRules.notFree(c, SmoothRules.MODE_B, wx, s, wz);
                            r = r == null ? "(corners free but drawn flat)" : r.replaceAll("-?\\d+ -?\\d+", "#");
                            why.merge(r, 1, Integer::sum);
                        }
                    }
                }
                count[k]++;
                kindAt[x][z] = k;
                hAt[x][z] = s == SmoothRules.NO_SURFACE ? 0 : s;
            }
        for (int z = 0; z < w; z++)
            for (int x = 0; x < w; x++) {
                int rgb = colour[kindAt[x][z]];
                // contour every 2 blocks
                boolean edge = x > 0 && z > 0 && (hAt[x][z] / 2 != hAt[x - 1][z] / 2 || hAt[x][z] / 2 != hAt[x][z - 1] / 2);
                if (edge) rgb = (rgb >> 1) & 0x7f7f7f;
                for (int j = 0; j < S; j++) for (int i = 0; i < S; i++) img.setRGB(x * S + i, z * S + j, rgb);
            }
        // the player and the view direction
        double dx = -Math.sin(Math.toRadians(yaw)), dz = Math.cos(Math.toRadians(yaw));
        for (int t = 0; t < 70; t++) {
            int ix = (int) ((px - x0 + dx * t) * S), iz = (int) ((pz - z0 + dz * t) * S);
            if (ix >= 0 && iz >= 0 && ix < w * S && iz < w * S) img.setRGB(ix, iz, 0xffffff);
        }
        for (int j = -3; j <= 3; j++) for (int i = -3; i <= 3; i++) img.setRGB((px - x0) * S + i, (pz - z0) * S + j, 0xffffff);
        ImageIO.write(img, "png", new File(args[1]));
        for (int k = 0; k < 8; k++) System.out.printf(Locale.ROOT, "  %-40s %6d%n", kinds[k], count[k]);
        System.out.printf(Locale.ROOT, "  of the smooth tops, inside a level 3 x 3 (tilted by R2f's staircases) %6d%n", tiltedLevel);
        System.out.println("  why natural tops beside a step stay cubes:");
        why.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(12).forEach(e -> System.out.printf(Locale.ROOT, "    %5d  %s%n", e.getValue(), e.getKey()));
    }

    private static int prop(String n, String key) {
        int i = n.indexOf(key + "=");
        int j = i + key.length() + 1, e = j;
        while (e < n.length() && Character.isDigit(n.charAt(e))) e++;
        return Integer.parseInt(n.substring(j, e));
    }
}
