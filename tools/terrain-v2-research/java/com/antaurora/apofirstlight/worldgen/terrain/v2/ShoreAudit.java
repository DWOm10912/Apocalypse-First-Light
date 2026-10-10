package com.antaurora.apofirstlight.worldgen.terrain.v2;

/**
 * Whole-plan shore audit (2026-10-10): every block column of every plan cell within 64 m of the unified water,
 * simulated like ShoreRender (top = ceil(h) - 1, the sea fill, RiverCarver), counting water faces that would stand
 * against open air (a dry neighbour whose ground is under the water's top), on the open coast, estuaries and marsh.
 * <pre>java ShoreAudit SEED</pre>
 */
public final class ShoreAudit {
    public static void main(String[] args) {
        long seed = Long.parseLong(args[0]);
        TerrainPlanSurface s = new TerrainPlanV2(seed).toSurface();
        int N = TerrainPlanSurface.N;
        RiverNetwork.Column col = new RiverNetwork.Column();
        RiverCarver.Edit e = new RiverCarver.Edit();
        long columns = 0, waterCols = 0, exposed = 0;
        int[] byClass = new int[4];
        StringBuilder ex = new StringBuilder();
        for (int r = 1; r < N - 1; r++) for (int c = 1; c < N - 1; c++) {
            int i = r * N + c;
            if (Math.abs(s.shore[i]) > 64) continue;
            int x0 = (int) (TerrainPlanSurface.ORIGIN + c * TerrainPlanSurface.CELL), z0 = (int) (TerrainPlanSurface.ORIGIN + r * TerrainPlanSurface.CELL);
            int[][] g = new int[18][18], wt = new int[18][18];
            for (int j = 0; j < 18; j++) for (int k = 0; k < 18; k++) {
                int x = x0 - 1 + k, z = z0 - 1 + j;
                double h = s.heightAt(x, z);
                int top = (int) Math.ceil(h) - 1, water = Integer.MIN_VALUE;
                if (top < 62 && s.seaFloodAt(x, z, h)) water = 62;
                RiverCarver.plan(s, x, z, top, col, e);
                if (e.kind != RiverCarver.NONE) { top = e.ground; water = e.water; }
                g[j][k] = top;
                wt[j][k] = water;
            }
            for (int j = 1; j < 17; j++) for (int k = 1; k < 17; k++) {
                columns++;
                if (wt[j][k] == Integer.MIN_VALUE || wt[j][k] <= g[j][k]) continue;
                waterCols++;
                int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int[] d : nb) {
                    int jj = j + d[1], kk = k + d[0];
                    boolean wet = wt[jj][kk] != Integer.MIN_VALUE && wt[jj][kk] > g[jj][kk];
                    int open = Math.max(g[jj][kk], wet ? wt[jj][kk] : Integer.MIN_VALUE) + 1;
                    if (open <= wt[j][k] && !(wet && wt[jj][kk] < wt[j][k])) {
                        exposed++;
                        int x = x0 - 1 + kk, z = z0 - 1 + jj;
                        byClass[s.waterClass(x, z)]++;
                        if (ex.length() < 1200) ex.append(String.format(" (%d %d g%d class %d)", x, z, g[jj][kk], s.waterClass(x, z)));
                    }
                }
            }
        }
        System.out.printf("shore columns %d, water columns %d, exposed water faces %d (neighbour class land %d, sea %d, estuary %d, marsh %d)%s%n",
                columns, waterCols, exposed, byClass[0], byClass[1], byClass[2], byClass[3], exposed > 0 ? ":" + ex : "");
    }
}
