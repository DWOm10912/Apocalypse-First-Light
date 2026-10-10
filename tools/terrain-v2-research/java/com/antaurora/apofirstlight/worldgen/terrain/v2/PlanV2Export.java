package com.antaurora.apofirstlight.worldgen.terrain.v2;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Offline export of the Terrain V2 research plan: the national 16 m rasters and sample windows for the comparison
 * with the USGS reference tiles (the same Python metrics measure both). Little-endian raw rasters + JSON headers.
 *   java -cp CLASSES com.antaurora.apofirstlight.worldgen.terrain.v2.PlanV2Export SEED OUT_DIR
 */
public final class PlanV2Export {
    public static void main(String[] args) throws IOException {
        long seed = Long.parseLong(args[0]);
        Path out = Path.of(args[1]);
        Files.createDirectories(out);
        long t0 = System.nanoTime();
        TerrainPlanV2 p = new TerrainPlanV2(seed);
        long t1 = System.nanoTime();
        int n = TerrainPlanV2.N * TerrainPlanV2.N;
        writeF(out.resolve("h.f32"), p.h);
        writeF(out.resolve("h0.f32"), p.h0);
        writeF(out.resolve("area.f32"), p.area);
        writeB(out.resolve("water.u8"), p.water);
        writeB(out.resolve("strahler.u8"), p.strahler);
        byte[] prov = new byte[n * 4];
        byte[] res = new byte[n];
        byte[] trunk = new byte[n];
        for (int i = 0; i < n; i++) {
            prov[i * 4] = (byte) Math.round(p.wPlain[i] * 255);
            prov[i * 4 + 1] = (byte) Math.round(p.wCoastal[i] * 255);
            prov[i * 4 + 2] = (byte) Math.round(p.wFoot[i] * 255);
            prov[i * 4 + 3] = (byte) Math.round(p.wBelt[i] * 255);
            res[i] = (byte) Math.round(p.resist[i] * 255);
            trunk[i] = (byte) (p.trunk[i] ? 1 : 0);
        }
        writeB(out.resolve("provinces.u8x4"), prov);
        writeB(out.resolve("resist.u8"), res);
        writeB(out.resolve("trunk.u8"), trunk);
        ByteBuffer rb = ByteBuffer.allocate(n * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int v : p.recv) rb.putInt(v);
        Files.write(out.resolve("recv.i32"), rb.array());
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("version", TerrainPlanV2.VERSION);
        meta.put("seed", seed);
        meta.put("grid", Map.of("n", TerrainPlanV2.N, "cell", TerrainPlanV2.CELL, "origin", TerrainPlanV2.ORIGIN));
        meta.put("sea_level_y", TerrainPlanV2.SEA);
        meta.put("belt_side", p.beltSide);
        meta.put("seconds_plan", (t1 - t0) / 1e9);
        meta.putAll(p.report);
        // sample windows: one per province, 6 km at 10 m, with 2 km at 2 m and 1 km at 1 m detail windows inside
        List<Map<String, Object>> wins = new ArrayList<>();
        wins.add(window(p, out, "afl_plain", pick(p, 0), detail(p, 0, pick(p, 0)), 6000));
        wins.add(window(p, out, "afl_belt", pick(p, 3), detail(p, 3, pick(p, 3)), 6000));
        wins.add(window(p, out, "afl_coastal", pick(p, 1), detail(p, 1, pick(p, 1)), 3000));
        wins.add(window(p, out, "afl_foothill", pick(p, 2), detail(p, 2, pick(p, 2)), 6000));
        meta.put("windows", wins);
        meta.put("seconds_total", (System.nanoTime() - t0) / 1e9);
        Files.writeString(out.resolve("plan.json"), json(meta), StandardCharsets.UTF_8);
        System.out.println(json(meta));
    }

    /** Window centre: the 512-block grid point whose 3 km neighbourhood has the most of the province (0 plain,
     *  1 coastal, 2 foothill, 3 belt), on the mainland, with the window kept inside the plan grid. */
    static double[] pick(TerrainPlanV2 p, int prov) {
        double best = -1, bx = 0, bz = 0;
        for (double x = -8192; x <= 8192; x += 512) for (double z = -8192; z <= 8192; z += 512) {
            double s = 0;
            int m = 0;
            double rad = prov == 1 ? 1000 : 1500;
            for (double dx = -rad; dx <= rad; dx += 250) for (double dz = -rad; dz <= rad; dz += 250) {
                int i = cell(x + dx, z + dz);
                m++;
                if (p.water[i] != 0 || p.landmass[i] != 0) continue;
                s += switch (prov) { case 0 -> p.wPlain[i]; case 1 -> p.wCoastal[i] - 2 * p.wBelt[i]; case 2 -> p.wFoot[i]; default -> p.wBelt[i]; };
            }
            s /= m;
            if (prov == 0) {   // plain: centred on a plain valley (the largest plain river in the neighbourhood)
                double big = 0;
                for (double dx = -1000; dx <= 1000; dx += 64) for (double dz = -1000; dz <= 1000; dz += 64) {
                    int i = cell(x + dx, z + dz);
                    if (p.water[i] == 0 && p.wPlain[i] > 0.7) big = Math.max(big, p.area[i]);
                }
                s += 0.12 * Math.log10(Math.max(1e6, big) / 1e6);
            }
            if (prov == 1) {   // coastal: the window with the most estuary and marsh, little open sea
                int est = 0, sea = 0;
                for (double dx = -1500; dx <= 1500; dx += 125) for (double dz = -1500; dz <= 1500; dz += 125) {
                    byte w = p.water[cell(x + dx, z + dz)];
                    if (w >= 2) est++;
                    if (w == 1) sea++;
                }
                s += est / 60.0 - sea / 400.0;
            }
            if (s > best) { best = s; bx = x; bz = z; }
        }
        return new double[]{bx, bz};
    }

