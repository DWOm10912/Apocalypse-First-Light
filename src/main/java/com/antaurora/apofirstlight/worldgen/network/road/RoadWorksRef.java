package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;

/**
 * The terrain works one noise router applies (C3.2): created empty with the router's plan density functions
 * (RandomStateSeedMixin) and filled by the world lock when the world's terrain_works gate is on (RoadNetworkRuntime.bind),
 * before the first chunk. Empty: the planned ground unchanged (other dimensions, r1 worlds, worlds created without roads,
 * and the lock's density probe, which runs before the binding).
 */
public final class RoadWorksRef {
    private volatile RoadWorks works;

    public RoadWorks get() { return works; }

    public void set(RoadWorks works) { this.works = works; }

    /** The last column's rock (#rock) per worker thread: the carvers ask block by block down a column. */
    private static final ThreadLocal<Object[]> ROCK = ThreadLocal.withInitial(() -> new Object[3]);

    /**
     * The rock kept against caves and carvers at block column (x, z): {lo, hi}. Round a tunnel's bores and under its
     * approaches (TunnelWorks#rock), under a town's footprint (TownWorks#rock), and elsewhere in the works (a road's
     * formation, its cut and fill slopes, a town's slopes) RoadWorks#ROCK under the finished surface up (the density caps
     * it at its surface; 2026-10-19: a cut in the foothills had cave mouths and springs in its face). Null without works or
     * off them. plan: the works' ground (the plan's height there).
     */
    public double[] rock(int x, int z, TerrainPlanSurface plan) {
        RoadWorks w = works;
        if (w == null) return null;
        Object[] c = ROCK.get();
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        if (c[0] != w || c[1] == null || (long) c[1] != key) {
            c[0] = w;
            c[1] = key;
            double[] r = TunnelWorks.rock(w, x, z);
            // a town's footprint: ROCK under its finished surface (Town Framework V1, 2026-10-17)
            if (r == null && !w.towns().isEmpty()) r = w.towns().rock(x, z);
            if (r == null && plan != null) {
                double f = w.densitySurface(x + 0.5, z + 0.5, plan.heightAt(x, z));
                if (!Double.isNaN(f)) r = new double[]{f - RoadWorks.ROCK, Double.POSITIVE_INFINITY};
            }
            c[2] = r;
        }
        return (double[]) c[2];
    }

    /**
     * Whether a spring (SpringFeatureRoadMixin) at block (x, y, z) would open in the works or run onto them: the works
     * reach its column or one SPRING_CLEAR away and it lies above their rock there (deep cave springs stay).
     */
    public boolean springBlocked(int x, int y, int z, TerrainPlanSurface plan) {
        RoadWorks w = works;
        if (w == null || plan == null) return false;
        int d = SPRING_CLEAR;
        int[][] at = {{0, 0}, {d, 0}, {-d, 0}, {0, d}, {0, -d}, {d, d}, {d, -d}, {-d, d}, {-d, -d}};
        for (int[] o : at) {
            int px = x + o[0], pz = z + o[1];
            double f = w.densitySurface(px + 0.5, pz + 0.5, plan.heightAt(px, pz));
            if (!Double.isNaN(f) && y >= f - RoadWorks.ROCK) return true;
        }
        return false;
    }

    /** How far round a spring the works are looked for (#springBlocked), blocks. */
    public static final int SPRING_CLEAR = 3;

    /** The planned height at block column (x, z) with the works applied (g: the plan's height there). */
    public double height(int x, int z, double g) {
        RoadWorks w = works;
        if (w == null) return g;
        double f = w.densitySurface(x + 0.5, z + 0.5, g);
        return Double.isNaN(f) ? g : f;
    }
}
