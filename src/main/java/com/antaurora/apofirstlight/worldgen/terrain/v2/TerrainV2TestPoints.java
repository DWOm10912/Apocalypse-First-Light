package com.antaurora.apofirstlight.worldgen.terrain.v2;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 2b acceptance points derived from a plan (2026-10-10; docs/worldgen/terrain_v2_phase2b_rivers_v1.md): the same
 * rules in the dev command (/afl dev terrain_v2 points) and the offline audit (RiverAudit), so the coordinates in the
 * doc are the ones the game prints for that seed. Pure Java, deterministic.
 */
public final class TerrainV2TestPoints {
    private TerrainV2TestPoints() {
    }

    public record Point(String name, int x, int z, String note) {
    }

    public static List<Point> rivers(TerrainPlanSurface s) {
        List<Point> out = new ArrayList<>();
        RiverNetwork r = s.rivers;
        if (r == null || r.lines() == 0) return out;
        // the main river: the main stem with the largest drainage at its mouth, half way along its land course
        int main = -1;
        float best = -1;
        for (int line = 0; line < r.lines(); line++) {
            if (r.lineParent(line) >= 0) continue;
            float a = r.vertexArea(r.lineEnd(line) - 1);
            if (a > best) { best = a; main = line; }
        }
        int a0 = r.lineStart(main), a1 = r.lineEnd(main);
        int mid = a0 + (a1 - a0) / 2;
        out.add(new Point("main_river", Math.round(r.vertexX(mid)), Math.round(r.vertexZ(mid)),
                String.format("width %.1f m, drainage %.1f km2, water top Y%d", r.vertexWidth(mid), r.vertexArea(mid) / 1e6, r.vertexLevel(mid))));
        // the confluence with the largest tributary
        int trib = -1;
        best = -1;
        for (int line = 0; line < r.lines(); line++) {
            if (r.lineParent(line) < 0) continue;
            float a = r.vertexArea(r.lineEnd(line) - 1);
            if (a > best) { best = a; trib = line; }
        }
        if (trib >= 0) {
            int e = r.lineEnd(trib) - 1;
            out.add(new Point("confluence", Math.round(r.vertexX(e)), Math.round(r.vertexZ(e)),
                    String.format("tributary %.1f km2 joins at water top Y%d", r.vertexArea(e) / 1e6, r.vertexLevel(e))));
        }
        // the main river's mouth: the first vertex in open water (estuary or sea)
        for (int i = a0; i < a1; i++) {
            int wc = s.waterClass(r.vertexX(i), r.vertexZ(i));
            if (wc == 1 || wc == 2) {
                out.add(new Point("river_mouth", Math.round(r.vertexX(i)), Math.round(r.vertexZ(i)),
                        wc == 2 ? "enters a drowned valley (estuary), sea level Y62" : "enters the sea, Y62"));
                break;
            }
        }
        // where the main river crosses a chunk border (x a multiple of 16) at full width, away from the ends
        for (int i = a0 + (a1 - a0) / 4; i < a1 - 1; i++) {
            float x0 = r.vertexX(i), x1 = r.vertexX(i + 1);
            if (Math.floorDiv((int) Math.floor(x0), 16) != Math.floorDiv((int) Math.floor(x1), 16) && r.vertexWidth(i) >= 5) {
                out.add(new Point("river_chunk_border", (int) Math.floor(Math.max(x0, x1)) & ~15, Math.round(r.vertexZ(i)),
                        String.format("the main river crosses the chunk border x = %d, width %.1f m", (int) Math.floor(Math.max(x0, x1)) & ~15, r.vertexWidth(i))));
                break;
            }
        }
        // the marsh cell with the most open water on the generated flat
        int bestCell = -1, bestCount = -1;
        int N = TerrainPlanSurface.N;
        for (int i = 0; i < N * N; i++) {
            if (s.water[i] != 3) continue;
            int c = i % N, row = i / N;
            int x0 = (int) (TerrainPlanSurface.ORIGIN + c * TerrainPlanSurface.CELL), z0 = (int) (TerrainPlanSurface.ORIGIN + row * TerrainPlanSurface.CELL);
            int count = 0;
            for (int k = 0; k < 16; k += 2) for (int j = 0; j < 16; j += 2)
                if (s.poolAt(x0 + j, z0 + k) && (int) Math.ceil(s.heightAt(x0 + j, z0 + k)) - 1 == RiverCarver.MARSH_TOP) count++;
            if (count > bestCount && count < 40) { bestCount = count; bestCell = i; }   // mixed water and marsh, not a lake
        }
        if (bestCell >= 0) {
            int x = (int) (TerrainPlanSurface.ORIGIN + (bestCell % N + 0.5) * TerrainPlanSurface.CELL);
            int z = (int) (TerrainPlanSurface.ORIGIN + (bestCell / N + 0.5) * TerrainPlanSurface.CELL);
            out.add(new Point("wetland", x, z, "tidal marsh flat Y63 with pools at Y62"));
        }
        return out;
    }
}
