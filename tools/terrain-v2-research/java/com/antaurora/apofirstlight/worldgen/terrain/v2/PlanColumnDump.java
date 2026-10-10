package com.antaurora.apofirstlight.worldgen.terrain.v2;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.InflaterInputStream;

/**
 * Offline column dump of a cached Terrain V2 plan surface (afl_cache/terrain_plan/*.bin) for the save analysis in
 * tools/terrain-v2-research/save_audit.py (2026-10-10).
 * <pre>java PlanColumnDump CACHE.bin SEED CHUNKS.txt OUT.bin</pre>
 * CHUNKS.txt holds "cx cz" lines; per chunk, per column (index lz * 16 + lx): float heightAt, float baseHeightAt,
 * byte stable, byte water, byte shore (all big-endian), at the column centre x + 0.5, z + 0.5 like the game's checks.
 */
public final class PlanColumnDump {
    public static void main(String[] args) throws Exception {
        long seed = Long.parseLong(args[1]);
        TerrainPlanSurface s;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(new FileInputStream(args[0])), 1 << 16))) {
            s = TerrainPlanSurface.read(in, seed, TerrainPlanV2.VERSION);
        }
        if (s == null) throw new IllegalStateException("cache does not match seed / version / format");
        List<String> lines = Files.readAllLines(Path.of(args[2]));
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(args[3]), 1 << 16))) {
            for (String line : lines) {
                if (line.isBlank()) continue;
                String[] p = line.trim().split("\\s+");
                int cx = Integer.parseInt(p[0]), cz = Integer.parseInt(p[1]);
                for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
                    int x = cx * 16 + lx, z = cz * 16 + lz;
                    out.writeFloat((float) s.heightAt(x + 0.5, z + 0.5));
                    out.writeFloat((float) s.baseHeightAt(x + 0.5, z + 0.5));
                    out.writeByte(s.stableDepth(x, z));
                    out.writeByte(s.waterClass(x, z));
                    out.writeByte(s.shoreDistance(x, z));
                }
            }
        }
    }
}
