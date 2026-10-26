package com.antaurora.apofirstlight.worldgen.river;

import com.antaurora.apofirstlight.terrainsmooth.SmoothRules;

import java.util.Locale;

import static com.antaurora.apofirstlight.worldgen.river.RiverSample.*;

/**
 * River / lake V1 R0 (docs/worldgen/river_lake_v1_design.md 7, 11): wet ground on RiverSample, offline, with the game's
 * own rules (SmoothRules over RiverSample.Cells, mode B):
 * <ul>
 *   <li>no spill: no wet cell or water block has air beside it at its level (vanilla water would flow out);</li>
 *   <li>no air under water: no dry top dips below the water surface (the gap would show as a pocket in the water);</li>
 *   <li>shores slope: dry tops beside water show no side band of half a block or more above the water, except at the cut
 *   bank (pool, outer side), which is meant to stand;</li>
 *   <li>wet beds take part: every wet top is Terrain Smooth's natural surface and sloped or level with its neighbours;</li>
 *   <li>the same sample without wet ground (an old world's river): beds are no natural surface, as before R0;</li>
 *   <li>the drawn depths: riffle and pool cross-sections on the centreline, against 4.2 (riffle 0.15-0.4 m, pool 1-2 m).</li>
 * </ul>
 *   ./gradlew wetGroundCheck
 */
public final class WetGroundCheck {
    private WetGroundCheck() {}

    private static int failures;