    /** Detail centre inside the window: plain - beside a 1-10 km2 creek; belt - a ridge flank; coastal - a marsh edge;
     *  foothill - the steepest 16 m cell within 1.5 km. */
    static double[] detail(TerrainPlanV2 p, int prov, double[] c) {
        double best = -1e9, bx = c[0], bz = c[1];
        for (double x = c[0] - 1500; x <= c[0] + 1500; x += 32) for (double z = c[1] - 1500; z <= c[1] + 1500; z += 32) {
            int i = cell(x, z);
            if (p.water[i] == 1 || p.water[i] == 2) continue;
            double s;
            if (prov == 0) s = p.wPlain[i] > 0.7 && p.area[i] >= 2e6 ? Math.log10(p.area[i]) * 10 - Math.hypot(x - c[0], z - c[1]) / 1000 : -1e9;
            else if (prov == 3) s = p.resist[i] > 0.25 && p.resist[i] < 0.6 ? p.slopeAt(i) * 10 - Math.hypot(x - c[0], z - c[1]) / 1000 : -1e9;
            else if (prov == 1) s = p.water[i] == 3 ? 5 - Math.hypot(x - c[0], z - c[1]) / 1000 : -1e9;
            else s = p.slopeAt(i);
            if (s > best) { best = s; bx = x; bz = z; }
        }
        return new double[]{bx, bz};
    }

    static int cell(double x, double z) {
        int c = TerrainPlanV2.clampI((int) Math.floor((x - TerrainPlanV2.ORIGIN) / TerrainPlanV2.CELL));
        int r = TerrainPlanV2.clampI((int) Math.floor((z - TerrainPlanV2.ORIGIN) / TerrainPlanV2.CELL));
        return TerrainPlanV2.idx(c, r);
    }

    static Map<String, Object> window(TerrainPlanV2 p, Path out, String id, double[] c, double[] d, int size) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("centre", c);
        m.put("detail_centre", d);
        sample(p, out, id + "_z", id + "_water", c, size, 10, m, "z");
        sample(p, out, id + "_fine2", null, d, 2000, 2, m, "fine2");
        sample(p, out, id + "_fine1", null, d, 1000, 1, m, "fine1");
        Files.writeString(out.resolve(id + ".json"), json(m), StandardCharsets.UTF_8);
        return m;
    }

    static void sample(TerrainPlanV2 p, Path out, String file, String waterFile, double[] c, int size, double cell,
                       Map<String, Object> m, String key) throws IOException {
        int k = (int) Math.round(size / cell);
        double x0 = c[0] - size / 2.0, z0 = c[1] - size / 2.0;
        float[] z = new float[k * k];
        byte[] w = new byte[k * k];
        for (int r = 0; r < k; r++) for (int col = 0; col < k; col++) {
            double x = x0 + (col + 0.5) * cell, zz = z0 + (r + 0.5) * cell;
            z[r * k + col] = (float) p.heightAt(x, zz);
            w[r * k + col] = (byte) (p.waterAt(x, zz) ? 1 : 0);
        }
        writeF(out.resolve(file + ".f32"), z);
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("file", file + ".f32");
        e.put("cell", cell);
        e.put("n", k);
        e.put("x0", x0);
        e.put("z0", z0);
        if (waterFile != null) { writeB(out.resolve(waterFile + ".u8"), w); e.put("water", waterFile + ".u8"); }
        m.put(key, e);
    }

    static void writeF(Path f, float[] a) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(a.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : a) b.putFloat(v);
        Files.write(f, b.array());
    }

    static void writeB(Path f, byte[] a) throws IOException { Files.write(f, a); }

    static String json(Object o) {
        if (o == null) return "null";
        if (o instanceof String s) return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
        if (o instanceof Number || o instanceof Boolean) {
            if (o instanceof Double dd && (dd.isNaN() || dd.isInfinite())) return "null";
            return o.toString();
        }
        if (o instanceof double[] a) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < a.length; i++) sb.append(i > 0 ? ", " : "").append(a[i]);
            return sb.append(']').toString();
        }
        if (o instanceof Map<?, ?> mp) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : mp.entrySet()) {
                sb.append(first ? "" : ", ").append(json(String.valueOf(e.getKey()))).append(": ").append(json(e.getValue()));
                first = false;
            }
            return sb.append('}').toString();
        }
        if (o instanceof List<?> l) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < l.size(); i++) sb.append(i > 0 ? ", " : "").append(json(l.get(i)));
            return sb.append(']').toString();
        }
        return json(o.toString());
    }
}
