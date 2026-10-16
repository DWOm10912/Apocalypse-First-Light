package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.network.route.RoadNetworkPlan;
import com.antaurora.apofirstlight.worldgen.network.route.RouteTerrain;
import com.antaurora.apofirstlight.worldgen.federal.FederalMap;
import com.antaurora.apofirstlight.worldgen.terrain.v2.FederalTerrainPlan;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.TreeMap;
import java.util.zip.InflaterInputStream;

/**
 * C3.3a group B (docs/worldgen/federal_highways_c3.md 11.11): the turf's outer edge against the natural ground the noise
 * made. Reads an area exported by tools/terrain-smooth/export-states.mjs from a test save and compares
 * <ul>
 *   <li>the natural block tops with RoadWorks.naturalTop (the prediction the turf edge is cut to);</li>
 *   <li>every shared edge between a graded_turf column and a natural grass column: turf edge height minus the natural
 *   surface there (the smoothed corner, the mean of the four column tops), as built in the save and as the current
 *   RoadWorks.column would build it now.</li>
 * </ul>
 * Usage: TurfEdgeCheck seed plan-cache states.bin
 */
public final class TurfEdgeCheck {
    public static void main(String[] a) throws Exception {
        long seed = Long.parseLong(a[0]);
        TerrainPlanSurface plan;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(new FileInputStream(a[1])), 1 << 16))) {
            plan = TerrainPlanSurface.read(in, seed, FederalTerrainPlan.VERSION, FederalTerrainPlan.GRID_N, FederalTerrainPlan.GRID_ORIGIN);
        }
        if (plan == null) throw new IllegalStateException("stale or foreign plan cache " + a[1]);
        FederalMap map = FederalMap.forSeed(seed);
        RouteTerrain terrain = new RouteTerrain(plan, map);
        String json = RoadNetworkBuild.build(RoadNetworkPlan.build(map, terrain).toJson()).json();
        RoadWorks.Water water = (x, z) -> {
            int w = terrain.water(x, z);
            return w == RouteTerrain.RIVER || w == RouteTerrain.LAKE || w == RouteTerrain.SEA;
        };
        RoadWorks works = new RoadWorks(RoadNetworkJson.designs(json), water);
        RoadWorks.Ground ground = RoadWorks.Ground.of(plan);

        ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(new File(a[2]).toPath()));
        int minX = b.getInt(), minZ = b.getInt(), w = b.getInt(), d = b.getInt(), minY = b.getInt(), h = b.getInt(), np = b.getInt();
        String[] pal = new String[np];
        for (int i = 0; i < np; i++) {
            byte[] s = new byte[b.getShort() & 0xFFFF];
            b.get(s);
            pal[i] = new String(s, StandardCharsets.UTF_8);
        }
        char[] ids = new char[w * d * h];
        for (int i = 0; i < ids.length; i++) ids[i] = b.getChar();
        // per column: top (y of the surface block, MIN when unknown), kind 0 natural grass/dirt, 1 turf, 2 other
        int[] top = new int[w * d], kind = new int[w * d];
        float[][] turf = new float[w * d][];
        for (int z = 0; z < d; z++) for (int x = 0; x < w; x++) {
            int c = z * w + x;
            top[c] = Integer.MIN_VALUE;
            kind[c] = 2;
            for (int y = h - 1; y >= 0; y--) {
                int id = ids[(z * w + x) * h + y];
                if (id == 0xFFFF) break;
                String n = pal[id];
                if (sky(n)) continue;
                top[c] = minY + y;
                if (n.startsWith("apocalypse_firstlight:graded_turf")) {
                    kind[c] = 1;
                    turf[c] = turfCorners(n, minY + y);
                } else if (n.startsWith("minecraft:grass_block") || n.equals("minecraft:dirt") || n.startsWith("minecraft:podzol")
                        || n.equals("minecraft:coarse_dirt")) kind[c] = 0;
                break;
            }
        }
        // 1. natural tops against the prediction (columns at least 3 blocks from any non-natural column)
        TreeMap<Integer, Integer> dh = new TreeMap<>(), fracOk = new TreeMap<>(), fracUp = new TreeMap<>();
        int n0 = 0, hit = 0;
        for (int z = 3; z < d - 3; z++) for (int x = 3; x < w - 3; x++) {
            boolean clean = true;
            for (int j = -3; j <= 3 && clean; j++) for (int i = -3; i <= 3 && clean; i++) clean = kind[(z + j) * w + x + i] == 0;
            if (!clean) continue;
            int pred = works.naturalTop(minX + x, minZ + z, ground);
            int e = top[z * w + x] + 1 - pred;
            double gb = works.densityHeight(minX + x, minZ + z, ground), fr = gb - Math.floor(gb);
            // where the actual top is one higher, how far below the next whole block the interpolated height was
            int bin = (int) Math.floor(fr * 200);
            (e == 0 ? fracOk : fracUp).merge(bin, 1, Integer::sum);
            dh.merge(e, 1, Integer::sum);
            n0++;
            if (e == 0) hit++;
        }
        System.out.printf(Locale.ROOT, "natural tops: %d columns, prediction exact %.1f %%, actual - predicted %s%n", n0, 100.0 * hit / Math.max(1, n0), dh);
        System.out.println("  frac bins (0.02) exact: " + fracOk.tailMap(180) + " / one higher: " + fracUp);
        // 2. turf / natural shared edges
        edges("save", w, d, minX, minZ, top, kind, turf, null, null);
        float[][] now = new float[w * d][];
        for (int z = 0; z < d; z++) for (int x = 0; x < w; x++) {
            if (kind[z * w + x] != 1) continue;
            RoadWorks.Column c = works.column(minX + x, minZ + z, ground);
            if (c.kind() != RoadWorks.Kind.TURF) continue;
            int data = c.data(), base = c.y() * 8 + ((data >> 16 & 1) != 0 ? 4 : 0);
            now[z * w + x] = new float[]{(base + (data & 15)) / 8F, (base + (data >> 4 & 15)) / 8F, (base + (data >> 8 & 15)) / 8F, (base + (data >> 12 & 15)) / 8F};
        }
        edges("now ", w, d, minX, minZ, top, kind, now, works, ground);
    }

    private static boolean sky(String n) {
        if (n.equals("minecraft:air") || n.equals("minecraft:cave_air")) return true;
        for (String p : new String[]{"minecraft:grass", "minecraft:tall_grass", "minecraft:fern", "minecraft:large_fern", "minecraft:dandelion",
                "minecraft:poppy", "minecraft:azure_bluet", "minecraft:oxeye_daisy", "minecraft:cornflower", "minecraft:allium",
                "minecraft:blue_orchid", "minecraft:red_tulip", "minecraft:orange_tulip", "minecraft:white_tulip", "minecraft:pink_tulip",
                "minecraft:lily_of_the_valley", "minecraft:sweet_berry_bush", "minecraft:dead_bush", "minecraft:snow[", "minecraft:sunflower",
                "minecraft:lilac", "minecraft:rose_bush", "minecraft:peony"})
            if (n.equals(p) || n.startsWith(p + "[") || p.endsWith("[") && n.startsWith(p)) return true;
        return n.contains("leaves") || n.contains("_log") || n.contains("sapling");
    }

    /** Absolute corners nw, ne, sw, se of a graded_turf state at y. */
    private static float[] turfCorners(String n, int y) {
        int[] c = new int[4];
        boolean shift = false;
        for (String kv : n.substring(n.indexOf('[') + 1, n.length() - 1).split(",")) {
            String[] p = kv.split("=");
            switch (p[0]) {
                case "nw" -> c[0] = Integer.parseInt(p[1]);
                case "ne" -> c[1] = Integer.parseInt(p[1]);
                case "sw" -> c[2] = Integer.parseInt(p[1]);
                case "se" -> c[3] = Integer.parseInt(p[1]);
                case "shift" -> shift = Boolean.parseBoolean(p[1]);
                default -> { }
            }
        }
        float base = y + (shift ? 0.5F : 0);
        return new float[]{base + c[0] / 8F, base + c[1] / 8F, base + c[2] / 8F, base + c[3] / 8F};
    }

    /**
     * Each turf column side facing a natural column: the turf's two corner heights there minus the natural surface at
     * those corners (SmoothRules mode B: the mean of the four column tops; the corner's turf column counted with the
     * natural top it shares the edge with, as a stand-in).
     */
    static void edges(String label, int w, int d, int minX, int minZ, int[] top, int[] kind, float[][] turf, Object u1, Object u2) {
        int[][] dirs = {{0, -1, 0, 1}, {0, 1, 2, 3}, {-1, 0, 0, 2}, {1, 0, 1, 3}};   // N, S, W, E: step, the side's corners
        TreeMap<Integer, Integer> dist = new TreeMap<>();
        int n = 0, samples = 0;
        double sum = 0, abs = 0;
        for (int z = 1; z < d - 1; z++) for (int x = 1; x < w - 1; x++) {
            int c = z * w + x;
            if (kind[c] != 1 || turf[c] == null) continue;
            for (int[] dd : dirs) {
                int o = (z + dd[1]) * w + x + dd[0];
                if (kind[o] != 0) continue;
                for (int k = 2; k <= 3; k++) {
                    int corner = dd[k];
                    int cxp = x + (corner & 1), czp = z + (corner >> 1);   // corner point in local block coords
                    // the natural columns around the point
                    int s = 0, m = 0;
                    for (int j = -1; j <= 0; j++) for (int i = -1; i <= 0; i++) {
                        int q = (czp + j) * w + cxp + i;
                        if (q < 0 || q >= w * d || kind[q] != 0) continue;
                        s += top[q] + 1;
                        m++;
                    }
                    if (m == 0) continue;
                    double e = turf[c][corner] - (double) s / m;
                    if (u1 != null && Math.abs(e) > 0.2 && samples < 14) {
                        samples++;
                        RoadWorks wk = (RoadWorks) u1;
                        RoadWorks.Ground gr = (RoadWorks.Ground) u2;
                        StringBuilder sb = new StringBuilder();
                        for (int j = -1; j <= 0; j++) for (int i = -1; i <= 0; i++) {
                            int q = (czp + j) * w + cxp + i;
                            sb.append(String.format(Locale.ROOT, " [%s top %d pred %d]", kind[q] == 0 ? "nat" : kind[q] == 1 ? "turf" : "oth", top[q] + 1,
                                    wk.naturalTop(minX + cxp + i, minZ + czp + j, gr)));
                        }
                        double px = minX + cxp, pz = minZ + czp, g = gr.at(px, pz), f = wk.surface(px, pz, g);
                        RoadWorks.Hit hh = wk.hit(px, pz, RoadWorks.REACH);
                        System.out.printf(Locale.ROOT, "  corner %.0f %.0f turf %.3f e %.3f g %.3f f %.3f v %s%s%n", px, pz, turf[c][corner], e, g, f,
                                hh == null ? "-" : String.format(Locale.ROOT, "%.2f", hh.v()), sb);
                    }
                    dist.merge((int) Math.round(e * 8), 1, Integer::sum);
                    n++;
                    sum += e;
                    abs += Math.abs(e);
                }
            }
        }
        System.out.printf(Locale.ROOT, "%s turf edge corners against the natural surface: %d, mean %.3f, mean |e| %.3f, eighths %s%n",
                label, n, sum / Math.max(1, n), abs / Math.max(1, n), dist);
    }
}