    private static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) failures++;
    }

    /** neighbour offsets of the N, S, W, E side bands and their corner indices (SmoothRules' SIDE) */
    private static final int[][] SIDE = {{0, -1, 1, 0}, {0, 1, 2, 3}, {-1, 0, 0, 2}, {1, 0, 3, 1}};

    /** Drawn top height above y at the column centre (the cube's 1 where not sloped). */
    private static double drawn(SmoothRules.Cells c, int x, int y, int z) {
        long g = SmoothRules.geometry(c, SmoothRules.MODE_B, x, y, z);
        return g < 0 ? 1 : SmoothRules.surface(SmoothRules.corners(g), 0.5, 0.5);
    }

    public static void main(String[] args) {
        RiverSample s = RiverSample.make();
        RiverSample.Cells c = s.cells();
        int wet = 0, water = 0, spill = 0, pocket = 0, bands = 0, cutBands = 0, endBands = 0, notSurface = 0, cubes = 0;
        for (int z = 0; z < SIZE_Z; z++)
            for (int x = 0; x < SIZE_X; x++) {
                int i = z * SIZE_X + x, t = s.top[i];
                if (s.wet[i]) wet++;
                // spill: the wet top and every water block beside air at its level
                for (int y = t - 1; y <= 0; y++) {
                    int k = c.cell(x, y, z);
                    if (k != SmoothRules.WET && k != SmoothRules.FLUID) continue;
                    if (k == SmoothRules.FLUID) water++;
                    for (int[] d : SIDE) if (c.cell(x + d[0], y, z + d[1]) == SmoothRules.AIR) spill++;
                }
                if (s.wet[i]) {
                    if (c.surface(x, z) != t - 1) notSurface++;
                    else if (SmoothRules.geometry(c, SmoothRules.MODE_B, x, t - 1, z) < 0 && t - 1 + 1 < SURFACE) cubes++;
                    continue;
                }
                // a dry top: never below the water, and its bands toward water low
                if (t >= 1 && RiverSample.below(c, x, t - 1, z, SURFACE)) pocket++;
                long g = SmoothRules.geometry(c, SmoothRules.MODE_B, x, t - 1, z);
                float[] k = g < 0 ? new float[]{1, 1, 1, 1} : SmoothRules.corners(g);
                for (int sd = 0; sd < 4; sd++) {
                    int nx = x + SIDE[sd][0], nz = z + SIDE[sd][1];
                    if (nx < 0 || nz < 0 || nx >= SIZE_X || nz >= SIZE_Z || !s.wet[nz * SIZE_X + nx]) continue;
                    double floor = t - 1;
                    // the band's bottom: the neighbour's surface at the two ends (bits 16-47), or the water when the top is a cube
                    double a = g < 0 ? SURFACE - floor : (g >>> (16 + 8 * sd) & 15) / 4.0, b = g < 0 ? SURFACE - floor : (g >>> (20 + 8 * sd) & 15) / 4.0;
                    double above = Math.max(k[SIDE[sd][2]] - Math.max(a, SURFACE - floor), k[SIDE[sd][3]] - Math.max(b, SURFACE - floor));
                    if (above < 0.5) continue;
                    double pool = s.pool[i];
                    // the cut bank: the outer side wherever the pool section takes part (poolSection's -9 beyond q 3.6);
                    // the basin's ends: the river cut off by the plain margin
                    double[] r = RiverSample.river(x + 0.5, z + 0.5);
                    if (nz < 3 || nz >= SIZE_Z - 3 || nx < 3 || nx >= SIZE_X - 3) endBands++;
                    else if (r[2] > 0 && r[2] < 8 && r[1] > 0.02) cutBands++;
                    else if (bands++ < 16)
                        System.out.printf(Locale.ROOT, "     band at x %d z %d side %d: top %d, neighbour %d, %.2f above the water, pool %.2f, depth %.2f%n",
                                x, z, sd, t, s.top[nz * SIZE_X + nx], above, pool, s.depth[i]);
                }
            }
        System.out.printf(Locale.ROOT, "sample %d x %d: %d wet tops, %d water blocks%n", SIZE_X, SIZE_Z, wet, water);
        check(spill == 0, "no wet cell or water block beside air at its level (" + spill + ")");
        check(pocket == 0, "no dry top below the water surface (" + pocket + ")");
        check(notSurface == 0, "every wet top is its column's natural surface (" + notSurface + " not)");
        System.out.printf(Locale.ROOT, "     wet tops under water left as cubes (a neighbour pins them): %d%n", cubes);
        check(bands == 0, "no side band of half a block or more above the water beside it, off the cut bank (" + bands + ")");
        System.out.printf(Locale.ROOT, "     cut bank faces above the water (meant): %d; the basin's end walls: %d%n", cutBands, endBands);

        // an old world: the same blocks without wet ground; water over dry ground is no natural surface
        RiverSample dry = RiverSample.make();
        java.util.Arrays.fill(dry.wet, false);
        RiverSample.Cells dc = dry.cells();
        int beds = 0, bedSurfaces = 0;
        for (int z = 0; z < SIZE_Z; z++)
            for (int x = 0; x < SIZE_X; x++)
                if (dry.top[z * SIZE_X + x] <= 0) { beds++; if (dc.surface(x, z) != SmoothRules.NO_SURFACE) bedSurfaces++; }
        check(bedSurfaces == 0, "without wet ground no bed under water is a natural surface (" + bedSurfaces + " of " + beds + ")");

        // the drawn cross-sections on the centreline rows: depth below the surface across the channel
        for (int[] row : new int[][]{{44, 0}, {22, 1}, {66, 1}}) {
            int z = row[0];
            double max = 0, sum = 0;
            int n = 0;
            StringBuilder line = new StringBuilder();
            for (int x = 2; x < 36; x++) {
                int t = s.top[z * SIZE_X + x];
                double d = SURFACE - (t - 1 + drawn(c, x, t - 1, z));
                line.append(d > 0 ? String.format(Locale.ROOT, "%5.2f", d) : "    .");
                if (d > 0) { max = Math.max(max, d); sum += d; n++; }
            }
            System.out.printf(Locale.ROOT, "     %s z %d: water %d m wide, mean %.2f m, max %.2f m%n     %s%n",
                    row[1] == 0 ? "riffle" : "pool  ", z, n, n == 0 ? 0 : sum / n, max, line);
            if (row[1] == 0) check(n > 0 && max <= 0.65 && sum / n >= 0.15 && sum / n <= 0.45, "riffle mean 0.15-0.45 m, max <= 0.65 m");
            else check(max >= 1.0 && max <= 2.0, "pool max 1-2 m");
        }
        System.out.println(failures == 0 ? "wetGroundCheck: all passed" : "wetGroundCheck: " + failures + " failed");
        if (failures != 0) System.exit(1);
    }
}
