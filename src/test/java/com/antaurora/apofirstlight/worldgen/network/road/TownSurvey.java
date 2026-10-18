package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.federal.FederalMap;
import com.antaurora.apofirstlight.worldgen.network.route.RouteTerrain;
import com.antaurora.apofirstlight.worldgen.terrain.v2.FederalTerrainPlan;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.InflaterInputStream;

/**
 * Town framework concept (docs/worldgen/federal_towns_v1.md): the planned terrain round given points of one cached
 * federal C2 terrain plan (the .bin a dev client wrote; read only), no world, no chunks. For each point a square grid of
 * the planned height (dm) and the water class (RouteTerrain: 0 dry, 1 river, 2 lake, 3 sea, 4 marsh), written as JSON
 * for the concept sheets (design/worldgen/federal_towns/source/data/).
 * <pre>
 *   TownSurvey terrain-plan.bin out-dir id,x,z,half,cell [id,x,z,half,cell ...]
 * </pre>
 */
public final class TownSurvey {
    private TownSurvey() {}

    public static void main(String[] args) throws Exception {
        String f = args[0];
        Path out = Path.of(args[1]);
        Files.createDirectories(out);
        String name = Path.of(f).getFileName().toString();
        long seed = Long.parseUnsignedLong(name.substring(name.lastIndexOf('_') + 1, name.length() - ".bin".length()));
        TerrainPlanSurface s;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(new FileInputStream(f)), 1 << 16))) {
            s = TerrainPlanSurface.read(in, seed, FederalTerrainPlan.VERSION, FederalTerrainPlan.GRID_N, FederalTerrainPlan.GRID_ORIGIN);
        }
        if (s == null) throw new IllegalStateException("stale terrain plan " + f);
        RouteTerrain t = new RouteTerrain(s, FederalMap.forSeed(seed));
        for (int a = 2; a < args.length; a++) {
            String[] p = args[a].split(",");
            String id = p[0];
            double x = Double.parseDouble(p[1]), z = Double.parseDouble(p[2]), half = Double.parseDouble(p[3]), cell = Double.parseDouble(p[4]);
            int n = (int) Math.round(2 * half / cell) + 1;
            StringBuilder h = new StringBuilder(), w = new StringBuilder();
            for (int j = 0; j < n; j++)
                for (int i = 0; i < n; i++) {
                    double px = x - half + i * cell, pz = z - half + j * cell;
                    if (h.length() > 0) { h.append(','); w.append(','); }
                    h.append(Math.round(t.height(px, pz) * 10));
                    w.append(t.water(px, pz));
                }
            String json = String.format(Locale.ROOT, "{\"id\":\"%s\",\"seed\":\"%s\",\"x\":%.1f,\"z\":%.1f,\"half\":%.1f,\"cell\":%.1f,\"n\":%d,\"source\":\"%s\",\"h\":[%s],\"w\":[%s]}",
                    id, Long.toUnsignedString(seed), x, z, half, cell, n, name, h, w);
            Files.writeString(out.resolve(id + ".json"), json, StandardCharsets.UTF_8);
            System.out.println("wrote " + id + " " + n + "x" + n);
        }
    }
}
